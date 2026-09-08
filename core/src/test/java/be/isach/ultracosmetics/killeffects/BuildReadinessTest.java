package be.isach.ultracosmetics.killeffects;

import org.junit.Test;
import junit.runner.Version;

import static org.junit.Assert.assertEquals;

public class BuildReadinessTest {

    @Test
    public void usesApprovedJUnitVersion() {
        assertEquals("4.13.2", Version.id());
    }
}
