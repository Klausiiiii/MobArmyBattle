package de.klausiiiii.mobArmyBattle.spectator;

import de.klausiiiii.mobArmyBattle.MobArmyBattle;
import de.klausiiiii.mobArmyBattle.battle.BattleManager;
import de.klausiiiii.mobArmyBattle.battle.BattleSession;
import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchManager;
import de.klausiiiii.mobArmyBattle.match.MatchPhaseType;
import de.klausiiiii.mobArmyBattle.match.Team;
import de.klausiiiii.mobArmyBattle.tournament.Tournament;
import de.klausiiiii.mobArmyBattle.tournament.TournamentManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SpectatorManager {

    public sealed interface PermissionResult {
        record Allowed(String matchId, String arenaWorldName) implements PermissionResult {}
        record Denied(String reason) implements PermissionResult {}
    }

    private final MobArmyBattle plugin;
    private final MatchManager matchManager;
    private final BattleManager battleManager;
    private final TournamentManager tournamentManager;
    private final Map<UUID, SpectateState> states = new HashMap<>();
    private final Map<UUID, Location> returnLocations = new HashMap<>();

    public SpectatorManager(MobArmyBattle plugin,
                            MatchManager matchManager,
                            BattleManager battleManager,
                            TournamentManager tournamentManager) {
        this.plugin = plugin;
        this.matchManager = matchManager;
        this.battleManager = battleManager;
        this.tournamentManager = tournamentManager;
    }

    public PermissionResult checkPermission(UUID viewerId, UUID targetCaptainId) {
        Match targetMatch = findMatchByCaptain(targetCaptainId);
        if (targetMatch == null) {
            return new PermissionResult.Denied("Captain ist nicht in einem laufenden Match.");
        }
        if (targetMatch.getCurrentPhase().getType() != MatchPhaseType.BATTLE) {
            return new PermissionResult.Denied("Match ist nicht in der Battle-Phase.");
        }
        Team targetTeam = targetMatch.findTeamOf(targetCaptainId);
        if (targetTeam == null) {
            return new PermissionResult.Denied("Target-Team nicht gefunden.");
        }
        String arenaWorld = arenaWorldNameFor(targetMatch, targetTeam);
        if (Bukkit.getWorld(arenaWorld) == null) {
            return new PermissionResult.Denied("Arena-Welt existiert nicht.");
        }

        Match viewerMatch = matchManager.getMatchOf(viewerId);
        if (viewerMatch != null) {
            if (!viewerMatch.getId().equals(targetMatch.getId())) {
                return new PermissionResult.Denied("Du bist in einem anderen Match.");
            }
            BattleSession session = battleManager.getSessionByPlayer(viewerId);
            if (session == null) return new PermissionResult.Denied("Du bist nicht in einer Battle-Session.");
            BattleSession.TeamState own = session.getStateByPlayerUUID(viewerId);
            if (own == null || !own.stats.isFinished()) {
                return new PermissionResult.Denied("Du musst erst beide Wellen abschließen.");
            }
            if (own.opponent != targetTeam) {
                return new PermissionResult.Denied("Nur dein Pair-Partner kann zugeschaut werden.");
            }
            return new PermissionResult.Allowed(targetMatch.getId(), arenaWorld);
        }

        Tournament tournament = tournamentManager.findEliminatedTournamentOf(viewerId);
        if (tournament != null
                && tournament == tournamentManager.getTournamentByMatchId(targetMatch.getId())) {
            return new PermissionResult.Allowed(targetMatch.getId(), arenaWorld);
        }

        return new PermissionResult.Denied("Du darfst nicht zuschauen.");
    }

    public boolean startSpectate(Player viewer, UUID targetCaptainId) {
        PermissionResult res = checkPermission(viewer.getUniqueId(), targetCaptainId);
        if (res instanceof PermissionResult.Denied d) {
            viewer.sendMessage("§c" + d.reason());
            return false;
        }
        PermissionResult.Allowed allowed = (PermissionResult.Allowed) res;
        if (Bukkit.getWorld(allowed.arenaWorldName()) == null) {
            viewer.sendMessage("§cArena-Welt nicht gefunden.");
            return false;
        }
        if (!states.containsKey(viewer.getUniqueId())) {
            returnLocations.put(viewer.getUniqueId(), viewer.getLocation());
        }
        return doSpectate(viewer, allowed.matchId(), allowed.arenaWorldName());
    }

    /**
     * Death-Spectate: Spieler ist gerade in der Battle-Phase gestorben und wählt
     * via GUI eine andere noch laufende Arena. Permission-Checks werden übersprungen
     * (Caller hat bereits über die GUI gefiltert), und es wird KEINE returnLocation
     * gespeichert — endSpectate fällt automatisch auf die Lobby zurück.
     */
    public boolean startDeathSpectate(Player viewer, UUID targetCaptainId) {
        Match targetMatch = findMatchByCaptain(targetCaptainId);
        if (targetMatch == null) {
            viewer.sendMessage("§cCaptain nicht in einem laufenden Match.");
            return false;
        }
        Team targetTeam = targetMatch.findTeamOf(targetCaptainId);
        if (targetTeam == null) {
            viewer.sendMessage("§cTeam nicht gefunden.");
            return false;
        }
        String arenaWorldName = arenaWorldNameFor(targetMatch, targetTeam);
        if (Bukkit.getWorld(arenaWorldName) == null) {
            viewer.sendMessage("§cArena-Welt existiert nicht.");
            return false;
        }
        return doSpectate(viewer, targetMatch.getId(), arenaWorldName);
    }

    private boolean doSpectate(Player viewer, String matchId, String arenaWorldName) {
        World arena = Bukkit.getWorld(arenaWorldName);
        if (arena == null) {
            viewer.sendMessage("§cArena-Welt nicht gefunden.");
            return false;
        }
        Location base = arena.getSpawnLocation();
        int x = base.getBlockX();
        int z = base.getBlockZ();
        int y = arena.getHighestBlockYAt(x, z);
        // 10 Blöcke über dem höchsten Block — Bird-Eye-View für Spectator.
        Location target = new Location(arena, x + 0.5, y + 11.0, z + 0.5);
        viewer.teleport(target);
        viewer.setGameMode(GameMode.SPECTATOR);
        states.put(viewer.getUniqueId(), new SpectateState(matchId, arenaWorldName));
        viewer.sendMessage("§7Spectator-Mode aktiv. /mab leave zum Beenden.");
        return true;
    }

    public void endSpectate(UUID viewerId) {
        SpectateState state = states.remove(viewerId);
        Location ret = returnLocations.remove(viewerId);
        Player p = Bukkit.getPlayer(viewerId);
        if (p == null) return;
        if (state != null) {
            World lobby = plugin.getWorldManager().getOrCreateLobbyWorld();
            Location target = ret != null ? ret : lobby.getSpawnLocation();
            p.teleport(target);
            p.setGameMode(GameMode.SURVIVAL);
        }
    }

    public void evictAll(String matchId) {
        List<UUID> toEvict = new ArrayList<>();
        for (Map.Entry<UUID, SpectateState> e : states.entrySet()) {
            if (e.getValue().matchId().equals(matchId)) toEvict.add(e.getKey());
        }
        for (UUID id : toEvict) endSpectate(id);
    }

    public boolean isSpectating(UUID viewerId) {
        return states.containsKey(viewerId);
    }

    public List<String> listAvailableTargets(UUID viewerId) {
        List<String> out = new ArrayList<>();
        for (UUID cap : findSpectatableCaptains(viewerId)) {
            Player p = Bukkit.getPlayer(cap);
            if (p != null) out.add(p.getName());
        }
        return out;
    }

    /** Wie {@link #listAvailableTargets} aber gibt UUIDs zurück (für die GUI). */
    public List<UUID> findSpectatableCaptains(UUID viewerId) {
        List<UUID> out = new ArrayList<>();
        for (Match m : matchManager.getActiveMatches()) {
            if (m.getCurrentPhase().getType() != MatchPhaseType.BATTLE) continue;
            for (Team t : m.getTeams()) {
                UUID cap = t.getCaptainId();
                if (cap == null) continue;
                PermissionResult res = checkPermission(viewerId, cap);
                if (res instanceof PermissionResult.Allowed) {
                    out.add(cap);
                }
            }
        }
        return out;
    }

    private Match findMatchByCaptain(UUID captainId) {
        for (Match m : matchManager.getActiveMatches()) {
            Team t = m.findTeamOf(captainId);
            if (t != null && captainId.equals(t.getCaptainId())) return m;
        }
        return null;
    }

    private static String arenaWorldNameFor(Match match, Team team) {
        int idx = match.getTeams().indexOf(team);
        return de.klausiiiii.mobArmyBattle.world.WorldManager.ARENA_WORLD_PREFIX + match.getId() + "_team-" + idx + "-arena";
    }
}
