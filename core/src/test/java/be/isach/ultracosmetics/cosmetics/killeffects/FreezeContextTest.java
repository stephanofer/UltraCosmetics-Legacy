package be.isach.ultracosmetics.cosmetics.killeffects;

import be.isach.ultracosmetics.cosmetics.killeffects.effects.FreezeTimeline;
import be.isach.ultracosmetics.cosmetics.killeffects.render.KillEffectRenderer;
import be.isach.ultracosmetics.cosmetics.killeffects.runtime.EntityIdAllocator;
import be.isach.ultracosmetics.cosmetics.killeffects.runtime.KillEffectScene;
import be.isach.ultracosmetics.cosmetics.killeffects.runtime.KillEffectSettings;
import be.isach.ultracosmetics.cosmetics.killeffects.runtime.PacketBudget;
import org.junit.Test;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import static org.junit.Assert.*;
import static be.isach.ultracosmetics.cosmetics.killeffects.effects.FreezeTimeline.Phase.*;

public class FreezeContextTest {
    @Test
    public void timelineMatchesEveryApprovedBoundary() {
        assertEquals(IMPACT, FreezeTimeline.phase(0));
        assertEquals(FREEZE, FreezeTimeline.phase(1)); assertEquals(FREEZE, FreezeTimeline.phase(6));
        assertEquals(SEAL, FreezeTimeline.phase(7)); assertEquals(SEAL, FreezeTimeline.phase(12));
        assertEquals(HOLD, FreezeTimeline.phase(13)); assertEquals(HOLD, FreezeTimeline.phase(42));
        assertEquals(FRACTURE, FreezeTimeline.phase(43)); assertEquals(FRACTURE, FreezeTimeline.phase(52));
        assertEquals(SHATTER, FreezeTimeline.phase(53)); assertEquals(SHATTER, FreezeTimeline.phase(58));
        assertEquals(CLEANUP, FreezeTimeline.phase(59)); assertEquals(CLEANUP, FreezeTimeline.phase(60));
    }

    @Test
    public void longerDurationExtendsOnlyHoldAndStillReachesCleanup() {
        for (int duration : new int[]{120, 140, 160}) {
            for (int elapsed = 0; elapsed <= 42; elapsed++) {
                assertEquals(elapsed, FreezeTimeline.animationTick(elapsed, duration));
            }
            for (int elapsed = 43; elapsed < duration - 17; elapsed++) {
                assertEquals(HOLD, FreezeTimeline.phase(FreezeTimeline.animationTick(elapsed, duration)));
            }
            assertEquals(43, FreezeTimeline.animationTick(duration - 17, duration));
            assertEquals(53, FreezeTimeline.animationTick(duration - 7, duration));
            assertEquals(59, FreezeTimeline.animationTick(duration - 1, duration));
        }
    }

    @Test
    public void snapshotsAndAudienceDoNotRetainMutableCollections() {
        UUID id = UUID.randomUUID();
        List<VictimSnapshot.SkinProperty> skin = new ArrayList<>();
        skin.add(new VictimSnapshot.SkinProperty("textures", "value", "signature"));
        Set<UUID> visible = new HashSet<>(); visible.add(id);
        VictimSnapshot victim = new VictimSnapshot(id, "Victim", skin, visible);
        Map<UUID, Boolean> audience = new HashMap<>(); audience.put(id, true);
        KillEffectPosition death = new KillEffectPosition(id, -0.1, 67.375, -16.1, 123.5f, -27.25f);
        KillEffectPosition anchor = new KillEffectPosition(id, -0.1, 64, -16.1, 0, 0);
        KillEffectContext context = new KillEffectContext(id, "Killer", victim, death, anchor, audience, 123, 0, false, true, false);
        skin.clear(); visible.clear(); audience.clear();
        assertEquals(1, victim.skin.size()); assertEquals(1, victim.visibleTo.size()); assertEquals(1, context.audience.size());
        assertEquals(-1, death.chunkX()); assertEquals(-2, death.chunkZ());
        assertEquals(67.375, context.death.y, 0); assertEquals(123.5f, context.death.yaw, 0); assertEquals(-27.25f, context.death.pitch, 0);
        assertEquals(64, context.anchor.y, 0); assertTrue(context.airborne);
        try { context.audience.clear(); fail("Audience must be immutable"); } catch (UnsupportedOperationException expected) { }
    }

    @Test(expected = IllegalArgumentException.class)
    public void nonFinitePositionsAreRejected() {
        new KillEffectPosition(UUID.randomUUID(), Double.NaN, 64, 0, 0, 0);
    }

    @Test
    public void iceFormsTouchingTwoByTwoLayersEvenAfterLegacyPacketRounding() {
        for (float yaw : new float[]{0, 90, 180, -90, 45, 123.5f}) {
            Map<Integer, double[]> spawned = new HashMap<>();
            Map<Integer, double[]> teleported = new HashMap<>();
            Set<Integer> hidden = new HashSet<>(), equipped = new HashSet<>();
            KillEffectRenderer renderer = (KillEffectRenderer) Proxy.newProxyInstance(
                    KillEffectRenderer.class.getClassLoader(), new Class<?>[]{KillEffectRenderer.class},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if (name.equals("spawnArmorStand") || name.equals("teleportEntity")) {
                            double[] position = {(double) args[2], (double) args[3], (double) args[4], (float) args[5]};
                            (name.equals("spawnArmorStand") ? spawned : teleported).put((int) args[1], position);
                        } else if (name.equals("hideArmorStand")) {
                            hidden.add((int) args[1]);
                        } else if (name.equals("equipIceHelmet")) {
                            equipped.add((int) args[1]);
                        }
                        return null;
                    });
            UUID viewer = UUID.randomUUID(), world = UUID.randomUUID();
            VictimSnapshot victim = new VictimSnapshot(UUID.randomUUID(), "Victim",
                    Collections.emptyList(), Collections.singleton(viewer));
            KillEffectPosition death = new KillEffectPosition(world, -0.1, 67.375, -16.1, yaw, -27.25f);
            KillEffectPosition anchor = new KillEffectPosition(world, -0.1, 64, -16.1, 0, 0);
            KillEffectContext context = new KillEffectContext(viewer, "Killer", victim, death, anchor,
                    Collections.singletonMap(viewer, true), 123, 0, false, true, false);
            KillEffectScene scene = new KillEffectScene(context, renderer, new EntityIdAllocator(),
                    new KillEffectSettings(Collections.emptyMap()), new PacketBudget(1024, 12000),
                    Logger.getAnonymousLogger());
            Set<List<Double>> clientCenters = new HashSet<>();
            double clientX = Math.floor(death.x * 32) / 32;
            double clientZ = Math.floor(death.z * 32) / 32;
            double firstStandY = Math.floor((death.y + 0.3125 - 1.6953125) * 32) / 32;
            for (int layer = 0; layer < 4; layer++) {
                int[] ids = scene.spawnIce(layer);
                assertEquals(4, ids.length);
                scene.stabilizeIce(ids, 0);
                for (int column = 0; column < ids.length; column++) {
                    double[] position = spawned.get(ids[column]);
                    double dx = position[0] - death.x, dz = position[2] - death.z;
                    assertEquals((column % 2 - 0.5) * 0.625, dx, 1e-9);
                    assertEquals((column / 2 - 0.5) * 0.625, dz, 1e-9);
                    // The rendered cube's bottom is the layer boundary, not the stand's feet.
                    assertEquals(layer * 0.625, position[1] + 1.6953125 - 0.3125 - death.y, 1e-9);
                    assertEquals(0, position[3], 0);
                    assertArrayEquals(position, teleported.get(ids[column]), 0);
                    double x = Math.floor(position[0] * 32) / 32;
                    double y = Math.floor(position[1] * 32) / 32;
                    double z = Math.floor(position[2] * 32) / 32;
                    assertEquals((column % 2 - 0.5) * 0.625, x - clientX, 0);
                    assertEquals(layer * 0.625, y - firstStandY, 0);
                    assertEquals((column / 2 - 0.5) * 0.625, z - clientZ, 0);
                    assertTrue("Each helmet must occupy a distinct grid cell", clientCenters.add(Arrays.asList(x, y, z)));
                }
            }
            assertEquals(16, spawned.size());
            assertEquals(16, clientCenters.size());
            assertEquals(spawned.keySet(), hidden);
            assertEquals(spawned.keySet(), equipped);
        }
    }
}
