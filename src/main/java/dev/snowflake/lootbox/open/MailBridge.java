package dev.snowflake.lootbox.open;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Server;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class MailBridge {
    private final Server server;
    private final boolean enabledByConfig;
    private volatile Object mailHandle;

    public MailBridge(Server server, boolean enabledByConfig) {
        this.server = Objects.requireNonNull(server, "server");
        this.enabledByConfig = enabledByConfig;
    }

    public boolean isAvailable() {
        return enabledByConfig && findMailHandle().isPresent();
    }

    public boolean send(UUID recipient, ItemStack item, String message, int expireDays) {
        if (!enabledByConfig) {
            return false;
        }
        Optional<Object> handle = findMailHandle();
        if (handle.isEmpty()) {
            return false;
        }
        ItemStack copy = item.clone();
        for (String methodName : List.of("send", "sendMail", "deliver", "deliverMail")) {
            for (Method method : handle.get().getClass().getMethods()) {
                if (!method.getName().equals(methodName)) {
                    continue;
                }
                Object[] arguments = arguments(method.getParameterTypes(), recipient, copy, message, expireDays);
                if (arguments == null) {
                    continue;
                }
                try {
                    method.invoke(handle.get(), arguments);
                    return true;
                } catch (IllegalAccessException | InvocationTargetException ignored) {
                    return false;
                }
            }
        }
        return false;
    }

    private Optional<Object> findMailHandle() {
        Object cached = mailHandle;
        if (cached != null) {
            return Optional.of(cached);
        }

        for (String pluginName : List.of("Mail")) {
            Plugin plugin = server.getPluginManager().getPlugin(pluginName);
            if (plugin != null && plugin.isEnabled()) {
                mailHandle = plugin;
                return Optional.of(plugin);
            }
        }
        return Optional.empty();
    }

    private static Object[] arguments(
            Class<?>[] types,
            UUID recipient,
            ItemStack item,
            String message,
            int expireDays
    ) {
        Object[] args = new Object[types.length];
        int consumed = 0;
        for (int i = 0; i < types.length; i++) {
            Class<?> type = types[i];
            if (type == UUID.class) {
                args[i] = recipient;
                consumed++;
            } else if (ItemStack.class.isAssignableFrom(type)) {
                args[i] = item;
                consumed++;
            } else if (type == String.class) {
                args[i] = message;
            } else if (type == int.class || type == Integer.class) {
                args[i] = expireDays;
            } else if (type == long.class || type == Long.class) {
                args[i] = (long) expireDays;
            } else {
                return null;
            }
        }
        return consumed >= 2 ? args : null;
    }
}

