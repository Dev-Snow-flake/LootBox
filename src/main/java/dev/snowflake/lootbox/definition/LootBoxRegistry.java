package dev.snowflake.lootbox.definition;

import dev.snowflake.lootbox.pool.PoolEntry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class LootBoxRegistry {
    private volatile Map<String, LootBoxDefinition> definitions = Map.of();
    private volatile Map<String, List<PoolEntry>> pools = Map.of();

    public synchronized void reload(
            Map<String, LootBoxDefinition> definitions,
            Map<String, List<PoolEntry>> configPools,
            List<PoolEntry> adminEntries
    ) {
        Map<String, LootBoxDefinition> definitionCopy = new LinkedHashMap<>(definitions);
        Map<String, List<PoolEntry>> merged = new LinkedHashMap<>();
        for (Map.Entry<String, List<PoolEntry>> entry : configPools.entrySet()) {
            merged.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        Map<String, List<PoolEntry>> adminByPool = new LinkedHashMap<>();
        for (PoolEntry entry : adminEntries) {
            adminByPool.computeIfAbsent(entry.poolId(), ignored -> new ArrayList<>()).add(entry);
        }
        for (Map.Entry<String, List<PoolEntry>> entry : adminByPool.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                merged.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
        }
        this.definitions = Map.copyOf(definitionCopy);
        this.pools = Map.copyOf(merged);
    }

    public List<LootBoxDefinition> definitions() {
        return List.copyOf(definitions.values());
    }

    public Optional<LootBoxDefinition> definition(String id) {
        return Optional.ofNullable(definitions.get(Objects.requireNonNull(id, "id")));
    }

    public List<PoolEntry> pool(String poolId) {
        return pools.getOrDefault(poolId, List.of());
    }

    public List<PoolEntry> poolForBox(String boxId) {
        return definition(boxId)
                .map(definition -> pool(definition.poolId()))
                .orElse(List.of());
    }

    public boolean hasBox(String boxId) {
        return definitions.containsKey(boxId);
    }
}

