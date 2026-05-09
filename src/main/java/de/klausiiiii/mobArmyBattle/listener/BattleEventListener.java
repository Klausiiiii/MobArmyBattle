package de.klausiiiii.mobArmyBattle.listener;

import de.klausiiiii.mobArmyBattle.battle.BattleManager;
import de.klausiiiii.mobArmyBattle.world.WorldManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

public class BattleEventListener implements Listener {

    private final BattleManager battleManager;

    public BattleEventListener(BattleManager battleManager) {
        this.battleManager = battleManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) return;
        Player killer = event.getEntity().getKiller();
        battleManager.onMobKilled(event.getEntity().getUniqueId(),
                killer != null ? killer.getUniqueId() : null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        String worldName = p.getWorld().getName();
        if (worldName.startsWith(WorldManager.ARENA_WORLD_PREFIX)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            battleManager.onPlayerDeath(p.getUniqueId());
        }
    }
}
