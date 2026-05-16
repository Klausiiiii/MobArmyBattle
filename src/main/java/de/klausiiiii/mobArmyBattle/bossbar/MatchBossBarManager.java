package de.klausiiiii.mobArmyBattle.bossbar;

import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchPhaseType;
import de.klausiiiii.mobArmyBattle.match.Team;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MatchBossBarManager {

    private final de.klausiiiii.mobArmyBattle.MobArmyBattle plugin;

    public MatchBossBarManager() {
        this(null);
    }

    public MatchBossBarManager(de.klausiiiii.mobArmyBattle.MobArmyBattle plugin) {
        this.plugin = plugin;
    }

    private static class BarEntry {
        BossBar bar;
        final Set<UUID> viewers = new HashSet<>();
    }

    private final Map<String, BarEntry> entries = new HashMap<>();

    public void tickAll(Iterable<Match> matches) {
        Set<String> seen = new HashSet<>();
        for (Match match : matches) {
            seen.add(match.getId());
            updateBar(match);
        }
        List<String> removed = new ArrayList<>();
        for (String id : entries.keySet()) {
            if (!seen.contains(id)) removed.add(id);
        }
        for (String id : removed) {
            BarEntry e = entries.remove(id);
            if (e != null) hideForAll(e);
        }
    }

    private void updateBar(Match match) {
        BarEntry entry = entries.computeIfAbsent(match.getId(), id -> {
            BarEntry e = new BarEntry();
            e.bar = BossBar.bossBar(Component.text("…"), 1.0f,
                    BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
            return e;
        });
        BossBar bar = entry.bar;
        MatchPhaseType phase = match.getCurrentPhase().getType();
        long elapsedMs = System.currentTimeMillis() - match.getPhaseStartedAt();
        int durationSec = durationFor(match, phase);

        Component title;
        float progress;
        if (durationSec < 0) {
            title = Component.text(phaseLabel(phase), phaseTextColor(phase));
            progress = 1.0f;
        } else {
            int remaining = Math.max(0, durationSec - (int) (elapsedMs / 1000));
            title = Component.text(phaseLabel(phase), phaseTextColor(phase))
                    .append(Component.text(" | ", NamedTextColor.GRAY))
                    .append(Component.text(format(remaining), NamedTextColor.WHITE));
            progress = Math.max(0.0f, Math.min(1.0f, remaining / (float) durationSec));
        }
        bar.name(title);
        bar.progress(progress);
        bar.color(barColor(phase));

        Set<UUID> shouldSee = new HashSet<>();
        for (Team t : match.getTeams()) {
            shouldSee.addAll(t.getMemberIds());
        }
        for (UUID id : shouldSee) {
            if (entry.viewers.contains(id)) continue;
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.showBossBar(bar);
                entry.viewers.add(id);
            }
        }
        Iterator<UUID> it = entry.viewers.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            if (!shouldSee.contains(id)) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) p.hideBossBar(bar);
                it.remove();
            }
        }
    }

    private void hideForAll(BarEntry entry) {
        for (UUID id : entry.viewers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.hideBossBar(entry.bar);
        }
        entry.viewers.clear();
    }

    public void clear() {
        for (BarEntry e : entries.values()) hideForAll(e);
        entries.clear();
    }

    private int durationFor(de.klausiiiii.mobArmyBattle.match.Match match, MatchPhaseType phase) {
        if (plugin == null) {
            return switch (phase) {
                case FARM -> 60 * 60;
                case WAVE_BUILD -> 5 * 60;
                default -> -1;
            };
        }
        var phases = plugin.effectiveConfig(match).phaseDurations();
        return switch (phase) {
            case FARM -> phases.farmDurationMin() * 60;
            case WAVE_BUILD -> phases.waveBuildDurationMin() * 60;
            default -> -1;
        };
    }

    private static NamedTextColor phaseTextColor(MatchPhaseType phase) {
        return switch (phase) {
            case LOBBY -> NamedTextColor.WHITE;
            case FARM -> NamedTextColor.GREEN;
            case WAVE_BUILD -> NamedTextColor.YELLOW;
            case BATTLE -> NamedTextColor.RED;
            case FINISHED -> NamedTextColor.BLUE;
        };
    }

    private static BossBar.Color barColor(MatchPhaseType phase) {
        return switch (phase) {
            case LOBBY -> BossBar.Color.WHITE;
            case FARM -> BossBar.Color.GREEN;
            case WAVE_BUILD -> BossBar.Color.YELLOW;
            case BATTLE -> BossBar.Color.RED;
            case FINISHED -> BossBar.Color.BLUE;
        };
    }

    private static String phaseLabel(MatchPhaseType phase) {
        return switch (phase) {
            case LOBBY -> "Lobby";
            case FARM -> "Farm-Phase";
            case WAVE_BUILD -> "Wellen bauen";
            case BATTLE -> "Battle";
            case FINISHED -> "Match beendet";
        };
    }

    private static String format(int sec) {
        int m = sec / 60;
        int s = sec % 60;
        return String.format("%02d:%02d", m, s);
    }
}
