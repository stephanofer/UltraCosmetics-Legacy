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

public final class SquidMissile extends KillEffect {
    public SquidMissile(UltraPlayer owner, KillEffectType type, UltraCosmetics plugin) { super(owner, type, plugin); }

    @Override
    protected KillEffectExecution createExecution(KillEffectContext context, KillEffectScene scene) {
        return new CatalogExecution(context, scene, getType()) {
            private int squid = -1;
            private double height = 0.8;

            @Override
            protected void frame(double t, int elapsed) {
                if (at(0)) { victim(); sound(Sound.POP, 1, 0.4f, 0.6f); }
                if (t < 10) {
                    moveVictim(0, smooth(t / 10) * 0.35, 0, context.death.yaw);
                    ring(Particle.PORTAL, 0.8 * (1 - t / 12), 0.8 + t * 0.06);
                }
                if (at(10)) {
                    removeVictim();
                    squid = scene.spawnSquid(height);
                    sound(Sound.LAUNCH, height, 0.55f, 0.85f);
                }
                if (t >= 10 && t < 44) {
                    double u = progress(t, 10, 39);
                    height = 0.8 + (lite ? 3.4 : 5.6) * u * u;
                    scene.move(squid, 0, height, 0, context.death.yaw, 0);
                    for (int i = 0; i < (lite ? 2 : 5); i++) {
                        scene.particle(i % 2 == 0 ? Particle.FLAME : Particle.SMOKE,
                                Math.sin(phase + i * 2.4) * 0.12, height - 0.3 - i * 0.15,
                                Math.cos(phase + i * 2.4) * 0.12, i);
                    }
                    if (at(21) || at(32)) sound(Sound.POP, height, 0.35f, (float) (0.8 + u));
                }
                // Let legacy teleport interpolation reach the apex before destroying the missile.
                if (at(44)) { scene.destroy(squid); climax(Sound.BLAST, height, 0.8f); }
                if (t >= 44 && t < 58) {
                    double u = progress(t, 44, 58);
                    burst(t < 48 ? Particle.SPARK : Particle.CYAN, 0, height, 0, 0.2 + 1.6 * smooth(u), u * u * 0.5);
                }
            }
        };
    }
}
