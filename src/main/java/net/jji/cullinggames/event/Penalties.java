package net.jji.cullinggames.event;

import java.util.ArrayList;
import java.util.UUID;
import net.jji.cullinggames.CullingGames;
import net.jji.cullinggames.colony.ColonyService;
import net.jji.cullinggames.compat.JujutsuCraft;
import net.jji.cullinggames.config.CgConfig;
import net.jji.cullinggames.network.AnnouncePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * The points timer, its penalty (CT seal or event life loss), event lives and elimination, and putting
 * everyone back to normal when the event ends.
 *
 * <p>The CT seal drains cursed energy to 0 while sealed and gives the saved amount back on unseal.
 * Jujutsu Craft's CT fields are never changed (see {@link JujutsuCraft}).
 */
public final class Penalties {
    private static final int TICKS_PER_MINUTE = 20 * 60;
    private static final String RESTORE_CE = "ce";
    private static final String RESTORE_MODE = "gameMode";
    private static final String RESTORE_SPAWN = "toSpawn";

    private Penalties() {}

    // --- timer ---

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        EventState state = EventState.get(server);
        if (!state.phase().isActive()) {
            return;
        }
        drainSealed(server, state);
        if (state.phase() == EventPhase.RUNNING && server.getTickCount() % 20 == 0) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                checkTimer(server, state, p);
            }
        }
    }

    /** Ticks a participant may go without gaining points; the longer_timer rule adds to it. */
    public static long timerLimitTicks(EventState state) {
        long minutes = CgConfig.TIMER_MINUTES.get();
        if (state.hasRule(EventRule.LONGER_TIMER.id())) {
            minutes += CgConfig.LONGER_TIMER_MINUTES.get();
        }
        return minutes * TICKS_PER_MINUTE;
    }

    /** Keeps sealed players at 0 cursed energy; Jujutsu Craft regenerates it every tick. */
    static void drainSealed(MinecraftServer server, EventState state) {
        for (UUID id : state.sealedPlayers()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && JujutsuCraft.cursedEnergy(p) > 0) {
                JujutsuCraft.setCursedEnergy(p, 0);
            }
        }
    }

    /** Called once a second per online participant. Package-visible for tests. */
    static void checkTimer(MinecraftServer server, EventState state, ServerPlayer player) {
        UUID id = player.getUUID();
        if (!state.isParticipant(id) || state.isEliminated(id) || state.isSealed(id)) {
            return;
        }
        long limit = timerLimitTicks(state);
        long since = state.ticksSincePointChange(id);
        if (since >= limit) {
            applyPenalty(server, state, player);
            return;
        }
        warnIfDue(player, limit - since, CgConfig.FIRST_WARNING_MINUTES.get());
        warnIfDue(player, limit - since, CgConfig.FINAL_WARNING_MINUTES.get());
    }

    /** Warns in the one-second window when exactly `minutes` are left. */
    private static void warnIfDue(ServerPlayer player, long ticksLeft, int minutes) {
        long at = (long) minutes * TICKS_PER_MINUTE;
        if (minutes > 0 && ticksLeft <= at && ticksLeft > at - 20) {
            Kogane.tell(player, minutes + (minutes == 1 ? " minute" : " minutes")
                    + " left to change your points, or face the penalty.");
            player.displayClientMessage(Kogane.line(minutes + " min left to score"), true);
            Kogane.cue(player, Kogane.Cue.WARNING);
            Kogane.announce(player, AnnouncePacket.Kind.WARNING, minutes + (minutes == 1 ? " minute left!" : " minutes left!"),
                    "Change your points, or face the penalty.");
        }
    }

    static void applyPenalty(MinecraftServer server, EventState state, ServerPlayer player) {
        if (state.penalty() == PenaltyMode.LOSE_LIFE) {
            state.resetTimer(player.getUUID());
            Kogane.tell(player, "Your points did not change in time. You lose a life.");
            loseLife(server, state, player, true);
        } else {
            seal(state, player);
        }
    }

    // --- CT seal ---

    static void seal(EventState state, ServerPlayer player) {
        if (state.isSealed(player.getUUID())) {
            return;
        }
        double ce = JujutsuCraft.isLoaded() ? JujutsuCraft.cursedEnergy(player) : 0;
        state.markSealed(player.getUUID(), Double.isNaN(ce) ? 0 : ce);
        JujutsuCraft.setCursedEnergy(player, 0);
        if (!JujutsuCraft.isLoaded()) {
            CullingGames.LOGGER.warn("Jujutsu Craft is not loaded: {} is marked sealed but nothing is drained",
                    player.getGameProfile().getName());
        }
        Kogane.tell(player, "Your points did not change in time. Your cursed technique is sealed until you score.");
        Kogane.announce(player, AnnouncePacket.Kind.PENALTY, "Technique sealed!", "Your points did not change. Score to unseal.", "seal");
        Kogane.cue(player, Kogane.Cue.PENALTY);
    }

    /** Unseals and gives back the saved cursed energy (capped at the current max). Offline players get it at login. */
    static void unseal(MinecraftServer server, EventState state, UUID id, boolean announce) {
        Double saved = state.clearSealed(id);
        if (saved == null) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player == null) {
            CompoundTag restore = new CompoundTag();
            restore.putDouble(RESTORE_CE, saved);
            state.addRestore(id, restore);
            return;
        }
        restoreCursedEnergy(player, saved);
        if (announce) {
            Kogane.tell(player, "Your cursed technique is unsealed.");
            Kogane.cue(player, Kogane.Cue.GOOD);
            Kogane.announce(player, AnnouncePacket.Kind.GOOD, "Technique unsealed", "Your cursed energy is back.");
        }
    }

    private static void restoreCursedEnergy(ServerPlayer player, double saved) {
        double max = JujutsuCraft.maxCursedEnergy(player);
        JujutsuCraft.setCursedEnergy(player, Double.isNaN(max) ? saved : Math.min(saved, max));
    }

    // --- lives ---

    /** Lowest priority and not when cancelled, so totems and death-preventing mods don't cost a life. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getServer() == null) {
            return;
        }
        EventState state = EventState.get(player.getServer());
        if (state.phase() == EventPhase.RUNNING && state.isParticipant(player.getUUID())
                && !state.isEliminated(player.getUUID())) {
            loseLife(player.getServer(), state, player, false);
        }
    }

    /** @param alive true when the player is alive now (timer penalty), false when they just died. */
    static void loseLife(MinecraftServer server, EventState state, ServerPlayer player, boolean alive) {
        int left = state.loseLife(player.getUUID());
        if (left > 0) {
            Kogane.tell(player, left + (left == 1 ? " life" : " lives") + " left.");
            Kogane.announce(player, AnnouncePacket.Kind.PENALTY, "Life lost!", left + (left == 1 ? " life" : " lives") + " left.", "life_lost");
            Kogane.cue(player, Kogane.Cue.PENALTY);
            return;
        }
        Kogane.broadcast(server, player.getGameProfile().getName() + " is out of the Culling Games.");
        Kogane.cue(player, Kogane.Cue.OUT);
        Kogane.announce(player, AnnouncePacket.Kind.PENALTY, "You are out!", "You can watch until the games end.", "out");
        state.rememberGameMode(player.getUUID(), player.gameMode.getGameModeForPlayer().getName());
        unseal(server, state, player.getUUID(), false);
        if (alive) {
            makeSpectator(player);
        }
        // A dead player becomes a spectator when they respawn (onRespawn).
    }

    private static void makeSpectator(ServerPlayer player) {
        player.setGameMode(GameType.SPECTATOR);
        Kogane.tell(player, "You are out. You can watch until the event ends.");
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            EventState state = EventState.get(player.getServer());
            if (state.phase().isActive() && state.isEliminated(player.getUUID())) {
                makeSpectator(player);
            }
        }
    }

    // --- event end and login restores ---

    /** Unseals everyone, gives eliminated players their game mode back, and optionally sends them to spawn. */
    static void onEventEnd(MinecraftServer server, EventState state) {
        for (UUID id : new ArrayList<>(state.sealedPlayers())) {
            unseal(server, state, id, true);
        }
        boolean toSpawn = CgConfig.TELEPORT_TO_SPAWN_ON_END.get();
        for (UUID id : state.participants()) {
            String mode = state.takeGameMode(id);
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                CompoundTag restore = new CompoundTag();
                if (mode != null) {
                    restore.putString(RESTORE_MODE, mode);
                }
                restore.putBoolean(RESTORE_SPAWN, toSpawn);
                state.addRestore(id, restore);
                continue;
            }
            if (mode != null) {
                player.setGameMode(GameType.byName(mode, GameType.SURVIVAL));
            }
            if (toSpawn) {
                sendToSpawn(server, player);
            }
        }
    }

    private static void sendToSpawn(MinecraftServer server, ServerPlayer player) {
        ServerLevel overworld = server.overworld();
        // The stored spawn height can be in the air (flat worlds, edited maps); land on solid ground instead.
        BlockPos spawn = ColonyService.safeStand(overworld, overworld.getSharedSpawnPos());
        player.stopRiding();
        player.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYRot(), player.getXRot());
        player.setDeltaMovement(0, 0, 0);
        player.fallDistance = 0;
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getServer() == null) {
            return;
        }
        MinecraftServer server = player.getServer();
        EventState state = EventState.get(server);
        CompoundTag restore = state.takeRestore(player.getUUID());
        if (restore == null) {
            return;
        }
        if (restore.contains(RESTORE_CE)) {
            restoreCursedEnergy(player, restore.getDouble(RESTORE_CE));
        }
        if (restore.contains(RESTORE_MODE)) {
            player.setGameMode(GameType.byName(restore.getString(RESTORE_MODE), GameType.SURVIVAL));
        }
        if (restore.getBoolean(RESTORE_SPAWN) && !state.isParticipant(player.getUUID())) {
            sendToSpawn(server, player);
        }
        Kogane.tell(player, "The Culling Games you were in have ended. You have been restored.");
    }
}
