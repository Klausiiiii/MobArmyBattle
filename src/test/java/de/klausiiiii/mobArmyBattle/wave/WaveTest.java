package de.klausiiiii.mobArmyBattle.wave;

import de.klausiiiii.mobArmyBattle.pool.MobEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WaveTest {

    private static final MobEntry ZOMBIE = new MobEntry("ZOMBIE", "none|none|none|none|none|none");
    private static final MobEntry SKELETON = new MobEntry("SKELETON", "none|none|none|none|none|none");

    @Test
    void newWaveIsEmpty() {
        Wave wave = new Wave();
        assertEquals(0, wave.totalMobCount());
        assertTrue(wave.getSlots().isEmpty());
        assertFalse(wave.isFinalised());
    }

    @Test
    void canAddSlot() {
        Wave wave = new Wave();
        wave.add(ZOMBIE, 5);
        assertEquals(5, wave.totalMobCount());
        assertEquals(5, wave.countOf(ZOMBIE));
    }

    @Test
    void addingExistingEntryIncrementsCount() {
        Wave wave = new Wave();
        wave.add(ZOMBIE, 3);
        wave.add(ZOMBIE, 4);
        assertEquals(7, wave.countOf(ZOMBIE));
        assertEquals(7, wave.totalMobCount());
    }

    @Test
    void differentEntriesTrackedSeparately() {
        Wave wave = new Wave();
        wave.add(ZOMBIE, 5);
        wave.add(SKELETON, 3);
        assertEquals(5, wave.countOf(ZOMBIE));
        assertEquals(3, wave.countOf(SKELETON));
        assertEquals(8, wave.totalMobCount());
    }

    @Test
    void canRemoveCount() {
        Wave wave = new Wave();
        wave.add(ZOMBIE, 5);
        int removed = wave.remove(ZOMBIE, 2);
        assertEquals(2, removed);
        assertEquals(3, wave.countOf(ZOMBIE));
    }

    @Test
    void removingMoreThanAvailableRemovesAll() {
        Wave wave = new Wave();
        wave.add(ZOMBIE, 3);
        int removed = wave.remove(ZOMBIE, 99);
        assertEquals(3, removed);
        assertEquals(0, wave.countOf(ZOMBIE));
    }

    @Test
    void removingDownToZeroDropsSlot() {
        Wave wave = new Wave();
        wave.add(ZOMBIE, 1);
        wave.remove(ZOMBIE, 1);
        assertTrue(wave.getSlots().isEmpty());
    }

    @Test
    void cannotFinaliseEmptyWave() {
        Wave wave = new Wave();
        assertThrows(IllegalStateException.class, wave::finalise);
    }

    @Test
    void canFinaliseNonEmptyWave() {
        Wave wave = new Wave();
        wave.add(ZOMBIE, 1);
        wave.finalise();
        assertTrue(wave.isFinalised());
    }

    @Test
    void cannotModifyFinalisedWave() {
        Wave wave = new Wave();
        wave.add(ZOMBIE, 1);
        wave.finalise();
        assertThrows(IllegalStateException.class, () -> wave.add(SKELETON, 1));
        assertThrows(IllegalStateException.class, () -> wave.remove(ZOMBIE, 1));
    }

    @Test
    void getSlotsIsImmutable() {
        Wave wave = new Wave();
        wave.add(ZOMBIE, 1);
        List<WaveSlot> slots = wave.getSlots();
        assertThrows(UnsupportedOperationException.class,
                () -> slots.add(new WaveSlot(SKELETON, 1)));
    }
}
