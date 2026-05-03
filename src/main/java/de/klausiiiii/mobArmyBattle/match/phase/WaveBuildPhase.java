package de.klausiiiii.mobArmyBattle.match.phase;

import de.klausiiiii.mobArmyBattle.MobArmyBattle;
import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchPhase;
import de.klausiiiii.mobArmyBattle.match.MatchPhaseType;
import de.klausiiiii.mobArmyBattle.match.Team;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

public class WaveBuildPhase implements MatchPhase {

    private final MobArmyBattle plugin;

    public WaveBuildPhase() {
        this(null);
    }

    public WaveBuildPhase(MobArmyBattle plugin) {
        this.plugin = plugin;
    }

    @Override
    public MatchPhaseType getType() {
        return MatchPhaseType.WAVE_BUILD;
    }

    @Override
    public void onEnter(Match match) {
        if (plugin == null) return;
        for (Team team : match.getTeams()) {
            if (team.isDisbanded() || team.getCaptainId() == null) continue;
            Player captain = Bukkit.getPlayer(team.getCaptainId());
            if (captain != null) {
                plugin.getWaveBuildGui().open(captain);
            }
        }
    }

    @Override
    public void onExit(Match match) {
    }

    @Override
    public void tick(Match match) {
        if (plugin == null) return;
        boolean allDone = true;
        for (Team team : match.getTeams()) {
            if (team.isDisbanded() || team.size() == 0) continue;
            if (!team.wavesFinalised()) {
                allDone = false;
                break;
            }
        }
        if (allDone) {
            match.transitionTo(new BattlePhase(plugin));
            for (Team team : match.getTeams()) {
                for (UUID memberId : team.getMemberIds()) {
                    Player p = Bukkit.getPlayer(memberId);
                    if (p != null) {
                        p.sendMessage("§6Alle Wellen bestätigt — Battle-Phase startet (Stub).");
                    }
                }
            }
        }
    }
}
