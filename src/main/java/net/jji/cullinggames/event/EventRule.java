package net.jji.cullinggames.event;

import java.util.Locale;

/**
 * Rules a player can add for the rule cost. A fixed menu, because the code has to enforce them.
 * Each rule can be added once per event and lasts until the event ends.
 */
public enum EventRule {
    REVEAL_POINTS("Everyone can see the leaderboard for free."),
    DOUBLE_POINTS("All kills give double points."),
    LONGER_TIMER("Everyone gets extra minutes before the points penalty."),
    FREE_TRANSFERS("Everyone can transfer points without buying it.");

    private final String description;

    EventRule(String description) {
        this.description = description;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String description() {
        return description;
    }

    public static EventRule byId(String id) {
        for (EventRule r : values()) {
            if (r.id().equalsIgnoreCase(id)) {
                return r;
            }
        }
        return null;
    }
}
