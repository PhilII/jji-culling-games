package net.jji.cullinggames.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.jji.cullinggames.CullingGames;
import net.jji.cullinggames.network.BarrierSyncPacket;
import net.jji.cullinggames.network.CgNetwork;
import net.jji.cullinggames.network.HudSyncPacket;
import net.jji.cullinggames.network.ShopActionPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Client-only registration: HUD and announcement overlays, shop key (K by default). */
public final class ClientSetup {
    static final KeyMapping OPEN_SHOP = new KeyMapping("key.cullinggames.shop",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.cullinggames");

    private ClientSetup() {}

    @Mod.EventBusSubscriber(modid = CullingGames.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void onOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAbove(VanillaGuiOverlay.CHAT_PANEL.id(), "kogane", new HudOverlay());
            event.registerAboveAll("announcements", new AnnouncementOverlay());
        }

        @SubscribeEvent
        public static void onKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN_SHOP);
        }
    }

    @Mod.EventBusSubscriber(modid = CullingGames.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ForgeBus {
        /** Dev only (./gradlew runClient -PselfTest): buys the shop item id given after joining, through the real packet path. */
        private static final String SELF_TEST_BUY = System.getProperty("cullinggames.selfTestBuy");
        private static int selfTestTicks;

        private ForgeBus() {}

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END || Minecraft.getInstance().player == null) {
                return;
            }
            while (OPEN_SHOP.consumeClick()) {
                CgNetwork.CHANNEL.sendToServer(new ShopActionPacket(ShopActionPacket.Action.OPEN, ""));
            }
            if (SELF_TEST_BUY != null && ++selfTestTicks == 200) {
                CullingGames.LOGGER.info("Self-test: buying {} through the shop packet", SELF_TEST_BUY);
                CgNetwork.CHANNEL.sendToServer(new ShopActionPacket(ShopActionPacket.Action.BUY, SELF_TEST_BUY));
            }
        }

        /** Forget the last server's HUD when leaving it. */
        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientPackets.hud = HudSyncPacket.INACTIVE;
            AnnouncementOverlay.clear();
            ClientPackets.barriers = BarrierSyncPacket.NONE;
        }
    }
}
