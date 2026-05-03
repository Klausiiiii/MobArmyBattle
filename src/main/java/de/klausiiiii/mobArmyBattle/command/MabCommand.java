package de.klausiiiii.mobArmyBattle.command;

import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchManager;
import de.klausiiiii.mobArmyBattle.match.MatchPhaseType;
import de.klausiiiii.mobArmyBattle.match.Team;
import de.klausiiiii.mobArmyBattle.match.phase.FarmPhase;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class MabCommand implements CommandExecutor {

    private final MatchManager matchManager;

    public MabCommand(MatchManager matchManager) {
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
                case "create" -> handleCreate(player);
                case "join" -> handleJoin(player, args);
                case "leave" -> handleLeave(player);
                case "start" -> handleStart(player);
                default -> sendUsage(player);
            }
        } catch (IllegalStateException | IllegalArgumentException e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        }
        return true;
    }

    private void handleCreate(Player player) {
        Match match = matchManager.createMatch(player.getUniqueId());
        player.sendMessage(Component.text("Match erstellt: " + match.getId() + ". Du bist Captain.",
                NamedTextColor.GREEN));
        player.sendMessage(Component.text("Andere joinen mit: /mab join " + player.getName(),
                NamedTextColor.GRAY));
    }

    private void handleJoin(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Verwendung: /mab join <captain-name>", NamedTextColor.RED));
            return;
        }
        Player captainPlayer = Bukkit.getPlayerExact(args[1]);
        if (captainPlayer == null) {
            player.sendMessage(Component.text("Spieler nicht online: " + args[1], NamedTextColor.RED));
            return;
        }
        matchManager.joinMatch(player.getUniqueId(), captainPlayer.getUniqueId());
        player.sendMessage(Component.text("Du bist " + captainPlayer.getName() + "s Match beigetreten.",
                NamedTextColor.GREEN));
        captainPlayer.sendMessage(Component.text(player.getName() + " ist deinem Match beigetreten.",
                NamedTextColor.GREEN));
    }

    private void handleLeave(Player player) {
        Match match = matchManager.getMatchOf(player.getUniqueId());
        if (match == null) {
            player.sendMessage(Component.text("Du bist in keinem Match.", NamedTextColor.RED));
            return;
        }
        matchManager.leaveMatch(player.getUniqueId());
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
        match.transitionTo(new FarmPhase());

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

    private void sendUsage(Player player) {
        player.sendMessage(Component.text("MobArmyBattle-Befehle:", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/mab create — Match erstellen", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/mab join <captain> — Match beitreten", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/mab leave — Match verlassen", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/mab start — Match starten (nur Captain)", NamedTextColor.GRAY));
    }
}
