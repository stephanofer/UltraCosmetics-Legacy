package be.isach.ultracosmetics.cosmetics.killeffects.render;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class KillEffectRendererFactory {
    private KillEffectRendererFactory() { }

    public static KillEffectRenderer create(JavaPlugin plugin) {
        String reason = null;
        if (!Bukkit.getBukkitVersion().split("-")[0].equals("1.8.8")) {
            reason = "Minecraft 1.8.8 is required";
        } else if (!Bukkit.getPluginManager().isPluginEnabled("packetevents")) {
            reason = "PacketEvents 2.13.0 must be installed and enabled";
        } else {
            try {
                return (KillEffectRenderer) Class.forName(
                        "be.isach.ultracosmetics.cosmetics.killeffects.packetevents.PacketEventsKillEffectRenderer")
                        .getConstructor().newInstance();
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                reason = "incompatible PacketEvents integration: " + cause;
            }
        }
        plugin.getLogger().warning("Kill Effects disabled: " + reason + ". Other cosmetics remain available.");
        return null;
    }
}
