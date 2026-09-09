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

public final class Bloodburst extends KillEffect {
    public Bloodburst(UltraPlayer owner, KillEffectType type, UltraCosmetics plugin) { super(owner, type, plugin); }

    @Override
    protected KillEffectExecution createExecution(KillEffectContext context, KillEffectScene scene) {
        return new CatalogExecution(context, scene, getType()) {
            @Override
            protected void frame(double t, int elapsed) {
                if (at(0)) { victim(); sound(Sound.POP, 1, 0.45f, 0.6f); }
                if (t < 18) {
                    double u = progress(t, 0, 18);
                    // Recoil belongs to the visual replica; the real victim is never moved.
                    double recoil = Math.sin(u * Math.PI) * 0.25;
                    double yaw = Math.toRadians(context.death.yaw);
                    moveVictim(Math.sin(yaw) * recoil, recoil * 0.8, -Math.cos(yaw) * recoil, context.death.yaw);
                    ring(Particle.RED, 0.85 - smooth(u) * 0.65, 0.9);
                    if (at(4) && !lite) scene.status(subject, (byte) 2);
                }
                if (at(18)) { if (!lite) scene.status(subject, (byte) 3); climax(Sound.BLAST, 1, 0.65f); }
                if (at(23)) removeVictim();
                if (t >= 18 && t < 42) {
                    double u = progress(t, 18, 42);
                    burst(t < 28 ? Particle.RED_FRAGMENT : Particle.RED, 0, 1, 0,
                            0.2 + 1.6 * (1 - (1 - u) * (1 - u)), 0.65 * u * u);
                    if (elapsed % 2 == 0) ring(Particle.RED, 0.3 + 1.7 * smooth(u), 0.15);
                }
                if (t >= 42 && t < 54 && elapsed % 3 == 0) {
                    ring(Particle.RED_FRAGMENT, 1.8, 0.12);
                }
            }
        };
    }
}
