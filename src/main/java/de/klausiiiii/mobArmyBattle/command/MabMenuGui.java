package de.klausiiiii.mobArmyBattle.command;

import de.klausiiiii.mobArmyBattle.MobArmyBattle;
import de.klausiiiii.mobArmyBattle.config.DeathPenaltyConfig;
import de.klausiiiii.mobArmyBattle.config.MabConfig;
import de.klausiiiii.mobArmyBattle.config.PhaseDurations;
import de.klausiiiii.mobArmyBattle.config.StarterKitConfig;
import de.klausiiiii.mobArmyBattle.config.WorldBorderConfig;
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
import org.bukkit.event.inventory.ClickType;
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
    private static final Component CONFIG_TITLE = Component.text("Match-Config", NamedTextColor.GOLD);

    private static final int MAIN_INFO_SLOT = 10;
    private static final int MAIN_PRIMARY_SLOT = 13;
    private static final int MAIN_POOL_SLOT = 12;
    private static final int MAIN_ACTION_SLOT = 14;
    private static final int MAIN_LEAVE_SLOT = 16;
    private static final int MAIN_CREATE_SLOT = 11;
    private static final int MAIN_JOIN_SLOT = 15;
    private static final int MAIN_CONFIG_SLOT = 22;

    private static final int CREATE_BACK_SLOT = 22;
    private static final int JOIN_BACK_SLOT = 49;

    // Config screen slot layout (6 rows x 9 cols = 54 slots)
    private static final int CFG_FARM_DURATION = 10;
    private static final int CFG_WAVE_BUILD_DURATION = 12;
    private static final int CFG_PREP_DURATION = 14;
    private static final int CFG_WAVE_PAUSE = 16;
    private static final int CFG_WAVE_HARD_TIMEOUT = 19;
    private static final int CFG_AUTO_FARM = 21;
    private static final int CFG_STARTER_KIT = 23;
    private static final int CFG_DEATH_PENALTY = 25;
    private static final int CFG_FARM_BORDER_EN = 28;
    private static final int CFG_FARM_BORDER_RADIUS = 30;
    private static final int CFG_ARENA_BORDER_EN = 32;
    private static final int CFG_ARENA_BORDER_RADIUS = 34;
    private static final int CFG_MOB_MULTIPLIER = 40;
    private static final int CFG_BACK = 45;
    private static final int CFG_INFO = 49;
    private static final int CFG_SUBMIT = 53;

    private enum MenuType { MAIN, CREATE, JOIN, CONFIG }
    private enum ConfigMode { CREATE, EDIT }

    private static class Session {
        MenuType type;
        Inventory inventory;
        final Map<Integer, UUID> joinCaptains = new HashMap<>();
        // CONFIG-only state
        MabConfig pendingConfig;
        ConfigMode configMode;
        int pendingMaxTeamSize;  // only valid for CONFIG/CREATE
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

    private void openConfigForCreate(Player player, int maxTeamSize) {
        Session session = sessions.computeIfAbsent(player.getUniqueId(), k -> new Session());
        session.type = MenuType.CONFIG;
        session.configMode = ConfigMode.CREATE;
        session.pendingMaxTeamSize = maxTeamSize;
        session.pendingConfig = plugin.getMabConfig();
        Inventory inv = Bukkit.createInventory(null, 54, CONFIG_TITLE);
        session.inventory = inv;
        renderConfig(session);
        player.openInventory(inv);
    }

    private void openConfigForEdit(Player player, Match match) {
        Session session = sessions.computeIfAbsent(player.getUniqueId(), k -> new Session());
        session.type = MenuType.CONFIG;
        session.configMode = ConfigMode.EDIT;
        session.pendingMaxTeamSize = match.getMaxTeamSize();
        MabConfig current = match.getMabConfig();
        session.pendingConfig = current != null ? current : plugin.getMabConfig();
        Inventory inv = Bukkit.createInventory(null, 54, CONFIG_TITLE);
        session.inventory = inv;
        renderConfig(session);
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
                    inv.setItem(MAIN_CONFIG_SLOT, button(Material.COMPARATOR,
                            "Match-Config", NamedTextColor.AQUA,
                            "Einstellungen für dieses Match"));
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
        ClickType click = event.getClick();

        switch (session.type) {
            case MAIN -> handleMainClick(player, slot);
            case CREATE -> handleCreateClick(player, slot);
            case JOIN -> handleJoinClick(player, session, slot);
            case CONFIG -> handleConfigClick(player, session, slot, click);
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
        if (phase == MatchPhaseType.LOBBY && slot == MAIN_CONFIG_SLOT && isHost) {
            openConfigForEdit(player, match);
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
        openConfigForCreate(player, size);
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
        plugin.cascadeIfHostLeaving(player);
        matchManager.leaveMatch(player.getUniqueId());
        plugin.getWorldManager().teleportToLobby(player);
        player.sendMessage(Component.text("Match verlassen.", NamedTextColor.YELLOW));
    }

    // ===== CONFIG screen =====

    private void renderConfig(Session session) {
        Inventory inv = session.inventory;
        inv.clear();
        fillBackground(inv);
        MabConfig c = session.pendingConfig;
        PhaseDurations p = c.phaseDurations();

        inv.setItem(CFG_FARM_DURATION, numericButton(Material.CLOCK,
                "Farm-Phase Dauer", p.farmDurationMin() + " min",
                "Klick: +1  Rechtsklick: -1  Shift: ±5"));
        inv.setItem(CFG_WAVE_BUILD_DURATION, numericButton(Material.COMPASS,
                "Wave-Build Dauer", p.waveBuildDurationMin() + " min",
                "Klick: +1  Rechtsklick: -1  Shift: ±5"));
        inv.setItem(CFG_PREP_DURATION, numericButton(Material.REPEATER,
                "Prep-Phase Dauer", p.prepDurationSec() + " s",
                "Klick: +5  Rechtsklick: -5  Shift: ±30"));
        inv.setItem(CFG_WAVE_PAUSE, numericButton(Material.REDSTONE,
                "Pause zw. Wellen", p.wavePauseSec() + " s",
                "Klick: +1  Rechtsklick: -1  Shift: ±5"));
        inv.setItem(CFG_WAVE_HARD_TIMEOUT, numericButton(Material.BELL,
                "Wave Timeout", p.waveHardTimeoutMin() + " min",
                "Klick: +1  Rechtsklick: -1  Shift: ±5"));
        inv.setItem(CFG_AUTO_FARM, toggleButton(
                "Auto Farm-Ende", p.autoFarmTransition(),
                "Klick: umschalten"));
        inv.setItem(CFG_STARTER_KIT, enumButton(Material.IRON_HELMET,
                "Starter-Kit", c.starterKit().type().name(),
                "Klick: nächster  Rechtsklick: vorheriger"));
        inv.setItem(CFG_DEATH_PENALTY, enumButton(Material.WITHER_SKELETON_SKULL,
                "Tod-Strafe", c.deathPenalty().mode().name(),
                "Klick: nächster  Rechtsklick: vorheriger"));
        inv.setItem(CFG_FARM_BORDER_EN, toggleButton(
                "Farm-Border aktiv", c.farmBorder().enabled(),
                "Klick: umschalten"));
        inv.setItem(CFG_FARM_BORDER_RADIUS, numericButton(Material.MAP,
                "Farm-Border Radius", c.farmBorder().radius() + " Blöcke",
                "Klick: +10  Rechtsklick: -10  Shift: ±50"));
        inv.setItem(CFG_ARENA_BORDER_EN, toggleButton(
                "Arena-Border aktiv", c.arenaBorder().enabled(),
                "Klick: umschalten"));
        inv.setItem(CFG_ARENA_BORDER_RADIUS, numericButton(Material.FILLED_MAP,
                "Arena-Border Radius", c.arenaBorder().radius() + " Blöcke",
                "Klick: +5  Rechtsklick: -5  Shift: ±25"));
        inv.setItem(CFG_MOB_MULTIPLIER, numericButton(Material.ZOMBIE_HEAD,
                "Farm Mob-Spawn", String.format(java.util.Locale.ROOT, "%.1fx", c.farmMobSpawnMultiplier()),
                "Klick: +0.1  Rechtsklick: -0.1  Shift: ±1.0"));

        inv.setItem(CFG_BACK, button(Material.ARROW, "Zurück", NamedTextColor.GRAY,
                session.configMode == ConfigMode.CREATE ? "Zurück zur Größenwahl" : "Zurück zum Hauptmenü"));
        inv.setItem(CFG_INFO, button(Material.PAPER,
                session.configMode == ConfigMode.CREATE
                        ? "Modus: Neues Match"
                        : "Modus: Bearbeiten",
                NamedTextColor.GOLD,
                "Team-Größe: " + session.pendingMaxTeamSize));
        inv.setItem(CFG_SUBMIT, button(Material.LIME_WOOL,
                session.configMode == ConfigMode.CREATE ? "Match erstellen" : "Übernehmen",
                NamedTextColor.GREEN,
                session.configMode == ConfigMode.CREATE ? "Match mit dieser Config anlegen" : "Änderungen speichern"));
    }

    private void handleConfigClick(Player player, Session session, int slot, ClickType click) {
        boolean isRight = click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT;
        boolean isShift = click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT;
        int sign = isRight ? -1 : 1;

        if (slot == CFG_BACK) {
            if (session.configMode == ConfigMode.CREATE) {
                openCreate(player);
            } else {
                openMain(player);
            }
            return;
        }
        if (slot == CFG_SUBMIT) {
            submitConfig(player, session);
            return;
        }

        MabConfig c = session.pendingConfig;
        PhaseDurations p = c.phaseDurations();

        try {
            if (slot == CFG_FARM_DURATION) {
                int v = clamp(p.farmDurationMin() + sign * (isShift ? 5 : 1), 1, 120);
                session.pendingConfig = withPhaseDurations(c, new PhaseDurations(v,
                        p.waveBuildDurationMin(), p.prepDurationSec(), p.wavePauseSec(),
                        p.waveHardTimeoutMin(), p.autoFarmTransition()));
            } else if (slot == CFG_WAVE_BUILD_DURATION) {
                int v = clamp(p.waveBuildDurationMin() + sign * (isShift ? 5 : 1), 1, 30);
                session.pendingConfig = withPhaseDurations(c, new PhaseDurations(
                        p.farmDurationMin(), v, p.prepDurationSec(), p.wavePauseSec(),
                        p.waveHardTimeoutMin(), p.autoFarmTransition()));
            } else if (slot == CFG_PREP_DURATION) {
                int v = clamp(p.prepDurationSec() + sign * (isShift ? 30 : 5), 0, 300);
                session.pendingConfig = withPhaseDurations(c, new PhaseDurations(
                        p.farmDurationMin(), p.waveBuildDurationMin(), v, p.wavePauseSec(),
                        p.waveHardTimeoutMin(), p.autoFarmTransition()));
            } else if (slot == CFG_WAVE_PAUSE) {
                int v = clamp(p.wavePauseSec() + sign * (isShift ? 5 : 1), 0, 60);
                session.pendingConfig = withPhaseDurations(c, new PhaseDurations(
                        p.farmDurationMin(), p.waveBuildDurationMin(), p.prepDurationSec(), v,
                        p.waveHardTimeoutMin(), p.autoFarmTransition()));
            } else if (slot == CFG_WAVE_HARD_TIMEOUT) {
                int v = clamp(p.waveHardTimeoutMin() + sign * (isShift ? 5 : 1), 1, 60);
                session.pendingConfig = withPhaseDurations(c, new PhaseDurations(
                        p.farmDurationMin(), p.waveBuildDurationMin(), p.prepDurationSec(),
                        p.wavePauseSec(), v, p.autoFarmTransition()));
            } else if (slot == CFG_AUTO_FARM) {
                session.pendingConfig = withPhaseDurations(c, new PhaseDurations(
                        p.farmDurationMin(), p.waveBuildDurationMin(), p.prepDurationSec(),
                        p.wavePauseSec(), p.waveHardTimeoutMin(), !p.autoFarmTransition()));
            } else if (slot == CFG_STARTER_KIT) {
                StarterKitConfig.Type[] cycle = {
                        StarterKitConfig.Type.NONE,
                        StarterKitConfig.Type.LEATHER_FULL,
                        StarterKitConfig.Type.IRON_FULL };
                int idx = indexOf(cycle, c.starterKit().type());
                if (idx < 0) idx = 0;
                int next = Math.floorMod(idx + sign, cycle.length);
                session.pendingConfig = new MabConfig(c.phaseDurations(),
                        new StarterKitConfig(cycle[next], java.util.Map.of()),
                        c.deathPenalty(), c.farmBorder(), c.arenaBorder(),
                        c.farmMobSpawnMultiplier(), c.reconnect());
            } else if (slot == CFG_DEATH_PENALTY) {
                DeathPenaltyConfig.Mode[] modes = DeathPenaltyConfig.Mode.values();
                int idx = indexOf(modes, c.deathPenalty().mode());
                if (idx < 0) idx = 0;
                int next = Math.floorMod(idx + sign, modes.length);
                session.pendingConfig = new MabConfig(c.phaseDurations(), c.starterKit(),
                        new DeathPenaltyConfig(modes[next]), c.farmBorder(), c.arenaBorder(),
                        c.farmMobSpawnMultiplier(), c.reconnect());
            } else if (slot == CFG_FARM_BORDER_EN) {
                WorldBorderConfig fb = c.farmBorder();
                session.pendingConfig = new MabConfig(c.phaseDurations(), c.starterKit(),
                        c.deathPenalty(), new WorldBorderConfig(!fb.enabled(), fb.radius()),
                        c.arenaBorder(), c.farmMobSpawnMultiplier(), c.reconnect());
            } else if (slot == CFG_FARM_BORDER_RADIUS) {
                WorldBorderConfig fb = c.farmBorder();
                int v = clamp(fb.radius() + sign * (isShift ? 50 : 10), 16, 2000);
                session.pendingConfig = new MabConfig(c.phaseDurations(), c.starterKit(),
                        c.deathPenalty(), new WorldBorderConfig(fb.enabled(), v),
                        c.arenaBorder(), c.farmMobSpawnMultiplier(), c.reconnect());
            } else if (slot == CFG_ARENA_BORDER_EN) {
                WorldBorderConfig ab = c.arenaBorder();
                session.pendingConfig = new MabConfig(c.phaseDurations(), c.starterKit(),
                        c.deathPenalty(), c.farmBorder(),
                        new WorldBorderConfig(!ab.enabled(), ab.radius()),
                        c.farmMobSpawnMultiplier(), c.reconnect());
            } else if (slot == CFG_ARENA_BORDER_RADIUS) {
                WorldBorderConfig ab = c.arenaBorder();
                int v = clamp(ab.radius() + sign * (isShift ? 25 : 5), 16, 500);
                session.pendingConfig = new MabConfig(c.phaseDurations(), c.starterKit(),
                        c.deathPenalty(), c.farmBorder(),
                        new WorldBorderConfig(ab.enabled(), v),
                        c.farmMobSpawnMultiplier(), c.reconnect());
            } else if (slot == CFG_MOB_MULTIPLIER) {
                double step = isShift ? 1.0 : 0.1;
                double v = clampD(c.farmMobSpawnMultiplier() + sign * step, 0.1, 10.0);
                v = Math.round(v * 10.0) / 10.0;
                session.pendingConfig = new MabConfig(c.phaseDurations(), c.starterKit(),
                        c.deathPenalty(), c.farmBorder(), c.arenaBorder(), v, c.reconnect());
            } else {
                return;
            }
            renderConfig(session);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("Ungültiger Wert: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    private void submitConfig(Player player, Session session) {
        if (session.configMode == ConfigMode.CREATE) {
            try {
                Match m = matchManager.createMatch(player.getUniqueId(),
                        session.pendingMaxTeamSize, session.pendingConfig);
                player.closeInventory();
                player.sendMessage(Component.text(
                        "Match erstellt: " + m.getId() + " (max " + session.pendingMaxTeamSize + " pro Team).",
                        NamedTextColor.GREEN));
                plugin.broadcastNewMatch(player, session.pendingMaxTeamSize);
            } catch (IllegalStateException | IllegalArgumentException e) {
                player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
            }
        } else {
            Match match = matchManager.getMatchOf(player.getUniqueId());
            if (match == null) {
                player.sendMessage(Component.text("Match nicht mehr vorhanden.", NamedTextColor.RED));
                player.closeInventory();
                return;
            }
            match.setMabConfig(session.pendingConfig);
            player.closeInventory();
            player.sendMessage(Component.text("Match-Config gespeichert.", NamedTextColor.GREEN));
        }
    }

    private MabConfig withPhaseDurations(MabConfig c, PhaseDurations newPd) {
        return new MabConfig(newPd, c.starterKit(), c.deathPenalty(),
                c.farmBorder(), c.arenaBorder(), c.farmMobSpawnMultiplier(), c.reconnect());
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double clampD(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static <T> int indexOf(T[] arr, T target) {
        for (int i = 0; i < arr.length; i++) if (arr[i] == target) return i;
        return -1;
    }

    private ItemStack numericButton(Material mat, String label, String value, String hint) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(label, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Aktuell: " + value, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                Component.text(hint, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack toggleButton(String label, boolean enabled, String hint) {
        ItemStack item = new ItemStack(enabled ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(label, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Aktuell: " + (enabled ? "AN" : "AUS"),
                        enabled ? NamedTextColor.GREEN : NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                Component.text(hint, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack enumButton(Material mat, String label, String value, String hint) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(label, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Aktuell: " + value, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                Component.text(hint, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
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
