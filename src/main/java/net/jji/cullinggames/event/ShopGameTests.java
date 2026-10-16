package net.jji.cullinggames.event;

import net.jji.cullinggames.CullingGames;
import net.jji.cullinggames.colony.Colony;
import net.jji.cullinggames.colony.ColonyStore;
import net.jji.cullinggames.config.CgConfig;
import net.jji.cullinggames.shop.ShopStore;
import net.jji.cullinggames.test.TestPlayers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Shop services and items, rules, transfers, cash-out, scheduled end and the Kogane bar. */
@GameTestHolder(CullingGames.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShopGameTests {
    private ShopGameTests() {}

    private static void check(GameTestHelper h, boolean ok, String what) {
        if (!ok) {
            CullingGames.LOGGER.error("GameTest check failed: {}", what);
            h.fail(what);
        }
    }

    @GameTest(template = "empty", batch = "shop")
    public static void shopRulesAndEnd(GameTestHelper h) {
        try {
            run(h);
        } catch (RuntimeException e) {
            CullingGames.LOGGER.error("shopRulesAndEnd threw", e);
            throw e;
        }
        h.succeed();
    }

    private static void run(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        EventManager.stop(server);
        ColonyStore colonies = ColonyStore.get(server);
        colonies.remove("gt_shop");
        BlockPos c = h.absolutePos(new BlockPos(2, 1, 2));
        colonies.add(Colony.circle("gt_shop", h.getLevel().dimension(), c.getX(), c.getZ(), 8));
        ShopStore shop = ShopStore.get(server);
        int shopSizeBefore = shop.all().size();

        ServerPlayer p1 = TestPlayers.create(h, "gt_b1");
        ServerPlayer p2 = TestPlayers.create(h, "gt_b2");
        int itemId = -1;
        try {
            EventManager.start(server, PenaltyMode.SEAL_CT, 3);
            EventState state = EventState.get(server);
            Points.set(server, p1.getUUID(), 500);

            // services
            int lb = Shop.Service.LEADERBOARD.price();
            check(h, Shop.buyService(server, p1, Shop.Service.LEADERBOARD).ok(), "buy leaderboard");
            check(h, state.points(p1.getUUID()) == 500 - lb, "leaderboard price taken");
            check(h, !Shop.buyService(server, p1, Shop.Service.LEADERBOARD).ok()
                    && state.points(p1.getUUID()) == 500 - lb, "can't buy the same service twice");
            check(h, !Shop.hasAccess(state, p2.getUUID(), Shop.Service.LEADERBOARD), "other players don't get it");

            for (int i = 0; i < 100; i++) {
                state.tickRunning();
            }
            check(h, Shop.buyService(server, p1, Shop.Service.TRANSFER).ok(), "buy transfer");
            check(h, state.ticksSincePointChange(p1.getUUID()) == 100, "spending does not reset the timer");

            // transfers
            int have = state.points(p1.getUUID());
            check(h, Shop.transfer(server, p1, p2.getUUID(), "gt_b2", 15).ok(), "transfer");
            check(h, state.points(p1.getUUID()) == have - 15 && state.points(p2.getUUID()) == 15, "points moved");
            check(h, state.ticksSincePointChange(p2.getUUID()) == 0, "receiving resets the receiver's timer");
            check(h, !Shop.transfer(server, p2, p1.getUUID(), "gt_b1", 1).ok(), "transfer needs to be bought");
            check(h, !Shop.transfer(server, p1, p2.getUUID(), "gt_b2", 10_000).ok()
                    && state.points(p2.getUUID()) == 15, "can't send more than you have");
            check(h, !Shop.transfer(server, p1, p1.getUUID(), "gt_b1", 1).ok(), "can't send to yourself");

            // items
            itemId = shop.add(new ItemStack(Items.DIAMOND, 2), 5);
            int before = state.points(p1.getUUID());
            check(h, Shop.buyItem(server, p1, itemId).ok(), "buy item");
            check(h, p1.getInventory().countItem(Items.DIAMOND) == 2, "item given");
            check(h, state.points(p1.getUUID()) == before - 5, "item price taken");
            check(h, !Shop.buyItem(server, p1, 9999).ok(), "unknown item refused");

            // rules
            Points.set(server, p2.getUUID(), CgConfig.RULE_COST.get() + 20);
            check(h, Shop.addRule(server, p2, EventRule.DOUBLE_POINTS).ok(), "add rule");
            check(h, !Shop.addRule(server, p2, EventRule.DOUBLE_POINTS).ok(), "same rule twice refused");
            int p2Before = state.points(p2.getUUID());
            MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(EntityType.ZOMBIE.create(h.getLevel()),
                    p2.damageSources().playerAttack(p2)));
            check(h, state.points(p2.getUUID()) == p2Before + 2 * CgConfig.WEAK_KILL_POINTS.get(), "double points rule");

            long limitBefore = Penalties.timerLimitTicks(state);
            Points.set(server, p1.getUUID(), CgConfig.RULE_COST.get());
            check(h, Shop.addRule(server, p1, EventRule.LONGER_TIMER).ok(), "add longer timer");
            check(h, Penalties.timerLimitTicks(state) == limitBefore + CgConfig.LONGER_TIMER_MINUTES.get() * 1200L,
                    "longer timer rule extends the limit");
            check(h, !Shop.addRule(server, p1, EventRule.REVEAL_POINTS).ok(), "can't afford a rule with 0 points");

            Points.set(server, p2.getUUID(), CgConfig.RULE_COST.get());
            check(h, Shop.addRule(server, p2, EventRule.REVEAL_POINTS).ok(), "add reveal points");
            check(h, Shop.hasAccess(state, p2.getUUID(), Shop.Service.LEADERBOARD), "reveal points opens the leaderboard");
            check(h, Shop.leaderboard(server, state, 10).contains("1."), "leaderboard has entries");

            // cash-out (Lightman's Currency may be missing in dev)
            Points.set(server, p1.getUUID(), 10);
            Shop.Result cash = Shop.cashOut(server, p1, 3);
            if (Shop.moneyItem() == null) {
                check(h, !cash.ok() && state.points(p1.getUUID()) == 10, "no cash-out without the coin item");
            } else {
                check(h, cash.ok() && state.points(p1.getUUID()) == 7, "cash-out takes points");
            }

            // saved state
            EventState copy = EventState.load(state.save(new CompoundTag()));
            check(h, copy.hasUnlocked(p1.getUUID(), "transfer") && copy.hasRule("double_points"),
                    "unlocks and rules survive save/load");

            // Kogane bar
            KoganeBar.update(server);
            String bar = KoganeBar.textFor(p1.getUUID());
            check(h, bar != null && bar.contains("pts"), "participant has a Kogane bar");

            // closed while paused
            EventManager.pause(server);
            check(h, !Shop.buyService(server, p2, Shop.Service.KILLLOG).ok(), "shop closed while paused");
            EventManager.resume(server);

            // scheduled end
            check(h, EventManager.stopIn(server, 1) == EventManager.Result.OK, "schedule end");
            check(h, state.endAtTicks() == state.runningTicks() + 1200, "end time set");
            for (int i = 0; i < 1200; i++) {
                EventManager.tick(server);
            }
            check(h, state.phase() == EventPhase.IDLE, "event ends at the scheduled time");
            KoganeBar.update(server);
            check(h, KoganeBar.textFor(p1.getUUID()) == null, "bars removed after the event");
        } finally {
            EventManager.stop(server);
            colonies.remove("gt_shop");
            if (itemId > 0) {
                shop.remove(itemId);
            }
            TestPlayers.remove(h, p1, p2);
        }
        check(h, shop.all().size() == shopSizeBefore, "shop cleaned up");
    }
}
