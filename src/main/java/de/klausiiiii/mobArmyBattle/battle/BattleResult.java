package de.klausiiiii.mobArmyBattle.battle;

public final class BattleResult {

    public enum Winner { A, B, DRAW }

    private BattleResult() {
    }

    public static Winner compare(BattleStats a, BattleStats b) {
        if (a.getWavesSurvived() != b.getWavesSurvived()) {
            return a.getWavesSurvived() > b.getWavesSurvived() ? Winner.A : Winner.B;
        }
        if (a.getMobKills() != b.getMobKills()) {
            return a.getMobKills() > b.getMobKills() ? Winner.A : Winner.B;
        }
        long aTime = a.isFinished() ? a.getFinishTimeMs() : Long.MAX_VALUE;
        long bTime = b.isFinished() ? b.getFinishTimeMs() : Long.MAX_VALUE;
        if (aTime != bTime) {
            return aTime < bTime ? Winner.A : Winner.B;
        }
        return Winner.DRAW;
    }
}
