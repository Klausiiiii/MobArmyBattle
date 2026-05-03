package de.klausiiiii.mobArmyBattle.match.phase;

import de.klausiiiii.mobArmyBattle.MobArmyBattle;
import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchPhase;
import de.klausiiiii.mobArmyBattle.match.MatchPhaseType;
import de.klausiiiii.mobArmyBattle.match.Team;
import de.klausiiiii.mobArmyBattle.world.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.UUID;

public class FarmPhase implements MatchPhase {

    private final MobArmyBattle plugin;

    public FarmPhase() {
        this(null);
    }

    public FarmPhase(MobArmyBattle plugin) {
        this.plugin = plugin;
    }

    @Override
    public MatchPhaseType getType() {
        return MatchPhaseType.FARM;
    }

    @Override
    public void onEnter(Match match) {
        if (plugin == null) {
            return;
        }
        WorldManager wm = plugin.getWorldManager();
        int teamIdx = 0;
        for (Team team : match.getTeams()) {
            String teamId = "team-" + (teamIdx++);
            World farmWorld = wm.createFarmWorld(match.getId(), teamId, match.getSeed());
            match.setFarmWorldName(team, farmWorld.getName());
            Location spawn = farmWorld.getSpawnLocation();
            for (UUID memberId : team.getMemberIds()) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null) {
                    member.teleport(spawn);
                }
            }
        }
    }

    @Override
    public void onExit(Match match) {
    }

    @Override
    public void tick(Match match) {
    }
}
