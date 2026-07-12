package dev.snowflake.lootbox.history;

public record StatsSnapshot(long opensTotal, long opensToday, long mythicCount) {
    public static final StatsSnapshot EMPTY = new StatsSnapshot(0L, 0L, 0L);
}

