package dev.EfraGroup.formulaRacing.Utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MinecraftVersionTest {

    @Test
    void unknownVersionsAreRejected() {
        assertFalse(MinecraftVersion.isKnownVersion("abc"));
        assertFalse(MinecraftVersion.isKnownVersion("1.99"));
        assertFalse(MinecraftVersion.isKnownVersion(""));
        assertFalse(MinecraftVersion.isKnownVersion(null));
        assertTrue(MinecraftVersion.isKnownVersion("26.2"));
    }

    @Test
    void protocolsIncreaseAcrossTheVersionSchemeChange() {
        assertTrue(MinecraftVersion.getProtocol("1.21.4") < MinecraftVersion.getProtocol("26.2"));
        assertTrue(MinecraftVersion.getProtocol("1.20.4") < MinecraftVersion.getProtocol("1.21.1"));
        assertTrue(MinecraftVersion.getProtocol("26.2") < MinecraftVersion.getProtocol("26.3"));
    }

    @Test
    void missingRequirementNeverBlocks() {
        assertTrue(MinecraftVersion.meetsMinimum(769, (String) null));
        assertTrue(MinecraftVersion.meetsMinimum(769, ""));
        assertTrue(MinecraftVersion.meetsMinimum(769, "   "));
    }

    @Test
    void olderClientIsBlockedByNewerRequirement() {
        int v1_21_4 = MinecraftVersion.getProtocol("1.21.4");
        int v26_2 = MinecraftVersion.getProtocol("26.2");

        assertFalse(MinecraftVersion.meetsMinimum(v1_21_4, "26.2"));
        assertTrue(MinecraftVersion.meetsMinimum(v26_2, "26.2"));
        assertTrue(MinecraftVersion.meetsMinimum(v26_2 + 1, "26.2"));
        assertTrue(MinecraftVersion.meetsMinimum(v1_21_4, "1.21.4"));
        assertFalse(MinecraftVersion.meetsMinimum(v1_21_4 - 1, "1.21.4"));
    }

    @Test
    void unknownClientProtocolBlocksWhenRequirementIsSet() {
        assertFalse(MinecraftVersion.meetsMinimum(-1, "26.2"));
    }

    @Test
    void snapshotsAlwaysSatisfyReleaseRequirements() {
        assertTrue(MinecraftVersion.meetsMinimum(1073742163, "26.3"));
    }

    @Test
    void orphanStoredValueNeverBlocks() {
        assertTrue(MinecraftVersion.meetsMinimum(769, "abc"));
    }

    @Test
    void protocolRoundTripsToName() {
        assertEquals("26.2", MinecraftVersion.getName(MinecraftVersion.getProtocol("26.2")));
        assertEquals("1.21.4", MinecraftVersion.getName(MinecraftVersion.getProtocol("1.21.4")));
    }

    @Test
    void unknownProtocolAndSnapshotsAreLabeled() {
        assertTrue(MinecraftVersion.getName(12345).contains("12345"));
        assertEquals("Snapshot", MinecraftVersion.getName(1073742163));
        assertTrue(MinecraftVersion.isSnapshot(1073741824));
        assertFalse(MinecraftVersion.isSnapshot(776));
    }
}
