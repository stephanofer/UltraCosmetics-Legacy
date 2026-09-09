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

public final class DivineJudgment extends KillEffect {
    public DivineJudgment(UltraPlayer owner, KillEffectType type, UltraCosmetics plugin) { super(owner, type, plugin); }

    @Override
    protected KillEffectExecution createExecution(KillEffectContext context, KillEffectScene scene) {
        return new CatalogExecution(context, scene, getType()) {
            private final double[] boltX = new double[17], boltZ = new double[17];
            {
                java.util.Random random = new java.util.Random(context.seed);
                for (int i = 1; i < 16; i++) {
                    boltX[i] = (random.nextDouble() - 0.5) * 0.65;
                    boltZ[i] = (random.nextDouble() - 0.5) * 0.65;
                }
            }

            @Override
            protected void frame(double t, int elapsed) {
                if (at(0)) { victim(); sound(Sound.CHIME, 2.5, 0.4f, 0.8f); }
                if (t < 30) {
                    moveVictim(0, smooth(progress(t, 0, 24)) * 0.7, 0, context.death.yaw);
                    if (elapsed % 2 == 0) {
                        ring(Particle.GOLD, 0.75, 2.8);
                        ring(Particle.WHITE, 0.7, 0.1 + smooth(progress(t, 0, 24)) * 1.8);
                    }
                }
                if (at(30)) { if (!lite) scene.status(subject, (byte) 2); climax(Sound.THUNDER, 2, 1.5f); }
                if (t >= 30 && t < 40) {
                    // A bounded electrical polyline avoids the global sky flash of a weather entity.
                    if (elapsed % 3 != 2) for (int i = 0; i < 16; i++) {
                        for (int j = 0; j < (lite ? 1 : 3); j++) {
                            double u = j / 3.0;
                            scene.particle(Particle.WHITE, boltX[i] + (boltX[i + 1] - boltX[i]) * u,
                                    5.5 - (i + u) * 5.2 / 16, boltZ[i] + (boltZ[i + 1] - boltZ[i]) * u, i * 3 + j);
                        }
                    }
                    ring(Particle.SPARK, 0.4 + progress(t, 30, 40) * 1.1, 1.3);
                }
                if (at(37)) removeVictim();
                if (t >= 40 && t < 56) {
                    double u = progress(t, 40, 56);
                    helix(Particle.GOLD, t, 0.5 * (1 - u), 1.6 + u * 1.8, 1 - u, 0);
                    if (elapsed % 2 == 0) ring(Particle.WHITE, 1.5 + u * 0.4, 0.15);
                }
            }
        };
    }
}
