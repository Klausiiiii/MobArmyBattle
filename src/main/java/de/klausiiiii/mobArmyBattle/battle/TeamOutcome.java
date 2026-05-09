package de.klausiiiii.mobArmyBattle.battle;

import java.util.Set;
import java.util.UUID;

public record TeamOutcome(UUID captainId, Set<UUID> memberIds, boolean winner, int mobKills) {
}
