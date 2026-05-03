package de.klausiiiii.mobArmyBattle.match;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class MatchMode {

    private static final int MAX_TEAM_SIZE = 4;

    private final List<Integer> teamSizes;

    public MatchMode(List<Integer> teamSizes) {
        if (teamSizes == null || teamSizes.size() != 2) {
            throw new IllegalArgumentException("MatchMode benötigt genau 2 Teams");
        }
        for (Integer size : teamSizes) {
            if (size == null || size < 1) {
                throw new IllegalArgumentException("Team-Größe muss >= 1 sein");
            }
            if (size > MAX_TEAM_SIZE) {
                throw new IllegalArgumentException("Team-Größe darf maximal " + MAX_TEAM_SIZE + " sein");
            }
        }
        this.teamSizes = List.copyOf(teamSizes);
    }

    public static MatchMode parse(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Mode-String darf nicht leer sein");
        }
        String[] parts = input.toLowerCase().split("v");
        if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            throw new IllegalArgumentException("Ungültiges Mode-Format (erwartet z.B. \"2v2\"): " + input);
        }
        int a, b;
        try {
            a = Integer.parseInt(parts[0]);
            b = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Ungültiges Mode-Format: " + input);
        }
        return new MatchMode(List.of(a, b));
    }

    public List<Integer> getTeamSizes() {
        return Collections.unmodifiableList(teamSizes);
    }

    public int getTeamCount() {
        return teamSizes.size();
    }

    public int getMaxSizeOfTeam(int teamIndex) {
        return teamSizes.get(teamIndex);
    }

    public String getDisplayName() {
        return teamSizes.get(0) + "v" + teamSizes.get(1);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MatchMode that)) return false;
        return teamSizes.equals(that.teamSizes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(teamSizes);
    }

    @Override
    public String toString() {
        return "MatchMode{" + getDisplayName() + "}";
    }
}
