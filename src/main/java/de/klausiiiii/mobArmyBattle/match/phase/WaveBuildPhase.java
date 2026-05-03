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
            if (team.isDisbanded() || team.size() == 0) continue;
            if (team.getPool().totalCount() == 0) {
                broadcastToTeam(team, "§cKeine Mobs gefarmt — euer Team ist ausgeschieden.");
                team.disband();
            }
        }

        long activeTeams = match.getTeams().stream()
                .filter(t -> !t.isDisbanded() && t.size() > 0)
                .count();
        if (activeTeams < 2) {
            broadcastToAll(match, "§eZu wenige aktive Teams — Match endet ohne Battle.");
            match.transitionTo(new FinishedPhase(plugin));
            return;
        }

        for (Team team : match.getTeams()) {
            if (team.isDisbanded() || team.getCaptainId() == null) continue;
            Player captain = Bukkit.getPlayer(team.getCaptainId());
            if (captain != null) {
                int poolSize = team.getPool().totalCount();
                if (poolSize == 1) {
                    captain.sendMessage("§eNur 1 Mob im Pool — du kannst nur Welle 1 bauen, Welle 2 muss aufgegeben werden.");
                }
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
        boolean anyActive = false;
        for (Team team : match.getTeams()) {
            if (team.isDisbanded() || team.size() == 0) continue;
            anyActive = true;
            if (!team.wavesFinalised()) {
                allDone = false;
                break;
            }
        }
        if (allDone && anyActive) {
            match.transitionTo(new BattlePhase(plugin));
            broadcastToAll(match, "§6Alle Wellen abgeschlossen — Battle-Phase startet (Stub).");
        }
    }

    private void broadcastToAll(Match match, String message) {
        for (Team team : match.getTeams()) {
            broadcastToTeam(team, message);
        }
    }

    private void broadcastToTeam(Team team, String message) {
        for (UUID memberId : team.getMemberIds()) {
            Player p = Bukkit.getPlayer(memberId);
            if (p != null) {
                p.sendMessage(message);
            }
        }
    }
}
