package de.klausiiiii.mobArmyBattle.wave;

import de.klausiiiii.mobArmyBattle.pool.MobEntry;

import java.util.Objects;

public final class WaveSlot {

    private final MobEntry entry;
    private final int count;

    public WaveSlot(MobEntry entry, int count) {
        if (entry == null) {
            throw new IllegalArgumentException("entry darf nicht null sein");
        }
        if (count < 1) {
            throw new IllegalArgumentException("count muss >= 1 sein");
        }
        this.entry = entry;
        this.count = count;
    }

    public MobEntry getEntry() {
        return entry;
    }

    public int getCount() {
        return count;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WaveSlot that)) return false;
        return count == that.count && entry.equals(that.entry);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entry, count);
    }

    @Override
    public String toString() {
        return "WaveSlot{" + entry + " x " + count + "}";
    }
}
