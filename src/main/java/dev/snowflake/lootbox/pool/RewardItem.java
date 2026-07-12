package dev.snowflake.lootbox.pool;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

public record RewardItem(
        String material,
        String displayName,
        List<String> lore,
        Integer customModelData,
        String serializedItem
) {
    private static final Gson GSON = new Gson();

    public RewardItem {
        material = material == null ? null : material.trim().toUpperCase();
        displayName = displayName == null ? "" : displayName;
        lore = List.copyOf(lore == null ? List.of() : lore);
    }

    public static RewardItem ofMaterial(Material material, String displayName, List<String> lore) {
        Objects.requireNonNull(material, "material");
        return new RewardItem(material.name(), displayName, lore, null, null);
    }

    public static RewardItem fromItemStack(ItemStack item) {
        Objects.requireNonNull(item, "item");
        return new RewardItem(item.getType().name(), itemName(item), lore(item), customModelData(item), serialize(item));
    }

    public ItemStack createStack(int amount) {
        if (serializedItem != null && !serializedItem.isBlank()) {
            ItemStack decoded = deserialize(serializedItem);
            decoded.setAmount(amount);
            return decoded;
        }
        Material itemMaterial = Material.matchMaterial(material == null ? "" : material);
        if (itemMaterial == null || itemMaterial.isAir()) {
            throw new IllegalStateException("Unknown reward material: " + material);
        }
        ItemStack stack = new ItemStack(itemMaterial, amount);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (!displayName.isBlank()) {
                meta.setDisplayName(color(displayName));
            }
            if (!lore.isEmpty()) {
                meta.setLore(lore.stream().map(RewardItem::color).toList());
            }
            if (customModelData != null) {
                meta.setCustomModelData(customModelData);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static RewardItem fromJson(String json) {
        RewardItem item = GSON.fromJson(json, RewardItem.class);
        if (item == null) {
            throw new IllegalArgumentException("item_json is empty");
        }
        return new RewardItem(item.material, item.displayName, item.lore, item.customModelData, item.serializedItem);
    }

    public static RewardItem fromJsonObject(JsonObject object) {
        return fromJson(GSON.toJson(object));
    }

    private static String serialize(ItemStack item) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (BukkitObjectOutputStream output = new BukkitObjectOutputStream(bytes)) {
                output.writeObject(item);
            }
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to serialize reward item", exception);
        }
    }

    private static ItemStack deserialize(String value) {
        try {
            byte[] bytes = Base64.getDecoder().decode(value);
            try (BukkitObjectInputStream input = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
                Object object = input.readObject();
                if (object instanceof ItemStack item) {
                    return item;
                }
                throw new IllegalArgumentException("Serialized reward is not an ItemStack");
            }
        } catch (IOException | ClassNotFoundException | IllegalArgumentException exception) {
            throw new IllegalStateException("Unable to deserialize reward item", exception);
        }
    }

    private static String itemName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() ? meta.getDisplayName() : "";
    }

    private static List<String> lore(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasLore() && meta.getLore() != null ? meta.getLore() : List.of();
    }

    private static Integer customModelData(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasCustomModelData() ? meta.getCustomModelData() : null;
    }

    private static String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value);
    }
}

