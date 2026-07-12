package dev.snowflake.lootbox.storage;

import dev.snowflake.lootbox.history.DeliveryMethod;
import dev.snowflake.lootbox.history.HistoryRecord;
import dev.snowflake.lootbox.history.StatsSnapshot;
import dev.snowflake.lootbox.pool.PoolEntry;
import dev.snowflake.lootbox.pool.RewardItem;
import dev.snowflake.lootbox.pool.Tier;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class JdbcLootBoxRepository implements LootBoxRepository {
    private final ConnectionProvider connections;

    public JdbcLootBoxRepository(ConnectionProvider connections) {
        this.connections = Objects.requireNonNull(connections, "connections");
    }

    @Override
    public void issueBox(UUID serial, String boxId, UUID issuedTo) throws SQLException {
        try (Connection connection = connections.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO lootbox_items(box_serial, box_id, status, issued_to)
                     VALUES (?, ?, 'ISSUED', ?)
                     ON CONFLICT (box_serial) DO NOTHING
                     """)) {
            statement.setObject(1, serial);
            statement.setString(2, boxId);
            statement.setObject(3, issuedTo);
            statement.executeUpdate();
        }
    }

    @Override
    public Optional<IssuedBox> findIssuedBox(UUID serial) throws SQLException {
        try (Connection connection = connections.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT box_serial, box_id, status, issued_to
                     FROM lootbox_items
                     WHERE box_serial = ?
                     """)) {
            statement.setObject(1, serial);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new IssuedBox(
                        rs.getObject("box_serial", UUID.class),
                        rs.getString("box_id"),
                        rs.getString("status"),
                        rs.getObject("issued_to", UUID.class)));
            }
        }
    }

    @Override
    public boolean reserveForOpening(UUID serial, UUID opener) throws SQLException {
        try (Connection connection = connections.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE lootbox_items
                     SET status = 'OPENING', opened_by = ?, opened_at = NOW()
                     WHERE box_serial = ? AND status = 'ISSUED'
                     """)) {
            statement.setObject(1, opener);
            statement.setObject(2, serial);
            return statement.executeUpdate() == 1;
        }
    }

    @Override
    public void releaseReservation(UUID serial) throws SQLException {
        try (Connection connection = connections.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE lootbox_items
                     SET status = 'ISSUED', opened_by = NULL, opened_at = NULL
                     WHERE box_serial = ? AND status = 'OPENING'
                     """)) {
            statement.setObject(1, serial);
            statement.executeUpdate();
        }
    }

    @Override
    public void markOpened(UUID serial, UUID opener) throws SQLException {
        try (Connection connection = connections.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE lootbox_items
                     SET status = 'OPENED', opened_by = ?, opened_at = NOW()
                     WHERE box_serial = ? AND status = 'OPENING'
                     """)) {
            statement.setObject(1, opener);
            statement.setObject(2, serial);
            statement.executeUpdate();
        }
    }

    @Override
    public long insertHistoryPending(
            UUID playerUuid,
            String boxId,
            String poolId,
            PoolEntry result,
            int amount,
            DeliveryMethod delivery,
            UUID boxSerial
    ) throws SQLException {
        try (Connection connection = connections.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO lootbox_history(
                         uuid, box_id, pool_id, result_item_json, result_tier,
                         result_amount, delivered_via, status, box_serial
                     )
                     VALUES (?, ?, ?, CAST(? AS jsonb), ?, ?, ?, 'PENDING', ?)
                     RETURNING history_id
                     """)) {
            statement.setObject(1, playerUuid);
            statement.setString(2, boxId);
            statement.setString(3, poolId);
            statement.setString(4, result.reward().toJson());
            statement.setString(5, result.tier().name());
            statement.setInt(6, amount);
            statement.setString(7, delivery.name());
            statement.setObject(8, boxSerial);
            try (ResultSet keys = statement.executeQuery()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("lootbox_history insert returned no generated key");
    }

    @Override
    public void markHistoryDelivered(long historyId) throws SQLException {
        updateHistoryStatus(historyId, "DELIVERED");
    }

    @Override
    public void markHistoryFailed(long historyId) throws SQLException {
        updateHistoryStatus(historyId, "FAILED");
    }

    @Override
    public List<PoolEntry> loadAdminPoolEntries() throws SQLException {
        try (Connection connection = connections.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT entry_id, pool_id, item_json::text AS item_json, weight, tier,
                            amount_min, amount_max, enabled
                     FROM lootbox_pools
                     ORDER BY pool_id ASC, entry_id ASC
                     """);
             ResultSet rs = statement.executeQuery()) {
            List<PoolEntry> entries = new ArrayList<>();
            while (rs.next()) {
                entries.add(mapPoolEntry(rs));
            }
            return entries;
        }
    }

    @Override
    public void replaceAdminPool(String poolId, List<PoolEntry> entries) throws SQLException {
        Objects.requireNonNull(poolId, "poolId");
        Objects.requireNonNull(entries, "entries");
        try (Connection connection = connections.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM lootbox_pools WHERE pool_id = ?");
                 PreparedStatement insert = connection.prepareStatement("""
                         INSERT INTO lootbox_pools(
                             pool_id, item_json, weight, tier, amount_min, amount_max, enabled, source
                         )
                         VALUES (?, CAST(? AS jsonb), ?, ?, ?, ?, ?, 'ADMIN')
                         """)) {
                delete.setString(1, poolId);
                delete.executeUpdate();
                for (PoolEntry entry : entries) {
                    insert.setString(1, poolId);
                    insert.setString(2, entry.reward().toJson());
                    insert.setInt(3, entry.weight());
                    insert.setString(4, entry.tier().name());
                    insert.setInt(5, entry.amountMin());
                    insert.setInt(6, entry.amountMax());
                    insert.setBoolean(7, entry.enabled());
                    insert.addBatch();
                }
                insert.executeBatch();
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    @Override
    public List<HistoryRecord> findHistory(UUID playerUuid, int limit, int offset) throws SQLException {
        try (Connection connection = connections.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT history_id, uuid, box_id, pool_id, result_item_json::text AS item_json,
                            result_tier, result_amount, delivered_via, status, box_serial, created_at
                     FROM lootbox_history
                     WHERE uuid = ?
                     ORDER BY created_at DESC
                     LIMIT ? OFFSET ?
                     """)) {
            statement.setObject(1, playerUuid);
            statement.setInt(2, Math.max(1, Math.min(100, limit)));
            statement.setInt(3, Math.max(0, offset));
            try (ResultSet rs = statement.executeQuery()) {
                List<HistoryRecord> records = new ArrayList<>();
                while (rs.next()) {
                    RewardItem item = RewardItem.fromJson(rs.getString("item_json"));
                    records.add(new HistoryRecord(
                            rs.getLong("history_id"),
                            rs.getObject("uuid", UUID.class),
                            rs.getString("box_id"),
                            rs.getString("pool_id"),
                            displayName(item),
                            Tier.valueOf(rs.getString("result_tier")),
                            rs.getInt("result_amount"),
                            DeliveryMethod.valueOf(rs.getString("delivered_via")),
                            rs.getString("status"),
                            rs.getObject("box_serial", UUID.class),
                            instant(rs, "created_at")));
                }
                return records;
            }
        }
    }

    @Override
    public StatsSnapshot loadStatsSnapshot() throws SQLException {
        try (Connection connection = connections.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT
                         COUNT(*) FILTER (WHERE status = 'DELIVERED') AS opens_total,
                         COUNT(*) FILTER (
                             WHERE status = 'DELIVERED'
                             AND created_at >= date_trunc('day', NOW())
                         ) AS opens_today,
                         COUNT(*) FILTER (
                             WHERE status = 'DELIVERED'
                             AND result_tier = 'MYTHIC'
                         ) AS mythic_count
                     FROM lootbox_history
                     """);
             ResultSet rs = statement.executeQuery()) {
            if (rs.next()) {
                return new StatsSnapshot(
                        rs.getLong("opens_total"),
                        rs.getLong("opens_today"),
                        rs.getLong("mythic_count"));
            }
        }
        return StatsSnapshot.EMPTY;
    }

    private void updateHistoryStatus(long historyId, String status) throws SQLException {
        try (Connection connection = connections.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE lootbox_history
                     SET status = ?
                     WHERE history_id = ?
                     """)) {
            statement.setString(1, status);
            statement.setLong(2, historyId);
            statement.executeUpdate();
        }
    }

    private static PoolEntry mapPoolEntry(ResultSet rs) throws SQLException {
        return new PoolEntry(
                rs.getLong("entry_id"),
                rs.getString("pool_id"),
                RewardItem.fromJson(rs.getString("item_json")),
                rs.getInt("weight"),
                Tier.valueOf(rs.getString("tier")),
                rs.getInt("amount_min"),
                rs.getInt("amount_max"),
                rs.getBoolean("enabled"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? Instant.EPOCH : value.toInstant();
    }

    private static String displayName(RewardItem item) {
        if (item.displayName() != null && !item.displayName().isBlank()) {
            return item.displayName();
        }
        return item.material();
    }
}

