package de.klausiiiii.mobArmyBattle.command;

import de.klausiiiii.mobArmyBattle.MobArmyBattle;
import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchManager;
import de.klausiiiii.mobArmyBattle.match.MatchPhaseType;
import de.klausiiiii.mobArmyBattle.match.Team;
import de.klausiiiii.mobArmyBattle.match.phase.FarmPhase;
import de.klausiiiii.mobArmyBattle.match.phase.WaveBuildPhase;
import de.klausiiiii.mobArmyBattle.spectator.SpectatorManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MabCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS =
            List.of("create", "join", "leave", "start", "pool", "endfarm", "tournament", "stats", "leaderboard", "spectate");
    private static final List<String> TOURNAMENT_SUBS =
            List.of("create", "join", "leave", "start", "list");

    private final MatchManager matchManager;
    private final MobArmyBattle plugin;
    private final SpectatorManager spectatorManager;

    public MabCommand(MobArmyBattle plugin, MatchManager matchManager) {
        this(plugin, matchManager, null);
    }

    public MabCommand(MobArmyBattle plugin, MatchManager matchManager, SpectatorManager spectatorManager) {
        this.plugin = plugin;
        this.matchManager = matchManager;
        this.spectatorManager = spectatorManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Nur Spieler dürfen /mab ausführen.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            plugin.getMabMenuGui().open(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        try {
            switch (sub) {
                case "create" -> handleCreate(player, args);
                case "join" -> handleJoin(player, args);
                case "leave" -> handleLeave(player);
                case "start" -> handleStart(player);
                case "pool" -> handlePool(player);
                case "endfarm" -> handleEndFarm(player);
                case "tournament", "tour", "t" -> handleTournament(player, args);
                case "stats" -> handleStats(player, args);
                case "leaderboard", "top" -> handleLeaderboard(player);
                case "spectate" -> handleSpectate(player, args);
                default -> sendUsage(player);
            }
        } catch (IllegalStateException | IllegalArgumentException e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        }
        return true;
    }

    private void handleCreate(Player player, String[] args) {
        if (!player.hasPermission("mobarmybattle.create.match")) {
            player.sendMessage(Component.text("Du hast keine Berechtigung für diesen Befehl.", NamedTextColor.RED));
            return;
        }
        int maxTeamSize = 1;
        if (args.length >= 2) {
            try {
                maxTeamSize = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text(
                        "Ungültige max-team-size: " + args[1] + " (Zahl >= 1, z.B. 1, 2, 4)",
                        NamedTextColor.RED));
                return;
            }
            if (maxTeamSize < 1) {
                player.sendMessage(Component.text(
                        "max-team-size muss >= 1 sein.",
                        NamedTextColor.RED));
                return;
            }
        }
        Match match = matchManager.createMatch(player.getUniqueId(), maxTeamSize);
        player.sendMessage(Component.text(
                "Match erstellt: " + match.getId() + " (max " + maxTeamSize + " pro Team). Du bist Captain Team 1.",
                NamedTextColor.GREEN));
        player.sendMessage(Component.text(
                "Andere joinen mit: /mab join " + player.getName() + " [team-nummer]",
                NamedTextColor.GRAY));
    }

    private void handleJoin(Player player, String[] args) {
        if (!player.hasPermission("mobarmybattle.join")) {
            player.sendMessage(Component.text("Du hast keine Berechtigung für diesen Befehl.", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Component.text("Verwendung: /mab join <captain> [1|2]", NamedTextColor.RED));
            return;
        }
        Player captainPlayer = Bukkit.getPlayerExact(args[1]);
        if (captainPlayer == null) {
            player.sendMessage(Component.text("Spieler nicht online: " + args[1], NamedTextColor.RED));
            return;
        }
        if (player == captainPlayer) {
            player.sendMessage(Component.text("Du kannst nicht deinem eigenen Match joinen.", NamedTextColor.RED));
            return;
        }
        Match existing = matchManager.getMatchOf(player.getUniqueId());
        if (existing != null) {
            matchManager.leaveMatch(player.getUniqueId());
            plugin.getWorldManager().teleportToLobby(player);
        }
        if (args.length >= 3) {
            int teamIndex;
            try {
                teamIndex = Integer.parseInt(args[2]) - 1;
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text(
                        "Ungültiger Team-Index: " + args[2] + " (1 oder 2)",
                        NamedTextColor.RED));
                return;
            }
            matchManager.joinMatch(player.getUniqueId(), captainPlayer.getUniqueId(), teamIndex);
        } else {
            matchManager.joinMatch(player.getUniqueId(), captainPlayer.getUniqueId());
        }
        Match match = matchManager.getMatchOf(player.getUniqueId());
        Team team = match.findTeamOf(player.getUniqueId());
        int teamNumber = match.getTeams().indexOf(team) + 1;
        player.sendMessage(Component.text(
                "Du bist Team " + teamNumber + " in " + captainPlayer.getName() + "s Match beigetreten.",
                NamedTextColor.GREEN));
        captainPlayer.sendMessage(Component.text(
                player.getName() + " ist Team " + teamNumber + " beigetreten.",
                NamedTextColor.GREEN));
    }

    private void handleLeave(Player player) {
        if (!player.hasPermission("mobarmybattle.join")) {
            player.sendMessage(Component.text("Du hast keine Berechtigung für diesen Befehl.", NamedTextColor.RED));
            return;
        }
        if (spectatorManager != null && spectatorManager.isSpectating(player.getUniqueId())) {
            spectatorManager.endSpectate(player.getUniqueId());
            player.sendMessage("§7Spectator-Mode beendet.");
            return;
        }
        Match match = matchManager.getMatchOf(player.getUniqueId());
        if (match == null) {
            player.sendMessage(Component.text("Du bist in keinem Match.", NamedTextColor.RED));
            return;
        }
        matchManager.leaveMatch(player.getUniqueId());
        plugin.getWorldManager().teleportToLobby(player);
        player.sendMessage(Component.text("Match verlassen.", NamedTextColor.YELLOW));
    }

    private void handleStart(Player player) {
        if (!player.hasPermission("mobarmybattle.create.match")) {
            player.sendMessage(Component.text("Du hast keine Berechtigung für diesen Befehl.", NamedTextColor.RED));
            return;
        }
        UUID playerId = player.getUniqueId();
        Match match = matchManager.getMatchOf(playerId);
        if (match == null) {
            player.sendMessage(Component.text("Du bist in keinem Match.", NamedTextColor.RED));
            return;
        }
        if (match.getCurrentPhase().getType() != MatchPhaseType.LOBBY) {
            player.sendMessage(Component.text("Match ist bereits gestartet.", NamedTextColor.RED));
            return;
        }
        Team team = match.findTeamOf(playerId);
        if (!team.getCaptainId().equals(playerId)) {
            player.sendMessage(Component.text("Nur der Captain darf starten.", NamedTextColor.RED));
            return;
        }
        if (!match.canStart()) {
            player.sendMessage(Component.text(
                    "Match kann nicht starten — beide Teams brauchen mindestens 1 Spieler.",
                    NamedTextColor.RED));
            return;
        }
        match.transitionTo(new FarmPhase(plugin));

        for (Team t : match.getTeams()) {
            for (UUID memberId : t.getMemberIds()) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null) {
                    member.sendMessage(Component.text("Match gestartet — Phase: FARM (Stub).",
                            NamedTextColor.GOLD));
                }
            }
        }
    }

    private void handleEndFarm(Player player) {
        if (!player.hasPermission("mobarmybattle.join")) {
            player.sendMessage(Component.text("Du hast keine Berechtigung für diesen Befehl.", NamedTextColor.RED));
            return;
        }
        Match match = matchManager.getMatchOf(player.getUniqueId());
        if (match == null) {
            player.sendMessage(Component.text("Du bist in keinem Match.", NamedTextColor.RED));
            return;
        }
        if (match.getCurrentPhase().getType() != MatchPhaseType.FARM) {
            player.sendMessage(Component.text("Match ist nicht in Farm-Phase.", NamedTextColor.RED));
            return;
        }
        Team team = match.findTeamOf(player.getUniqueId());
        if (!team.getCaptainId().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("Nur ein Captain darf die Farm-Phase beenden.", NamedTextColor.RED));
            return;
        }
        match.transitionTo(new WaveBuildPhase(plugin));
        for (Team t : match.getTeams()) {
            for (UUID memberId : t.getMemberIds()) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null) {
                    member.sendMessage(Component.text(
                            "Farm-Phase beendet — Captains bauen jetzt Wellen.",
                            NamedTextColor.GOLD));
                }
            }
        }
    }

    private void handlePool(Player player) {
        if (!player.hasPermission("mobarmybattle.join")) {
            player.sendMessage(Component.text("Du hast keine Berechtigung für diesen Befehl.", NamedTextColor.RED));
            return;
        }
        Match match = matchManager.getMatchOf(player.getUniqueId());
        if (match == null) {
            player.sendMessage(Component.text("Du bist in keinem Match.", NamedTextColor.RED));
            return;
        }
        Team team = match.findTeamOf(player.getUniqueId());
        if (team == null || team.getPool().getEntries().isEmpty()) {
            player.sendMessage(Component.text("Pool ist leer.", NamedTextColor.GRAY));
            return;
        }
        player.sendMessage(Component.text("Team-Pool (" + team.getPool().totalCount() + " Mobs):",
                NamedTextColor.GOLD));
        team.getPool().getEntries().forEach((entry, count) -> {
            String label = entry.getEntityTypeName().toLowerCase();
            String eq = entry.getEquipmentSignature();
            boolean hasEq = !eq.equals("none|none|none|none|none|none");
            String suffix = hasEq ? " (" + summarize(eq) + ")" : "";
            player.sendMessage(Component.text(
                    "  " + label + suffix + " x " + count,
                    NamedTextColor.GRAY));
        });
    }

    private void handleTournament(Player player, String[] args) {
        if (args.length < 2) {
            sendTournamentUsage(player);
            return;
        }
        String sub = args[1].toLowerCase();
        switch (sub) {
            case "create" -> handleTournamentCreate(player, args);
            case "join" -> handleTournamentJoin(player, args);
            case "leave" -> handleTournamentLeave(player);
            case "start" -> handleTournamentStart(player, args);
            case "list" -> handleTournamentList(player);
            default -> sendTournamentUsage(player);
        }
    }

    private void handleTournamentCreate(Player player, String[] args) {
        if (!player.hasPermission("mobarmybattle.create.tournament")) {
            player.sendMessage(Component.text("Du hast keine Berechtigung für diesen Befehl.", NamedTextColor.RED));
            return;
        }
        if (args.length < 3) {
            player.sendMessage(Component.text("Verwendung: /mab tournament create <name>", NamedTextColor.RED));
            return;
        }
        String name = args[2];
        plugin.getTournamentManager().create(name, player.getUniqueId());
        player.sendMessage(Component.text(
                "Tournament '" + name + "' erstellt — du bist Master.",
                NamedTextColor.GREEN));
        player.sendMessage(Component.text(
                "Captains joinen mit: /mab tournament join " + name,
                NamedTextColor.GRAY));
    }

    private void handleTournamentJoin(Player player, String[] args) {
        if (!player.hasPermission("mobarmybattle.join")) {
            player.sendMessage(Component.text("Du hast keine Berechtigung für diesen Befehl.", NamedTextColor.RED));
            return;
        }
        if (args.length < 3) {
            player.sendMessage(Component.text("Verwendung: /mab tournament join <name>", NamedTextColor.RED));
            return;
        }
        String name = args[2];
        plugin.getTournamentManager().join(name, player.getUniqueId());
        player.sendMessage(Component.text(
                "Tournament '" + name + "' beigetreten — warte auf Master-Start.",
                NamedTextColor.GREEN));
    }

    private void handleTournamentLeave(Player player) {
        if (!player.hasPermission("mobarmybattle.join")) {
            player.sendMessage(Component.text("Du hast keine Berechtigung für diesen Befehl.", NamedTextColor.RED));
            return;
        }
        var t = plugin.getTournamentManager().getByCaptain(player.getUniqueId());
        if (t == null) {
            player.sendMessage(Component.text("Du bist in keinem Tournament.", NamedTextColor.RED));
            return;
        }
        plugin.getTournamentManager().leave(player.getUniqueId());
        player.sendMessage(Component.text(
                "Tournament '" + t.getName() + "' verlassen.",
                NamedTextColor.YELLOW));
    }

    private void handleTournamentStart(Player player, String[] args) {
        if (!player.hasPermission("mobarmybattle.join")) {
            player.sendMessage(Component.text("Du hast keine Berechtigung für diesen Befehl.", NamedTextColor.RED));
            return;
        }
        if (args.length < 3) {
            player.sendMessage(Component.text("Verwendung: /mab tournament start <name>", NamedTextColor.RED));
            return;
        }
        plugin.getTournamentManager().start(args[2], player.getUniqueId());
    }

    private void handleTournamentList(Player player) {
        if (!player.hasPermission("mobarmybattle.join")) {
            player.sendMessage(Component.text("Du hast keine Berechtigung für diesen Befehl.", NamedTextColor.RED));
            return;
        }
        var tournaments = plugin.getTournamentManager().listAll();
        if (tournaments.isEmpty()) {
            player.sendMessage(Component.text("Keine aktiven Tournaments.", NamedTextColor.GRAY));
            return;
        }
        player.sendMessage(Component.text("Aktive Tournaments:", NamedTextColor.GOLD));
        for (var t : tournaments) {
            player.sendMessage(Component.text(
                    "  " + t.getName() + " — " + t.getStatus()
                            + " (" + t.getRegisteredCaptains().size() + " Captains)",
                    NamedTextColor.GRAY));
        }
    }

    private void handleStats(Player player, String[] args) {
        if (!player.hasPermission("mobarmybattle.stats")) {
            player.sendMessage(Component.text("Du hast keine Berechtigung für diesen Befehl.", NamedTextColor.RED));
            return;
        }
        UUID targetId;
        String displayName;
        if (args.length >= 2) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                player.sendMessage(Component.text("Spieler nicht online: " + args[1], NamedTextColor.RED));
                return;
            }
            targetId = target.getUniqueId();
            displayName = target.getName();
        } else {
            targetId = player.getUniqueId();
            displayName = player.getName();
        }
        var stats = plugin.getStatsRepository().get(targetId);
        player.sendMessage(Component.text("Stats für " + displayName + ":", NamedTextColor.GOLD));
        player.sendMessage(Component.text(
                "  Matches: " + stats.getMatchesTotal()
                        + " (W: " + stats.getMatchesWon()
                        + " / L: " + stats.getMatchesLost() + ")",
                NamedTextColor.GRAY));
        player.sendMessage(Component.text(
                String.format("  Win-Rate: %.1f%%", stats.getWinRate() * 100),
                NamedTextColor.GRAY));
        player.sendMessage(Component.text(
                "  Mob-Kills: " + stats.getMobKillsTotal(),
                NamedTextColor.GRAY));
    }

    private void handleLeaderboard(Player player) {
        if (!player.hasPermission("mobarmybattle.stats")) {
            player.sendMessage(Component.text("Du hast keine Berechtigung für diesen Befehl.", NamedTextColor.RED));
            return;
        }
        var top = plugin.getStatsRepository().getLeaderboard(10);
        if (top.isEmpty()) {
            player.sendMessage(Component.text("Leaderboard ist leer.", NamedTextColor.GRAY));
            return;
        }
        player.sendMessage(Component.text("Top 10:", NamedTextColor.GOLD));
        int rank = 1;
        for (var s : top) {
            String name = nameOf(s.getPlayerId());
            player.sendMessage(Component.text(
                    "  " + rank + ". " + name + " — "
                            + s.getMatchesWon() + "W / " + s.getMatchesLost() + "L"
                            + " · " + s.getMobKillsTotal() + " Kills",
                    NamedTextColor.GRAY));
            rank++;
        }
    }

    private void handleSpectate(Player player, String[] args) {
        if (!player.hasPermission("mobarmybattle.spectate")) {
            player.sendMessage(Component.text("Du hast keine Berechtigung für diesen Befehl.", NamedTextColor.RED));
            return;
        }
        if (spectatorManager == null) {
            player.sendMessage("§cSpectator-Funktion nicht verfügbar.");
            return;
        }
        if (args.length < 2) {
            List<String> targets = spectatorManager.listAvailableTargets(player.getUniqueId());
            if (targets.isEmpty()) {
                player.sendMessage("§cKeine zuschaubaren Targets verfügbar.");
            } else {
                player.sendMessage("§7Verfügbare Targets: §f" + String.join(", ", targets));
                player.sendMessage("§7Nutze §f/mab spectate <captain>");
            }
            return;
        }
        String captainName = args[1];
        Player target = Bukkit.getPlayerExact(captainName);
        if (target == null) {
            player.sendMessage("§cSpieler '" + captainName + "' ist nicht online.");
            return;
        }
        spectatorManager.startSpectate(player, target.getUniqueId());
    }

    private String nameOf(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) return p.getName();
        var offline = Bukkit.getOfflinePlayer(id);
        String name = offline.getName();
        return name != null ? name : id.toString().substring(0, 8);
    }

    private void sendTournamentUsage(Player player) {
        player.sendMessage(Component.text("Tournament-Befehle:", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/mab tournament create <name>", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/mab tournament join <name>", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/mab tournament leave", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/mab tournament start <name> — nur Master", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/mab tournament list", NamedTextColor.GRAY));
    }

    private String summarize(String eq) {
        for (String slot : eq.split("\\|")) {
            if (!slot.equals("none")) {
                return slot;
            }
        }
        return "geared";
    }

    private void sendUsage(Player player) {
        player.sendMessage(Component.text("MobArmyBattle-Befehle:", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/mab create [max-team-size] — Match erstellen (Default 1, max-Spieler pro Team)", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/mab join <captain> [1|2] — Match beitreten", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/mab leave — Match verlassen", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/mab start — Match starten (nur Captain)", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/mab pool — Team-Pool anzeigen", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/mab endfarm — Farm-Phase beenden (nur Captain)", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/mab tournament <create|join|leave|start|list>", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/mab stats [player] — Lifetime-Stats", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/mab leaderboard — Top 10", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/mab spectate [captain] — Battle zuschauen", NamedTextColor.GRAY));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return filterByPrefix(SUBCOMMANDS, args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("join")) {
            List<String> captainNames = new ArrayList<>();
            for (UUID captainId : matchManager.getCaptainIds()) {
                Player captain = Bukkit.getPlayer(captainId);
                if (captain != null && captain != sender) {
                    captainNames.add(captain.getName());
                }
            }
            return filterByPrefix(captainNames, args[1]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            return filterByPrefix(List.of("1", "2", "3", "4"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("join")) {
            return filterByPrefix(List.of("1", "2", "3", "4", "5", "6", "7", "8"), args[2]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("tournament")) {
            return filterByPrefix(TOURNAMENT_SUBS, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("tournament")) {
            String sub = args[1].toLowerCase(Locale.ROOT);
            if (sub.equals("join") || sub.equals("start")) {
                List<String> names = new ArrayList<>();
                plugin.getTournamentManager().listAll().forEach(t -> names.add(t.getName()));
                return filterByPrefix(names, args[2]);
            }
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("spectate") && sender instanceof Player p) {
            if (spectatorManager == null) return Collections.emptyList();
            return filterByPrefix(spectatorManager.listAvailableTargets(p.getUniqueId()), args[1]);
        }

        return Collections.emptyList();
    }

    private List<String> filterByPrefix(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String opt : options) {
            if (opt.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(opt);
            }
        }
        return result;
    }
}
