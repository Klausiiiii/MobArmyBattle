package de.klausiiiii.mobArmyBattle.battle;

public class BattleStats {

    private int wavesSurvived = 0;
    private int mobKills = 0;
    private long finishTimeMs = 0L;
    private boolean finished = false;

    public int getWavesSurvived() {
        return wavesSurvived;
    }

    public int getMobKills() {
        return mobKills;
    }

    public long getFinishTimeMs() {
        return finishTimeMs;
    }

    public boolean isFinished() {
        return finished;
    }

    public void recordMobKill() {
        mobKills++;
    }

    public void recordWaveSurvived() {
        wavesSurvived++;
    }

    public void markFinished(long elapsedMs) {
        if (finished) {
            throw new IllegalStateException("Stats sind bereits finalisiert");
        }
        this.finishTimeMs = elapsedMs;
        this.finished = true;
    }
}
