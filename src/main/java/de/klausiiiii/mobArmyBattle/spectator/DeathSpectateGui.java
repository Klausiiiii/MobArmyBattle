package de.klausiiiii.mobArmyBattle.spectator;

import de.klausiiiii.mobArmyBattle.MobArmyBattle;
import de.klausiiiii.mobArmyBattle.battle.BattleManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DeathSpectateGui implements Listener {

    private static final Component TITLE = Component.text("Spectate-Auswahl", NamedTextColor.GOLD);
    private static final int INV_SIZE = 27;

    private static class GuiSession {
        Inventory inventory;
        boolean useDeathBypass;
        final Map<Integer, UUID> slotToCaptain = new HashMap<>();
    }

    private final MobArmyBattle plugin;
    private final BattleManager battleManager;
    private final SpectatorManager spectatorManager;
    private final Map<UUID, GuiSession> sessions = new HashMap<>();

    public DeathSpectateGui(MobArmyBattle plugin,
                            BattleManager battleManager,
                            SpectatorManager spectatorManager) {
        this.plugin = plugin;
        this.battleManager = battleManager;
        this.spectatorManager = spectatorManager;
    }

    /**
     * @param useDeathBypass {@code true} = aufgerufen aus Death-Flow → Permission-Check
     *                       wird übersprungen, keine returnLocation gespeichert.
     *                       {@code false} = aufgerufen aus {@code /mab spectate} →
     *                       reguläres {@link SpectatorManager#startSpectate} mit Check
     *                       und returnLocation-Preservation.
     */
    public void open(Player viewer, List<UUID> captains, boolean useDeathBypass) {
        if (captains.isEmpty()) {
            viewer.sendMessage(Component.text(
                    "Keine aktive Arena zum Zuschauen.", NamedTextColor.GRAY));
            return;
        }
        Inventory inv = Bukkit.createInventory(null, INV_SIZE, TITLE);
        GuiSession session = new GuiSession();
        session.inventory = inv;
        session.useDeathBypass = useDeathBypass;
        fillBackground(inv);
        int slot = 0;
        for (UUID cap : captains) {
            if (slot >= INV_SIZE) break;
            inv.setItem(slot, captainHead(cap));
            session.slotToCaptain.put(slot, cap);
            slot++;
        }
        sessions.put(viewer.getUniqueId(), session);
        viewer.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        GuiSession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (event.getView().getTopInventory() != session.inventory) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != session.inventory) return;
        int slot = event.getRawSlot();
        UUID captain = session.slotToCaptain.get(slot);
        if (captain == null) return;
        boolean useDeathBypass = session.useDeathBypass;
        sessions.remove(player.getUniqueId());
        player.closeInventory();
        if (useDeathBypass) {
            spectatorManager.startDeathSpectate(player, captain);
        } else {
            spectatorManager.startSpectate(player, captain);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        GuiSession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (event.getInventory() != session.inventory) return;
        sessions.remove(player.getUniqueId());
    }

    private ItemStack captainHead(UUID captainId) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        Player live = Bukkit.getPlayer(captainId);
        String name;
        if (live != null) {
            meta.setOwningPlayer(live);
            name = live.getName();
        } else {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(captainId));
            name = "Unbekannt";
        }
        meta.displayName(Component.text("Team " + name, NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Klick zum Zuschauen",
                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    private void fillBackground(Inventory inv) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.text(" "));
        pane.setItemMeta(meta);
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, pane);
        }
    }
}
