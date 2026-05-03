package de.klausiiiii.mobArmyBattle.listener;

import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchManager;
import de.klausiiiii.mobArmyBattle.match.MatchPhaseType;
import de.klausiiiii.mobArmyBattle.match.Team;
import de.klausiiiii.mobArmyBattle.pool.EquipmentSerializer;
import de.klausiiiii.mobArmyBattle.pool.MobEntry;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public class MobKillListener implements Listener {

    private final MatchManager matchManager;

    public MobKillListener(MatchManager matchManager) {
        this.matchManager = matchManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) return;

        Player killer = entity.getKiller();
        if (killer == null) return;

        Match match = matchManager.getMatchOf(killer.getUniqueId());
        if (match == null) return;
        if (match.getCurrentPhase().getType() != MatchPhaseType.FARM) return;

        Team team = match.findTeamOf(killer.getUniqueId());
        if (team == null) return;

        String farmWorldName = match.getFarmWorldName(team);
        if (farmWorldName == null) return;
        if (!entity.getWorld().getName().equals(farmWorldName)) return;

        String typeName = entity.getType().name();
        String signature = EquipmentSerializer.serialize(entity);
        team.getPool().add(new MobEntry(typeName, signature));
    }
}
