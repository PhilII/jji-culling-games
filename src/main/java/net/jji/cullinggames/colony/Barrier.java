package net.jji.cullinggames.colony;

import net.jji.cullinggames.event.EventState;
import net.jji.cullinggames.event.Kogane;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Keeps participants inside their colony while an event is active (running or paused).
 * Checked every few ticks, not every tick, to stay cheap on a 4 GB server.
 */
public final class Barrier {
    private static final int CHECK_INTERVAL_TICKS = 5;

    private Barrier() {}

    /** The colony this player is held in, or null if the barrier doesn't apply to them. */
    public static Colony heldColony(MinecraftServer server, ServerPlayer player) {
        EventState state = EventState.get(server);
        if (!state.phase().isActive() || !state.isParticipant(player.getUUID()) || isExempt(player)) {
            return null;
        }
        return ColonyStore.get(server).get(state.colonyOf(player.getUUID()));
    }

    /** Admins watching in creative or spectator are not held. */
    private static boolean isExempt(ServerPlayer player) {
        GameType mode = player.gameMode.getGameModeForPlayer();
        return mode == GameType.CREATIVE || mode == GameType.SPECTATOR;
    }

    /** Pushes the player back in if they are outside. Returns true if they were moved. */
    public static boolean enforce(MinecraftServer server, ServerPlayer player) {
        Colony colony = heldColony(server, player);
        if (colony == null || colony.contains(player.level().dimension(), player.getX(), player.getZ())) {
            return false;
        }
        if (ColonyService.pushInside(server, player, colony)) {
            player.displayClientMessage(Kogane.line("You cannot leave " + colony.name() + "."), true);
            return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (!EventState.get(server).phase().isActive()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            enforce(server, player);
        }
    }

    @SubscribeEvent
    public static void onEnderPearl(EntityTeleportEvent.EnderPearl event) {
        blockIfOutside(event, event.getPlayer());
    }

    @SubscribeEvent
    public static void onChorusFruit(EntityTeleportEvent.ChorusFruit event) {
        LivingEntity entity = event.getEntityLiving();
        if (entity instanceof ServerPlayer player) {
            blockIfOutside(event, player);
        }
    }

    private static void blockIfOutside(EntityTeleportEvent event, ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        Colony colony = heldColony(server, player);
        if (colony != null && !colony.contains(player.level().dimension(), event.getTargetX(), event.getTargetZ())) {
            event.setCanceled(true);
            player.displayClientMessage(Kogane.line("You cannot leave " + colony.name() + "."), true);
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getServer() == null) {
            return;
        }
        Colony colony = heldColony(player.getServer(), player);
        if (colony != null && !colony.dimension().equals(event.getDimension())) {
            event.setCanceled(true);
            player.displayClientMessage(Kogane.line("You cannot leave " + colony.name() + "."), true);
        }
    }
}
