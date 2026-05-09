package de.klausiiiii.mobArmyBattle.config;

public record DeathPenaltyConfig(Mode mode) {
    public enum Mode {
        NONE(0, false),
        SOFT(5, false),
        DROP_ITEMS(0, true),
        HARD(25, true);

        private final int poolPercent;
        private final boolean dropItems;

        Mode(int poolPercent, boolean dropItems) {
            this.poolPercent = poolPercent;
            this.dropItems = dropItems;
        }

        public int poolPercent() { return poolPercent; }
        public boolean dropItems() { return dropItems; }
    }

    public DeathPenaltyConfig {
        if (mode == null) throw new IllegalArgumentException("mode darf nicht null sein");
    }
}
