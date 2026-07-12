package dev.snowflake.lootbox.config;

import dev.snowflake.lootbox.definition.LootBoxDefinition;
import dev.snowflake.lootbox.pool.PoolEntry;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public record LootBoxSettings(
        DatabaseSettings database,
        String hmacSecret,
        Duration clickCooldown,
        boolean fallbackToMail,
        int mailExpireDays,
        boolean titleEnabled,
        boolean particleEnabled,
        boolean soundEnabled,
        boolean announceMythicToServer,
        Map<String, LootBoxDefinition> definitions,
        Map<String, List<PoolEntry>> configPools
) {
    public LootBoxSettings {
        if (hmacSecret == null || hmacSecret.isBlank()) {
            throw new IllegalArgumentException("security.hmac_secret must not be blank");
        }
        if (hmacSecret.equals("change-this-lootbox-secret")) {
            throw new IllegalArgumentException("security.hmac_secret must be changed before enabling LootBox");
        }
        definitions = Map.copyOf(definitions);
        configPools = Map.copyOf(configPools);
        if (definitions.isEmpty()) {
            throw new IllegalArgumentException("at least one lootbox definition is required");
        }
    }
}

