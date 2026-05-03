package de.klausiiiii.mobArmyBattle.wave;

import de.klausiiiii.mobArmyBattle.pool.MobEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Wave {

    private final Map<MobEntry, Integer> slots = new LinkedHashMap<>();
    private boolean finalised = false;

    public void add(MobEntry entry, int count) {
        if (finalised) {
            throw new IllegalStateException("Welle ist bereits finalisiert");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("count muss > 0 sein");
        }
        slots.merge(entry, count, Integer::sum);
    }

    public int remove(MobEntry entry, int count) {
        if (finalised) {
            throw new IllegalStateException("Welle ist bereits finalisiert");
        }
        if (count <= 0) return 0;
        Integer cur = slots.get(entry);
        if (cur == null) return 0;
        int rm = Math.min(cur, count);
        int rest = cur - rm;
        if (rest == 0) {
            slots.remove(entry);
        } else {
            slots.put(entry, rest);
        }
        return rm;
    }

    public int countOf(MobEntry entry) {
        return slots.getOrDefault(entry, 0);
    }

    public int totalMobCount() {
        return slots.values().stream().mapToInt(Integer::intValue).sum();
    }

    public List<WaveSlot> getSlots() {
        List<WaveSlot> result = new ArrayList<>(slots.size());
        slots.forEach((k, v) -> result.add(new WaveSlot(k, v)));
        return Collections.unmodifiableList(result);
    }

    public void finalise() {
        if (slots.isEmpty()) {
            throw new IllegalStateException("Welle braucht mind. 1 Mob");
        }
        finalised = true;
    }

    public boolean isFinalised() {
        return finalised;
    }
}
