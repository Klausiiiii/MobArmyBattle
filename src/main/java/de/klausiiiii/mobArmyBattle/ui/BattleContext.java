package de.klausiiiii.mobArmyBattle.ui;

public record BattleContext(
        int mobsAlive,
        int mobsTotalThisWave,
        int mobKills,
        int teamMembersAlive,
        int teamMembersTotal,
        int currentWaveNumber,
        String pairCaptainName
) {}
