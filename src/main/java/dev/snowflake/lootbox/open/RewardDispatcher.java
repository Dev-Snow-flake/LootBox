package dev.snowflake.lootbox.open;

import dev.snowflake.lootbox.history.DeliveryMethod;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class RewardDispatcher {
    private final MailBridge mailBridge;
    private final int mailExpireDays;

    public RewardDispatcher(MailBridge mailBridge, int mailExpireDays) {
        this.mailBridge = Objects.requireNonNull(mailBridge, "mailBridge");
        this.mailExpireDays = mailExpireDays;
    }

    public boolean canDeliver(Player player, ItemStack item) {
        return hasInventorySpace(player, item) || mailBridge.isAvailable();
    }

    public DeliveryMethod deliveryMethodFor(Player player, ItemStack item) {
        return hasInventorySpace(player, item) ? DeliveryMethod.INVENTORY : DeliveryMethod.MAIL;
    }

    public DeliveryMethod dispatch(Player player, ItemStack item, String mailMessage) {
        return dispatch(player, item, mailMessage, deliveryMethodFor(player, item));
    }

    public DeliveryMethod dispatch(Player player, ItemStack item, String mailMessage, DeliveryMethod preferred) {
        if (preferred == DeliveryMethod.MAIL) {
            if (mailBridge.send(player.getUniqueId(), item, mailMessage, mailExpireDays)) {
                return DeliveryMethod.MAIL;
            }
            throw new IllegalStateException("mail fallback is unavailable");
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        if (leftovers.isEmpty()) {
            return DeliveryMethod.INVENTORY;
        }
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
        return DeliveryMethod.INVENTORY;
    }

    private static boolean hasInventorySpace(Player player, ItemStack item) {
        Map<Integer, ItemStack> probe = new HashMap<>();
        probe.put(0, item.clone());
        ItemStack[] storage = player.getInventory().getStorageContents().clone();
        for (ItemStack slot : storage) {
            if (probe.isEmpty()) {
                return true;
            }
            ItemStack remaining = probe.values().iterator().next();
            if (slot == null || slot.getType().isAir()) {
                return true;
            }
            if (slot.isSimilar(remaining) && slot.getAmount() < slot.getMaxStackSize()) {
                int room = slot.getMaxStackSize() - slot.getAmount();
                remaining.setAmount(Math.max(0, remaining.getAmount() - room));
                if (remaining.getAmount() <= 0) {
                    return true;
                }
            }
        }
        return false;
    }
}

