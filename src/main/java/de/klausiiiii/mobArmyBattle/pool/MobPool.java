package de.klausiiiii.mobArmyBattle.pool;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class MobPool {

    private final Map<MobEntry, Integer> entries = new LinkedHashMap<>();

    public void add(MobEntry entry) {
        entries.merge(entry, 1, Integer::sum);
    }

    public int remove(MobEntry entry, int amount) {
        if (amount <= 0) return 0;
        Integer current = entries.get(entry);
        if (current == null) return 0;
        int toRemove = Math.min(current, amount);
        int remaining = current - toRemove;
        if (remaining == 0) {
            entries.remove(entry);
        } else {
            entries.put(entry, remaining);
        }
        return toRemove;
    }

    public int countOf(MobEntry entry) {
        return entries.getOrDefault(entry, 0);
    }

    public int totalCount() {
        return entries.values().stream().mapToInt(Integer::intValue).sum();
    }

    public Map<MobEntry, Integer> getEntries() {
        return Collections.unmodifiableMap(entries);
    }

    public int applyPenalty(int percent) {
        if (percent <= 0) return 0;
        if (percent >= 100) {
            int total = totalCount();
            entries.clear();
            return total;
        }
        int totalLost = 0;
        for (Map.Entry<MobEntry, Integer> e : new LinkedHashMap<>(entries).entrySet()) {
            int loss = e.getValue() * percent / 100;
            if (loss > 0) {
                totalLost += remove(e.getKey(), loss);
            }
        }
        return totalLost;
    }
}
