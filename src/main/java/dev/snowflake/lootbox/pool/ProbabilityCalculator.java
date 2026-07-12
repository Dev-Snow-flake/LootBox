package dev.snowflake.lootbox.pool;

import java.security.SecureRandom;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

public final class ProbabilityCalculator {
    private final RandomGenerator random;

    public ProbabilityCalculator() {
        this(new SecureRandom());
    }

    public ProbabilityCalculator(RandomGenerator random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    public PoolEntry draw(List<PoolEntry> entries) {
        List<PoolEntry> enabled = enabledEntries(entries);
        long total = totalWeight(enabled);
        if (total <= 0L) {
            throw new IllegalArgumentException("pool has no enabled positive-weight entries");
        }
        long roll = random.nextLong(total);
        long cumulative = 0L;
        for (PoolEntry entry : enabled) {
            cumulative += entry.weight();
            if (roll < cumulative) {
                return entry;
            }
        }
        return enabled.getLast();
    }

    public List<ProbabilityView> views(List<PoolEntry> entries) {
        List<PoolEntry> enabled = enabledEntries(entries);
        long total = totalWeight(enabled);
        if (total <= 0L) {
            return List.of();
        }
        return enabled.stream()
                .map(entry -> new ProbabilityView(entry, entry.weight() * 100.0D / total))
                .toList();
    }

    private static List<PoolEntry> enabledEntries(List<PoolEntry> entries) {
        Objects.requireNonNull(entries, "entries");
        return entries.stream()
                .filter(PoolEntry::enabled)
                .filter(entry -> entry.weight() > 0)
                .toList();
    }

    private static long totalWeight(List<PoolEntry> entries) {
        return entries.stream().mapToLong(PoolEntry::weight).sum();
    }
}

