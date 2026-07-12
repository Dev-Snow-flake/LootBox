package dev.snowflake.lootbox.pool;

import org.bukkit.Particle;
import org.bukkit.Sound;

public enum Tier {
    COMMON("\u00A7f", "ENTITY_ITEM_PICKUP", "HAPPY_VILLAGER"),
    UNCOMMON("\u00A7a", "ENTITY_ITEM_PICKUP", "HAPPY_VILLAGER"),
    RARE("\u00A7b", "ENTITY_PLAYER_LEVELUP", "TOTEM_OF_UNDYING"),
    EPIC("\u00A7d", "ENTITY_PLAYER_LEVELUP", "TOTEM_OF_UNDYING"),
    LEGENDARY("\u00A76", "UI_TOAST_CHALLENGE_COMPLETE", "END_ROD"),
    MYTHIC("\u00A7c", "UI_TOAST_CHALLENGE_COMPLETE", "DRAGON_BREATH");

    private final String color;
    private final String sound;
    private final String particle;

    Tier(String color, String sound, String particle) {
        this.color = color;
        this.sound = sound;
        this.particle = particle;
    }

    public String color() {
        return color;
    }

    public Sound sound() {
        return Sound.valueOf(sound);
    }

    public Particle particle() {
        return Particle.valueOf(particle);
    }
}

