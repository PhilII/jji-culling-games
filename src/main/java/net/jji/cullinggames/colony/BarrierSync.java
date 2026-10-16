package net.jji.cullinggames.colony;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.jji.cullinggames.event.EventState;
import net.jji.cullinggames.network.BarrierSyncPacket;
import net.jji.cullinggames.network.CgNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Tells clients with the mod where the colony walls are, so they can draw the black barrier. Checked once a
 * second; a player only gets a packet when what they should see changed (login, start, stop, colony edits).
 */
public final class BarrierSync {
    private static final Map<UUID, BarrierSyncPacket> SENT = new HashMap<>();

    private BarrierSync() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 20 != 10) {
            return;
        }
        MinecraftServer server = event.getServer();
        BarrierSyncPacket now = current(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (CgNetwork.hasMod(p) && !now.equals(SENT.get(p.getUUID()))) {
                CgNetwork.send(p, now);
                SENT.put(p.getUUID(), now);
            }
        }
        SENT.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
    }

    static BarrierSyncPacket current(MinecraftServer server) {
        if (!EventState.get(server).phase().isActive()) {
            return BarrierSyncPacket.NONE;
        }
        return new BarrierSyncPacket(ColonyStore.get(server).all().stream().map(BarrierSyncPacket.Wall::of).toList());
    }
}
