package be.isach.ultracosmetics.cosmetics.killeffects.runtime;

import org.junit.Test;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.*;
import static be.isach.ultracosmetics.cosmetics.killeffects.runtime.CapacityPolicy.Detail.*;

public class RuntimeLimitsTest {
    @Test
    public void budgetsCountFanoutAndRejectOverflowWithoutSpending() {
        PacketBudget budget = new PacketBudget(24, 480);
        assertTrue(budget.spend(24, 24 * 20));
        assertFalse(budget.spend(1, 1));
        assertFalse(budget.spend(Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertFalse(budget.spend(-1, 0));
        assertEquals(24, budget.getPoints());
        assertEquals(480, budget.getSends());
        budget.reset();
        assertTrue(budget.spend(1, 20));
    }

    @Test
    public void allocatorDoesNotRecycleReleasedOrActiveIds() {
        EntityIdAllocator allocator = new EntityIdAllocator();
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 10000; i++) {
            int id = allocator.allocate();
            assertTrue(id > 1_900_000_000);
            assertTrue(seen.add(id));
            if (i % 2 == 0) { allocator.release(id); allocator.release(id); }
        }
        assertEquals(5000, allocator.activeCount());
    }

    @Test
    public void fullLiteAndSkipRespectAllCapacityBoundaries() {
        assertEquals(FULL, CapacityPolicy.decide(0, 0, 0, 12, 6, 2, true));
        assertEquals(LITE, CapacityPolicy.decide(2, 2, 2, 12, 6, 2, true));
        assertEquals(SKIP, CapacityPolicy.decide(2, 2, 2, 12, 6, 2, false));
        assertEquals(SKIP, CapacityPolicy.decide(12, 0, 0, 12, 6, 2, true));
        assertEquals(SKIP, CapacityPolicy.decide(6, 6, 0, 12, 6, 2, true));
    }

    @Test
    public void defaultsMatchApprovedLimits() {
        KillEffectSettings s = new KillEffectSettings(Collections.emptyMap());
        assertEquals(32, s.range); assertEquals(16, s.fullRange);
        assertEquals(12, s.maxGlobal); assertEquals(6, s.maxWorld); assertEquals(2, s.maxChunk);
        assertEquals(200, s.maxDuration); assertEquals(17, s.maxEntities); assertEquals(100, s.cooldown);
        assertTrue(s.sounds); assertTrue(s.respectVanish); assertTrue(s.lite);
    }

    @Test
    public void fullAudienceAdmissionReservesStructuralSendsWithoutOverflow() {
        assertTrue(CapacityPolicy.supportsFullAudience(42, 0));
        assertTrue(CapacityPolicy.supportsFullAudience(22, 3000));
        assertFalse(CapacityPolicy.supportsFullAudience(23, 3000));
        assertFalse(CapacityPolicy.supportsFullAudience(43, 0));
        assertFalse(CapacityPolicy.supportsFullAudience(Integer.MAX_VALUE, 0));
        assertFalse(CapacityPolicy.supportsFullAudience(0, 0));
    }

    @Test
    public void invalidAndOversizedSettingsAreClampedBeforeNarrowing() {
        Map<String, Object> values = new HashMap<>();
        String p = "Kill-Effects-Settings.";
        values.put(p + "Visible-Range", -50);
        values.put(p + "Full-Detail-Range", Long.MAX_VALUE);
        values.put(p + "Max-Active-Global", 1);
        values.put(p + "Max-Active-Per-World", 100);
        values.put(p + "Max-Full-Effects-Per-Chunk", 100);
        values.put(p + "Preview-Cooldown", Long.MAX_VALUE);
        values.put(p + "Max-Duration-Ticks", Double.NaN);
        values.put(p + "Max-Entities-Per-Scene", "invalid");
        KillEffectSettings s = new KillEffectSettings(values);
        assertEquals(4, s.range); assertEquals(4, s.fullRange);
        assertEquals(1, s.maxWorld); assertEquals(1, s.maxChunk);
        assertEquals(1200, s.cooldown); assertEquals(200, s.maxDuration); assertEquals(17, s.maxEntities);
    }

    @Test
    public void previousLimitsCannotTruncateTheLargerLongerFreeze() {
        Map<String, Object> values = new HashMap<>();
        values.put("Kill-Effects-Settings.Max-Duration-Ticks", 100);
        values.put("Kill-Effects-Settings.Max-Entities-Per-Scene", 12);
        KillEffectSettings s = new KillEffectSettings(values);
        assertEquals(160, s.maxDuration);
        assertEquals(17, s.maxEntities);
    }
}
