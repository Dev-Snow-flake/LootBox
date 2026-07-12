package dev.snowflake.lootbox.history;

import dev.snowflake.lootbox.pool.Tier;
import java.time.Instant;
import java.util.UUID;

public record HistoryRecord(
        long historyId,
        UUID playerUuid,
        String boxId,
        String poolId,
        String resultName,
        Tier tier,
        int amount,
        DeliveryMethod deliveredVia,
        String status,
        UUID boxSerial,
        Instant createdAt
) {
}

