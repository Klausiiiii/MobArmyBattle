package de.klausiiiii.mobArmyBattle.match;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MatchModeTest {

    @Test
    void parsesSymmetric1v1() {
        MatchMode mode = MatchMode.parse("1v1");
        assertEquals(List.of(1, 1), mode.getTeamSizes());
        assertEquals(2, mode.getTeamCount());
    }

    @Test
    void parsesSymmetric4v4() {
        MatchMode mode = MatchMode.parse("4v4");
        assertEquals(List.of(4, 4), mode.getTeamSizes());
    }

    @Test
    void parsesAsymmetric2v3() {
        MatchMode mode = MatchMode.parse("2v3");
        assertEquals(List.of(2, 3), mode.getTeamSizes());
    }

    @Test
    void parseIsCaseInsensitive() {
        MatchMode mode = MatchMode.parse("3V2");
        assertEquals(List.of(3, 2), mode.getTeamSizes());
    }

    @Test
    void rejectsBadFormat() {
        assertThrows(IllegalArgumentException.class, () -> MatchMode.parse("abc"));
        assertThrows(IllegalArgumentException.class, () -> MatchMode.parse("1"));
        assertThrows(IllegalArgumentException.class, () -> MatchMode.parse("1v"));
        assertThrows(IllegalArgumentException.class, () -> MatchMode.parse("v1"));
        assertThrows(IllegalArgumentException.class, () -> MatchMode.parse(""));
    }

    @Test
    void rejectsNullInput() {
        assertThrows(IllegalArgumentException.class, () -> MatchMode.parse(null));
    }

    @Test
    void rejectsZeroSizes() {
        assertThrows(IllegalArgumentException.class, () -> MatchMode.parse("0v1"));
        assertThrows(IllegalArgumentException.class, () -> MatchMode.parse("1v0"));
    }

    @Test
    void rejectsTooLargeSizes() {
        assertThrows(IllegalArgumentException.class, () -> MatchMode.parse("5v5"));
    }

    @Test
    void displayNameRoundtrips() {
        assertEquals("1v1", MatchMode.parse("1v1").getDisplayName());
        assertEquals("2v3", MatchMode.parse("2v3").getDisplayName());
    }

    @Test
    void canConstructDirectly() {
        MatchMode mode = new MatchMode(List.of(2, 2));
        assertEquals(List.of(2, 2), mode.getTeamSizes());
        assertEquals("2v2", mode.getDisplayName());
    }

    @Test
    void rejectsLessThanTwoTeams() {
        assertThrows(IllegalArgumentException.class, () -> new MatchMode(List.of(1)));
    }

    @Test
    void rejectsMoreThanTwoTeams() {
        assertThrows(IllegalArgumentException.class, () -> new MatchMode(List.of(1, 1, 1)));
    }

    @Test
    void teamSizesAreImmutable() {
        MatchMode mode = MatchMode.parse("2v2");
        assertThrows(UnsupportedOperationException.class,
                () -> mode.getTeamSizes().add(3));
    }

    @Test
    void equalsAndHashCode() {
        assertEquals(MatchMode.parse("2v3"), MatchMode.parse("2v3"));
        assertEquals(MatchMode.parse("2v3").hashCode(), MatchMode.parse("2v3").hashCode());
        assertNotEquals(MatchMode.parse("2v2"), MatchMode.parse("3v3"));
    }
}
