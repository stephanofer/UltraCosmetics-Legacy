package be.isach.ultracosmetics.cosmetics.killeffects;

import be.isach.ultracosmetics.cosmetics.killeffects.effects.FreezeTimeline;
import org.junit.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
    public void snapshotsAndAudienceDoNotRetainMutableCollections() {
        UUID id = UUID.randomUUID();
        List<VictimSnapshot.SkinProperty> skin = new ArrayList<>();
        skin.add(new VictimSnapshot.SkinProperty("textures", "value", "signature"));
        Set<UUID> visible = new HashSet<>(); visible.add(id);
        VictimSnapshot victim = new VictimSnapshot(id, "Victim", skin, visible);
        Map<UUID, Boolean> audience = new HashMap<>(); audience.put(id, true);
        KillEffectPosition at = new KillEffectPosition(id, -0.1, 64, -16.1, 0, 0);
        KillEffectContext context = new KillEffectContext(id, "Killer", victim, at, at, audience, 123, 0, false, false, false);
        skin.clear(); visible.clear(); audience.clear();
        assertEquals(1, victim.skin.size()); assertEquals(1, victim.visibleTo.size()); assertEquals(1, context.audience.size());
        assertEquals(-1, at.chunkX()); assertEquals(-2, at.chunkZ());
        try { context.audience.clear(); fail("Audience must be immutable"); } catch (UnsupportedOperationException expected) { }
    }

    @Test(expected = IllegalArgumentException.class)
    public void nonFinitePositionsAreRejected() {
        new KillEffectPosition(UUID.randomUUID(), Double.NaN, 64, 0, 0, 0);
    }
}
