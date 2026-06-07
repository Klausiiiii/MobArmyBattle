package de.klausiiiii.mobArmyBattle.config;

public record PhaseDurations(
        int farmDurationMin,
        int waveBuildDurationMin,
        int prepDurationSec,
        int wavePauseSec,
        int waveHardTimeoutMin,
        boolean autoFarmTransition,
        int postBattleViewSec
) {
    public PhaseDurations {
        if (farmDurationMin < 1) throw new IllegalArgumentException("farm-duration-min muss >= 1 sein");
        if (waveBuildDurationMin < 1) throw new IllegalArgumentException("wave-build-duration-min muss >= 1 sein");
        if (prepDurationSec < 0) throw new IllegalArgumentException("prep-duration-sec muss >= 0 sein");
        if (wavePauseSec < 0) throw new IllegalArgumentException("wave-pause-sec muss >= 0 sein");
        if (waveHardTimeoutMin < 1) throw new IllegalArgumentException("wave-hard-timeout-min muss >= 1 sein");
        if (postBattleViewSec < 0) throw new IllegalArgumentException("post-battle-view-sec muss >= 0 sein");
    }

    /** Backwards-compat constructor for callers that haven't been updated yet. */
    public PhaseDurations(int farmDurationMin, int waveBuildDurationMin, int prepDurationSec,
                          int wavePauseSec, int waveHardTimeoutMin, boolean autoFarmTransition) {
        this(farmDurationMin, waveBuildDurationMin, prepDurationSec, wavePauseSec,
                waveHardTimeoutMin, autoFarmTransition, 8);
    }
}
