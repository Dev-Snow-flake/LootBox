package dev.snowflake.lootbox.api;

import dev.snowflake.lootbox.definition.LootBoxDefinition;
import java.util.List;
import org.bukkit.entity.Player;

public interface LootBoxAPI {
    List<LootBoxDefinition> definitions();

    boolean issue(Player player, String boxId, int amount);
}

