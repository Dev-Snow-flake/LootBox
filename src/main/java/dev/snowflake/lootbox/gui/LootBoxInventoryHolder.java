package dev.snowflake.lootbox.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public record LootBoxInventoryHolder(ViewType type, String id) implements InventoryHolder {
    @Override
    public Inventory getInventory() {
        return null;
    }

    public enum ViewType {
        GALLERY,
        CHANCE,
        ADMIN_POOL
    }
}

