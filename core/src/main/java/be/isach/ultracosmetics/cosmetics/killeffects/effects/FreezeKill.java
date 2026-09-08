package be.isach.ultracosmetics.cosmetics.killeffects.effects;

import be.isach.ultracosmetics.UltraCosmetics;
import be.isach.ultracosmetics.config.SettingsManager;
import be.isach.ultracosmetics.cosmetics.killeffects.KillEffect;
import be.isach.ultracosmetics.cosmetics.killeffects.KillEffectContext;
import be.isach.ultracosmetics.cosmetics.killeffects.render.KillEffectRenderer.Particle;
import be.isach.ultracosmetics.cosmetics.killeffects.runtime.AbstractKillEffectExecution;
import be.isach.ultracosmetics.cosmetics.killeffects.runtime.KillEffectExecution;
import be.isach.ultracosmetics.cosmetics.killeffects.runtime.KillEffectScene;
import be.isach.ultracosmetics.cosmetics.killeffects.runtime.KillEffectSettings;
import be.isach.ultracosmetics.cosmetics.type.KillEffectType;
import be.isach.ultracosmetics.player.UltraPlayer;
import java.util.Random;

public final class FreezeKill extends KillEffect {
    private static final double[] CIRCLE_X = new double[24], CIRCLE_Z = new double[24];
    static {
        for (int i = 0; i < 24; i++) {
            CIRCLE_X[i] = Math.cos(i * Math.PI / 12);
            CIRCLE_Z[i] = Math.sin(i * Math.PI / 12);
        }
    }

    public FreezeKill(UltraPlayer owner, KillEffectType type, UltraCosmetics plugin) { super(owner, type, plugin); }

    @Override
    protected KillEffectExecution createExecution(KillEffectContext context, KillEffectScene scene) {
        final int duration = context.lite ? 12 : KillEffectSettings.clamp(
                SettingsManager.getConfig().getInt(getType().getConfigPath() + ".Duration", 60), 60, 80);
        return new Execution(context, scene, duration);
    }

    private static final class Execution extends AbstractKillEffectExecution {
        private final KillEffectContext context;
        private final KillEffectScene scene;
        private final int duration;
        private final Random random;
        private int body = -1, lower = -1, upper = -1;
        private int previousTick = -1;

        private Execution(KillEffectContext context, KillEffectScene scene, int duration) {
            super(duration, scene::close);
            this.context = context;
            this.scene = scene;
            this.duration = duration;
            random = new Random(context.seed);
        }

        @Override
        protected void onTick(int elapsed) {
            int t = context.lite ? elapsed : (int) ((long) elapsed * 60 / duration);
            if (elapsed == 0) {
                if (!context.lite && !context.airborne) body = scene.spawnPlayer();
                ring(Particle.SNOW, 0.8, 0.15, 24);
                scene.sound(0.35f, 0.7f);
            }
            if (context.lite) {
                if (t < 7) ring(Particle.CYAN, 0.7 - t * 0.07, t * 0.22, 12);
                return;
            }
            if (!context.airborne) {
                if (t >= 1 && lower == -1) lower = scene.spawnIce(0);
                if (t >= 4 && upper == -1) upper = scene.spawnIce(1);
                // Provisional 2-tick correction; the real-server spike must establish the lowest stable frequency.
                if (elapsed % 2 == 0 && t < 56) {
                    double vibration = t >= 43 ? Math.sin(t * 2.4) * 0.025 : 0;
                    scene.stabilizeIce(lower, vibration, 0);
                    scene.stabilizeIce(upper, -vibration, 1);
                }
            }
            if (t == previousTick) return;
            previousTick = t;
            switch (FreezeTimeline.phase(t)) {
                case FREEZE:
                    ring(Particle.ICE, 0.55, t / 3.0, 12);
                    break;
                case SEAL:
                    if (elapsed % 2 == 0) ring(Particle.CYAN, 0.65, 1, 24);
                    break;
                case HOLD:
                    if (elapsed % 5 == 0) {
                        scene.particle(Particle.SNOW, random.nextDouble() - 0.5, 1.9, random.nextDouble() - 0.5, 0);
                        scene.particle(Particle.CLOUD, 0, 1.55, 0, 0);
                    }
                    break;
                case FRACTURE:
                    ring(Particle.ICE, 0.54, random.nextDouble() * 1.8, 6 + (t - 43));
                    if (t == 43) scene.sound(0.18f, 1.8f);
                    break;
                case SHATTER:
                    if (t == 53) scene.sound(0.65f, 1.3f);
                    ring(Particle.ICE, 0.6 + (t - 53) * 0.28, 1 + (t - 53) * 0.08, 24);
                    if (t >= 54) scene.destroy(upper);
                    if (t >= 56) scene.destroy(lower);
                    if (t == 57) scene.status(body, (byte) 3);
                    break;
                case CLEANUP:
                    ring(Particle.CLOUD, 0.35, 1, 12);
                    scene.destroy(body);
                    break;
                default: break;
            }
        }

        private void ring(Particle particle, double radius, double height, int count) {
            int capped = Math.min(24, count);
            for (int i = 0; i < capped; i++) {
                int index = i * 24 / capped;
                scene.particle(particle, CIRCLE_X[index] * radius, height, CIRCLE_Z[index] * radius, i);
            }
        }
    }
}
