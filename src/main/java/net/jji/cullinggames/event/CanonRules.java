package net.jji.cullinggames.event;

import java.util.List;

/**
 * The eight rules of the original Culling Game, paraphrased, for players to read. Rules bought on this server
 * continue from 9. The server version differs (minutes instead of days, sealing instead of removal), which
 * {@link #SERVER_NOTE} says.
 */
public final class CanonRules {
    public static final List<String> RULES = List.of(
            "Anyone who awakens a technique must join the game at a colony within 19 days.",
            "Anyone who misses that deadline loses their technique.",
            "A non-player who enters a colony becomes a player.",
            "Players earn points by taking the lives of other players.",
            "The game master gives each life a value: 5 points for a sorcerer, 1 for a non-sorcerer.",
            "A player may spend 100 points to add a new rule.",
            "The game master must accept a new rule unless it badly obstructs the game.",
            "A player whose points don't change for 19 days loses their technique.");

    public static final String SERVER_NOTE = "On this server: minutes instead of days, and your technique is sealed until you score, not removed.";

    private CanonRules() {}
}
