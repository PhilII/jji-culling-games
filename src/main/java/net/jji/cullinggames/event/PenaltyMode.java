package net.jji.cullinggames.event;

/** What happens when a participant's points don't change in time. Chosen per event. */
public enum PenaltyMode {
    /** CT sealed until the player's points change again (or the event ends). */
    SEAL_CT,
    /** The player loses one event life. */
    LOSE_LIFE
}
