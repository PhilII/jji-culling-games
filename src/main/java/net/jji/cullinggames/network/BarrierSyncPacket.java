package net.jji.cullinggames.network;

import java.util.List;
import net.jji.cullinggames.colony.Colony;
import net.minecraft.network.FriendlyByteBuf;

/** Server → client: the colony walls to draw. Empty while no event runs. Sent when it changes. */
public record BarrierSyncPacket(List<Wall> walls) {

    public static final BarrierSyncPacket NONE = new BarrierSyncPacket(List.of());

    /** One colony edge. circle: a, b = centre block, c = radius. box: a, b = min corner, c, d = max corner (inclusive). */
    public record Wall(String dimension, boolean circle, int a, int b, int c, int d) {
        public static Wall of(Colony colony) {
            int[] g = colony.geometry();
            return new Wall(colony.dimension().location().toString(), colony.shape() == Colony.Shape.CIRCLE, g[0], g[1], g[2], g[3]);
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeCollection(walls, (b, w) -> {
            b.writeUtf(w.dimension(), 128);
            b.writeBoolean(w.circle());
            b.writeVarInt(w.a());
            b.writeVarInt(w.b());
            b.writeVarInt(w.c());
            b.writeVarInt(w.d());
        });
    }

    public static BarrierSyncPacket decode(FriendlyByteBuf buf) {
        return new BarrierSyncPacket(buf.readList(b -> new Wall(b.readUtf(128), b.readBoolean(),
                b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt())));
    }
}
