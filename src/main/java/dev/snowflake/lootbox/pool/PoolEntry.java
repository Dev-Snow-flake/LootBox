package dev.snowflake.lootbox.pool;

import java.util.Objects;

public record PoolEntry(
        long entryId,
        String poolId,
        RewardItem reward,
        int weight,
        Tier tier,
        int amountMin,
        int amountMax,
        boolean enabled
) {
    public PoolEntry {
        poolId = requireId(poolId);
        reward = Objects.requireNonNull(reward, "reward");
        tier = Objects.requireNonNull(tier, "tier");
        if (weight <= 0) {
            throw new IllegalArgumentException("weight must be positive");
        }
        if (amountMin <= 0 || amountMax < amountMin) {
            throw new IllegalArgumentException("invalid amount range");
        }
    }

    public PoolEntry withEntryId(long newEntryId) {
        return new PoolEntry(newEntryId, poolId, reward, weight, tier, amountMin, amountMax, enabled);
    }

    public PoolEntry enabledCopy(boolean value) {
        return new PoolEntry(entryId, poolId, reward, weight, tier, amountMin, amountMax, value);
    }

    private static String requireId(String value) {
        Objects.requireNonNull(value, "poolId");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("poolId must not be blank");
        }
        if (!trimmed.matches("[A-Za-z0-9_.:-]{1,96}")) {
            throw new IllegalArgumentException("poolId contains unsupported characters: " + value);
        }
        return trimmed;
    }
}

