package net.jji.cullinggames.event;

import net.jji.cullinggames.CullingGames;
import net.jji.cullinggames.config.CgConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** All event transitions go through here. Commands call these; nothing else mutates EventState. */
public final class EventManager {
    private EventManager() {}

    public enum Result { OK, ALREADY_RUNNING, NOT_ACTIVE, NOT_RUNNING, NOT_PAUSED }

    public static Result start(MinecraftServer server, PenaltyMode penalty) {
        EventState state = EventState.get(server);
        if (state.phase().isActive()) {
            return Result.ALREADY_RUNNING;
        }
        state.begin(penalty, System.currentTimeMillis());
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (isEligible(p)) {
                state.addParticipant(p.getUUID());
            }
        }
        CullingGames.LOGGER.info("Culling Games #{} started ({} participants, penalty {})",
                state.eventNumber(), state.participants().size(), penalty);
        Kogane.broadcast(server, "The Culling Games have begun. Participants: " + state.participants().size() + ".");
        return Result.OK;
    }

    public static Result stop(MinecraftServer server) {
        EventState state = EventState.get(server);
        if (!state.phase().isActive()) {
            return Result.NOT_ACTIVE;
        }
        int number = state.eventNumber();
        state.end();
        CullingGames.LOGGER.info("Culling Games #{} stopped", number);
        Kogane.broadcast(server, "The Culling Games are over.");
        return Result.OK;
    }

    public static Result pause(MinecraftServer server) {
        EventState state = EventState.get(server);
        if (state.phase() != EventPhase.RUNNING) {
            return Result.NOT_RUNNING;
        }
        state.setPhase(EventPhase.PAUSED);
        Kogane.broadcast(server, "The Culling Games are paused.");
        return Result.OK;
    }

    public static Result resume(MinecraftServer server) {
        EventState state = EventState.get(server);
        if (state.phase() != EventPhase.PAUSED) {
            return Result.NOT_PAUSED;
        }
        state.setPhase(EventPhase.RUNNING);
        Kogane.broadcast(server, "The Culling Games continue.");
        return Result.OK;
    }

    public static boolean addParticipant(MinecraftServer server, ServerPlayer player) {
        EventState state = EventState.get(server);
        return state.phase().isActive() && state.addParticipant(player.getUUID());
    }

    public static boolean removeParticipant(MinecraftServer server, ServerPlayer player) {
        return EventState.get(server).removeParticipant(player.getUUID());
    }

    /** Players in creative or spectator (admins watching) are not pulled in automatically. */
    private static boolean isEligible(ServerPlayer p) {
        GameType mode = p.gameMode.getGameModeForPlayer();
        return mode == GameType.SURVIVAL || mode == GameType.ADVENTURE;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        EventState state = EventState.get(event.getServer());
        if (state.phase() == EventPhase.RUNNING) {
            state.tickRunning();
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        EventState state = EventState.get(server);
        if (!state.phase().isActive()) {
            return;
        }
        if (state.isParticipant(player.getUUID())) {
            Kogane.tell(player, "Welcome back. The Culling Games are " + (state.phase() == EventPhase.PAUSED ? "paused." : "still running."));
        } else if (CgConfig.ALLOW_LATE_JOIN.get() && isEligible(player)) {
            state.addParticipant(player.getUUID());
            Kogane.tell(player, "You have joined the Culling Games.");
        }
    }
}
