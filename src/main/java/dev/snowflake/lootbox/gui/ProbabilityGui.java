package dev.snowflake.lootbox.gui;

import dev.snowflake.lootbox.definition.LootBoxDefinition;
import dev.snowflake.lootbox.definition.LootBoxRegistry;
import dev.snowflake.lootbox.pool.PoolEntry;
import dev.snowflake.lootbox.pool.ProbabilityCalculator;
import dev.snowflake.lootbox.pool.ProbabilityView;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ProbabilityGui {
    private static final DecimalFormat PERCENT = new DecimalFormat("0.###");

    private final LootBoxRegistry registry;
    private final ProbabilityCalculator calculator;

    public ProbabilityGui(LootBoxRegistry registry, ProbabilityCalculator calculator) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.calculator = Objects.requireNonNull(calculator, "calculator");
    }

    public void openGallery(Player player) {
        List<LootBoxDefinition> definitions = registry.definitions();
        int size = inventorySize(Math.max(9, definitions.size()));
        Inventory inventory = Bukkit.createInventory(
                new LootBoxInventoryHolder(LootBoxInventoryHolder.ViewType.GALLERY, "gallery"),
                size,
                ChatColor.DARK_GREEN + "LootBox Gallery");
        for (LootBoxDefinition definition : definitions) {
            ItemStack item = new ItemStack(definition.material());
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(color(definition.displayName()));
                meta.setLore(List.of(
                        ChatColor.GRAY + "ID: " + definition.id(),
                        ChatColor.GRAY + "Pool: " + definition.poolId(),
                        ChatColor.YELLOW + "Use /lootbox chance " + definition.id()));
                item.setItemMeta(meta);
            }
            inventory.addItem(item);
        }
        player.openInventory(inventory);
    }

    public boolean openChance(Player player, String boxId) {
        LootBoxDefinition definition = registry.definition(boxId).orElse(null);
        if (definition == null) {
            player.sendMessage(ChatColor.RED + "Unknown LootBox: " + boxId);
            return false;
        }
        List<ProbabilityView> views = calculator.views(registry.pool(definition.poolId()));
        int size = inventorySize(Math.max(9, views.size()));
        Inventory inventory = Bukkit.createInventory(
                new LootBoxInventoryHolder(LootBoxInventoryHolder.ViewType.CHANCE, boxId),
                size,
                ChatColor.DARK_AQUA + "Chance: " + definition.id());
        for (ProbabilityView view : views) {
            PoolEntry entry = view.entry();
            ItemStack item = entry.reward().createStack(Math.max(1, entry.amountMin()));
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                String name = meta.hasDisplayName() ? meta.getDisplayName() : item.getType().name();
                meta.setDisplayName(entry.tier().color() + ChatColor.stripColor(name));
                meta.setLore(List.of(
                        ChatColor.GRAY + "Tier: " + entry.tier().color() + entry.tier().name(),
                        ChatColor.GRAY + "Amount: " + entry.amountMin() + "~" + entry.amountMax(),
                        ChatColor.GRAY + "Weight: " + entry.weight(),
                        ChatColor.YELLOW + "Chance: " + PERCENT.format(view.percent()) + "%"));
                item.setItemMeta(meta);
            }
            inventory.addItem(item);
        }
        if (views.isEmpty()) {
            inventory.setItem(4, info(Material.BARRIER, ChatColor.RED + "No enabled rewards"));
        }
        player.openInventory(inventory);
        return true;
    }

    public void openAdminPool(Player player, String poolId) {
        Inventory inventory = Bukkit.createInventory(
                new LootBoxInventoryHolder(LootBoxInventoryHolder.ViewType.ADMIN_POOL, poolId),
                54,
                ChatColor.DARK_RED + "Edit Pool: " + poolId);
        for (PoolEntry entry : registry.pool(poolId)) {
            if (!entry.enabled()) {
                continue;
            }
            ItemStack item = entry.reward().createStack(Math.max(1, entry.amountMin()));
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() && meta.getLore() != null
                        ? new java.util.ArrayList<>(meta.getLore())
                        : new java.util.ArrayList<>();
                lore.add(ChatColor.DARK_GRAY + "LootBox weight=" + entry.weight());
                lore.add(ChatColor.DARK_GRAY + "LootBox tier=" + entry.tier().name());
                lore.add(ChatColor.DARK_GRAY + "LootBox amount=" + entry.amountMin() + "-" + entry.amountMax());
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            inventory.addItem(item);
        }
        player.openInventory(inventory);
        player.sendMessage(ChatColor.YELLOW
                + "Place reward items in the GUI. Closing it saves them with default weight 1 and COMMON tier.");
    }

    private static ItemStack info(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static int inventorySize(int requested) {
        return Math.min(54, ((requested + 8) / 9) * 9);
    }

    private static String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value);
    }
}

