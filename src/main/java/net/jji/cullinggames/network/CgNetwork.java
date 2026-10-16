package net.jji.cullinggames.network;

import java.util.function.Supplier;
import net.jji.cullinggames.CullingGames;
import net.jji.cullinggames.event.Views;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * The mod's network channel. Optional on both sides: players without the mod can still join
 * (they get chat and the bossbar instead of the HUD and screen).
 */
public final class CgNetwork {
    private static final String PROTOCOL = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(CullingGames.MOD_ID, "main"), () -> PROTOCOL,
            NetworkRegistry.acceptMissingOr(PROTOCOL), NetworkRegistry.acceptMissingOr(PROTOCOL));

    private CgNetwork() {}

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(HudSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(HudSyncPacket::encode).decoder(HudSyncPacket::decode)
                .consumerMainThread(CgNetwork::onHud).add();
        CHANNEL.messageBuilder(ShopSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ShopSyncPacket::encode).decoder(ShopSyncPacket::decode)
                .consumerMainThread(CgNetwork::onShop).add();
        CHANNEL.messageBuilder(ShopActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ShopActionPacket::encode).decoder(ShopActionPacket::decode)
                .consumerMainThread(CgNetwork::onAction).add();
        CHANNEL.messageBuilder(AnnouncePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(AnnouncePacket::encode).decoder(AnnouncePacket::decode)
                .consumerMainThread(CgNetwork::onAnnounce).add();
    }

    /** True if this player's client has the mod (and so draws the HUD itself). */
    public static boolean hasMod(ServerPlayer player) {
        return player.connection != null && CHANNEL.isRemotePresent(player.connection.connection);
    }

    public static void send(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    private static void onHud(HudSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> net.jji.cullinggames.client.ClientPackets.onHud(msg));
        ctx.get().setPacketHandled(true);
    }

    private static void onShop(ShopSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> net.jji.cullinggames.client.ClientPackets.onShop(msg));
        ctx.get().setPacketHandled(true);
    }

    private static void onAnnounce(AnnouncePacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> net.jji.cullinggames.client.ClientPackets.onAnnounce(msg));
        ctx.get().setPacketHandled(true);
    }

    private static void onAction(ShopActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sender = ctx.get().getSender();
        if (sender != null) {
            send(sender, Views.handle(sender, msg));
        }
        ctx.get().setPacketHandled(true);
    }
}
