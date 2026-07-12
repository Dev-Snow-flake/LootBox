package dev.snowflake.lootbox.open;

import dev.snowflake.lootbox.definition.LootBoxDefinition;
import dev.snowflake.lootbox.definition.LootBoxRegistry;
import dev.snowflake.lootbox.history.DeliveryMethod;
import dev.snowflake.lootbox.item.LootBoxItemData;
import dev.snowflake.lootbox.item.LootBoxItemFactory;
import dev.snowflake.lootbox.pool.PoolEntry;
import dev.snowflake.lootbox.pool.ProbabilityCalculator;
import dev.snowflake.lootbox.pool.Tier;
import dev.snowflake.lootbox.storage.LootBoxRepository;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ExecutionException;
import java.util.function.BooleanSupplier;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class OpenService {
    private final LootBoxRegistry registry;
    private final LootBoxItemFactory itemFactory;
    private final LootBoxRepository repository;
    private final ProbabilityCalculator probability;
    private final RewardDispatcher rewards;
    private final Logger logger;
    private final Plugin plugin;
    private final boolean announceMythic;
    private final boolean titleEnabled;
    private final boolean particleEnabled;
    private final boolean soundEnabled;

    public OpenService(
            Plugin plugin,
            LootBoxRegistry registry,
            LootBoxItemFactory itemFactory,
            LootBoxRepository repository,
            ProbabilityCalculator probability,
            RewardDispatcher rewards,
            Logger logger,
            boolean announceMythic,
            boolean titleEnabled,
            boolean particleEnabled,
            boolean soundEnabled
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.itemFactory = Objects.requireNonNull(itemFactory, "itemFactory");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.probability = Objects.requireNonNull(probability, "probability");
        this.rewards = Objects.requireNonNull(rewards, "rewards");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.announceMythic = announceMythic;
        this.titleEnabled = titleEnabled;
        this.particleEnabled = particleEnabled;
        this.soundEnabled = soundEnabled;
    }

    public OpenResult open(Player player, ItemStack boxItem, BooleanSupplier consumeOneBox) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(consumeOneBox, "consumeOneBox");

        LootBoxItemData itemData = itemFactory.read(boxItem)
                .orElse(null);
        if (itemData == null) {
            return OpenResult.failure(ChatColor.RED + "This is not a valid LootBox.");
        }

        try {
            LootBoxRepository.IssuedBox issued = repository.findIssuedBox(itemData.serial())
                    .orElse(null);
            if (issued == null || !issued.boxId().equals(itemData.boxId())) {
                return OpenResult.failure(ChatColor.RED + "This LootBox is not registered.");
            }
            if (!"ISSUED".equals(issued.status())) {
                return OpenResult.failure(ChatColor.RED + "This LootBox was already opened or voided.");
            }
            LootBoxDefinition definition = registry.definition(issued.boxId()).orElse(null);
            if (definition == null) {
                return OpenResult.failure(ChatColor.RED + "Unknown LootBox type: " + issued.boxId());
            }
            List<PoolEntry> entries = registry.pool(definition.poolId());
            if (entries.stream().noneMatch(PoolEntry::enabled)) {
                return OpenResult.failure(ChatColor.RED + "This LootBox has no enabled rewards.");
            }
            if (!repository.reserveForOpening(itemData.serial(), player.getUniqueId())) {
                return OpenResult.failure(ChatColor.RED + "This LootBox is already being opened.");
            }

            PoolEntry entry = probability.draw(entries);
            int amount = amount(entry);
            ItemStack reward = entry.reward().createStack(amount);
            if (!sync(() -> rewards.canDeliver(player, reward))) {
                repository.releaseReservation(itemData.serial());
                return OpenResult.failure(ChatColor.RED + "Your inventory is full and mail is unavailable.");
            }
            DeliveryMethod delivery = sync(() -> rewards.deliveryMethodFor(player, reward));
            long historyId = repository.insertHistoryPending(
                    player.getUniqueId(),
                    definition.id(),
                    definition.poolId(),
                    entry,
                    amount,
                    delivery,
                    itemData.serial());
            if (!sync(consumeOneBox::getAsBoolean)) {
                repository.releaseReservation(itemData.serial());
                repository.markHistoryFailed(historyId);
                return OpenResult.failure(ChatColor.RED + "Unable to consume the LootBox item.");
            }

            DeliveryMethod actualDelivery;
            try {
                actualDelivery = sync(() -> rewards.dispatch(
                        player,
                        reward,
                        "LootBox reward from " + definition.displayName(),
                        delivery));
            } catch (RuntimeException exception) {
                try {
                    repository.markHistoryFailed(historyId);
                } catch (SQLException failure) {
                    logger.log(Level.WARNING, "Unable to mark failed LootBox history " + historyId, failure);
                }
                throw exception;
            }
            repository.markHistoryDelivered(historyId);
            repository.markOpened(itemData.serial(), player.getUniqueId());
            sync(() -> {
                announce(player, definition, entry, amount, actualDelivery);
                return null;
            });
            return OpenResult.success(
                    ChatColor.GREEN + "Opened " + ChatColor.stripColor(color(definition.displayName())) + ".",
                    entry,
                    amount,
                    actualDelivery);
        } catch (SQLException exception) {
            logger.log(Level.SEVERE, "LootBox open failed for " + player.getName(), exception);
            return OpenResult.failure(ChatColor.RED + "LootBox database error. Try again later.");
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "LootBox reward dispatch failed for " + player.getName(), exception);
            return OpenResult.failure(ChatColor.RED + "LootBox open failed. Contact an administrator.");
        }
    }

    private void announce(Player player, LootBoxDefinition definition, PoolEntry entry, int amount, DeliveryMethod method) {
        String itemName = ChatColor.stripColor(entry.reward().displayName() == null || entry.reward().displayName().isBlank()
                ? entry.reward().material()
                : color(entry.reward().displayName()));
        String message = entry.tier().color() + "[LootBox] " + player.getName()
                + " obtained " + itemName + " x" + amount + " from " + color(definition.displayName()) + ".";
        player.sendMessage(message + ChatColor.GRAY + " (" + method.name().toLowerCase() + ")");
        if (titleEnabled) {
            player.sendTitle(
                    ChatColor.GOLD + "LootBox Opened",
                    entry.tier().color() + itemName + ChatColor.WHITE + " x" + amount,
                    10,
                    45,
                    10);
        }
        if (soundEnabled) {
            player.playSound(player.getLocation(), entry.tier().sound(), 1.0F, 1.0F);
        }
        if (particleEnabled) {
            player.getWorld().spawnParticle(entry.tier().particle(), player.getLocation().add(0.0D, 1.0D, 0.0D), 24);
        }
        if (announceMythic && entry.tier() == Tier.MYTHIC) {
            Bukkit.broadcastMessage(ChatColor.RED + "[LootBox] " + player.getName()
                    + " pulled a MYTHIC reward: " + itemName + " x" + amount + "!");
        }
    }

    private static int amount(PoolEntry entry) {
        if (entry.amountMin() == entry.amountMax()) {
            return entry.amountMin();
        }
        return ThreadLocalRandom.current().nextInt(entry.amountMin(), entry.amountMax() + 1);
    }

    private static String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value);
    }

    private <T> T sync(Callable<T> callable) {
        if (Bukkit.isPrimaryThread()) {
            try {
                return callable.call();
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }
        try {
            return Bukkit.getScheduler().callSyncMethod(plugin, callable).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for main-thread LootBox action", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(cause);
        }
    }
}

