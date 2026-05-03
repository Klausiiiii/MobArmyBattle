package de.klausiiiii.mobArmyBattle.pool;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MobPoolTest {

    @Test
    void newPoolIsEmpty() {
        MobPool pool = new MobPool();
        assertEquals(0, pool.totalCount());
        assertTrue(pool.getEntries().isEmpty());
    }

    @Test
    void addingFirstMobCreatesEntry() {
        MobPool pool = new MobPool();
        pool.add(new MobEntry("ZOMBIE", "none|none|none|none|none|none"));
        assertEquals(1, pool.totalCount());
        assertEquals(1, pool.countOf(new MobEntry("ZOMBIE", "none|none|none|none|none|none")));
    }

    @Test
    void addingSameMobIncrementsCount() {
        MobPool pool = new MobPool();
        MobEntry zombie = new MobEntry("ZOMBIE", "none|none|none|none|none|none");
        pool.add(zombie);
        pool.add(zombie);
        pool.add(zombie);
        assertEquals(3, pool.totalCount());
        assertEquals(3, pool.countOf(zombie));
    }

    @Test
    void differentEquipmentTrackedSeparately() {
        MobPool pool = new MobPool();
        MobEntry vanilla = new MobEntry("SKELETON", "none|none|none|none|none|none");
        MobEntry iron = new MobEntry("SKELETON", "bow|none|iron_helmet|iron_chestplate|none|none");
        pool.add(vanilla);
        pool.add(iron);
        pool.add(iron);
        assertEquals(3, pool.totalCount());
        assertEquals(1, pool.countOf(vanilla));
        assertEquals(2, pool.countOf(iron));
    }

    @Test
    void countOfReturnsZeroForUnknownEntry() {
        MobPool pool = new MobPool();
        pool.add(new MobEntry("ZOMBIE", "none|none|none|none|none|none"));
        assertEquals(0, pool.countOf(new MobEntry("CREEPER", "none|none|none|none|none|none")));
    }

    @Test
    void canRemoveEntries() {
        MobPool pool = new MobPool();
        MobEntry e = new MobEntry("ZOMBIE", "none|none|none|none|none|none");
        pool.add(e); pool.add(e); pool.add(e);
        int removed = pool.remove(e, 2);
        assertEquals(2, removed);
        assertEquals(1, pool.countOf(e));
    }

    @Test
    void removingMoreThanAvailableRemovesAll() {
        MobPool pool = new MobPool();
        MobEntry e = new MobEntry("ZOMBIE", "none|none|none|none|none|none");
        pool.add(e); pool.add(e);
        int removed = pool.remove(e, 10);
        assertEquals(2, removed);
        assertEquals(0, pool.countOf(e));
    }

    @Test
    void removingDownToZeroDropsEntry() {
        MobPool pool = new MobPool();
        MobEntry e = new MobEntry("ZOMBIE", "none|none|none|none|none|none");
        pool.add(e);
        pool.remove(e, 1);
        assertTrue(pool.getEntries().isEmpty());
    }

    @Test
    void applyPenaltyReducesEachEntryByPercent() {
        MobPool pool = new MobPool();
        MobEntry zombie = new MobEntry("ZOMBIE", "none|none|none|none|none|none");
        MobEntry skeleton = new MobEntry("SKELETON", "none|none|none|none|none|none");
        for (int i = 0; i < 100; i++) pool.add(zombie);
        for (int i = 0; i < 50; i++) pool.add(skeleton);
        int lost = pool.applyPenalty(10);
        assertEquals(15, lost);
        assertEquals(90, pool.countOf(zombie));
        assertEquals(45, pool.countOf(skeleton));
    }

    @Test
    void penaltyZeroDoesNothing() {
        MobPool pool = new MobPool();
        pool.add(new MobEntry("ZOMBIE", "none|none|none|none|none|none"));
        int lost = pool.applyPenalty(0);
        assertEquals(0, lost);
        assertEquals(1, pool.totalCount());
    }

    @Test
    void penaltyHundredEmptiesPool() {
        MobPool pool = new MobPool();
        for (int i = 0; i < 5; i++) pool.add(new MobEntry("ZOMBIE", "none|none|none|none|none|none"));
        int lost = pool.applyPenalty(100);
        assertEquals(5, lost);
        assertEquals(0, pool.totalCount());
    }

    @Test
    void penaltyRoundsDown() {
        MobPool pool = new MobPool();
        for (int i = 0; i < 7; i++) pool.add(new MobEntry("ZOMBIE", "none|none|none|none|none|none"));
        int lost = pool.applyPenalty(10);
        assertEquals(0, lost);
        assertEquals(7, pool.totalCount());
    }

    @Test
    void getEntriesReturnsImmutableSnapshot() {
        MobPool pool = new MobPool();
        pool.add(new MobEntry("ZOMBIE", "none|none|none|none|none|none"));
        Map<MobEntry, Integer> snapshot = pool.getEntries();
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.put(new MobEntry("CREEPER", "none|none|none|none|none|none"), 1));
    }
}
