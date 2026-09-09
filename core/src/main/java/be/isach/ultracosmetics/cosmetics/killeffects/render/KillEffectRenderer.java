package be.isach.ultracosmetics.cosmetics.killeffects.render;

import be.isach.ultracosmetics.cosmetics.killeffects.VictimSnapshot;
import org.bukkit.entity.Player;
import java.util.List;
import java.util.UUID;

/** Each rendering method sends exactly one packet to one viewer. */
public interface KillEffectRenderer extends AutoCloseable {
    enum Particle { SNOW, ICE, CYAN, CLOUD }
    final class NameTag {
        public final String prefix;
        public final String suffix;

        public NameTag(String prefix, String suffix) {
            this.prefix = prefix;
            this.suffix = suffix;
        }
    }

    List<VictimSnapshot.SkinProperty> captureSkin(Player player);
    NameTag captureNameTag(Player player);
    boolean knowsProfile(UUID viewer, UUID profile);
    void profile(UUID viewer, UUID profile, VictimSnapshot victim, boolean add);
    void createNameTag(UUID viewer, String teamName, VictimSnapshot victim);
    void removeNameTag(UUID viewer, String teamName);
    void spawnPlayer(UUID viewer, int entity, UUID profile, double x, double y, double z, float yaw, float pitch);
    void headRotation(UUID viewer, int entity, float yaw);
    void spawnArmorStand(UUID viewer, int entity, double x, double y, double z, float yaw);
    void hideArmorStand(UUID viewer, int entity);
    void equipIceHelmet(UUID viewer, int entity);
    void teleportEntity(UUID viewer, int entity, double x, double y, double z, float yaw, float pitch);
    void stopEntityVelocity(UUID viewer, int entity);
    void playEntityStatus(UUID viewer, int entity, byte status);
    void animateEntity(UUID viewer, int entity, int animation);
    void spawnParticle(UUID viewer, Particle particle, double x, double y, double z);
    void playSound(UUID viewer, double x, double y, double z, float volume, float pitch);
    void destroyEntities(UUID viewer, int[] entities);
    @Override void close();
}
