package net.jji.cullinggames.network;

import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

/**
 * Server → client: everything the shop screen shows. Sent when the screen opens and after every purchase.
 * rewardMode = Kogane's reward shop after an event (rewardOffers); otherwise the event shop.
 */
public record ShopSyncPacket(int points, int ruleCost, String closedReason, String message,
                             List<ItemOffer> items, List<ServiceOffer> services, List<RuleOffer> rules,
                             boolean rewardMode, List<RewardOffer> rewardOffers, CashOut cashOut) {

    /** The event shop. */
    public ShopSyncPacket(int points, int ruleCost, String closedReason, String message,
                          List<ItemOffer> items, List<ServiceOffer> services, List<RuleOffer> rules) {
        this(points, ruleCost, closedReason, message, items, services, rules, false, List.of(), CashOut.NONE);
    }

    /** Points → coins: price per coin and how many more this player may take this event (-1 = no limit). */
    public record CashOut(boolean available, int pointsPerCoin, int coinsLeft) {
        public static final CashOut NONE = new CashOut(false, 1, 0);
    }

    public record ItemOffer(int id, ItemStack stack, int price) {}

    public record ServiceOffer(String id, int price, boolean owned) {}

    public record RuleOffer(String id, String description, boolean active) {}

    /**
     * A reward. blocked = why it can't be bought ("" if it can, price aside). For fragments, have/need is the
     * player's progress towards the weapon.
     */
    public record RewardOffer(String id, String name, ItemStack icon, int price, String blocked,
                              boolean fragment, int have, int need) {}

    /** Empty closedReason = the shop is open for this player. */
    public boolean open() {
        return closedReason.isEmpty();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(points);
        buf.writeVarInt(ruleCost);
        buf.writeUtf(closedReason, 256);
        buf.writeUtf(message, 256);
        buf.writeCollection(items, (b, o) -> {
            b.writeVarInt(o.id());
            b.writeItem(o.stack());
            b.writeVarInt(o.price());
        });
        buf.writeCollection(services, (b, o) -> {
            b.writeUtf(o.id(), 32);
            b.writeVarInt(o.price());
            b.writeBoolean(o.owned());
        });
        buf.writeCollection(rules, (b, o) -> {
            b.writeUtf(o.id(), 32);
            b.writeUtf(o.description(), 256);
            b.writeBoolean(o.active());
        });
        buf.writeBoolean(rewardMode);
        buf.writeCollection(rewardOffers, (b, o) -> {
            b.writeUtf(o.id(), 32);
            b.writeUtf(o.name(), 64);
            b.writeItem(o.icon());
            b.writeVarInt(o.price());
            b.writeUtf(o.blocked(), 64);
            b.writeBoolean(o.fragment());
            b.writeVarInt(o.have());
            b.writeVarInt(o.need());
        });
        buf.writeBoolean(cashOut.available());
        buf.writeVarInt(cashOut.pointsPerCoin());
        buf.writeInt(cashOut.coinsLeft());
    }

    public static ShopSyncPacket decode(FriendlyByteBuf buf) {
        int points = buf.readVarInt();
        int ruleCost = buf.readVarInt();
        String closed = buf.readUtf(256);
        String message = buf.readUtf(256);
        List<ItemOffer> items = buf.readList(b -> new ItemOffer(b.readVarInt(), b.readItem(), b.readVarInt()));
        List<ServiceOffer> services = buf.readList(b -> new ServiceOffer(b.readUtf(32), b.readVarInt(), b.readBoolean()));
        List<RuleOffer> rules = buf.readList(b -> new RuleOffer(b.readUtf(32), b.readUtf(256), b.readBoolean()));
        boolean rewardMode = buf.readBoolean();
        List<RewardOffer> rewards = buf.readList(b -> new RewardOffer(b.readUtf(32), b.readUtf(64), b.readItem(),
                b.readVarInt(), b.readUtf(64), b.readBoolean(), b.readVarInt(), b.readVarInt()));
        CashOut cashOut = new CashOut(buf.readBoolean(), buf.readVarInt(), buf.readInt());
        return new ShopSyncPacket(points, ruleCost, closed, message, items, services, rules, rewardMode, rewards, cashOut);
    }
}
