package de.klausiiiii.mobArmyBattle.command;

import de.klausiiiii.mobArmyBattle.MobArmyBattle;
import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchManager;
import de.klausiiiii.mobArmyBattle.match.MatchPhaseType;
import de.klausiiiii.mobArmyBattle.match.Team;
import de.klausiiiii.mobArmyBattle.match.phase.FarmPhase;
import de.klausiiiii.mobArmyBattle.match.phase.WaveBuildPhase;
import de.klausiiiii.mobArmyBattle.pool.MobEntry;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MabMenuGui implements Listener {

    private static final Component MAIN_TITLE = Component.text("MobArmyBattle", NamedTextColor.GOLD);
    private static final Component CREATE_TITLE = Component.text("Match erstellen", NamedTextColor.GOLD);
    private static final Component JOIN_TITLE = Component.text("Match beitreten", NamedTextColor.GOLD);

    private static final int MAIN_INFO_SLOT = 10;
    private static final int MAIN_PRIMARY_SLOT = 13;
    private static final int MAIN_POOL_SLOT = 12;
    private static final int MAIN_ACTION_SLOT = 14;
    private static final int MAIN_LEAVE_SLOT = 16;
    private static final int MAIN_CREATE_SLOT = 11;
    private static final int MAIN_JOIN_SLOT = 15;

    private static final int CREATE_BACK_SLOT = 22;
    private static final int JOIN_BACK_SLOT = 49;

    private enum MenuType { MAIN, CREATE, JOIN }

    private static class Session {
        MenuType type;
        Inventory inventory;
        final Map<Integer, UUID> joinCaptains = new HashMap<>();
    }

    private final MobArmyBattle plugin;
    private final MatchManager matchManager;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public MabMenuGui(MobArmyBattle plugin, MatchManager matchManager) {
        this.plugin = plugin;
        this.matchManager = matchManager;
    }

    public void open(Player player) {
        openMain(player);
    }

    private void openMain(Player player) {
        Session session = sessions.computeIfAbsent(player.getUniqueId(), k -> new Session());
        session.type = MenuType.MAIN;
        session.joinCaptains.clear();
        Inventory inv = Bukkit.createInventory(null, 27, MAIN_TITLE);
        session.inventory = inv;
        renderMain(player, session);
        player.openInventory(inv);
    }

    private void openCreate(Player player) {
        Session session = sessions.computeIfAbsent(player.getUniqueId(), k -> new Session());
        session.type = MenuType.CREATE;
        Inventory inv = Bukkit.createInventory(null, 27, CREATE_TITLE);
        session.inventory = inv;
        renderCreate(inv);
        player.openInventory(inv);
    }

    private void openJoin(Player player) {
        Session session = sessions.computeIfAbsent(player.getUniqueId(), k -> new Session());
        session.type = MenuType.JOIN;
        session.joinCaptains.clear();
        Inventory inv = Bukkit.createInventory(null, 54, JOIN_TITLE);
        session.inventory = inv;
        renderJoin(player, session);
        player.openInventory(inv);
    }

    private void renderMain(Player player, Session session) {
        Inventory inv = session.inventory;
        inv.clear();
        fillBackground(inv);

        Match match = matchManager.getMatchOf(player.getUniqueId());
        if (match == null) {
            inv.setItem(MAIN_CREATE_SLOT, button(Material.NETHER_STAR, "Match erstellen",
                    NamedTextColor.GREEN, "Größe wählen"));
            inv.setItem(MAIN_JOIN_SLOT, button(Material.COMPASS, "Match beitreten",
                    NamedTextColor.AQUA, "Captain wählen"));
            return;
        }

        Team team = match.findTeamOf(player.getUniqueId());
        boolean isCaptain = team != null && player.getUniqueId().equals(team.getCaptainId());
        boolean isHost = player.getUniqueId().equals(match.getHostId());
        int teamNumber = team == null ? 0 : match.getTeams().indexOf(team) + 1;
        MatchPhaseType phase = match.getCurrentPhase().getType();

        inv.setItem(MAIN_INFO_SLOT, matchInfoIcon(match, team, teamNumber, isCaptain));

        switch (phase) {
            case LOBBY -> {
                if (isHost) {
                    boolean canStart = match.canStart();
                    inv.setItem(MAIN_PRIMARY_SLOT, button(
                            canStart ? Material.LIME_WOOL : Material.GRAY_WOOL,
                            canStart ? "Match starten" : "Warten auf 2. Team",
                            canStart ? NamedTextColor.GREEN : NamedTextColor.GRAY,
                            canStart ? "Host-Aktion" : "Min. 2 Teams nötig"));
                }
                inv.setItem(MAIN_LEAVE_SLOT, button(Material.BARRIER,
                        "Match verlassen", NamedTextColor.RED, ""));
            }
            case FARM -> {
                inv.setItem(MAIN_POOL_SLOT, button(Material.CHEST,
                        "Team-Pool anzeigen", NamedTextColor.AQUA, ""));
                if (isHost) {
                    inv.setItem(MAIN_ACTION_SLOT, button(Material.CLOCK,
                            "Farm beenden", NamedTextColor.GOLD, "Host-Aktion"));
                }
                inv.setItem(MAIN_LEAVE_SLOT, button(Material.BARRIER,
                        "Match verlassen", NamedTextColor.RED, ""));
            }
            case WAVE_BUILD -> {
                inv.setItem(MAIN_POOL_SLOT, button(Material.CHEST,
                        "Team-Pool anzeigen", NamedTextColor.AQUA, ""));
                if (isCaptain) {
                    inv.setItem(MAIN_ACTION_SLOT, button(Material.CRAFTING_TABLE,
                            "Wellen-Builder öffnen", NamedTextColor.GOLD, "Captain-Aktion"));
                }
                inv.setItem(MAIN_LEAVE_SLOT, button(Material.BARRIER,
                        "Match verlassen", NamedTextColor.RED, ""));
            }
            case BATTLE, FINISHED -> {
                inv.setItem(MAIN_POOL_SLOT, button(Material.CHEST,
                        "Team-Pool anzeigen", NamedTextColor.AQUA, ""));
                inv.setItem(MAIN_LEAVE_SLOT, button(Material.BARRIER,
                        "Match verlassen", NamedTextColor.RED, ""));
            }
        }
    }

    private void renderCreate(Inventory inv) {
        fillBackground(inv);
        inv.setItem(10, sizeButton(1));
        inv.setItem(12, sizeButton(2));
        inv.setItem(14, sizeButton(3));
        inv.setItem(16, sizeButton(4));
        inv.setItem(CREATE_BACK_SLOT, button(Material.ARROW, "Zurück", NamedTextColor.GRAY, ""));
    }

    private void renderJoin(Player player, Session session) {
        Inventory inv = session.inventory;
        fillBackground(inv);
        int slot = 0;
        for (UUID captainId : matchManager.getCaptainIds()) {
            if (captainId.equals(player.getUniqueId())) continue;
            Player captain = Bukkit.getPlayer(captainId);
            if (captain == null) continue;
            if (slot >= 45) break;
            inv.setItem(slot, captainHead(captain));
            session.joinCaptains.put(slot, captainId);
            slot++;
        }
        if (slot == 0) {
            inv.setItem(22, button(Material.PAPER, "Keine offenen Matches",
                    NamedTextColor.GRAY, "Erstelle selbst eines"));
        }
        inv.setItem(JOIN_BACK_SLOT, button(Material.ARROW, "Zurück", NamedTextColor.GRAY, ""));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (event.getView().getTopInventory() != session.inventory) return;
        if (event.getClickedInventory() != session.inventory) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        int slot = event.getRawSlot();

        switch (session.type) {
            case MAIN -> handleMainClick(player, slot);
            case CREATE -> handleCreateClick(player, slot);
            case JOIN -> handleJoinClick(player, session, slot);
        }
    }

    private void handleMainClick(Player player, int slot) {
        Match match = matchManager.getMatchOf(player.getUniqueId());
        if (match == null) {
            if (slot == MAIN_CREATE_SLOT) openCreate(player);
            else if (slot == MAIN_JOIN_SLOT) openJoin(player);
            return;
        }
        Team team = match.findTeamOf(player.getUniqueId());
        boolean isCaptain = team != null && player.getUniqueId().equals(team.getCaptainId());
        boolean isHost = player.getUniqueId().equals(match.getHostId());
        MatchPhaseType phase = match.getCurrentPhase().getType();

        if (slot == MAIN_LEAVE_SLOT) {
            doLeave(player);
            return;
        }
        if (slot == MAIN_POOL_SLOT && (phase == MatchPhaseType.FARM
                || phase == MatchPhaseType.WAVE_BUILD
                || phase == MatchPhaseType.BATTLE
                || phase == MatchPhaseType.FINISHED)) {
            player.closeInventory();
            sendPool(player, team);
            return;
        }
        if (phase == MatchPhaseType.LOBBY && slot == MAIN_PRIMARY_SLOT && isHost && match.canStart()) {
            player.closeInventory();
            match.transitionTo(new FarmPhase(plugin));
            broadcast(match, "Match gestartet — Phase: FARM (Stub).", NamedTextColor.GOLD);
            return;
        }
        if (phase == MatchPhaseType.FARM && slot == MAIN_ACTION_SLOT && isHost) {
            player.closeInventory();
            match.transitionTo(new WaveBuildPhase(plugin));
            broadcast(match, "Farm-Phase beendet — Captains bauen jetzt Wellen.", NamedTextColor.GOLD);
            return;
        }
        if (phase == MatchPhaseType.WAVE_BUILD && slot == MAIN_ACTION_SLOT && isCaptain) {
            player.closeInventory();
            plugin.getWaveBuildGui().open(player);
        }
    }

    private void handleCreateClick(Player player, int slot) {
        if (slot == CREATE_BACK_SLOT) {
            openMain(player);
            return;
        }
        int size;
        if (slot == 10) size = 1;
        else if (slot == 12) size = 2;
        else if (slot == 14) size = 3;
        else if (slot == 16) size = 4;
        else return;
        try {
            Match m = matchManager.createMatch(player.getUniqueId(), size);
            player.closeInventory();
            player.sendMessage(Component.text(
                    "Match erstellt: " + m.getId() + " (max " + size + " pro Team).",
                    NamedTextColor.GREEN));
        } catch (IllegalStateException | IllegalArgumentException e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        }
    }

    private void handleJoinClick(Player player, Session session, int slot) {
        if (slot == JOIN_BACK_SLOT) {
            openMain(player);
            return;
        }
        UUID captainId = session.joinCaptains.get(slot);
        if (captainId == null) return;
        try {
            matchManager.joinMatch(player.getUniqueId(), captainId);
            player.closeInventory();
            Match match = matchManager.getMatchOf(player.getUniqueId());
            Team team = match.findTeamOf(player.getUniqueId());
            int teamNumber = match.getTeams().indexOf(team) + 1;
            Player captain = Bukkit.getPlayer(captainId);
            String captainName = captain != null ? captain.getName() : "?";
            player.sendMessage(Component.text(
                    "Team " + teamNumber + " in " + captainName + "s Match beigetreten.",
                    NamedTextColor.GREEN));
            if (captain != null) {
                captain.sendMessage(Component.text(
                        player.getName() + " ist Team " + teamNumber + " beigetreten.",
                        NamedTextColor.GREEN));
            }
        } catch (IllegalStateException | IllegalArgumentException e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (event.getInventory() != session.inventory) return;
        sessions.remove(player.getUniqueId());
    }

    private void doLeave(Player player) {
        player.closeInventory();
        matchManager.leaveMatch(player.getUniqueId());
        plugin.getWorldManager().teleportToLobby(player);
        player.sendMessage(Component.text("Match verlassen.", NamedTextColor.YELLOW));
    }

    private void sendPool(Player player, Team team) {
        if (team == null || team.getPool().getEntries().isEmpty()) {
            player.sendMessage(Component.text("Pool ist leer.", NamedTextColor.GRAY));
            return;
        }
        player.sendMessage(Component.text(
                "Team-Pool (" + team.getPool().totalCount() + " Mobs):",
                NamedTextColor.GOLD));
        for (Map.Entry<MobEntry, Integer> e : team.getPool().getEntries().entrySet()) {
            String label = e.getKey().getEntityTypeName().toLowerCase();
            String eq = e.getKey().getEquipmentSignature();
            boolean hasEq = !eq.equals("none|none|none|none|none|none");
            player.sendMessage(Component.text(
                    "  " + label + (hasEq ? " (equipped)" : "") + " x " + e.getValue(),
                    NamedTextColor.GRAY));
        }
    }

    private void broadcast(Match match, String message, NamedTextColor color) {
        for (Team t : match.getTeams()) {
            for (UUID memberId : t.getMemberIds()) {
                Player m = Bukkit.getPlayer(memberId);
                if (m != null) {
                    m.sendMessage(Component.text(message, color));
                }
            }
        }
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

    private ItemStack button(Material mat, String label, NamedTextColor color, String lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(label, color).decoration(TextDecoration.ITALIC, false));
        if (lore != null && !lore.isEmpty()) {
            meta.lore(List.of(Component.text(lore, NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack sizeButton(int size) {
        Material mat = switch (size) {
            case 1 -> Material.IRON_SWORD;
            case 2 -> Material.IRON_AXE;
            case 3 -> Material.DIAMOND_SWORD;
            default -> Material.NETHERITE_SWORD;
        };
        return button(mat, size + "v" + size + " (max " + size + " pro Team)",
                NamedTextColor.AQUA, "Klick zum Erstellen");
    }

    private ItemStack matchInfoIcon(Match match, Team team, int teamNumber, boolean isCaptain) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Match: " + match.getId(), NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(line("Phase: " + match.getCurrentPhase().getType().name()));
        if (team != null) {
            lore.add(line("Dein Team: " + teamNumber + (isCaptain ? " (Captain)" : "")));
            lore.add(line("Mitglieder: " + team.size() + "/" + match.getMaxTeamSize()));
        }
        lore.add(line("Teams gesamt: " + match.getTeams().size()));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static Component line(String text) {
        return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    private ItemStack captainHead(Player captain) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(captain);
        meta.displayName(Component.text(captain.getName(), NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        Match m = matchManager.getMatchOf(captain.getUniqueId());
        if (m != null) {
            List<Component> lore = new ArrayList<>();
            lore.add(line("Match: " + m.getId()));
            lore.add(line("Teams: " + m.getTeams().size()
                    + " · Max " + m.getMaxTeamSize() + " pro Team"));
            lore.add(line("Phase: " + m.getCurrentPhase().getType().name()));
            lore.add(line("Klick zum Beitreten"));
            meta.lore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }
}
