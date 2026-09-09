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

public final class HeadRocket extends KillEffect {
    public HeadRocket(UltraPlayer owner, KillEffectType type, UltraCosmetics plugin) { super(owner, type, plugin); }

    @Override
    protected KillEffectExecution createExecution(KillEffectContext context, KillEffectScene scene) {
        return new CatalogExecution(context, scene, getType()) {
            private double height = 1.6;

            @Override
            protected void frame(double t, int elapsed) {
                if (at(0)) {
                    subject = scene.spawnHead(0, height, 0, context.death.yaw);
                    sound(Sound.IGNITE, height, 0.4f, 0.8f);
                }
                if (t < 12) {
                    scene.moveHead(subject, 0, 1.6 + Math.sin(t * 0.5) * 0.035, 0, context.death.yaw);
                    if (elapsed % 2 == 0) ring(Particle.GOLD, 0.25 + t * 0.025, 1.2);
                }
                if (at(12)) sound(Sound.LAUNCH, height, 0.55f, 1.2f);
                if (t >= 12 && t < 45) {
                    double u = progress(t, 12, 40);
                    height = 1.6 + (lite ? 2.7 : 4.5) * u * u;
                    scene.moveHead(subject, 0, height, 0, (float) (context.death.yaw + 540 * smooth(u)));
                    for (int i = 0; i < (lite ? 3 : 6); i++) {
                        double angle = phase + t * 0.35 + i * Math.PI;
                        scene.particle(i % 2 == 0 ? Particle.FLAME : Particle.SPARK,
                                Math.cos(angle) * 0.13, height - 0.4 - i * 0.13, Math.sin(angle) * 0.13, i);
                    }
                }
                if (at(45)) { removeVictim(); climax(Sound.BLAST, height, 1.3f); }
                if (t >= 45 && t < 58) {
                    double u = progress(t, 45, 58);
                    burst(t < 50 ? Particle.GOLD : Particle.SPARK, 0, height, 0, 0.15 + 1.5 * smooth(u), 0.7 * u * u);
                }
            }
        };
    }
}
