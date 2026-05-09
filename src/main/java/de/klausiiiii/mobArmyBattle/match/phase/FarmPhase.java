package de.klausiiiii.mobArmyBattle.match.phase;

import de.klausiiiii.mobArmyBattle.MobArmyBattle;
import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchPhase;
import de.klausiiiii.mobArmyBattle.match.MatchPhaseType;
import de.klausiiiii.mobArmyBattle.match.Team;
import de.klausiiiii.mobArmyBattle.ui.Notifications;
import de.klausiiiii.mobArmyBattle.world.StarterKitApplier;
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
            Location raw = farmWorld.getSpawnLocation();
            Location safe = WorldManager.safeSpawnAt(farmWorld, raw.getBlockX(), raw.getBlockZ());
            farmWorld.setSpawnLocation(safe);
            for (UUID memberId : team.getMemberIds()) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null) {
                    member.teleport(safe);
                    StarterKitApplier.applyKit(member, plugin.getMabConfig().starterKit());
                }
            }
        }
        for (Team t : match.getTeams()) {
            Notifications.farmStart(t);
        }
    }

    @Override
    public void onExit(Match match) {
    }

    @Override
    public void tick(Match match) {
        if (plugin == null) return;
        var phases = plugin.getMabConfig().phaseDurations();
        if (!phases.autoFarmTransition()) return;
        long elapsedMs = System.currentTimeMillis() - match.getPhaseStartedAt();
        long durationMs = phases.farmDurationMin() * 60_000L;
        if (elapsedMs < durationMs) return;
        match.transitionTo(new WaveBuildPhase(plugin));
        for (Team t : match.getTeams()) {
            for (UUID memberId : t.getMemberIds()) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null) {
                    member.sendMessage("§6Farm-Phase beendet — Captains bauen jetzt Wellen.");
                }
            }
        }
    }
}
