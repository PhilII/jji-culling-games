package net.jji.cullinggames.network;

import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

/** Server → client: everything the shop screen shows. Sent when the screen opens and after every purchase. */
public record ShopSyncPacket(int points, int ruleCost, String closedReason, String message,
                             List<ItemOffer> items, List<ServiceOffer> services, List<RuleOffer> rules) {

    public record ItemOffer(int id, ItemStack stack, int price) {}

    public record ServiceOffer(String id, int price, boolean owned) {}

    public record RuleOffer(String id, String description, boolean active) {}

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
    }

    public static ShopSyncPacket decode(FriendlyByteBuf buf) {
        int points = buf.readVarInt();
        int ruleCost = buf.readVarInt();
        String closed = buf.readUtf(256);
        String message = buf.readUtf(256);
        List<ItemOffer> items = buf.readList(b -> new ItemOffer(b.readVarInt(), b.readItem(), b.readVarInt()));
        List<ServiceOffer> services = buf.readList(b -> new ServiceOffer(b.readUtf(32), b.readVarInt(), b.readBoolean()));
        List<RuleOffer> rules = buf.readList(b -> new RuleOffer(b.readUtf(32), b.readUtf(256), b.readBoolean()));
        return new ShopSyncPacket(points, ruleCost, closed, message, items, services, rules);
    }
}
