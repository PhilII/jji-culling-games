package net.jji.cullinggames.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.jji.cullinggames.config.CgConfig;
import net.jji.cullinggames.network.AnnouncePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;

/**
 * Timed reveals (Red, 20 Oct), run on event time so pauses don't count:
 * the colony of the top players by points every revealIntervalMinutes, and a glow for hunted players
 * (0 points, no kill or assist yet) every huntedIntervalMinutes.
 */
public final class Reveals {
    private static final long TICKS_PER_MINUTE = 20 * 60;

    private Reveals() {}

    /** Called by EventManager after each running tick. */
    static void tick(MinecraftServer server, EventState state) {
        long t = state.runningTicks();
        int revealMinutes = CgConfig.REVEAL_INTERVAL_MINUTES.get();
        if (revealMinutes > 0 && t % (revealMinutes * TICKS_PER_MINUTE) == 0) {
            revealTop(server, state);
        }
        if (CgConfig.HUNTED_ENABLED.get() && t % (CgConfig.HUNTED_INTERVAL_MINUTES.get() * TICKS_PER_MINUTE) == 0) {
            glowHunted(server, state);
        }
    }

    /** "name - colony" for the top revealTopCount participants still in, highest points first; 0 points never count. */
    static List<String> topLines(MinecraftServer server, EventState state) {
        List<String> lines = new ArrayList<>();
        state.allPoints().entrySet().stream()
                .filter(e -> e.getValue() > 0 && state.isParticipant(e.getKey()) && !state.isEliminated(e.getKey()))
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .limit(CgConfig.REVEAL_TOP_COUNT.get())
                .forEach(e -> {
                    String colony = state.colonyOf(e.getKey());
                    lines.add(name(server, e.getKey()) + " - " + (colony == null ? "unknown" : colony));
                });
        return lines;
    }

    static void revealTop(MinecraftServer server, EventState state) {
        List<String> lines = topLines(server, state);
        if (lines.isEmpty()) {
            return;
        }
        StringBuilder chat = new StringBuilder("The top players are in:");
        for (int i = 0; i < lines.size(); i++) {
            chat.append("\n ").append(i + 1).append(". ").append(lines.get(i));
        }
        Kogane.broadcast(server, chat.toString());
        Kogane.announceAll(server, AnnouncePacket.Kind.INFO, "Top " + lines.size() + " located!", String.join(", ", lines));
    }

    /** Glowing for every online hunted participant. Returns how many glow. */
    static int glowHunted(MinecraftServer server, EventState state) {
        int ticks = CgConfig.HUNTED_GLOW_SECONDS.get() * 20;
        int count = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!state.isHunted(p.getUUID()) || p.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
                continue;
            }
            p.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, false));
            Kogane.tell(p, "You have 0 points, so you are hunted: you glow for " + ticks / 20 + " s. A kill or an assist ends it.");
            Kogane.announce(p, AnnouncePacket.Kind.WARNING, "You are hunted!", "Everyone can see you for " + ticks / 20 + " seconds.");
            Kogane.cue(p, Kogane.Cue.WARNING);
            count++;
        }
        if (count > 0) {
            Kogane.broadcast(server, count + (count == 1 ? " hunted player glows" : " hunted players glow")
                    + " for " + ticks / 20 + " s. Find them.");
        }
        return count;
    }

    private static String name(MinecraftServer server, UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        return server.getProfileCache() == null ? id.toString()
                : server.getProfileCache().get(id).map(p -> p.getName()).orElse(id.toString());
    }
}
