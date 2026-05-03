package de.klausiiiii.mobArmyBattle.wave;

import de.klausiiiii.mobArmyBattle.pool.MobEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WaveSlotTest {

    private static final MobEntry ZOMBIE = new MobEntry("ZOMBIE", "none|none|none|none|none|none");

    @Test
    void slotHoldsEntryAndCount() {
        WaveSlot slot = new WaveSlot(ZOMBIE, 5);
        assertEquals(ZOMBIE, slot.getEntry());
        assertEquals(5, slot.getCount());
    }

    @Test
    void rejectsNullEntry() {
        assertThrows(IllegalArgumentException.class, () -> new WaveSlot(null, 1));
    }

    @Test
    void rejectsZeroOrNegativeCount() {
        assertThrows(IllegalArgumentException.class, () -> new WaveSlot(ZOMBIE, 0));
        assertThrows(IllegalArgumentException.class, () -> new WaveSlot(ZOMBIE, -1));
    }

    @Test
    void slotsWithSameEntryAndCountAreEqual() {
        WaveSlot a = new WaveSlot(ZOMBIE, 3);
        WaveSlot b = new WaveSlot(ZOMBIE, 3);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void slotsWithDifferentCountAreNotEqual() {
        WaveSlot a = new WaveSlot(ZOMBIE, 3);
        WaveSlot b = new WaveSlot(ZOMBIE, 5);
        assertNotEquals(a, b);
    }
}
