package de.klausiiiii.mobArmyBattle.world;

import de.klausiiiii.mobArmyBattle.MobArmyBattle;
import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchPhaseType;
import de.klausiiiii.mobArmyBattle.match.Team;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Lädt asynchron die Chunks im Radius um den Farm-Spawn vor und teleportiert
 * danach das Team rein. Verhindert das Rubberbanding direkt nach Phase-Start.
 *
 * <p>Liegt bewusst NICHT in {@link de.klausiiiii.mobArmyBattle.match.phase.FarmPhase}:
 * der {@code Bukkit.getScheduler().runTask(Plugin, ...)} Call würde sonst dafür
 * sorgen dass die FarmPhase-Klasse {@code org.bukkit.plugin.Plugin} im Bytecode
 * referenziert, was die Domain-Tests (paper-api ist {@code compileOnly}) crashen
 * lässt sobald sie eine FarmPhase-Instanz konstruieren.
 */
public final class FarmWorldLoader {

    /** Chunk-Radius um Spawn der vor dem Teleport sicher generiert sein muss. */
    private static final int PRELOAD_CHUNK_RADIUS = 5;

    private FarmWorldLoader() {}

    public static void preloadAndTeleport(MobArmyBattle plugin, Match match, Team team,
                                          World farmWorld, Location spawn) {
        announceLoading(team);
        int centerCx = spawn.getBlockX() >> 4;
        int centerCz = spawn.getBlockZ() >> 4;
        List<CompletableFuture<Chunk>> futures = new ArrayList<>();
        for (int dx = -PRELOAD_CHUNK_RADIUS; dx <= PRELOAD_CHUNK_RADIUS; dx++) {
            for (int dz = -PRELOAD_CHUNK_RADIUS; dz <= PRELOAD_CHUNK_RADIUS; dz++) {
                futures.add(farmWorld.getChunkAtAsync(centerCx + dx, centerCz + dz, true));
            }
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .whenComplete((v, t) -> Bukkit.getScheduler().runTask(plugin,
                        () -> teleportTeam(plugin, match, team, spawn)));
    }

    private static void announceLoading(Team team) {
        for (UUID memberId : team.getMemberIds()) {
            Player p = Bukkit.getPlayer(memberId);
            if (p != null) p.sendMessage("§7Generiere Farm-Welt …");
        }
    }

    private static void teleportTeam(MobArmyBattle plugin, Match match, Team team, Location spawn) {
        if (match.getCurrentPhase().getType() != MatchPhaseType.FARM) return;
        for (UUID memberId : team.getMemberIds()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                member.teleport(spawn);
                StarterKitApplier.applyKit(member, plugin.effectiveConfig(match).starterKit());
            }
        }
    }
}
