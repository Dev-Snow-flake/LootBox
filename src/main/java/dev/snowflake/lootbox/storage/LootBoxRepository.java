package dev.snowflake.lootbox.storage;

import dev.snowflake.lootbox.history.DeliveryMethod;
import dev.snowflake.lootbox.history.HistoryRecord;
import dev.snowflake.lootbox.history.StatsSnapshot;
import dev.snowflake.lootbox.pool.PoolEntry;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LootBoxRepository {
    void issueBox(UUID serial, String boxId, UUID issuedTo) throws SQLException;

    Optional<IssuedBox> findIssuedBox(UUID serial) throws SQLException;

    boolean reserveForOpening(UUID serial, UUID opener) throws SQLException;

    void releaseReservation(UUID serial) throws SQLException;

    void markOpened(UUID serial, UUID opener) throws SQLException;

    long insertHistoryPending(
            UUID playerUuid,
            String boxId,
            String poolId,
            PoolEntry result,
            int amount,
            DeliveryMethod delivery,
            UUID boxSerial
    ) throws SQLException;

    void markHistoryDelivered(long historyId) throws SQLException;

    void markHistoryFailed(long historyId) throws SQLException;

    List<PoolEntry> loadAdminPoolEntries() throws SQLException;

    void replaceAdminPool(String poolId, List<PoolEntry> entries) throws SQLException;

    List<HistoryRecord> findHistory(UUID playerUuid, int limit, int offset) throws SQLException;

    StatsSnapshot loadStatsSnapshot() throws SQLException;

    record IssuedBox(UUID serial, String boxId, String status, UUID issuedTo) {
    }
}

