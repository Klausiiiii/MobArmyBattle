package de.klausiiiii.mobArmyBattle.wave;

import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchManager;
import de.klausiiiii.mobArmyBattle.match.Team;
import de.klausiiiii.mobArmyBattle.pool.MobEntry;
import de.klausiiiii.mobArmyBattle.pool.MobPool;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class WaveBuildGui implements Listener {

    private static final int INVENTORY_SIZE = 54;
    private static final int TAB_WAVE1_SLOT = 18;
    private static final int TAB_WAVE2_SLOT = 26;
    private static final int RESET_BUTTON_SLOT = 45;
    private static final int CONFIRM_BUTTON_SLOT = 49;
    private static final int CANCEL_BUTTON_SLOT = 53;

    private static class GuiSession {
        final UUID playerId;
        final Match match;
        final Team team;
        int activeWave = 1;

        GuiSession(UUID playerId, Match match, Team team) {
            this.playerId = playerId;
            this.match = match;
            this.team = team;
        }
    }

    private final MatchManager matchManager;
    private final Map<UUID, GuiSession> sessions = new HashMap<>();

    public WaveBuildGui(MatchManager matchManager) {
        this.matchManager = matchManager;
    }

    public void open(Player player) {
        Match match = matchManager.getMatchOf(player.getUniqueId());
        if (match == null) return;
        Team team = match.findTeamOf(player.getUniqueId());
        if (team == null) return;
        if (!team.getCaptainId().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("Nur der Captain darf Wellen bauen.", NamedTextColor.RED));
            return;
        }
        GuiSession session = new GuiSession(player.getUniqueId(), match, team);
        sessions.put(player.getUniqueId(), session);
        Inventory inv = Bukkit.createInventory(null, INVENTORY_SIZE,
                Component.text("Wellen bauen — Welle " + session.activeWave, NamedTextColor.GOLD));
        renderInto(inv, session);
        player.openInventory(inv);
    }

    private void renderInto(Inventory inv, GuiSession session) {
        inv.clear();
        renderPool(inv, session);
        inv.setItem(TAB_WAVE1_SLOT, tabIcon(1, session.activeWave == 1, session.team.getWave1().isFinalised()));
        inv.setItem(TAB_WAVE2_SLOT, tabIcon(2, session.activeWave == 2, session.team.getWave2().isFinalised()));
        for (int i = 19; i < 26; i++) {
            inv.setItem(i, separator());
        }
        renderCurrentWave(inv, session);
        inv.setItem(RESET_BUTTON_SLOT, button(Material.ORANGE_WOOL, "Welle zurücksetzen", NamedTextColor.GOLD));
        inv.setItem(CONFIRM_BUTTON_SLOT, button(Material.LIME_WOOL, "Welle bestätigen", NamedTextColor.GREEN));
        inv.setItem(CANCEL_BUTTON_SLOT, button(Material.RED_WOOL, "Schließen (ohne speichern)", NamedTextColor.RED));
    }

    private void renderPool(Inventory inv, GuiSession session) {
        MobPool pool = session.team.getPool();
        Wave currentWave = currentWave(session);
        Map<MobEntry, Integer> remaining = new HashMap<>(pool.getEntries());
        for (WaveSlot slot : currentWave.getSlots()) {
            remaining.merge(slot.getEntry(), -slot.getCount(), Integer::sum);
        }
        int slotIdx = 0;
        for (Map.Entry<MobEntry, Integer> e : remaining.entrySet()) {
            if (slotIdx >= 18) break;
            int count = e.getValue();
            if (count <= 0) continue;
            inv.setItem(slotIdx++, mobIcon(e.getKey(), count, "verfügbar"));
        }
    }

    private void renderCurrentWave(Inventory inv, GuiSession session) {
        Wave wave = currentWave(session);
        int slotIdx = 27;
        for (WaveSlot slot : wave.getSlots()) {
            if (slotIdx >= 45) break;
            inv.setItem(slotIdx++, mobIcon(slot.getEntry(), slot.getCount(), "in Welle"));
        }
    }

    private Wave currentWave(GuiSession session) {
        return session.activeWave == 1 ? session.team.getWave1() : session.team.getWave2();
    }

    private ItemStack tabIcon(int waveNum, boolean active, boolean finalised) {
        Material mat = finalised ? Material.GREEN_CONCRETE : (active ? Material.YELLOW_CONCRETE : Material.GRAY_CONCRETE);
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Welle " + waveNum + (finalised ? " (bestätigt)" : ""),
                active ? NamedTextColor.YELLOW : NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack separator() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" "));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack button(Material mat, String label, NamedTextColor color) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(label, color).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack mobIcon(MobEntry entry, int count, String suffix) {
        Material mat = spawnEggFor(entry.getEntityTypeName());
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        String name = entry.getEntityTypeName().toLowerCase().replace('_', ' ');
        meta.displayName(Component.text(name, NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("x " + count + " " + suffix, NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        if (!entry.getEquipmentSignature().equals("none|none|none|none|none|none")) {
            lore.add(Component.text("(equipped)", NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private Material spawnEggFor(String entityName) {
        try {
            EntityType.valueOf(entityName);
            Material egg = Material.matchMaterial(entityName.toLowerCase(Locale.ROOT) + "_spawn_egg");
            return egg != null ? egg : Material.PIG_SPAWN_EGG;
        } catch (IllegalArgumentException ignored) {
            return Material.PIG_SPAWN_EGG;
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        GuiSession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (event.getClickedInventory() == null) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= INVENTORY_SIZE) return;

        if (slot == TAB_WAVE1_SLOT) {
            session.activeWave = 1;
            refreshTitle(player, session);
            return;
        }
        if (slot == TAB_WAVE2_SLOT) {
            session.activeWave = 2;
            refreshTitle(player, session);
            return;
        }
        if (slot == RESET_BUTTON_SLOT) {
            resetWave(session);
            renderInto(event.getInventory(), session);
            return;
        }
        if (slot == CONFIRM_BUTTON_SLOT) {
            tryFinalise(player, session, event.getInventory());
            return;
        }
        if (slot == CANCEL_BUTTON_SLOT) {
            sessions.remove(player.getUniqueId());
            player.closeInventory();
            return;
        }
        if (slot < 18) {
            ItemStack item = event.getCurrentItem();
            if (item == null || item.getType().isAir()) return;
            MobEntry entry = poolEntryAtSlot(session, slot);
            if (entry == null) return;
            int amount = event.isShiftClick() ? 5 : 1;
            int available = remainingForEntry(session, entry);
            int toAdd = Math.min(amount, available);
            if (toAdd <= 0) return;
            currentWave(session).add(entry, toAdd);
            renderInto(event.getInventory(), session);
            return;
        }
        if (slot >= 27 && slot < 45) {
            ItemStack item = event.getCurrentItem();
            if (item == null || item.getType().isAir()) return;
            MobEntry entry = waveEntryAtSlot(session, slot);
            if (entry == null) return;
            int amount = event.isShiftClick() ? 5 : 1;
            currentWave(session).remove(entry, amount);
            renderInto(event.getInventory(), session);
        }
    }

    private void refreshTitle(Player player, GuiSession session) {
        Inventory newInv = Bukkit.createInventory(null, INVENTORY_SIZE,
                Component.text("Wellen bauen — Welle " + session.activeWave, NamedTextColor.GOLD));
        renderInto(newInv, session);
        player.openInventory(newInv);
    }

    private MobEntry poolEntryAtSlot(GuiSession session, int slot) {
        MobPool pool = session.team.getPool();
        Wave currentWave = currentWave(session);
        Map<MobEntry, Integer> remaining = new HashMap<>(pool.getEntries());
        for (WaveSlot ws : currentWave.getSlots()) {
            remaining.merge(ws.getEntry(), -ws.getCount(), Integer::sum);
        }
        int idx = 0;
        for (Map.Entry<MobEntry, Integer> e : remaining.entrySet()) {
            if (e.getValue() <= 0) continue;
            if (idx == slot) return e.getKey();
            idx++;
        }
        return null;
    }

    private MobEntry waveEntryAtSlot(GuiSession session, int slot) {
        Wave wave = currentWave(session);
        int idx = slot - 27;
        List<WaveSlot> slots = wave.getSlots();
        if (idx < 0 || idx >= slots.size()) return null;
        return slots.get(idx).getEntry();
    }

    private int remainingForEntry(GuiSession session, MobEntry entry) {
        int poolCount = session.team.getPool().countOf(entry);
        int waveCount = currentWave(session).countOf(entry);
        return poolCount - waveCount;
    }

    private void resetWave(GuiSession session) {
        Wave wave = currentWave(session);
        if (wave.isFinalised()) return;
        for (WaveSlot slot : new ArrayList<>(wave.getSlots())) {
            wave.remove(slot.getEntry(), slot.getCount());
        }
    }

    private void tryFinalise(Player player, GuiSession session, Inventory inv) {
        Wave wave = currentWave(session);
        if (wave.isFinalised()) {
            player.sendMessage(Component.text("Welle ist bereits bestätigt.", NamedTextColor.YELLOW));
            return;
        }
        try {
            wave.finalise();
        } catch (IllegalStateException e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
            return;
        }
        player.sendMessage(Component.text("Welle " + session.activeWave + " bestätigt.",
                NamedTextColor.GREEN));
        if (session.team.wavesFinalised()) {
            player.sendMessage(Component.text("Beide Wellen bestätigt.", NamedTextColor.GREEN));
            sessions.remove(player.getUniqueId());
            player.closeInventory();
        } else {
            session.activeWave = session.activeWave == 1 ? 2 : 1;
            refreshTitle(player, session);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        sessions.remove(player.getUniqueId());
    }
}
