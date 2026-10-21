package net.jji.cullinggames.event;

import net.jji.cullinggames.CullingGames;
import net.jji.cullinggames.colony.Barrier;
import net.jji.cullinggames.colony.Colony;
import net.jji.cullinggames.colony.ColonyStore;
import net.jji.cullinggames.compat.JujutsuCraft;
import net.jji.cullinggames.config.CgConfig;
import net.jji.cullinggames.test.TestPlayers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Timer, CT seal, event lives and restore at event end. */
@GameTestHolder(CullingGames.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PenaltyGameTests {
    private PenaltyGameTests() {}

    private static void check(GameTestHelper h, boolean ok, String what) {
        if (!ok) {
            CullingGames.LOGGER.error("GameTest check failed: {}", what);
            h.fail(what);
        }
    }

    private static void runTimerOut(EventState state) {
        long limit = (long) CgConfig.TIMER_MINUTES.get() * 20 * 60;
        for (long i = 0; i < limit; i++) {
            state.tickRunning();
        }
    }

    private static void setup(GameTestHelper h, String colony) {
        MinecraftServer server = h.getLevel().getServer();
        EventManager.stop(server);
        ColonyStore store = ColonyStore.get(server);
        store.remove(colony);
        BlockPos c = h.absolutePos(new BlockPos(2, 1, 2));
        store.add(Colony.circle(colony, h.getLevel().dimension(), c.getX(), c.getZ(), 8));
    }

    private static void wrap(GameTestHelper h, String name, Runnable body) {
        try {
            body.run();
        } catch (RuntimeException e) {
            CullingGames.LOGGER.error(name + " threw", e);
            throw e;
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "offline")
    public static void offlineTimerHolds(GameTestHelper h) {
        wrap(h, "offlineTimerHolds", () -> runOfflineTimer(h));
    }

    /** Weekend events: time logged out never counts towards the penalty, so nobody is sealed the moment they rejoin. */
    private static void runOfflineTimer(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        setup(h, "gt_off");
        ServerPlayer p1 = TestPlayers.create(h, "gt_o1");
        ServerPlayer p2 = TestPlayers.create(h, "gt_o2");
        ServerPlayer p2Again = null;
        try {
            check(h, EventManager.start(server, PenaltyMode.SEAL_CT) == EventManager.Result.OK, "start");
            EventState state = EventState.get(server);
            for (int i = 0; i < 40; i++) {
                EventManager.tick(server);
            }
            check(h, state.ticksSincePointChange(p2.getUUID()) == 40, "online time counts");

            TestPlayers.remove(h, p2);
            long limit = Penalties.timerLimitTicks(state);
            for (long i = 0; i < limit + 100; i++) {
                EventManager.tick(server);
            }
            check(h, state.ticksSincePointChange(p1.getUUID()) == limit + 140, "online player's timer keeps running");
            check(h, state.ticksSincePointChange(p2.getUUID()) == 40, "offline player's timer holds");

            p2Again = TestPlayers.create(h, "gt_o2", p2.getUUID());
            Penalties.checkTimer(server, state, p2Again);
            check(h, !state.isSealed(p2.getUUID()), "not sealed on rejoin");
            for (int i = 0; i < 60; i++) {
                EventManager.tick(server);
            }
            check(h, state.ticksSincePointChange(p2.getUUID()) == 100, "timer runs again after rejoin");
            check(h, EventState.load(state.save(new CompoundTag())).ticksSincePointChange(p2.getUUID()) == 100,
                    "held timer survives save/load");
        } finally {
            EventManager.stop(server);
            ColonyStore.get(server).remove("gt_off");
            TestPlayers.remove(h, p1);
            if (p2Again != null) {
                TestPlayers.remove(h, p2Again);
            }
        }
    }

    @GameTest(template = "empty", batch = "seal")
    public static void sealAndRestore(GameTestHelper h) {
        wrap(h, "sealAndRestore", () -> runSeal(h));
    }

    private static void runSeal(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        setup(h, "gt_seal");
        boolean jjc = JujutsuCraft.isLoaded();
        ServerPlayer p1 = TestPlayers.create(h, "gt_s1");
        ServerPlayer p2 = TestPlayers.create(h, "gt_s2");
        ServerPlayer p2Again = null;
        try {
            check(h, EventManager.start(server, PenaltyMode.SEAL_CT, 3) == EventManager.Result.OK, "start seal_ct");
            EventState state = EventState.get(server);
            if (jjc) {
                JujutsuCraft.setCursedEnergy(p1, 20);
                JujutsuCraft.setCursedEnergy(p2, 25);
            }
            double ct = jjc ? JujutsuCraft.technique(p1) : 0;

            Penalties.checkTimer(server, state, p1);
            check(h, !state.isSealed(p1.getUUID()), "not sealed before the timer runs out");

            runTimerOut(state);
            Penalties.checkTimer(server, state, p1);
            check(h, state.isSealed(p1.getUUID()), "sealed when the timer runs out");
            if (jjc) {
                check(h, JujutsuCraft.cursedEnergy(p1) == 0, "cursed energy drained by seal");
                JujutsuCraft.setCursedEnergy(p1, 15);
                Penalties.drainSealed(server, state);
                check(h, JujutsuCraft.cursedEnergy(p1) == 0, "regenerated energy drained again");
                check(h, JujutsuCraft.technique(p1) == ct, "CT itself never changes");
            }
            check(h, state.lives(p1.getUUID()) == 3, "seal mode does not cost lives");

            EventState copy = EventState.load(state.save(new CompoundTag()));
            check(h, copy.isSealed(p1.getUUID()), "seal survives save/load");

            Points.add(server, p1.getUUID(), 1);
            check(h, !state.isSealed(p1.getUUID()), "unsealed when points change");
            if (jjc) {
                check(h, JujutsuCraft.cursedEnergy(p1) == 20, "saved cursed energy given back");
            }

            // p2: sealed, logs out, event ends while offline, gets restored at login
            Penalties.checkTimer(server, state, p2);
            check(h, state.isSealed(p2.getUUID()), "p2 sealed");
            TestPlayers.remove(h, p2);
            check(h, EventManager.stop(server) == EventManager.Result.OK, "stop");
            check(h, state.sealedPlayers().isEmpty(), "nobody sealed after stop");
            p2Again = TestPlayers.create(h, "gt_s2", p2.getUUID());
            if (jjc) {
                check(h, JujutsuCraft.cursedEnergy(p2Again) == 25, "offline player's cursed energy restored at login");
            }
            check(h, state.takeRestore(p2.getUUID()) == null, "restore applied once");
        } finally {
            EventManager.stop(server);
            ColonyStore.get(server).remove("gt_seal");
            TestPlayers.remove(h, p1);
            if (p2Again != null) {
                TestPlayers.remove(h, p2Again);
            }
        }
    }

    @GameTest(template = "empty", batch = "lives")
    public static void livesAndElimination(GameTestHelper h) {
        wrap(h, "livesAndElimination", () -> runLives(h));
    }

    private static void runLives(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        setup(h, "gt_lives");
        ServerPlayer p1 = TestPlayers.create(h, "gt_l1");
        ServerPlayer p2 = TestPlayers.create(h, "gt_l2");
        try {
            check(h, EventManager.start(server, PenaltyMode.LOSE_LIFE, 2) == EventManager.Result.OK, "start lose_life");
            EventState state = EventState.get(server);
            check(h, state.lives(p1.getUUID()) == 2, "starts with the chosen lives");

            runTimerOut(state);
            Penalties.checkTimer(server, state, p1);
            check(h, state.lives(p1.getUUID()) == 1, "timer penalty takes a life");
            check(h, !state.isSealed(p1.getUUID()), "lose_life mode never seals");
            check(h, state.ticksSincePointChange(p1.getUUID()) == 0, "timer restarts after the penalty");
            Penalties.checkTimer(server, state, p1);
            check(h, state.lives(p1.getUUID()) == 1, "no second penalty right away");

            runTimerOut(state);
            Penalties.checkTimer(server, state, p1);
            check(h, state.isEliminated(p1.getUUID()), "eliminated at 0 lives");
            check(h, p1.gameMode.getGameModeForPlayer() == GameType.SPECTATOR, "alive eliminated player becomes spectator");
            check(h, Barrier.heldColony(server, p1) == null, "spectators are not held by the barrier");

            LivingDeathEvent saved = new LivingDeathEvent(p2, p2.damageSources().generic());
            saved.setCanceled(true);
            MinecraftForge.EVENT_BUS.post(saved);
            check(h, state.lives(p2.getUUID()) == 2, "cancelled death (totem) costs no life");
            MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(p2, p2.damageSources().generic()));
            check(h, state.lives(p2.getUUID()) == 1, "death costs a life");
            EventManager.pause(server);
            MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(p2, p2.damageSources().generic()));
            check(h, state.lives(p2.getUUID()) == 1, "no life lost while paused");
            EventManager.resume(server);
            MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(p2, p2.damageSources().generic()));
            check(h, state.isEliminated(p2.getUUID()), "dead player eliminated at 0 lives");
            check(h, p2.gameMode.getGameModeForPlayer() != GameType.SPECTATOR, "dead player not spectator until respawn");
            MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(p2, false));
            check(h, p2.gameMode.getGameModeForPlayer() == GameType.SPECTATOR, "spectator after respawn");

            check(h, EventManager.stop(server) == EventManager.Result.OK, "stop");
            check(h, p1.gameMode.getGameModeForPlayer() == GameType.SURVIVAL
                    && p2.gameMode.getGameModeForPlayer() == GameType.SURVIVAL, "game modes restored at end");
        } finally {
            EventManager.stop(server);
            ColonyStore.get(server).remove("gt_lives");
            TestPlayers.remove(h, p1, p2);
        }
    }
}
