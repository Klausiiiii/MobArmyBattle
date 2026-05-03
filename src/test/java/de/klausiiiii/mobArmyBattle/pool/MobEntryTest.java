package de.klausiiiii.mobArmyBattle.pool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MobEntryTest {

    @Test
    void entriesWithSameTypeAndEquipmentAreEqual() {
        MobEntry a = new MobEntry("ZOMBIE", "none|none|none|none|none|none");
        MobEntry b = new MobEntry("ZOMBIE", "none|none|none|none|none|none");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void entriesWithDifferentTypeAreNotEqual() {
        MobEntry a = new MobEntry("ZOMBIE", "none|none|none|none|none|none");
        MobEntry b = new MobEntry("SKELETON", "none|none|none|none|none|none");
        assertNotEquals(a, b);
    }

    @Test
    void entriesWithDifferentEquipmentAreNotEqual() {
        MobEntry a = new MobEntry("ZOMBIE", "none|none|none|none|none|none");
        MobEntry b = new MobEntry("ZOMBIE", "iron_sword|none|none|none|none|none");
        assertNotEquals(a, b);
    }

    @Test
    void rejectsNullType() {
        assertThrows(IllegalArgumentException.class,
                () -> new MobEntry(null, "none|none|none|none|none|none"));
    }

    @Test
    void rejectsBlankType() {
        assertThrows(IllegalArgumentException.class,
                () -> new MobEntry("", "none|none|none|none|none|none"));
    }

    @Test
    void rejectsNullEquipment() {
        assertThrows(IllegalArgumentException.class,
                () -> new MobEntry("ZOMBIE", null));
    }

    @Test
    void toStringContainsTypeAndEquipment() {
        MobEntry e = new MobEntry("ZOMBIE", "iron_sword|none|none|none|none|none");
        String s = e.toString();
        assertTrue(s.contains("ZOMBIE"));
        assertTrue(s.contains("iron_sword"));
    }
}
