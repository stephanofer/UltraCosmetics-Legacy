package be.isach.ultracosmetics.cosmetics.killeffects.compatibility;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Pure decisions only. Legacy effect remapping is activated when its target is registered. */
public final class KillEffectMigration {
    private KillEffectMigration() { }

    public static String category(String value) {
        if (value == null) return "";
        String key = value.toLowerCase(Locale.ROOT).replace('-', '_');
        return key.equals("death_effects") ? "kill_effects" : key;
    }

    public static String effect(String value, Map<String, String> aliases, Set<String> registered) {
        if (value == null) return null;
        String key = value.toLowerCase(Locale.ROOT);
        if (registered.contains(key)) return key;
        String target = aliases.get(key);
        return target != null && registered.contains(target) ? target : null;
    }

    public static <T> Map<String, T> missingSettings(Map<String, T> existing, Map<String, T> legacy, Map<String, String> paths) {
        Map<String, T> additions = new LinkedHashMap<>();
        paths.forEach((oldPath, newPath) -> {
            if (!existing.containsKey(newPath) && legacy.containsKey(oldPath)) {
                additions.put(newPath, legacy.get(oldPath));
            }
        });
        return additions;
    }

    /** Translate leaf paths only; copying sections would overwrite explicit destination children. */
    public static String catalogPath(String path) {
        String result = path.replace("Death-Effects", "Kill-Effects");
        result = result.replace("Kill-Effects.Explosion.", "Kill-Effects.Bloodburst.")
                .replace("Kill-Effects.Firework.", "Kill-Effects.FireworkFinale.")
                .replace("Kill-Effects.Lightning.", "Kill-Effects.DivineJudgment.");
        return result;
    }
}
