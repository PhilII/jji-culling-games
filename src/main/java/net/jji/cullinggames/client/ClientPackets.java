package net.jji.cullinggames.client;

import net.jji.cullinggames.network.AnnouncePacket;
import net.jji.cullinggames.network.BarrierSyncPacket;
import net.jji.cullinggames.network.HudSyncPacket;
import net.jji.cullinggames.network.ShopSyncPacket;
import net.minecraft.client.Minecraft;

/** Client-side packet targets. Only ever loaded on the client (called through DistExecutor). */
public final class ClientPackets {
    /** Latest HUD data from the server; INACTIVE hides the HUD. */
    static volatile HudSyncPacket hud = HudSyncPacket.INACTIVE;

    /** Colony walls to draw; NONE when no event runs. */
    static volatile BarrierSyncPacket barriers = BarrierSyncPacket.NONE;

    private ClientPackets() {}

    public static void onHud(HudSyncPacket msg) {
        hud = msg;
    }

    /** Opens the shop screen, or refreshes it if it's already open. */
    public static void onShop(ShopSyncPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ShopScreen screen) {
            screen.update(msg);
        } else {
            mc.setScreen(new ShopScreen(msg));
            CgSounds.play("shop");
        }
    }

    public static void onBarriers(BarrierSyncPacket msg) {
        barriers = msg;
    }

    public static void onAnnounce(AnnouncePacket msg) {
        AnnouncementOverlay.show(msg);
    }
}
