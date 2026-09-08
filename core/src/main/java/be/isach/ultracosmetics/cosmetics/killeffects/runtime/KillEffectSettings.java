package be.isach.ultracosmetics.cosmetics.killeffects.runtime;

import java.util.Map;

public final class KillEffectSettings {
    public final int range, fullRange, maxGlobal, maxWorld, maxChunk, maxDuration, maxEntities, cooldown, anchorSearch;
    public final boolean sounds, respectVanish, lite;

    public KillEffectSettings(Map<String, Object> config) {
        String p = "Kill-Effects-Settings.";
        range = number(config.get(p + "Visible-Range"), 32, 4, 64);
        fullRange = number(config.get(p + "Full-Detail-Range"), 16, 1, range);
        maxGlobal = number(config.get(p + "Max-Active-Global"), 12, 1, 24);
        maxWorld = number(config.get(p + "Max-Active-Per-World"), 6, 1, maxGlobal);
        maxChunk = number(config.get(p + "Max-Full-Effects-Per-Chunk"), 2, 1, maxWorld);
        // Floors also protect existing configs: Freeze needs up to 160 ticks and 16 helmets plus a player.
        maxDuration = number(config.get(p + "Max-Duration-Ticks"), 200, 160, 200);
        maxEntities = number(config.get(p + "Max-Entities-Per-Scene"), 17, 17, 24);
        cooldown = number(config.get(p + "Preview-Cooldown"), 5, 1, 60) * 20;
        anchorSearch = number(config.get(p + "Anchor-Search-Distance"), 3, 0, 5);
        sounds = !Boolean.FALSE.equals(config.get(p + "Sounds"));
        respectVanish = !Boolean.FALSE.equals(config.get(p + "Respect-Vanish"));
        lite = !Boolean.FALSE.equals(config.get(p + "Lite-Mode-On-Capacity"));
    }

    private static int number(Object value, int fallback, int min, int max) {
        double number = value instanceof Number ? ((Number) value).doubleValue() : fallback;
        if (!Double.isFinite(number)) number = fallback;
        return (int) Math.max(min, Math.min(max, number));
    }

    public static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
