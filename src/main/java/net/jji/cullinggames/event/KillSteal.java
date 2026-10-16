package net.jji.cullinggames.event;

/** How many points a killer takes from the participant they kill. */
public enum KillSteal {
    NONE,
    FLAT,
    PERCENT;

    /** Points to take from a victim who has {@code victimPoints}; never more than they have. */
    public int amount(int victimPoints, int flat, int percent) {
        int raw = switch (this) {
            case NONE -> 0;
            case FLAT -> flat;
            case PERCENT -> (int) Math.floor(victimPoints * percent / 100.0);
        };
        return Math.max(0, Math.min(raw, victimPoints));
    }
}
