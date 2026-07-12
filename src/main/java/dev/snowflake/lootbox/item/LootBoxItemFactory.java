package dev.snowflake.lootbox.item;

import dev.snowflake.lootbox.definition.LootBoxDefinition;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class LootBoxItemFactory {
    private final NamespacedKey serialKey;
    private final NamespacedKey boxIdKey;
    private final NamespacedKey signatureKey;
    private final byte[] secret;

    public LootBoxItemFactory(Plugin plugin, String secret) {
        Objects.requireNonNull(plugin, "plugin");
        this.serialKey = new NamespacedKey(plugin, "box_serial");
        this.boxIdKey = new NamespacedKey(plugin, "lootbox_id");
        this.signatureKey = new NamespacedKey(plugin, "box_signature");
        this.secret = Objects.requireNonNull(secret, "secret").getBytes(StandardCharsets.UTF_8);
    }

    public ItemStack create(LootBoxDefinition definition, UUID serial) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(serial, "serial");
        ItemStack item = new ItemStack(definition.material(), 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(definition.displayName()));
            List<String> lore = new ArrayList<>();
            for (String line : definition.lore()) {
                lore.add(color(line));
            }
            lore.add(ChatColor.DARK_GRAY + "Serial: " + serial);
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            PersistentDataContainer data = meta.getPersistentDataContainer();
            data.set(serialKey, PersistentDataType.STRING, serial.toString());
            data.set(boxIdKey, PersistentDataType.STRING, definition.id());
            data.set(signatureKey, PersistentDataType.STRING, signature(serial, definition.id()));
            item.setItemMeta(meta);
        }
        return item;
    }

    public Optional<LootBoxItemData> read(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return Optional.empty();
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        PersistentDataContainer data = meta.getPersistentDataContainer();
        String serialText = data.get(serialKey, PersistentDataType.STRING);
        String boxId = data.get(boxIdKey, PersistentDataType.STRING);
        String signature = data.get(signatureKey, PersistentDataType.STRING);
        if (serialText == null || boxId == null || signature == null) {
            return Optional.empty();
        }
        UUID serial;
        try {
            serial = UUID.fromString(serialText);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
        String expected = signature(serial, boxId);
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8))) {
            return Optional.empty();
        }
        return Optional.of(new LootBoxItemData(serial, boxId));
    }

    public boolean isLootBox(ItemStack item) {
        return read(item).isPresent();
    }

    private String signature(UUID serial, String boxId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] digest = mac.doFinal((serial + ":" + boxId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("Unable to sign lootbox item", exception);
        }
    }

    private static String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value);
    }
}

