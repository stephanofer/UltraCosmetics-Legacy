package be.isach.ultracosmetics.cosmetics.killeffects.effects;

import be.isach.ultracosmetics.UltraCosmetics;
import be.isach.ultracosmetics.cosmetics.killeffects.KillEffect;
import be.isach.ultracosmetics.cosmetics.killeffects.KillEffectContext;
import be.isach.ultracosmetics.cosmetics.killeffects.render.KillEffectRenderer.Particle;
import be.isach.ultracosmetics.cosmetics.killeffects.render.KillEffectRenderer.Sound;
import be.isach.ultracosmetics.cosmetics.killeffects.runtime.KillEffectExecution;
import be.isach.ultracosmetics.cosmetics.killeffects.runtime.KillEffectScene;
import be.isach.ultracosmetics.cosmetics.type.KillEffectType;
import be.isach.ultracosmetics.player.UltraPlayer;

public final class FireworkFinale extends KillEffect {
    private static final double LAUNCH_HEIGHT = 0.35;
    private static final double RISE = 4.45;
    // Movement uses the player's feet; the explosion originates at the torso.
    private static final double APEX = LAUNCH_HEIGHT + RISE + 0.9;

    public FireworkFinale(UltraPlayer owner, KillEffectType type, UltraCosmetics plugin) { super(owner, type, plugin); }

    @Override
    protected KillEffectExecution createExecution(KillEffectContext context, KillEffectScene scene) {
        return new CatalogExecution(context, scene, getType()) {
            @Override
            protected void frame(double t, int elapsed) {
                if (at(0)) {
                    // Keep the full silhouette in lite scenes too; only the particle density is reduced.
                    subject = scene.spawnPlayer(context.anchor.y - context.death.y);
                    sound(Sound.IGNITE, 0.2, 0.45f, 1.1f);
                }
                if (t < 10) {
                    double height = smooth(t / 10) * LAUNCH_HEIGHT;
                    scene.move(subject, 0, height, 0, context.death.yaw, 0);
                    helix(Particle.GOLD, t, 0.65 * (1 - t / 14), height, 2.2, 0);
                }
                if (at(10)) sound(Sound.LAUNCH, LAUNCH_HEIGHT, 0.7f, 1.1f);
                if (t >= 10 && t < 38) {
                    double u = progress(t, 10, 33);
                    double height = LAUNCH_HEIGHT + RISE * u * u;
                    scene.move(subject, 0, height, 0, context.death.yaw, 0);
                    // A widening spark exhaust makes the entire body read as the firework.
                    int count = lite ? 4 : 10;
                    for (int i = 0; i < count; i++) {
                        double tail = i / (double) (count - 1);
                        double angle = phase + t * 0.5 + i * 2.4;
                        double radius = 0.08 + tail * 0.28;
                        scene.particle(i % 3 == 0 ? Particle.GOLD : Particle.SPARK,
                                Math.cos(angle) * radius, height - 0.1 - tail * (0.4 + 1.4 * u),
                                Math.sin(angle) * radius, i);
                    }
                    if (t >= 33) helix(Particle.WHITE, t, 0.35, height + 0.2, 1.4, 0);
                }
                // Allow legacy teleport interpolation to catch up before the body bursts.
                if (at(38)) {
                    removeVictim();
                    climax(Sound.EXPLOSION, APEX, 0.75f);
                    sound(Sound.BLAST, APEX, 0.9f, 1.1f);
                    burst(Particle.WHITE, 0, APEX, 0, 0.75, 0);
                    ring(Particle.SPARK, 0.85, APEX);
                }
                if (t >= 38 && t < 51) {
                    double u = progress(t, 38, 51);
                    burst(Particle.GOLD, 0, APEX, 0, 0.5 + 2.2 * (1 - (1 - u) * (1 - u)), 0);
                }
                if (at(46)) sound(Sound.BLAST, APEX - 0.2, 0.6f, 1.5f);
                if (at(49)) sound(Sound.TWINKLE, APEX - 0.3, 0.55f, 1.2f);
                if (t >= 46 && t < 59) {
                    double u = progress(t, 46, 59);
                    // Two offset secondary blooms read as a staged finale rather than one large cloud.
                    burst(Particle.CYAN, -1.2, APEX - 0.3, 0, 0.2 + u * 1.2, u * u * 0.6);
                    if (!lite) burst(Particle.RED, 1.2, APEX - 0.3, 0, 0.2 + u * 1.2, u * u * 0.6);
                }
            }
        };
    }
}
