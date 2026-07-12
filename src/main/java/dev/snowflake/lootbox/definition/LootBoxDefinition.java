package dev.snowflake.lootbox.definition;

import java.util.List;
import java.util.Objects;
import org.bukkit.Material;

public record LootBoxDefinition(
        String id,
        String displayName,
        Material material,
        String poolId,
        List<String> lore
) {
    public LootBoxDefinition {
        id = requireId(id, "id");
        displayName = Objects.requireNonNull(displayName, "displayName");
        material = Objects.requireNonNull(material, "material");
        poolId = requireId(poolId, "poolId");
        lore = List.copyOf(lore == null ? List.of() : lore);
    }

    private static String requireId(String value, String field) {
        Objects.requireNonNull(value, field);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (!trimmed.matches("[A-Za-z0-9_.:-]{1,96}")) {
            throw new IllegalArgumentException(field + " contains unsupported characters: " + value);
        }
        return trimmed;
    }
}

