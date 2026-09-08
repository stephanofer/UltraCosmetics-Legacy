package be.isach.ultracosmetics.cosmetics.killeffects.compatibility;

import org.junit.Test;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class KillEffectMigrationTest {
    @Test
    public void historicalAndPublicCategoriesResolveIdentically() {
        assertEquals("kill_effects", KillEffectMigration.category("DEATH_EFFECTS"));
        assertEquals("kill_effects", KillEffectMigration.category("Kill-Effects"));
        assertEquals("kill_effects", KillEffectMigration.category("kill_effects"));
        assertEquals("pets", KillEffectMigration.category("PETS"));
        assertEquals("", KillEffectMigration.category(null));
    }

    @Test
    public void aliasesCannotActivateBeforeTheirTargetExists() {
        Map<String, String> aliases = Collections.singletonMap("explosion", "bloodburst");
        assertNull(KillEffectMigration.effect("Explosion", aliases, Collections.singleton("freeze")));
        assertEquals("bloodburst", KillEffectMigration.effect("Explosion", aliases, Collections.singleton("bloodburst")));
        assertEquals("freeze", KillEffectMigration.effect("FREEZE", aliases, Collections.singleton("freeze")));
        assertNull(KillEffectMigration.effect(null, aliases, Collections.emptySet()));
    }

    @Test
    public void migrationIsIdempotentAndNeverOverwritesExplicitNewSettings() {
        Map<String, Boolean> existing = new HashMap<>();
        Map<String, Boolean> old = Collections.singletonMap("Death-Effects", false);
        Map<String, String> paths = Collections.singletonMap("Death-Effects", "Kill-Effects");
        Map<String, Boolean> changes = KillEffectMigration.missingSettings(existing, old, paths);
        assertEquals(Boolean.FALSE, changes.get("Kill-Effects"));
        existing.putAll(changes);
        assertTrue(KillEffectMigration.missingSettings(existing, old, paths).isEmpty());
        existing.put("Kill-Effects", true);
        assertTrue(KillEffectMigration.missingSettings(existing, old, paths).isEmpty());
        assertEquals(Boolean.FALSE, old.get("Death-Effects"));
    }
}
