package net.jji.cullinggames.network;

import net.minecraft.network.FriendlyByteBuf;

/** Client → server: open the shop, buy something, add a rule or buy a reward. The server checks everything. */
public record ShopActionPacket(Action action, String id) {
    public enum Action { OPEN, BUY, RULE, REWARD, CASHOUT }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeUtf(id, 32);
    }

    public static ShopActionPacket decode(FriendlyByteBuf buf) {
        return new ShopActionPacket(buf.readEnum(Action.class), buf.readUtf(32));
    }
}
