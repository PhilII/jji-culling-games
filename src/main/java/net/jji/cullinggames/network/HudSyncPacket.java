package net.jji.cullinggames.network;

import net.minecraft.network.FriendlyByteBuf;

/** Server → client, once a second: what the HUD shows. Only the receiving player's own numbers. */
public record HudSyncPacket(boolean active, int points, long ticksLeft, long limitTicks, boolean sealed, boolean paused,
                            long endsInTicks, String colony, int lives, int startingLives) {

    public static final HudSyncPacket INACTIVE = new HudSyncPacket(false, 0, 0, 0, false, false, -1, "", 0, 0);

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(active);
        buf.writeVarInt(points);
        buf.writeVarLong(ticksLeft);
        buf.writeVarLong(limitTicks);
        buf.writeBoolean(sealed);
        buf.writeBoolean(paused);
        buf.writeLong(endsInTicks);
        buf.writeUtf(colony, 64);
        buf.writeVarInt(lives);
        buf.writeVarInt(startingLives);
    }

    public static HudSyncPacket decode(FriendlyByteBuf buf) {
        return new HudSyncPacket(buf.readBoolean(), buf.readVarInt(), buf.readVarLong(), buf.readVarLong(),
                buf.readBoolean(), buf.readBoolean(), buf.readLong(), buf.readUtf(64), buf.readVarInt(), buf.readVarInt());
    }
}
