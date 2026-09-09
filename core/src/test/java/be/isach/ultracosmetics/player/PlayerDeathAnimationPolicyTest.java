package be.isach.ultracosmetics.player;

import be.isach.ultracosmetics.cosmetics.killeffects.runtime.EntityIdAllocator;
import org.junit.Test;
import java.util.UUID;
import static org.junit.Assert.*;

public class PlayerDeathAnimationPolicyTest {
    @Test
    public void deadBodyIsHiddenOnlyFromOthersUntilRespawn() {
        PlayerDeathAnimationPolicy policy = new PlayerDeathAnimationPolicy();
        UUID player = UUID.randomUUID();
        policy.track(player, 42);
        assertFalse(policy.hideBody(42, 43));
        policy.markDead(player, 42);
        assertTrue(policy.hideBody(42, 43));
        assertFalse(policy.hideBody(42, 42));
        assertFalse(policy.hideBody(new EntityIdAllocator().allocate(), 43));
        policy.respawn(player);
        assertFalse(policy.hideBody(42, 43));
        assertTrue(policy.suppress(42, 3));
        policy.markDead(player, 42);
        assertTrue(policy.hideBody(42, 43));
        policy.remove(player);
        assertFalse(policy.hideBody(42, 43));
    }

    @Test
    public void changedIdsAndShutdownClearHiddenBodies() {
        PlayerDeathAnimationPolicy policy = new PlayerDeathAnimationPolicy();
        UUID player = UUID.randomUUID();
        policy.markDead(player, 42);
        policy.track(player, 44);
        assertFalse(policy.hideBody(42, 43));
        assertFalse(policy.hideBody(44, 43));
        policy.markDead(player, 44);
        policy.clear();
        assertFalse(policy.hideBody(44, 43));
    }

    @Test
    public void onlyRemoteRealPlayersReceivePositiveVisualHealth() {
        PlayerDeathAnimationPolicy policy = new PlayerDeathAnimationPolicy();
        UUID player = UUID.randomUUID();
        policy.track(player, 42);
        assertEquals(1.0f, policy.visualHealth(42, 43, 0), 0);
        assertEquals(1.0f, policy.visualHealth(42, 43, -1), 0);
        assertEquals(0, policy.visualHealth(42, 42, 0), 0);
        assertEquals(12.5f, policy.visualHealth(42, 43, 12.5f), 0);
        assertEquals(0, policy.visualHealth(44, 43, 0), 0);
        assertEquals(0, policy.visualHealth(new EntityIdAllocator().allocate(), 43, 0), 0);
        policy.remove(player);
        assertEquals(0, policy.visualHealth(42, 43, 0), 0);
    }

    @Test
    public void blocksOnlyDeathStatusOfRegisteredPlayers() {
        PlayerDeathAnimationPolicy policy = new PlayerDeathAnimationPolicy();
        policy.track(UUID.randomUUID(), 42);
        assertTrue(policy.suppress(42, 3));
        for (int status = 0; status < 256; status++) {
            if (status != 3) assertFalse(policy.suppress(42, status));
        }
        assertFalse(policy.suppress(43, 3));
        assertFalse(policy.suppress(new EntityIdAllocator().allocate(), 3));
    }

    @Test
    public void refreshAndDisconnectDoNotLeaveStaleEntityIds() {
        PlayerDeathAnimationPolicy policy = new PlayerDeathAnimationPolicy();
        UUID player = UUID.randomUUID();
        policy.track(player, 42);
        policy.track(player, 42);
        assertTrue(policy.suppress(42, 3));
        policy.track(player, 44);
        assertFalse(policy.suppress(42, 3));
        assertTrue(policy.suppress(44, 3));
        policy.remove(player);
        policy.remove(player);
        assertFalse(policy.suppress(44, 3));
    }

    @Test
    public void clearRestoresVanillaForAllPlayers() {
        PlayerDeathAnimationPolicy policy = new PlayerDeathAnimationPolicy();
        policy.track(UUID.randomUUID(), 42);
        policy.track(UUID.randomUUID(), 43);
        policy.clear();
        assertFalse(policy.suppress(42, 3));
        assertFalse(policy.suppress(43, 3));
    }
}
