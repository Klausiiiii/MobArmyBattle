package de.klausiiiii.mobArmyBattle.ui;

import de.klausiiiii.mobArmyBattle.match.Team;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.UUID;

public final class Notifications {

    private static final Title.Times TIMES =
            Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(2), Duration.ofMillis(500));

    private Notifications() {}

    public static void farmStart(Team team) {
        sendTeam(team, "§a§lFarm-Phase", "§7Sammelt Mobs", Sound.UI_TOAST_CHALLENGE_COMPLETE);
    }

    public static void waveBuildStart(Team team) {
        sendTeam(team, "§e§lWellen bauen", "§7Captain: /mab build", Sound.BLOCK_NOTE_BLOCK_PLING);
    }

    public static void battleStart(Team team) {
        sendTeam(team, "§c§lBattle!", "§7Welle 1 startet gleich", Sound.ENTITY_ENDER_DRAGON_GROWL);
    }

    public static void waveSpawned(Team team, int waveNumber, int mobCount) {
        sendTeam(team, "§6§lWelle " + waveNumber, "§7" + mobCount + " Mobs", Sound.ENTITY_ZOMBIE_AMBIENT);
    }

    public static void wavePassed(Team team, int waveNumber) {
        sendTeam(team, "§a§lWelle " + waveNumber + " bestanden", "", Sound.UI_TOAST_CHALLENGE_COMPLETE);
    }

    public static void wavePrep(Team team, int waveNumber, int prepSec) {
        sendTeam(team, "§e§lBauphase", "§7Welle " + waveNumber + " in " + prepSec + "s",
                Sound.BLOCK_NOTE_BLOCK_BELL);
    }

    public static void waveTimedOut(Team team, int waveNumber) {
        sendTeam(team, "§c§lZeit abgelaufen!", "§7Welle " + waveNumber + " verloren",
                Sound.ENTITY_VILLAGER_NO);
    }

    public static void victory(Team team, String winnerTeamName) {
        sendTeam(team, "§6§lSIEG", "§7" + winnerTeamName, Sound.UI_TOAST_CHALLENGE_COMPLETE);
    }

    public static void defeat(Team team, String winnerTeamName) {
        sendTeam(team, "§c§lNiederlage", "§7" + winnerTeamName, Sound.ENTITY_VILLAGER_NO);
    }

    private static void sendTeam(Team team, String title, String subtitle, Sound sound) {
        LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();
        Title t = Title.title(legacy.deserialize(title), legacy.deserialize(subtitle), TIMES);
        for (UUID id : team.getMemberIds()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.showTitle(t);
                p.playSound(p.getLocation(), sound, 1.0f, 1.0f);
            }
        }
    }
}
