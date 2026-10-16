package net.jji.cullinggames.event;

import net.jji.cullinggames.CullingGames;
import net.jji.cullinggames.colony.Colony;
import net.jji.cullinggames.colony.ColonyStore;
import net.jji.cullinggames.config.CgConfig;
import net.jji.cullinggames.shop.RewardStore;
import net.jji.cullinggames.shop.RewardStore.Reward;
import net.jji.cullinggames.shop.RewardStore.Type;
import net.jji.cullinggames.test.TestPlayers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Kogane's reward shop after an event: opening rules, limits, commands, fragments and pending delivery. */
@GameTestHolder(CullingGames.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RewardGameTests {
    private static final String[] TEST_REWARDS = {"gt_apple", "gt_cmd", "gt_bad", "gt_frag"};

    private RewardGameTests() {}

    private static void check(GameTestHelper h, boolean ok, String what) {
        if (!ok) {
            CullingGames.LOGGER.error("GameTest check failed: {}", what);
            h.fail(what);
        }
    }

    @GameTest(template = "empty", batch = "rewards")
    public static void rewardShop(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        EventManager.stop(server);
        ColonyStore colonies = ColonyStore.get(server);
        colonies.remove("gt_rewards");
        BlockPos c = h.absolutePos(new BlockPos(2, 1, 2));
        colonies.add(Colony.circle("gt_rewards", h.getLevel().dimension(), c.getX(), c.getZ(), 8));
        RewardStore store = RewardStore.get(server);
        for (String id : TEST_REWARDS) {
            store.remove(id);
        }
        store.add(new Reward("gt_apple", Type.ITEM, "Apples", new ItemStack(Items.GOLDEN_APPLE, 2), "", 10).perPlayer(1));
        store.add(new Reward("gt_cmd", Type.COMMAND, "Diamond", new ItemStack(Items.DIAMOND), "give %player% minecraft:diamond 1", 20).once(true));
        store.add(new Reward("gt_bad", Type.COMMAND, "Broken", new ItemStack(Items.PAPER), "nosuchcommand %player%", 5));
        store.add(new Reward("gt_frag", Type.FRAGMENT, "Sword fragment", new ItemStack(Items.IRON_SWORD), "", 30).fragmentsNeeded(2));

        ServerPlayer p1 = TestPlayers.create(h, "gt_r1");
        ServerPlayer p2 = TestPlayers.create(h, "gt_r2");
        ServerPlayer p3 = null;
        try {
            EventManager.start(server, PenaltyMode.SEAL_CT, 3);
            EventState state = EventState.get(server);
            Points.set(server, p1.getUUID(), 300);
            Points.set(server, p2.getUUID(), 50);
            check(h, !Rewards.buy(server, p1, "gt_apple").ok(), "rewards closed while the event runs");
            EventManager.stop(server);
            check(h, state.points(p1.getUUID()) == 300, "points survive the end of the event");

            // item reward, per-player limit
            check(h, Rewards.buy(server, p1, "gt_apple").ok(), "item reward bought");
            check(h, state.points(p1.getUUID()) == 290 && p1.getInventory().countItem(Items.GOLDEN_APPLE) == 2, "item delivered, points taken");
            check(h, !Rewards.buy(server, p1, "gt_apple").ok() && state.points(p1.getUUID()) == 290, "per-player limit");

            // command reward, once per player
            check(h, Rewards.buy(server, p1, "gt_cmd").ok() && p1.getInventory().countItem(Items.DIAMOND) == 1, "command reward runs");
            check(h, !Rewards.buy(server, p1, "gt_cmd").ok() && state.points(p1.getUUID()) == 270, "once per player");

            // a failing command takes no points
            check(h, !Rewards.buy(server, p1, "gt_bad").ok() && state.points(p1.getUUID()) == 270, "failed command refunds");

            // fragments: a full set gives the weapon, then the per-event fragment cap applies
            check(h, Rewards.buy(server, p1, "gt_frag").ok() && store.fragments(p1.getUUID(), "minecraft:iron_sword") == 1, "first fragment");
            check(h, Rewards.buy(server, p1, "gt_frag").ok() && p1.getInventory().countItem(Items.IRON_SWORD) == 1
                    && store.fragments(p1.getUUID(), "minecraft:iron_sword") == 0, "full set gives the weapon");
            check(h, !Rewards.buy(server, p1, "gt_frag").ok(), "fragment cap per event");

            // full inventory: the reward waits instead of dropping
            for (int i = 0; i < 36; i++) {
                p2.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
            }
            check(h, Rewards.buy(server, p2, "gt_apple").ok() && store.hasPending(p2.getUUID()), "pending when full");
            check(h, state.points(p2.getUUID()) == 40, "pending purchase is paid");
            p2.getInventory().setItem(0, ItemStack.EMPTY);
            Rewards.deliverPending(server, p2);
            check(h, !store.hasPending(p2.getUUID()) && p2.getInventory().countItem(Items.GOLDEN_APPLE) == 2, "pending delivered");

            // not enough points
            Points.set(server, p2.getUUID(), 5);
            check(h, !Rewards.buy(server, p2, "gt_cmd").ok() && state.points(p2.getUUID()) == 5, "can't afford");

            // someone who wasn't in the event
            p3 = TestPlayers.create(h, "gt_r3");
            check(h, !Rewards.buy(server, p3, "gt_apple").ok(), "no points, no rewards");

            // default prices follow the config until an admin sets one
            Reward cfg = new Reward("gt_cfg", Type.ITEM, "Cfg", new ItemStack(Items.STICK), "", 1).priceFromConfig("fragment");
            int oldPrice = CgConfig.FRAGMENT_PRICE.get();
            CgConfig.FRAGMENT_PRICE.set(37);
            check(h, cfg.price() == 37, "price follows config");
            CgConfig.FRAGMENT_PRICE.set(oldPrice);
            check(h, cfg.price(5).price() == 5, "set price overrides config");

            // the next event wipes leftover points and closes the shop for them
            EventManager.start(server, PenaltyMode.SEAL_CT, 3);
            EventManager.stop(server);
            store.useEvent(state.eventNumber());
            check(h, store.boughtThisEvent(p1.getUUID(), "gt_apple") == 0, "per-event counters reset");
            check(h, store.boughtEver(p1.getUUID(), "gt_cmd"), "once-only purchases remembered");
        } finally {
            EventManager.stop(server);
            for (String id : TEST_REWARDS) {
                store.remove(id);
            }
            store.setFragments(p1.getUUID(), "minecraft:iron_sword", 0);
            colonies.remove("gt_rewards");
            TestPlayers.remove(h, p1, p2);
            if (p3 != null) {
                TestPlayers.remove(h, p3);
            }
        }
        h.succeed();
    }
}
