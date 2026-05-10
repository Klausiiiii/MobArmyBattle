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

    /**
     * Renders the sidebar lines for a player viewing a match.
     *
     * @param match       current match (must be non-null, must not be in LOBBY/FINISHED)
     * @param viewer      the team whose data to render (must be non-null)
     * @param battleCtx   battle-phase data (mob counts, kills, pair partner). May be null
     *                    in non-BATTLE phases. Must be non-null in BATTLE phase, otherwise
     *                    a placeholder line is shown.
     * @param currentTimeMs current wall-clock millis for elapsed-time calculation
     * @return ordered list of sidebar lines (top to bottom), max 14 entries.
     *         Empty list for LOBBY/FINISHED phases.
     */
    public static List<String> render(Match match, Team viewer, BattleContext battleCtx, long currentTimeMs) {
        MatchPhaseType phase = match.getCurrentPhase().getType();
        return switch (phase) {
            case FARM -> renderFarm(match, viewer, currentTimeMs);
            case WAVE_BUILD -> renderWaveBuild(match, viewer, currentTimeMs);
            case BATTLE -> renderBattle(match, viewer, battleCtx, currentTimeMs);
            default -> List.of();           // LOBBY/FINISHED → empty
        };
    }

    // ── shared header/footer helpers ─────────────────────────────────────────

    private static void addHeader(List<String> lines, Match match, String phaseLabel, long currentTimeMs) {
        lines.add("§e§lMobArmyBattle");
        lines.add(phaseLabel);
        lines.add("§7Zeit: §f" + formatElapsed(match.getPhaseStartedAt(), currentTimeMs));
    }

    private static void addFooter(List<String> lines, Match match) {
        lines.add("§7Teams aktiv: " + activeTeamCount(match) + "/" + match.getTeams().size());
    }

    // ── phase renderers ───────────────────────────────────────────────────────

    private static List<String> renderWaveBuild(Match match, Team viewer, long currentTimeMs) {
        List<String> lines = new ArrayList<>();
        addHeader(lines, match, "§7Phase: §eWelle bauen", currentTimeMs);
        lines.add(SEP);
        lines.add(waveLine(1, viewer.getWave1().totalMobCount()));
        lines.add(waveLine(2, viewer.getWave2().totalMobCount()));
        lines.add(SEP);
        addFooter(lines, match);
        return lines;
    }

    private static String waveLine(int n, int count) {
        String color = count == 0 ? "§c" : "§f";
        return "§7Welle " + n + ": " + color + count + " Mobs";
    }

    private static List<String> renderFarm(Match match, Team viewer, long currentTimeMs) {
        List<String> lines = new ArrayList<>();
        addHeader(lines, match, "§7Phase: §aFarm", currentTimeMs);
        lines.add(SEP);
        lines.add("§6Pool:");
        appendPoolLines(lines, viewer);
        lines.add(SEP);
        addFooter(lines, match);
        return lines;
    }

    private static List<String> renderBattle(Match match, Team viewer, BattleContext ctx, long currentTimeMs) {
        List<String> lines = new ArrayList<>();
        boolean prep = ctx != null && ctx.inPrepPhase();
        String phaseLabel = prep
                ? "§7Phase: §6Bauphase - W" + (ctx == null ? 0 : ctx.currentWaveNumber())
                : "§7Phase: §cBattle - W" + (ctx == null ? 0 : ctx.currentWaveNumber());
        addHeader(lines, match, phaseLabel, currentTimeMs);
        lines.add(SEP);
        if (ctx == null) {
            lines.add("§7(Daten lädt …)");
        } else if (prep) {
            int s = Math.max(0, ctx.prepSecondsLeft());
            lines.add("§7Spawn in: §f" + String.format("%02d:%02d", s / 60, s % 60));
            String teamColor = ctx.teamMembersAlive() == 0 ? "§c" : "§f";
            lines.add("§7Team: " + teamColor + ctx.teamMembersAlive() + "/" + ctx.teamMembersTotal() + " lebt");
        } else {
            lines.add("§7Mobs übrig: §f" + ctx.mobsAlive() + "/" + ctx.mobsTotalThisWave());
            lines.add("§7Kills: §f" + ctx.mobKills());
            String teamColor = ctx.teamMembersAlive() == 0 ? "§c" : "§f";
            lines.add("§7Team: " + teamColor + ctx.teamMembersAlive() + "/" + ctx.teamMembersTotal() + " lebt");
        }
        lines.add(SEP);
        if (ctx != null && ctx.pairCaptainName() != null) {
            lines.add("§7Pair gegen §f" + ctx.pairCaptainName());
        }
        addFooter(lines, match);
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
            pretty = pretty + " (ausgerüstet)";
        }
        return pretty;
    }

    private static int activeTeamCount(Match match) {
        int n = 0;
        for (Team t : match.getTeams()) if (!t.isDisbanded() && t.size() > 0) n++;
        return n;
    }

    private static String formatElapsed(long startMs, long nowMs) {
        long sec = Math.max(0, (nowMs - startMs) / 1000);
        return String.format("%02d:%02d", sec / 60, sec % 60);
    }
}
