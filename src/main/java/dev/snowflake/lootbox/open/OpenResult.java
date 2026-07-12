package dev.snowflake.lootbox.open;

import dev.snowflake.lootbox.history.DeliveryMethod;
import dev.snowflake.lootbox.pool.PoolEntry;

public record OpenResult(
        boolean success,
        String message,
        PoolEntry reward,
        int amount,
        DeliveryMethod deliveryMethod
) {
    public static OpenResult failure(String message) {
        return new OpenResult(false, message, null, 0, null);
    }

    public static OpenResult success(String message, PoolEntry reward, int amount, DeliveryMethod deliveryMethod) {
        return new OpenResult(true, message, reward, amount, deliveryMethod);
    }
}

