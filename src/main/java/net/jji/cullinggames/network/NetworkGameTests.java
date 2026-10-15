package net.jji.cullinggames.network;

import io.netty.buffer.Unpooled;
import java.util.List;
import net.jji.cullinggames.CullingGames;
import net.jji.cullinggames.colony.Colony;
import net.jji.cullinggames.colony.ColonyStore;
import net.jji.cullinggames.event.EventManager;
import net.jji.cullinggames.event.EventState;
import net.jji.cullinggames.event.PenaltyMode;
import net.jji.cullinggames.event.Points;
import net.jji.cullinggames.event.Shop;
import net.jji.cullinggames.event.Views;
import net.jji.cullinggames.test.TestPlayers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Packet encoding and the server side of the HUD and shop screen. */
@GameTestHolder(CullingGames.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NetworkGameTests {
    private NetworkGameTests() {}

    private static void check(GameTestHelper h, boolean ok, String what) {
        if (!ok) {
            CullingGames.LOGGER.error("GameTest check failed: {}", what);
            h.fail(what);
        }
    }

    private static FriendlyByteBuf buf() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    @GameTest(template = "empty", batch = "packets")
    public static void packetsRoundTrip(GameTestHelper h) {
        HudSyncPacket hud = new HudSyncPacket(true, 42, 1234, 22800, true, false, 600, "shibuya", 2, 3);
        FriendlyByteBuf b = buf();
        hud.encode(b);
        check(h, HudSyncPacket.decode(b).equals(hud), "HUD packet round trip");

        ShopSyncPacket shop = new ShopSyncPacket(17, 100, "", "Bought.",
                List.of(new ShopSyncPacket.ItemOffer(1, new ItemStack(Items.DIAMOND, 3), 5)),
                List.of(new ShopSyncPacket.ServiceOffer("leaderboard", 10, true)),
                List.of(new ShopSyncPacket.RuleOffer("double_points", "All kills give double points.", false)));
        b = buf();
        shop.encode(b);
        ShopSyncPacket back = ShopSyncPacket.decode(b);
        check(h, back.points() == 17 && back.ruleCost() == 100 && back.open() && "Bought.".equals(back.message()),
                "shop header round trip");
        check(h, back.items().size() == 1 && back.items().get(0).stack().is(Items.DIAMOND)
                && back.items().get(0).stack().getCount() == 3 && back.items().get(0).price() == 5, "shop items round trip");
        check(h, back.services().equals(shop.services()) && back.rules().equals(shop.rules()), "services and rules round trip");

        ShopActionPacket action = new ShopActionPacket(ShopActionPacket.Action.RULE, "longer_timer");
        b = buf();
        action.encode(b);
        check(h, ShopActionPacket.decode(b).equals(action), "action packet round trip");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "views")
    public static void hudAndShopViews(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        EventManager.stop(server);
        ColonyStore colonies = ColonyStore.get(server);
        colonies.remove("gt_view");
        BlockPos c = h.absolutePos(new BlockPos(2, 1, 2));
        colonies.add(Colony.circle("gt_view", h.getLevel().dimension(), c.getX(), c.getZ(), 8));
        ServerPlayer p = TestPlayers.create(h, "gt_v1");
        try {
            check(h, !Views.hud(server, p).active(), "no HUD before the event");
            check(h, !Views.shop(server, p, "").open(), "shop closed before the event");

            EventManager.start(server, PenaltyMode.SEAL_CT, 3);
            Points.set(server, p.getUUID(), 30);
            HudSyncPacket hud = Views.hud(server, p);
            check(h, hud.active() && hud.points() == 30 && "gt_view".equals(hud.colony()) && hud.lives() == 3
                    && hud.ticksLeft() == hud.limitTicks(), "HUD shows own points, colony, lives and full timer");

            ShopSyncPacket afterBuy = Views.handle(p, new ShopActionPacket(ShopActionPacket.Action.BUY, "leaderboard"));
            int price = Shop.Service.LEADERBOARD.price();
            check(h, afterBuy.points() == 30 - price, "buy from the screen takes points");
            check(h, afterBuy.services().stream().anyMatch(s -> s.id().equals("leaderboard") && s.owned()),
                    "screen shows the service as owned");
            check(h, !afterBuy.message().isEmpty(), "screen gets a result message");

            ShopSyncPacket bad = Views.handle(p, new ShopActionPacket(ShopActionPacket.Action.BUY, "nonsense"));
            check(h, bad.points() == 30 - price, "unknown item changes nothing");
            ShopSyncPacket poor = Views.handle(p, new ShopActionPacket(ShopActionPacket.Action.RULE, "double_points"));
            check(h, poor.points() == 30 - price && !EventState.get(server).hasRule("double_points"),
                    "rule refused without enough points");

            EventManager.stop(server);
            check(h, !Views.hud(server, p).active(), "HUD hidden after the event");
        } finally {
            EventManager.stop(server);
            colonies.remove("gt_view");
            TestPlayers.remove(h, p);
        }
        h.succeed();
    }
}
