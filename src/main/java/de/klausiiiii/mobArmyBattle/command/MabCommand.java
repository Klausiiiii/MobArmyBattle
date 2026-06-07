package de.klausiiiii.mobArmyBattle.command;

import de.klausiiiii.mobArmyBattle.MobArmyBattle;
import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchManager;
import de.klausiiiii.mobArmyBattle.match.MatchPhaseType;
import de.klausiiiii.mobArmyBattle.match.Team;
import de.klausiiiii.mobArmyBattle.match.TeamVisibility;
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
            List.of("create", "join", "leave", "start", "pool", "endfarm", "build", "stats", "leaderboard", "spectate", "invite", "kick", "forcecancel", "reload");

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
                case "build" -> handleBuild(player);
                case "stats" -> handleStats(player, args);
                case "leaderboard", "top" -> handleLeaderboard(player);
                case "spectate" -> handleSpectate(player, args);
                case "invite" -> handleInvite(player, args);
                case "kick" -> handleKick(player, args);
                case "forcecancel" -> handleForceCancel(player, args);
                case "reload" -> handleReload(player);
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
        Match match = matchManager.createMatch(player.getUniqueId(), maxTeamSize, plugin.getMabConfig());
        player.sendMessage(Component.text(
                "Match erstellt: " + match.getId() + " (max " + maxTeamSize + " pro Team). Du bist Captain Team 1.",
                NamedTextColor.GREEN));
        player.sendMessage(Component.text(
                "Andere joinen mit: /mab join " + player.getName() + " [team-nummer]",
                NamedTextColor.GRAY));
        plugin.broadcastNewMatch(player, maxTeamSize);
    }

    private void handleJoin(Player player, String[] args) {
        if (!player.hasPermission("mobarmybattle.join")) {
            player.sendMessage(Component.text("Du hast keine Berechtigung für diesen Befehl.", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Component.text("Verwendung: /mab join <captain> [team-nr]", NamedTextColor.RED));
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
        Match targetMatch = matchManager.getMatchOf(captainPlayer.getUniqueId());
        if (targetMatch == null) {
            player.sendMessage(Component.text("Dieser Captain hat kein Match.", NamedTextColor.RED));
            return;
        }

        if (args.length >= 3) {
            int teamIndex;
            try {
                teamIndex = Integer.parseInt(args[2]) - 1;
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text(
                        "Ungültiger Team-Index: " + args[2],
                        NamedTextColor.RED));
                return;
            }
            // Honor visibility rules. Password teams must be joined via the GUI.
            if (teamIndex >= 0 && teamIndex < targetMatch.getTeams().size()) {
                Team target = targetMatch.getTeams().get(teamIndex);
                if (target.getVisibility() == TeamVisibility.PASSWORD) {
                    player.sendMessage(Component.text(
                            "Dieses Team braucht ein Passwort — bitte über das Menü beitreten.",
                            NamedTextColor.RED));
                    return;
                }
                matchManager.joinExistingTeam(player.getUniqueId(), captainPlayer.getUniqueId(), teamIndex, null);
            } else if (teamIndex == targetMatch.getTeams().size()) {
                // New team via command always public.
                matchManager.joinMatchAsNewTeam(player.getUniqueId(),
                        captainPlayer.getUniqueId(), TeamVisibility.PUBLIC, null);
            } else {
                player.sendMessage(Component.text(
                        "Ungültiger Team-Index: " + args[2],
                        NamedTextColor.RED));
                return;
            }
        } else if (targetMatch.getMaxTeamSize() > 1 && plugin.getTeamSelectorGui() != null) {
            plugin.getTeamSelectorGui().open(player, captainPlayer.getUniqueId());
            return;
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

    private void handleInvite(Player player, String[] args) {
        if (!player.hasPermission("mobarmybattle.join")) {
            player.sendMessage(Component.text("Du hast keine Berechtigung für diesen Befehl.", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Component.text("Verwendung: /mab invite <player>", NamedTextColor.RED));
            return;
        }
        Match match = matchManager.getMatchOf(player.getUniqueId());
        if (match == null) {
            player.sendMessage(Component.text("Du bist in keinem Match.", NamedTextColor.RED));
            return;
        }
        Team team = match.findTeamOf(player.getUniqueId());
        if (team == null || !player.getUniqueId().equals(team.getCaptainId())) {
            player.sendMessage(Component.text("Nur der Captain darf einladen.", NamedTextColor.RED));
            return;
        }
        if (team.isFull()) {
            player.sendMessage(Component.text("Dein Team ist voll.", NamedTextColor.RED));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(Component.text("Spieler nicht online: " + args[1], NamedTextColor.RED));
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("Du bist bereits in deinem Team.", NamedTextColor.RED));
            return;
        }
        if (matchManager.getMatchOf(target.getUniqueId()) != null) {
            player.sendMessage(Component.text(
                    target.getName() + " ist bereits in einem Match.", NamedTextColor.RED));
            return;
        }
        team.invite(target.getUniqueId());
        int teamNumber = match.getTeams().indexOf(team) + 1;
        target.sendMessage(TeamSelectorGui.buildInviteMessage(player.getName(), teamNumber));
        player.sendMessage(Component.text(
                target.getName() + " wurde eingeladen.", NamedTextColor.GREEN));
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
        plugin.cascadeIfHostLeaving(player);
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
        if (!playerId.equals(match.getHostId())) {
            player.sendMessage(Component.text("Nur der Host (Match-Ersteller) darf starten.", NamedTextColor.RED));
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
        if (!player.getUniqueId().equals(match.getHostId())) {
            player.sendMessage(Component.text("Nur der Host (Match-Ersteller) darf die Farm-Phase beenden.", NamedTextColor.RED));
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

    private void handleBuild(Player player) {
        if (!player.hasPermission("mobarmybattle.join")) {
            player.sendMessage(Component.text("Du hast keine Berechtigung für diesen Befehl.", NamedTextColor.RED));
            return;
        }
        Match match = matchManager.getMatchOf(player.getUniqueId());
        if (match == null) {
            player.sendMessage(Component.text("Du bist in keinem Match.", NamedTextColor.RED));
            return;
        }
        if (match.getCurrentPhase().getType() != MatchPhaseType.WAVE_BUILD) {
            player.sendMessage(Component.text("Wellen bauen geht nur in der Wave-Build-Phase.", NamedTextColor.RED));
            return;
        }
        Team team = match.findTeamOf(player.getUniqueId());
        if (team == null || !player.getUniqueId().equals(team.getCaptainId())) {
            player.sendMessage(Component.text("Nur der Captain darf Wellen bauen.", NamedTextColor.RED));
            return;
        }
        plugin.getWaveBuildGui().open(player);
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
            List<UUID> captains = spectatorManager.findSpectatableCaptains(player.getUniqueId());
            plugin.getDeathSpectateGui().open(player, captains, false);
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

    private void handleKick(Player player, String[] args) {
        Match match = matchManager.getMatchOf(player.getUniqueId());
        if (match == null) {
            player.sendMessage("§cDu bist in keinem Match.");
            return;
        }
        Team team = match.findTeamOf(player.getUniqueId());
        if (team == null || !player.getUniqueId().equals(team.getCaptainId())) {
            player.sendMessage("§cNur der Captain kann kicken.");
            return;
        }
        if (args.length < 2) {
            player.sendMessage("§7Usage: /mab kick <player>");
            return;
        }
        String targetName = args[1];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            player.sendMessage("§cSpieler '" + targetName + "' nicht online.");
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage("§cDu kannst dich nicht selbst kicken — nutze /mab leave.");
            return;
        }
        if (!team.hasMember(target.getUniqueId())) {
            player.sendMessage("§cSpieler ist nicht in deinem Team.");
            return;
        }
        matchManager.leaveMatch(target.getUniqueId());
        plugin.getWorldManager().teleportToLobby(target);
        target.sendMessage("§eDu wurdest aus dem Team gekickt.");
        player.sendMessage("§a" + target.getName() + " wurde gekickt.");
    }

    private void handleForceCancel(Player player, String[] args) {
        if (!player.hasPermission("mobarmybattle.admin")) {
            player.sendMessage("§cKeine Berechtigung.");
            return;
        }
        if (args.length < 2) {
            player.sendMessage("§7Usage: /mab forcecancel <matchId>");
            return;
        }
        String matchId = args[1];
        Match match = matchManager.getMatchById(matchId);
        if (match == null) {
            player.sendMessage("§cKein Match mit ID '" + matchId + "'.");
            return;
        }
        matchManager.forceCancelMatch(match,
                plugin.getBattleManager(),
                plugin.getSpectatorManager(),
                plugin.getWorldManager());
        player.sendMessage("§aMatch " + matchId + " wurde abgebrochen.");
    }

    private void handleReload(Player player) {
        if (!player.hasPermission("mobarmybattle.reload")) {
            player.sendMessage(Component.text("Du hast keine Berechtigung für diesen Befehl.", NamedTextColor.RED));
            return;
        }
        plugin.reloadMabConfig();
        player.sendMessage(Component.text("MabConfig neu geladen.", NamedTextColor.GREEN));
    }

    private String nameOf(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) return p.getName();
        var offline = Bukkit.getOfflinePlayer(id);
        String name = offline.getName();
        return name != null ? name : id.toString().substring(0, 8);
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
        player.sendMessage(Component.text("/mab start — Match starten (nur Host)", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/mab pool — Team-Pool anzeigen", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/mab endfarm — Farm-Phase beenden (nur Host)", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/mab build — Wellen-Bau-GUI wieder öffnen (nur Captain, in WAVE_BUILD)", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/mab stats [player] — Lifetime-Stats", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/mab leaderboard — Top 10", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/mab spectate [captain] — Battle zuschauen", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/mab kick <player> — Captain kickt Team-Mitglied", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/mab invite <player> — Captain lädt Spieler in privates Team ein", NamedTextColor.GRAY));
        if (player.hasPermission("mobarmybattle.admin")) {
            player.sendMessage(Component.text("/mab forcecancel <matchId> — Admin: Match abbrechen", NamedTextColor.GRAY));
        }
        if (player.hasPermission("mobarmybattle.reload")) {
            player.sendMessage(Component.text("/mab reload — Konfiguration neu laden", NamedTextColor.GRAY));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> visible = new ArrayList<>(SUBCOMMANDS);
            if (!sender.hasPermission("mobarmybattle.reload")) {
                visible.remove("reload");
            }
            if (!sender.hasPermission("mobarmybattle.admin")) {
                visible.remove("forcecancel");
            }
            return filterByPrefix(visible, args[0]);
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

        if (args.length == 2 && args[0].equalsIgnoreCase("spectate") && sender instanceof Player p) {
            if (spectatorManager == null) return Collections.emptyList();
            return filterByPrefix(spectatorManager.listAvailableTargets(p.getUniqueId()), args[1]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("invite") && sender instanceof Player p) {
            Match match = matchManager.getMatchOf(p.getUniqueId());
            if (match == null) return Collections.emptyList();
            Team team = match.findTeamOf(p.getUniqueId());
            if (team == null || !p.getUniqueId().equals(team.getCaptainId())) return Collections.emptyList();
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.equals(p)) continue;
                if (matchManager.getMatchOf(online.getUniqueId()) != null) continue;
                names.add(online.getName());
            }
            return filterByPrefix(names, args[1]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("kick") && sender instanceof Player p) {
            Match match = matchManager.getMatchOf(p.getUniqueId());
            if (match == null) return Collections.emptyList();
            Team team = match.findTeamOf(p.getUniqueId());
            if (team == null || !p.getUniqueId().equals(team.getCaptainId())) return Collections.emptyList();
            List<String> names = new ArrayList<>();
            for (UUID id : team.getMemberIds()) {
                if (id.equals(p.getUniqueId())) continue;
                Player m = Bukkit.getPlayer(id);
                if (m != null) names.add(m.getName());
            }
            return filterByPrefix(names, args[1]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("forcecancel") && sender.hasPermission("mobarmybattle.admin")) {
            List<String> ids = new ArrayList<>();
            for (Match m : matchManager.getActiveMatches()) ids.add(m.getId());
            return filterByPrefix(ids, args[1]);
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
