package dev.snowflake.lootbox.listener;

import dev.snowflake.lootbox.item.LootBoxItemFactory;
import dev.snowflake.lootbox.open.OpenResult;
import dev.snowflake.lootbox.open.OpenService;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class LootBoxListener implements Listener {
    private final Plugin plugin;
    private final LootBoxItemFactory itemFactory;
    private final OpenService openService;
    private final Executor databaseExecutor;
    private final Duration cooldown;
    private final Clock clock;
    private final Consumer<String> warnings;
    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();

    public LootBoxListener(
            Plugin plugin,
            LootBoxItemFactory itemFactory,
            OpenService openService,
            Executor databaseExecutor,
            Duration cooldown,
            Clock clock,
            Consumer<String> warnings
    ) {
        this.plugin = plugin;
        this.itemFactory = itemFactory;
        this.openService = openService;
        this.databaseExecutor = databaseExecutor;
        this.cooldown = cooldown;
        this.clock = clock;
        this.warnings = warnings;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND && event.getHand() != EquipmentSlot.OFF_HAND) {
            return;
        }
        ItemStack item = event.getItem();
        if (!itemFactory.isLootBox(item)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        long now = clock.millis();
        long previous = lastClick.getOrDefault(player.getUniqueId(), 0L);
        if (now - previous < cooldown.toMillis()) {
            player.sendMessage(ChatColor.RED + "Please wait before opening another LootBox.");
            return;
        }
        lastClick.put(player.getUniqueId(), now);
        ItemStack snapshot = item == null ? null : item.clone();
        EquipmentSlot hand = event.getHand();
        databaseExecutor.execute(() -> {
            try {
                OpenResult result = openService.open(player, snapshot, () -> consumeOne(player, hand));
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!result.success()) {
                        player.sendMessage(result.message());
                    }
                });
            } catch (RuntimeException exception) {
                warnings.accept("Uncaught lootbox open task failure: " + exception.getMessage());
                Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(ChatColor.RED + "LootBox open failed. Check console."));
            }
        });
    }

    private boolean consumeOne(Player player, EquipmentSlot hand) {
        ItemStack current = hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        if (!itemFactory.isLootBox(current)) {
            return false;
        }
        if (current.getAmount() <= 1) {
            if (hand == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
            } else {
                player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            }
        } else {
            current.setAmount(current.getAmount() - 1);
        }
        return true;
    }
}

