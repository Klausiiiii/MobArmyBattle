package de.klausiiiii.mobArmyBattle.ui;

import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.Team;
import de.klausiiiii.mobArmyBattle.match.phase.FarmPhase;
import de.klausiiiii.mobArmyBattle.pool.MobEntry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SidebarRendererTest {

    private static final String NAKED = "none|none|none|none|none|none";

    @Test
    void rendersFarmLayoutWithEmptyPool() {
        Match match = new Match("m1");
        Team t = new Team(UUID.randomUUID(), 4);
        match.addTeam(t);
        match.addTeam(new Team(UUID.randomUUID(), 4));
        match.transitionTo(new FarmPhase());

        List<String> lines = SidebarRenderer.render(match, t, null, match.getPhaseStartedAt());

        assertEquals("§e§lMobArmyBattle", lines.get(0));
        assertEquals("§7Phase: §aFarm", lines.get(1));
        assertTrue(lines.get(2).startsWith("§7Zeit: §f"));
        assertTrue(lines.contains("§6Pool:"));
        assertEquals("§7Teams aktiv: 2/2", lines.get(lines.size() - 1));
    }

    @Test
    void rendersFarmLayoutWithPopulatedPool() {
        Match match = new Match("m1");
        Team t = new Team(UUID.randomUUID(), 4);
        match.addTeam(t);
        match.addTeam(new Team(UUID.randomUUID(), 4));
        match.transitionTo(new FarmPhase());
        for (int i = 0; i < 5; i++) t.getPool().add(new MobEntry("ZOMBIE", NAKED));
        for (int i = 0; i < 3; i++) t.getPool().add(new MobEntry("SKELETON", NAKED));

        List<String> lines = SidebarRenderer.render(match, t, null, match.getPhaseStartedAt());

        assertTrue(lines.stream().anyMatch(l -> l.contains("Zombie") && l.contains("x5")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("Skeleton") && l.contains("x3")));
    }

    @Test
    void truncatesPoolToTop7Entries() {
        Match match = new Match("m1");
        Team t = new Team(UUID.randomUUID(), 4);
        match.addTeam(t);
        match.addTeam(new Team(UUID.randomUUID(), 4));
        match.transitionTo(new FarmPhase());
        String[] types = {"ZOMBIE", "SKELETON", "SPIDER", "CREEPER", "ENDERMAN", "WITCH", "PIGLIN", "BLAZE", "GHAST"};
        for (int i = 0; i < types.length; i++) {
            for (int j = 0; j <= i; j++) t.getPool().add(new MobEntry(types[i], NAKED));
        }

        List<String> lines = SidebarRenderer.render(match, t, null, match.getPhaseStartedAt());

        long poolLines = lines.stream().filter(l -> l.startsWith("§7 ")).count();
        assertEquals(7, poolLines, "Pool sollte auf Top-7 truncated sein");
    }
}
