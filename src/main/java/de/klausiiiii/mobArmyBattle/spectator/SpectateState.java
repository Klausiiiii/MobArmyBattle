package de.klausiiiii.mobArmyBattle.spectator;

public record SpectateState(String matchId, String arenaWorldName) {
    public SpectateState {
        if (matchId == null || matchId.isBlank()) {
            throw new IllegalArgumentException("matchId darf nicht leer sein");
        }
        if (arenaWorldName == null || arenaWorldName.isBlank()) {
            throw new IllegalArgumentException("arenaWorldName darf nicht leer sein");
        }
    }
}
