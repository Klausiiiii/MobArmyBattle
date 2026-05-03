package de.klausiiiii.mobArmyBattle.command;

import de.klausiiiii.mobArmyBattle.MobArmyBattle;
import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchManager;
import de.klausiiiii.mobArmyBattle.match.MatchPhaseType;
import de.klausiiiii.mobArmyBattle.match.Team;
import de.klausiiiii.mobArmyBattle.match.phase.FarmPhase;
import de.klausiiiii.mobArmyBattle.match.phase.WaveBuildPhase;
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

    private static final List<String> SUBCOMMANDS = List.of("create", "join", "leave", "start", "pool", "endfarm");

    private final MatchManager matchManager;
    private final MobArmyBattle plugin;

    public MabCommand(MobArmyBattle plugin, MatchManager matchManager) {
        this.plugin = plugin;
        this.matchManager = matchManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Nur Spieler dürfen /mab ausführen.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendUsage(player);
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
                default -> sendUsage(player);
            }
        } catch (IllegalStateException | IllegalArgumentException e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        }
        return true;
    }

    private void handleCreate(Player player, String[] args) {
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
