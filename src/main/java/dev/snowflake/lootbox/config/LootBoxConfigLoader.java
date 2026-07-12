package dev.snowflake.lootbox.config;

import dev.snowflake.lootbox.definition.LootBoxDefinition;
import dev.snowflake.lootbox.pool.PoolEntry;
import dev.snowflake.lootbox.pool.RewardItem;
import dev.snowflake.lootbox.pool.Tier;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public final class LootBoxConfigLoader {
    private LootBoxConfigLoader() {
    }

    public static LootBoxSettings load(FileConfiguration config) {
        DatabaseSettings database = new DatabaseSettings(
                config.getString("database.mode", "standalone"),
                config.getString("database.jdbc_url", ""),
                config.getString("database.username", ""),
                config.getString("database.password", ""),
                config.getInt("database.maximum_pool_size", 4),
                config.getLong("database.connection_timeout_ms", 5000L),
                config.getBoolean("database.migrate", true));

        Map<String, LootBoxDefinition> definitions = loadDefinitions(section(config, "boxes"));
        Map<String, List<PoolEntry>> pools = loadPools(section(config, "pools"));
        validateReferencedPools(definitions, pools);

        return new LootBoxSettings(
                database,
                config.getString("security.hmac_secret", ""),
                Duration.ofMillis(Math.max(0L, config.getLong("security.click_cooldown_ms", 250L))),
                config.getBoolean("delivery.fallback_to_mail", true),
                Math.max(1, config.getInt("delivery.mail_expire_days", 7)),
                config.getBoolean("presentation.title_enabled", true),
                config.getBoolean("presentation.particle_enabled", true),
                config.getBoolean("presentation.sound_enabled", true),
                config.getBoolean("presentation.announce_mythic_to_server", true),
                definitions,
                pools);
    }

    private static Map<String, LootBoxDefinition> loadDefinitions(ConfigurationSection boxes) {
        Map<String, LootBoxDefinition> definitions = new LinkedHashMap<>();
        for (String id : boxes.getKeys(false)) {
            ConfigurationSection box = section(boxes, id);
            Material material = material(box.getString("material", "CHEST"), "boxes." + id + ".material");
            String display = box.getString("display_name", id);
            String pool = box.getString("pool", id);
            definitions.put(id, new LootBoxDefinition(id, display, material, pool, box.getStringList("lore")));
        }
        return definitions;
    }

    private static Map<String, List<PoolEntry>> loadPools(ConfigurationSection pools) {
        Map<String, List<PoolEntry>> result = new LinkedHashMap<>();
        for (String poolId : pools.getKeys(false)) {
            List<Map<?, ?>> rawEntries = pools.getMapList(poolId);
            List<PoolEntry> entries = new ArrayList<>();
            int index = 0;
            for (Map<?, ?> raw : rawEntries) {
                index++;
                String path = "pools." + poolId + "[" + index + "]";
                Material material = material(string(raw, "material", "STONE"), path + ".material");
                String displayName = string(raw, "display_name", material.name());
                List<String> lore = list(raw.get("lore"));
                Tier tier = tier(string(raw, "tier", "COMMON"), path + ".tier");
                int weight = integer(raw, "weight", 1);
                int amountMin = integer(raw, "amount_min", 1);
                int amountMax = integer(raw, "amount_max", amountMin);
                boolean enabled = bool(raw, "enabled", true);
                RewardItem reward = RewardItem.ofMaterial(material, displayName, lore);
                entries.add(new PoolEntry(0L, poolId, reward, weight, tier, amountMin, amountMax, enabled));
            }
            result.put(poolId, List.copyOf(entries));
        }
        return result;
    }

    private static void validateReferencedPools(
            Map<String, LootBoxDefinition> definitions,
            Map<String, List<PoolEntry>> pools
    ) {
        for (LootBoxDefinition definition : definitions.values()) {
            if (!pools.containsKey(definition.poolId())) {
                throw new IllegalArgumentException("box " + definition.id()
                        + " references missing pool " + definition.poolId());
            }
        }
    }

    private static ConfigurationSection section(ConfigurationSection parent, String path) {
        ConfigurationSection section = parent.getConfigurationSection(path);
        if (section == null) {
            throw new IllegalArgumentException("missing config section: " + parent.getCurrentPath() + "." + path);
        }
        return section;
    }

    private static ConfigurationSection section(FileConfiguration config, String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            throw new IllegalArgumentException("missing config section: " + path);
        }
        return section;
    }

    private static Material material(String value, String path) {
        Material material = Material.matchMaterial(value == null ? "" : value);
        if (material == null || material.isAir()) {
            throw new IllegalArgumentException(path + " is not a valid item material: " + value);
        }
        return material;
    }

    private static Tier tier(String value, String path) {
        try {
            return Tier.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(path + " is not a valid tier: " + value, exception);
        }
    }

    private static String string(Map<?, ?> raw, String key, String fallback) {
        Object value = raw.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static int integer(Map<?, ?> raw, String key, int fallback) {
        Object value = raw.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static boolean bool(Map<?, ?> raw, String key, boolean fallback) {
        Object value = raw.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private static List<String> list(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }
}

