package dev.snowflake.lootbox.gui;

import dev.snowflake.lootbox.definition.LootBoxRegistry;
import dev.snowflake.lootbox.pool.PoolEntry;
import dev.snowflake.lootbox.pool.RewardItem;
import dev.snowflake.lootbox.pool.Tier;
import dev.snowflake.lootbox.storage.LootBoxRepository;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class PoolEditorListener implements Listener {
    private final Plugin plugin;
    private final LootBoxRepository repository;
    private final LootBoxRegistry registry;
    private final Executor databaseExecutor;
    private final Runnable reloadRegistry;
    private final Consumer<String> warnings;

    public PoolEditorListener(
            Plugin plugin,
            LootBoxRepository repository,
            LootBoxRegistry registry,
            Executor databaseExecutor,
            Runnable reloadRegistry,
            Consumer<String> warnings
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.databaseExecutor = Objects.requireNonNull(databaseExecutor, "databaseExecutor");
        this.reloadRegistry = Objects.requireNonNull(reloadRegistry, "reloadRegistry");
        this.warnings = Objects.requireNonNull(warnings, "warnings");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof LootBoxInventoryHolder holder
                && holder.type() != LootBoxInventoryHolder.ViewType.ADMIN_POOL) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof LootBoxInventoryHolder holder)
                || holder.type() != LootBoxInventoryHolder.ViewType.ADMIN_POOL) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        List<PoolEntry> entries = entriesFromInventory(holder.id(), event.getInventory());
        databaseExecutor.execute(() -> {
            try {
                repository.replaceAdminPool(holder.id(), entries);
                reloadRegistry.run();
                Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(ChatColor.GREEN
                        + "Saved pool " + holder.id() + " with " + entries.size() + " entries."));
            } catch (SQLException exception) {
                warnings.accept("Failed to save lootbox pool " + holder.id() + ": " + exception.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(ChatColor.RED
                        + "Failed to save pool " + holder.id() + ". Check console."));
            }
        });
    }

    private static List<PoolEntry> entriesFromInventory(String poolId, Inventory inventory) {
        List<PoolEntry> entries = new ArrayList<>();
        for (ItemStack item : inventory.getContents()) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            ItemStack reward = item.clone();
            int amount = Math.max(1, reward.getAmount());
            reward.setAmount(1);
            entries.add(new PoolEntry(
                    0L,
                    poolId,
                    RewardItem.fromItemStack(reward),
                    1,
                    Tier.COMMON,
                    amount,
                    amount,
                    true));
        }
        return List.copyOf(entries);
    }
}

