package net.jji.cullinggames.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.jji.cullinggames.CullingGames;
import net.jji.cullinggames.colony.Colony;
import net.jji.cullinggames.colony.ColonyService;
import net.jji.cullinggames.colony.ColonyStore;
import net.jji.cullinggames.config.CgConfig;
import net.jji.cullinggames.network.AnnouncePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** All event transitions go through here. Commands call these; nothing else mutates EventState. */
public final class EventManager {
    private EventManager() {}

    public enum Result { OK, ALREADY_RUNNING, NOT_ACTIVE, NOT_RUNNING, NOT_PAUSED, NO_COLONIES }

    public static Result start(MinecraftServer server, PenaltyMode penalty) {
        return start(server, penalty, CgConfig.EVENT_LIVES.get());
    }

    public static Result start(MinecraftServer server, PenaltyMode penalty, int lives) {
        EventState state = EventState.get(server);
        if (state.phase().isActive()) {
            return Result.ALREADY_RUNNING;
        }
        ColonyStore colonies = ColonyStore.get(server);
        if (colonies.isEmpty()) {
            return Result.NO_COLONIES;
        }
        state.begin(penalty, lives, System.currentTimeMillis());
        List<ServerPlayer> joining = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (isEligible(p)) {
                state.addParticipant(p.getUUID());
                joining.add(p);
            }
        }
        spread(server, state, new ArrayList<>(colonies.all()), joining);
        CullingGames.LOGGER.info("Culling Games #{} started ({} participants, penalty {}, {} lives)",
                state.eventNumber(), state.participants().size(), penalty, lives);
        Kogane.broadcast(server, "The Culling Games have begun. Participants: " + state.participants().size() + ".");
        Kogane.announceAll(server, AnnouncePacket.Kind.START, "The Culling Games have begun!",
                state.participants().size() + (state.participants().size() == 1 ? " player" : " players")
                        + ". Earn points, or lose your technique.");
        return Result.OK;
    }

    public static Result stop(MinecraftServer server) {
        EventState state = EventState.get(server);
        if (!state.phase().isActive()) {
            return Result.NOT_ACTIVE;
        }
        int number = state.eventNumber();
        Penalties.onEventEnd(server, state);
        state.end();
        CullingGames.LOGGER.info("Culling Games #{} stopped", number);
        Kogane.broadcast(server, "The Culling Games are over.");
        Kogane.announceAll(server, AnnouncePacket.Kind.END, "The Culling Games are over!", "Everyone has been restored.");
        return Result.OK;
    }

    /** Ends the event after `minutes` of running time, with a warning to spend points. */
    public static Result stopIn(MinecraftServer server, int minutes) {
        EventState state = EventState.get(server);
        if (!state.phase().isActive()) {
            return Result.NOT_ACTIVE;
        }
        state.setEndAt(state.runningTicks() + (long) minutes * 20 * 60);
        Kogane.broadcast(server, "The Culling Games end in " + minutes + (minutes == 1 ? " minute" : " minutes")
                + ". Spend your points before then.");
        Kogane.announceAll(server, AnnouncePacket.Kind.WARNING,
                "The games end in " + minutes + (minutes == 1 ? " minute!" : " minutes!"), "Spend your points before then.");
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
        if (!state.phase().isActive() || !state.addParticipant(player.getUUID())) {
            return false;
        }
        placeInEmptiestColony(server, state, player);
        return true;
    }

    /** Spreads players evenly across colonies in random order, then teleports each into theirs. */
    private static void spread(MinecraftServer server, EventState state, List<Colony> colonies, List<ServerPlayer> players) {
        Collections.shuffle(players);
        Collections.shuffle(colonies);
        for (int i = 0; i < players.size(); i++) {
            place(server, state, players.get(i), colonies.get(i % colonies.size()));
        }
    }

    private static void placeInEmptiestColony(MinecraftServer server, EventState state, ServerPlayer player) {
        ColonyStore colonies = ColonyStore.get(server);
        colonies.all().stream()
                .min(Comparator.comparingLong(c -> state.countIn(c.name())))
                .ifPresent(c -> place(server, state, player, c));
    }

    /** Assign before teleporting, so the barrier allows the move into the colony's dimension. */
    private static void place(MinecraftServer server, EventState state, ServerPlayer player, Colony colony) {
        state.assignColony(player.getUUID(), colony.name());
        ColonyService.teleportRandom(server, player, colony);
        Kogane.tell(player, "You have been placed in colony " + colony.name() + ".");
        Kogane.announce(player, AnnouncePacket.Kind.INFO, colony.name() + " colony", "You cannot leave until the games end.");
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
        tick(event.getServer());
    }

    /** One server tick of event time. Package-visible for tests. */
    static void tick(MinecraftServer server) {
        EventState state = EventState.get(server);
        if (state.phase() != EventPhase.RUNNING) {
            return;
        }
        state.tickRunning();
        if (state.endAtTicks() >= 0) {
            long left = state.endAtTicks() - state.runningTicks();
            if (left == 20 * 60) {
                Kogane.broadcast(server, "One minute until the Culling Games end. Spend your points now.");
                Kogane.announceAll(server, AnnouncePacket.Kind.WARNING, "1 minute left!", "Spend your points now.");
            } else if (left <= 0) {
                stop(server);
            }
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
            placeInEmptiestColony(server, state, player);
        }
    }
}
