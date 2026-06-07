package de.klausiiiii.mobArmyBattle.listener;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Locks players in place (position + look direction) by recording an anchor location
 * on freeze and snapping every {@link PlayerMoveEvent} back to it. Used by
 * {@code WaveBuildPhase} to keep all participants stationary while captains pick
 * their waves.
 */
public class PlayerFreezeManager implements Listener {

    private final Map<UUID, Location> anchors = new HashMap<>();

    public void freeze(Player player) {
        if (player == null) return;
        anchors.put(player.getUniqueId(), player.getLocation().clone());
    }

    public void unfreeze(UUID playerId) {
        anchors.remove(playerId);
    }

    public boolean isFrozen(UUID playerId) {
        return anchors.containsKey(playerId);
    }

    public void unfreezeAll() {
        anchors.clear();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location anchor = anchors.get(event.getPlayer().getUniqueId());
        if (anchor == null) return;
        Location to = event.getTo();
        if (to == null) return;
        if (to.getX() == anchor.getX()
                && to.getY() == anchor.getY()
                && to.getZ() == anchor.getZ()
                && to.getYaw() == anchor.getYaw()
                && to.getPitch() == anchor.getPitch()) {
            return;
        }
        event.setTo(anchor.clone());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        anchors.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Stale anchor (player disconnected during wave-build, match ended without them) —
        // never re-freeze a freshly joined player to an old world location.
        anchors.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        Location anchor = anchors.get(event.getPlayer().getUniqueId());
        if (anchor == null) return;
        Location to = event.getTo();
        if (to == null) return;
        // Real cross-world teleport (e.g. /mab leave → lobby) means the player has left
        // the wave-build context — release the freeze instead of yanking them back.
        if (anchor.getWorld() != null && !anchor.getWorld().equals(to.getWorld())) {
            anchors.remove(event.getPlayer().getUniqueId());
        }
    }
}
