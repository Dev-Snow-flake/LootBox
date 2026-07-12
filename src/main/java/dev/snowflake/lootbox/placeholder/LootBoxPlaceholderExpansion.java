package dev.snowflake.lootbox.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

public final class LootBoxPlaceholderExpansion extends PlaceholderExpansion {
    private final Plugin plugin;
    private final StatsCache stats;

    public LootBoxPlaceholderExpansion(Plugin plugin, StatsCache stats) {
        this.plugin = plugin;
        this.stats = stats;
    }

    @Override
    public String getIdentifier() {
        return "lootbox";
    }

    @Override
    public String getAuthor() {
        return "Snowflake";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        return switch (params.toLowerCase()) {
            case "opens_total" -> Long.toString(stats.snapshot().opensTotal());
            case "opens_today" -> Long.toString(stats.snapshot().opensToday());
            case "mythic_count" -> Long.toString(stats.snapshot().mythicCount());
            default -> null;
        };
    }
}

