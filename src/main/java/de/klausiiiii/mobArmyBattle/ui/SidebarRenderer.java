package de.klausiiiii.mobArmyBattle.ui;

import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchPhaseType;
import de.klausiiiii.mobArmyBattle.match.Team;
import de.klausiiiii.mobArmyBattle.pool.MobEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class SidebarRenderer {

    private static final int MAX_POOL_LINES = 7;
    private static final String SEP = "§7§m─────────";

    private SidebarRenderer() {}

    public static List<String> render(Match match, Team viewer, BattleContext battleCtx, long currentTimeMs) {
        MatchPhaseType phase = match.getCurrentPhase().getType();
        return switch (phase) {
            case FARM -> renderFarm(match, viewer, currentTimeMs);
            case WAVE_BUILD -> List.of();  // Task 2
            case BATTLE -> List.of();       // Task 3
            default -> List.of();           // LOBBY/FINISHED → empty
        };
    }

    private static List<String> renderFarm(Match match, Team viewer, long currentTimeMs) {
        List<String> lines = new ArrayList<>();
        lines.add("§e§lMobArmyBattle");
        lines.add("§7Phase: §aFarm");
        lines.add("§7Zeit: §f" + formatElapsed(match.getPhaseStartedAt(), currentTimeMs));
        lines.add(SEP);
        lines.add("§6Pool:");
        appendPoolLines(lines, viewer);
        lines.add(SEP);
        lines.add("§7Teams aktiv: " + activeTeamCount(match) + "/" + match.getTeams().size());
        return lines;
    }

    private static void appendPoolLines(List<String> out, Team viewer) {
        List<Map.Entry<MobEntry, Integer>> sorted = new ArrayList<>(viewer.getPool().getEntries().entrySet());
        sorted.sort(Comparator.<Map.Entry<MobEntry, Integer>>comparingInt(Map.Entry::getValue).reversed());
        int n = Math.min(sorted.size(), MAX_POOL_LINES);
        for (int i = 0; i < n; i++) {
            Map.Entry<MobEntry, Integer> e = sorted.get(i);
            out.add("§7 " + prettyName(e.getKey()) + " x" + e.getValue());
        }
    }

    private static String prettyName(MobEntry entry) {
        String type = entry.getEntityTypeName();
        String pretty = type.charAt(0) + type.substring(1).toLowerCase().replace('_', ' ');
        if (!"none|none|none|none|none|none".equals(entry.getEquipmentSignature())) {
            pretty = pretty + " (geared)";
        }
        return pretty;
    }

    private static int activeTeamCount(Match match) {
        int n = 0;
        for (Team t : match.getTeams()) if (!t.isDisbanded() && t.size() > 0) n++;
        return n;
    }

    static String formatElapsed(long startMs, long nowMs) {
        long sec = Math.max(0, (nowMs - startMs) / 1000);
        return String.format("%02d:%02d", sec / 60, sec % 60);
    }
}
