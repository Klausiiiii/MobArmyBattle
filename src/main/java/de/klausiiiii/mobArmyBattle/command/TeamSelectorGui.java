package de.klausiiiii.mobArmyBattle.command;

import de.klausiiiii.mobArmyBattle.MobArmyBattle;
import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchManager;
import de.klausiiiii.mobArmyBattle.match.Team;
import de.klausiiiii.mobArmyBattle.match.TeamVisibility;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
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

/**
 * Shown when a player picks a captain to join in a multi-team match
 * (maxTeamSize > 1). Lets them either pick an existing team (subject to its
 * visibility/password/invite rules) or create their own with a chosen
 * visibility. 1v1 matches skip this GUI — handled directly by the caller.
 */
public class TeamSelectorGui implements Listener {

    private static final Component TITLE = Component.text("Team-Auswahl", NamedTextColor.GOLD);
    private static final int INV_SIZE = 54;

    // Team-list slots: rows 0-2 (slots 0-26).
    // Create buttons: row 4 (slots 36, 38, 40, 42).
    // Footer: row 5 (slots 45 = back).
    private static final int CREATE_PUBLIC_SLOT = 38;
    private static final int CREATE_PRIVATE_SLOT = 40;
    private static final int CREATE_PASSWORD_SLOT = 42;
    private static final int BACK_SLOT = 49;

    private static class Session {
        Inventory inventory;
        UUID captainId; // host captain of the target match
        // slot → team index in match
        final Map<Integer, Integer> slotToTeamIdx = new HashMap<>();
        boolean inChatPrompt = false;
    }

    private final MobArmyBattle plugin;
    private final MatchManager matchManager;
    private final ChatInputManager chatInput;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public TeamSelectorGui(MobArmyBattle plugin, MatchManager matchManager, ChatInputManager chatInput) {
        this.plugin = plugin;
        this.matchManager = matchManager;
        this.chatInput = chatInput;
    }

    public void open(Player player, UUID captainId) {
        Match match = matchManager.getMatchOf(captainId);
        if (match == null) {
            player.sendMessage(Component.text("Captain ist in keinem Match mehr.", NamedTextColor.RED));
            return;
        }
        Session session = new Session();
        session.captainId = captainId;
        Inventory inv = Bukkit.createInventory(null, INV_SIZE, TITLE);
        session.inventory = inv;
        sessions.put(player.getUniqueId(), session);
        renderInto(player, session, match);
        player.openInventory(inv);
    }

    private void renderInto(Player viewer, Session session, Match match) {
        Inventory inv = session.inventory;
        inv.clear();
        fillBackground(inv);
        session.slotToTeamIdx.clear();

        int slot = 0;
        List<Team> teams = match.getTeams();
        for (int i = 0; i < teams.size() && slot < 27; i++) {
            Team team = teams.get(i);
            if (team.isDisbanded()) continue;
            inv.setItem(slot, teamIcon(team, i, viewer.getUniqueId(), match.getMaxTeamSize()));
            session.slotToTeamIdx.put(slot, i);
            slot++;
        }

        inv.setItem(CREATE_PUBLIC_SLOT, createButton(Material.LIME_WOOL,
                "Neues Public-Team", NamedTextColor.GREEN,
                "Jeder darf joinen — kein Passwort, keine Einladung."));
        inv.setItem(CREATE_PRIVATE_SLOT, createButton(Material.RED_WOOL,
                "Neues Privates Team", NamedTextColor.RED,
                "Nur per Einladung vom Captain (/mab invite <player>)."));
        inv.setItem(CREATE_PASSWORD_SLOT, createButton(Material.YELLOW_WOOL,
                "Neues Passwort-Team", NamedTextColor.YELLOW,
                "Passwort im Chat eingeben (nach Klick)."));

        inv.setItem(BACK_SLOT, createButton(Material.BARRIER,
                "Abbrechen", NamedTextColor.GRAY,
                "Zurück ohne beizutreten."));
    }

    private ItemStack teamIcon(Team team, int teamIndex, UUID viewerId, int maxSize) {
        boolean full = team.isFull();
        UUID capId = team.getCaptainId();
        ItemStack item;
        SkullMeta meta;
        if (capId != null) {
            item = new ItemStack(Material.PLAYER_HEAD);
            meta = (SkullMeta) item.getItemMeta();
            OfflinePlayer cap = Bukkit.getOfflinePlayer(capId);
            meta.setOwningPlayer(cap);
        } else {
            item = new ItemStack(Material.PAPER);
            meta = null;
        }

        String capName = capId == null ? "—" : nameOf(capId);
        TeamVisibility vis = team.getVisibility();
        NamedTextColor titleColor = switch (vis) {
            case PUBLIC -> NamedTextColor.GREEN;
            case PASSWORD -> NamedTextColor.YELLOW;
            case PRIVATE -> NamedTextColor.RED;
        };
        String visLabel = switch (vis) {
            case PUBLIC -> "Public";
            case PASSWORD -> "Passwort";
            case PRIVATE -> "Privat";
        };

        Component name = Component.text("Team " + (teamIndex + 1) + " · " + capName,
                titleColor).decoration(TextDecoration.ITALIC, false);

        List<Component> lore = new ArrayList<>();
        lore.add(line("Sichtbarkeit: " + visLabel, titleColor));
        lore.add(line("Mitglieder: " + team.size() + "/" + (maxSize > 0 ? maxSize : "∞"),
                NamedTextColor.GRAY));
        if (full) {
            lore.add(line("Team voll", NamedTextColor.DARK_RED));
        } else {
            switch (vis) {
                case PUBLIC -> lore.add(line("Klick zum Beitreten", NamedTextColor.GRAY));
                case PASSWORD -> lore.add(line("Klick → Passwort im Chat eingeben",
                        NamedTextColor.GRAY));
                case PRIVATE -> {
                    if (team.isInvited(viewerId)) {
                        lore.add(line("Du bist eingeladen — Klick zum Beitreten",
                                NamedTextColor.AQUA));
                    } else {
                        lore.add(line("Bitte den Captain um eine Einladung",
                                NamedTextColor.GRAY));
                    }
                }
            }
        }

        ItemMeta m = meta != null ? meta : item.getItemMeta();
        m.displayName(name);
        m.lore(lore);
        item.setItemMeta(m);
        return item;
    }

    private ItemStack createButton(Material mat, String label, NamedTextColor color, String lore) {
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

    private void fillBackground(Inventory inv) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.text(" "));
        pane.setItemMeta(meta);
        for (int i = 27; i < 36; i++) inv.setItem(i, pane);
        for (int i = 45; i < 54; i++) inv.setItem(i, pane);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (event.getView().getTopInventory() != session.inventory) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != session.inventory) return;
        int slot = event.getRawSlot();

        if (slot == BACK_SLOT) {
            sessions.remove(player.getUniqueId());
            player.closeInventory();
            return;
        }
        if (slot == CREATE_PUBLIC_SLOT) {
            createTeam(player, session, TeamVisibility.PUBLIC, null);
            return;
        }
        if (slot == CREATE_PRIVATE_SLOT) {
            createTeam(player, session, TeamVisibility.PRIVATE, null);
            return;
        }
        if (slot == CREATE_PASSWORD_SLOT) {
            promptPasswordForCreate(player, session);
            return;
        }

        Integer teamIdx = session.slotToTeamIdx.get(slot);
        if (teamIdx == null) return;
        Match match = matchManager.getMatchOf(session.captainId);
        if (match == null) {
            player.sendMessage(Component.text("Match nicht mehr vorhanden.", NamedTextColor.RED));
            sessions.remove(player.getUniqueId());
            player.closeInventory();
            return;
        }
        if (teamIdx >= match.getTeams().size()) return;
        Team target = match.getTeams().get(teamIdx);

        if (target.isFull()) {
            player.sendMessage(Component.text("Dieses Team ist voll.", NamedTextColor.RED));
            return;
        }

        switch (target.getVisibility()) {
            case PUBLIC -> joinExisting(player, session, teamIdx, null);
            case PASSWORD -> promptPasswordForJoin(player, session, teamIdx);
            case PRIVATE -> {
                if (target.isInvited(player.getUniqueId())) {
                    joinExisting(player, session, teamIdx, null);
                } else {
                    player.sendMessage(Component.text(
                            "Dieses Team ist privat — du brauchst eine Einladung vom Captain.",
                            NamedTextColor.RED));
                }
            }
        }
    }

    private void createTeam(Player player, Session session, TeamVisibility vis, String password) {
        try {
            Team newTeam = matchManager.joinMatchAsNewTeam(player.getUniqueId(),
                    session.captainId, vis, password);
            sessions.remove(player.getUniqueId());
            player.closeInventory();
            Match match = matchManager.getMatchOf(player.getUniqueId());
            int teamNumber = match.getTeams().indexOf(newTeam) + 1;
            String visLabel = switch (vis) {
                case PUBLIC -> "öffentliches";
                case PRIVATE -> "privates";
                case PASSWORD -> "passwortgeschütztes";
            };
            player.sendMessage(Component.text(
                    "Neues " + visLabel + " Team " + teamNumber + " erstellt — du bist Captain.",
                    NamedTextColor.GREEN));
            if (vis == TeamVisibility.PRIVATE) {
                player.sendMessage(Component.text(
                        "Lade Mitglieder ein mit: /mab invite <player>",
                        NamedTextColor.GRAY));
            } else if (vis == TeamVisibility.PASSWORD) {
                player.sendMessage(Component.text(
                        "Gib das Passwort an deine Mitspieler weiter.",
                        NamedTextColor.GRAY));
            }
            notifyMatchOfNewTeam(match, player, teamNumber, vis);
        } catch (IllegalStateException | IllegalArgumentException e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        }
    }

    private void promptPasswordForCreate(Player player, Session session) {
        session.inChatPrompt = true;
        player.closeInventory();
        chatInput.prompt(player,
                "Wähle ein Passwort für dein Team:",
                pw -> {
                    sessions.remove(player.getUniqueId());
                    createTeam(player, session, TeamVisibility.PASSWORD, pw);
                },
                () -> {
                    sessions.remove(player.getUniqueId());
                    player.sendMessage(Component.text(
                            "Team-Erstellung abgebrochen.", NamedTextColor.GRAY));
                });
    }

    private void promptPasswordForJoin(Player player, Session session, int teamIdx) {
        session.inChatPrompt = true;
        player.closeInventory();
        chatInput.prompt(player,
                "Gib das Passwort für dieses Team ein:",
                pw -> {
                    sessions.remove(player.getUniqueId());
                    joinExisting(player, session, teamIdx, pw);
                },
                () -> {
                    sessions.remove(player.getUniqueId());
                    player.sendMessage(Component.text(
                            "Beitritt abgebrochen.", NamedTextColor.GRAY));
                });
    }

    private void joinExisting(Player player, Session session, int teamIdx, String password) {
        try {
            matchManager.joinExistingTeam(player.getUniqueId(), session.captainId, teamIdx, password);
            sessions.remove(player.getUniqueId());
            player.closeInventory();
            Match match = matchManager.getMatchOf(player.getUniqueId());
            Team team = match.findTeamOf(player.getUniqueId());
            int teamNumber = match.getTeams().indexOf(team) + 1;
            player.sendMessage(Component.text(
                    "Team " + teamNumber + " beigetreten.", NamedTextColor.GREEN));
            Player cap = team.getCaptainId() != null ? Bukkit.getPlayer(team.getCaptainId()) : null;
            if (cap != null && !cap.equals(player)) {
                cap.sendMessage(Component.text(
                        player.getName() + " ist deinem Team beigetreten.",
                        NamedTextColor.GREEN));
            }
        } catch (IllegalStateException | IllegalArgumentException e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        }
    }

    private void notifyMatchOfNewTeam(Match match, Player creator, int teamNumber, TeamVisibility vis) {
        String visLabel = switch (vis) {
            case PUBLIC -> "öffentliches";
            case PRIVATE -> "privates";
            case PASSWORD -> "passwortgeschütztes";
        };
        Component msg = Component.text(
                creator.getName() + " hat ein " + visLabel + " Team " + teamNumber + " erstellt.",
                NamedTextColor.GRAY);
        for (Team t : match.getTeams()) {
            for (UUID memberId : t.getMemberIds()) {
                if (memberId.equals(creator.getUniqueId())) continue;
                Player m = Bukkit.getPlayer(memberId);
                if (m != null) m.sendMessage(msg);
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (event.getInventory() != session.inventory) return;
        // Don't drop the session if we're closing only to ask for a password —
        // the chat callback still needs the captainId.
        if (session.inChatPrompt) return;
        sessions.remove(player.getUniqueId());
    }

    private static String nameOf(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) return p.getName();
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        String n = op.getName();
        return n != null ? n : id.toString().substring(0, 8);
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    // Helper used by /mab invite to build a clickable accept-link.
    public static Component buildInviteMessage(String captainName, int teamNumber) {
        String cmd = "/mab join " + captainName + " " + teamNumber;
        Component link = Component.text(" [Annehmen]", NamedTextColor.GREEN, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand(cmd))
                .hoverEvent(HoverEvent.showText(Component.text(cmd, NamedTextColor.YELLOW)));
        return Component.text(captainName + " hat dich in Team " + teamNumber + " eingeladen.",
                NamedTextColor.AQUA).append(link);
    }
}
