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

public final class SoulVortex extends KillEffect {
    public SoulVortex(UltraPlayer owner, KillEffectType type, UltraCosmetics plugin) { super(owner, type, plugin); }

    @Override
    protected KillEffectExecution createExecution(KillEffectContext context, KillEffectScene scene) {
        return new CatalogExecution(context, scene, getType()) {
            private int soul = -1;

            @Override
            protected void frame(double t, int elapsed) {
                if (at(0)) { victim(); sound(Sound.PORTAL, 0.5, 0.25f, 1.8f); }
                if (t < 24) {
                    double u = smooth(progress(t, 0, 24));
                    moveVictim(Math.sin(t * 0.16) * u * 0.12, u * 0.45, 0,
                            (float) (context.death.yaw + 90 * u));
                    helix(Particle.PORTAL, -t, 1 - u * 0.35, 0.1, 2.2, 0);
                }
                if (at(24)) {
                    removeVictim();
                    soul = scene.spawnHead(0, 2.05, 0, context.death.yaw + 90);
                    sound(Sound.CHIME, 2, 0.25f, 0.6f);
                }
                if (t >= 24 && t < 46) {
                    double u = smooth(progress(t, 24, 46));
                    double radius = Math.sin(u * Math.PI) * 0.7;
                    double angle = phase + u * Math.PI * 3;
                    scene.moveHead(soul, Math.cos(angle) * radius, 2.05 * (1 - u) + 0.2 * u,
                            Math.sin(angle) * radius, (float) (context.death.yaw + 90 + u * 540));
                    helix(Particle.PORTAL, -t, 0.8 * (1 - u), 0.1, 2 * (1 - u), 0);
                }
                if (t < 46 && elapsed % 2 == 0) {
                    ring(Particle.PORTAL, smooth(progress(t, 0, 10)) * 1.15, 0.12);
                    ring(Particle.SMOKE, 0.55, 0.15);
                }
                if (at(46)) { scene.destroy(soul); climax(Sound.FIZZ, 0.3, 0.5f); }
                if (t >= 46 && t < 57) ring(Particle.PORTAL, 1.15 * (1 - smooth(progress(t, 46, 57))), 0.15);
            }
        };
    }
}
