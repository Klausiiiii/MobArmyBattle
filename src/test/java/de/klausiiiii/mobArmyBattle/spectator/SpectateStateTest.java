package de.klausiiiii.mobArmyBattle.spectator;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpectateStateTest {

    @Test
    void recordHoldsMatchAndArenaIds() {
        SpectateState s = new SpectateState("match-1", "mab_arena_match-1_team-0-arena");
        assertEquals("match-1", s.matchId());
        assertEquals("mab_arena_match-1_team-0-arena", s.arenaWorldName());
    }

    @Test
    void recordEqualsByValue() {
        SpectateState a = new SpectateState("m", "w");
        SpectateState b = new SpectateState("m", "w");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
