package de.klausiiiii.mobArmyBattle.match.phase;

import de.klausiiiii.mobArmyBattle.MobArmyBattle;
import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchPhase;
import de.klausiiiii.mobArmyBattle.match.MatchPhaseType;
import de.klausiiiii.mobArmyBattle.match.Team;
import de.klausiiiii.mobArmyBattle.ui.Notifications;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
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

        List<Team> emptyPoolTeams = new ArrayList<>();
        for (Team team : match.getTeams()) {
            if (team.isDisbanded() || team.size() == 0) continue;
            if (team.getPool().totalCount() == 0) {
                emptyPoolTeams.add(team);
            }
        }

        for (Team team : emptyPoolTeams) {
            broadcastToTeam(team, "§cKeine Mobs gefarmt — euer Team scheidet aus und schaut zu.");
            team.eliminate();
        }

        long activeTeams = match.getTeams().stream()
                .filter(t -> !t.isDisbanded() && !t.isEliminated() && t.size() > 0)
                .count();
        if (activeTeams < 2) {
            broadcastToAllRemaining(match, "§eZu wenige aktive Teams — Match endet ohne Battle.");
            match.transitionTo(new FinishedPhase(plugin));
            return;
        }

        for (Team team : match.getTeams()) {
            if (team.isDisbanded() || team.isEliminated() || team.getCaptainId() == null) continue;
            Player captain = Bukkit.getPlayer(team.getCaptainId());
            if (captain != null) {
                int poolSize = team.getPool().totalCount();
                if (poolSize == 1) {
                    captain.sendMessage("§eNur 1 Mob im Pool — du kannst nur Welle 1 bauen, Welle 2 muss aufgegeben werden.");
                }
                plugin.getWaveBuildGui().open(captain);
            }
        }
        for (Team t : match.getTeams()) {
            if (t.isDisbanded() || t.isEliminated()) continue;
            Notifications.waveBuildStart(t);
        }

        // Freeze every player still in the match (active + eliminated) so nobody
        // moves or looks around while captains build waves.
        if (plugin.getPlayerFreezeManager() != null) {
            for (Team team : match.getTeams()) {
                if (team.isDisbanded()) continue;
                for (UUID memberId : team.getMemberIds()) {
                    Player p = Bukkit.getPlayer(memberId);
                    if (p != null) {
                        plugin.getPlayerFreezeManager().freeze(p);
                    }
                }
            }
        }

        // Freeze every mob in every farm world (AI off, velocity zeroed) and stop
        // natural spawns / spawner blocks so the mob population cannot grow while
        // captains build their waves. Worlds are abandoned after wave-build, so no
        // restoration is needed in onExit.
        for (Team team : match.getTeams()) {
            String farmName = match.getFarmWorldName(team);
            if (farmName == null) continue;
            World farm = Bukkit.getWorld(farmName);
            if (farm == null) continue;
            farm.setGameRule(GameRules.SPAWN_MOBS, false);
            farm.setGameRule(GameRules.SPAWN_MONSTERS, false);
            farm.setGameRule(GameRules.SPAWNER_BLOCKS_WORK, false);
            farm.setSpawnFlags(false, false);
            for (org.bukkit.entity.Entity e : farm.getEntities()) {
                if (e instanceof Mob mob) {
                    mob.setAI(false);
                    mob.setVelocity(new Vector(0, 0, 0));
                }
            }
        }
    }

    @Override
    public void onExit(Match match) {
        if (plugin == null) return;
        if (plugin.getPlayerFreezeManager() == null) return;
        for (Team team : match.getTeams()) {
            if (team.isDisbanded()) continue;
            for (UUID memberId : team.getMemberIds()) {
                plugin.getPlayerFreezeManager().unfreeze(memberId);
            }
        }
    }

    @Override
    public void tick(Match match) {
        if (plugin == null) return;
        boolean allDone = true;
        boolean anyActive = false;
        for (Team team : match.getTeams()) {
            if (team.isDisbanded() || team.isEliminated() || team.size() == 0) continue;
            anyActive = true;
            if (!team.wavesFinalised()) {
                allDone = false;
                break;
            }
        }
        if (allDone && anyActive) {
            match.transitionTo(new BattlePhase(plugin));
            broadcastToAllRemaining(match, "§6Alle Wellen abgeschlossen — Battle-Phase startet (Stub).");
        }
    }

    private void broadcastToAllRemaining(Match match, String message) {
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
