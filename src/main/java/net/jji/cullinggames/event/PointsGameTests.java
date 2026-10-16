package net.jji.cullinggames.event;

import net.jji.cullinggames.CullingGames;
import net.jji.cullinggames.colony.Colony;
import net.jji.cullinggames.colony.ColonyStore;
import net.jji.cullinggames.test.TestPlayers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Points and kill log. Deaths are simulated by posting LivingDeathEvent, which is what a real death fires. */
@GameTestHolder(CullingGames.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PointsGameTests {
    private PointsGameTests() {}

    private static void check(GameTestHelper h, boolean ok, String what) {
        if (!ok) {
            CullingGames.LOGGER.error("GameTest check failed: {}", what);
            h.fail(what);
        }
    }

    private static boolean kill(LivingEntity victim, ServerPlayer killer) {
        return MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(victim, killer.damageSources().playerAttack(killer)));
    }

    @GameTest(template = "empty", batch = "points")
    public static void pointsAndKillLog(GameTestHelper h) {
        try {
            run(h);
        } catch (RuntimeException e) {
            CullingGames.LOGGER.error("pointsAndKillLog threw", e);
            throw e;
        }
    }

    private static void run(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        EventManager.stop(server);
        ColonyStore store = ColonyStore.get(server);
        store.remove("gt_pts");
        BlockPos c = h.absolutePos(new BlockPos(2, 1, 2));
        store.add(Colony.circle("gt_pts", h.getLevel().dimension(), c.getX(), c.getZ(), 8));

        ServerPlayer p1 = TestPlayers.create(h, "gt_k1");
        ServerPlayer p2 = TestPlayers.create(h, "gt_k2");
        ServerPlayer outsider = null;
        try {
            check(h, EventManager.start(server, PenaltyMode.SEAL_CT) == EventManager.Result.OK, "event starts");
            EventState state = EventState.get(server);
            check(h, state.points(p1.getUUID()) == 0 && state.points(p2.getUUID()) == 0, "everyone starts at 0");

            // timer: advances with running time, resets on a point change
            for (int i = 0; i < 40; i++) {
                state.tickRunning();
            }
            check(h, state.ticksSincePointChange(p1.getUUID()) == 40, "timer counts running ticks since join");

            kill(p2, p1);
            check(h, state.points(p1.getUUID()) == 5, "player kill gives 5");
            check(h, state.ticksSincePointChange(p1.getUUID()) == 0, "point change resets timer");
            check(h, state.ticksSincePointChange(p2.getUUID()) == 40, "victim's timer is not reset");
            check(h, state.kills().size() == 1, "kill logged");
            KillRecord k = state.kills().get(0);
            check(h, k.killerId().equals(p1.getUUID()) && p2.getUUID().equals(k.victimId())
                    && "gt_k2".equals(k.victimName()) && k.points() == 5, "kill record has killer, victim and points");

            LivingEntity zombie = EntityType.ZOMBIE.create(h.getLevel());
            LivingEntity cow = EntityType.COW.create(h.getLevel());
            kill(zombie, p1);
            check(h, state.points(p1.getUUID()) == 6, "hostile mob gives 1");
            check(h, "minecraft:zombie".equals(state.kills().get(1).victimName()), "mob kill logged by entity id");
            kill(cow, p1);
            check(h, state.points(p1.getUUID()) == 6 && state.kills().size() == 2, "passive mob gives nothing");
            kill(p1, p1);
            check(h, state.points(p1.getUUID()) == 6, "killing yourself gives nothing");

            // Jujutsu Craft summons carry OWNER_UUID: kills credit the summoner, and summons are never worth points.
            LivingEntity shikigami = EntityType.ZOMBIE.create(h.getLevel());
            shikigami.getPersistentData().putString("OWNER_UUID", p1.getStringUUID());
            kill(shikigami, p1);
            check(h, state.points(p1.getUUID()) == 6, "killing your own summon gives nothing");
            kill(shikigami, p2);
            check(h, state.points(p2.getUUID()) == 0, "killing someone else's summon gives nothing");
            MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(zombie, shikigami.damageSources().mobAttack(shikigami)));
            check(h, state.points(p1.getUUID()) == 7, "a summon's kill credits its owner");

            LivingDeathEvent cancelled = new LivingDeathEvent(zombie, p1.damageSources().playerAttack(p1));
            cancelled.setCanceled(true);
            MinecraftForge.EVENT_BUS.post(cancelled);
            check(h, state.points(p1.getUUID()) == 7, "cancelled death (totem) gives nothing");

            outsider = TestPlayers.create(h, "gt_k3");
            EventManager.removeParticipant(server, outsider);
            kill(zombie, outsider);
            check(h, state.points(outsider.getUUID()) == 0, "non-participant gets nothing");
            kill(outsider, p1);
            check(h, state.points(p1.getUUID()) == 7, "killing a non-participant gives nothing");

            EventManager.pause(server);
            kill(zombie, p1);
            check(h, state.points(p1.getUUID()) == 7, "no points while paused");
            EventManager.resume(server);

            check(h, Points.add(server, p2.getUUID(), 10) == 10, "admin add");
            check(h, Points.set(server, p2.getUUID(), -5) == 0, "points never go below 0");

            EventState copy = EventState.load(state.save(new CompoundTag()));
            check(h, copy.points(p1.getUUID()) == 7 && copy.kills().equals(state.kills())
                    && copy.ticksSincePointChange(p2.getUUID()) == state.ticksSincePointChange(p2.getUUID()),
                    "points, timer and kill log survive save/load");

            EventManager.stop(server);
            check(h, state.points(p1.getUUID()) == 7 && state.kills().size() == 3, "results kept after stop");
            EventManager.start(server, PenaltyMode.SEAL_CT);
            check(h, state.points(p1.getUUID()) == 0 && state.kills().isEmpty(), "next event starts clean");
        } finally {
            EventManager.stop(server);
            store.remove("gt_pts");
            TestPlayers.remove(h, p1, p2);
            if (outsider != null) {
                TestPlayers.remove(h, outsider);
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "steal")
    public static void killStealsVictimPoints(GameTestHelper h) {
        try {
            runSteal(h);
        } catch (RuntimeException e) {
            CullingGames.LOGGER.error("killStealsVictimPoints threw", e);
            throw e;
        }
    }

    private static void runSteal(GameTestHelper h) {
        check(h, KillSteal.FLAT.amount(3, 5, 0) == 3, "flat steal is capped at what the victim has");
        check(h, KillSteal.PERCENT.amount(10, 0, 25) == 2, "percent steal rounds down");
        check(h, KillSteal.NONE.amount(100, 5, 50) == 0, "none steals nothing");

        MinecraftServer server = h.getLevel().getServer();
        EventManager.stop(server);
        ColonyStore store = ColonyStore.get(server);
        store.remove("gt_steal");
        BlockPos c = h.absolutePos(new BlockPos(2, 1, 2));
        store.add(Colony.circle("gt_steal", h.getLevel().dimension(), c.getX(), c.getZ(), 8));
        ServerPlayer killer = TestPlayers.create(h, "gt_st1");
        ServerPlayer victim = TestPlayers.create(h, "gt_st2");
        KillSteal modeBefore = net.jji.cullinggames.config.CgConfig.KILL_STEAL_MODE.get();
        int flatBefore = net.jji.cullinggames.config.CgConfig.KILL_STEAL_FLAT.get();
        int percentBefore = net.jji.cullinggames.config.CgConfig.KILL_STEAL_PERCENT.get();
        try {
            EventManager.start(server, PenaltyMode.SEAL_CT, 3);
            EventState state = EventState.get(server);
            net.jji.cullinggames.config.CgConfig.KILL_STEAL_MODE.set(KillSteal.FLAT);
            net.jji.cullinggames.config.CgConfig.KILL_STEAL_FLAT.set(5);
            Points.set(server, victim.getUUID(), 20);
            for (int i = 0; i < 30; i++) {
                state.tickRunning();
            }
            kill(victim, killer);
            check(h, state.points(killer.getUUID()) == 10, "default kill: 5 from Kogane + 5 taken from the victim");
            check(h, state.points(victim.getUUID()) == 15, "victim loses the stolen points");
            check(h, state.ticksSincePointChange(victim.getUUID()) == 30, "losing points doesn't reset the victim's timer");
            check(h, state.kills().get(0).points() == 10, "kill log records the full gain");

            net.jji.cullinggames.config.CgConfig.KILL_STEAL_MODE.set(KillSteal.PERCENT);
            net.jji.cullinggames.config.CgConfig.KILL_STEAL_PERCENT.set(50);
            kill(victim, killer);
            check(h, state.points(killer.getUUID()) == 22 && state.points(victim.getUUID()) == 8,
                    "percent mode takes 50% (7 of 15)");

            Points.set(server, victim.getUUID(), 0);
            net.jji.cullinggames.config.CgConfig.KILL_STEAL_MODE.set(KillSteal.FLAT);
            kill(victim, killer);
            check(h, state.points(killer.getUUID()) == 27 && state.points(victim.getUUID()) == 0,
                    "a victim with 0 points only gives the base 5");
        } finally {
            net.jji.cullinggames.config.CgConfig.KILL_STEAL_MODE.set(modeBefore);
            net.jji.cullinggames.config.CgConfig.KILL_STEAL_FLAT.set(flatBefore);
            net.jji.cullinggames.config.CgConfig.KILL_STEAL_PERCENT.set(percentBefore);
            EventManager.stop(server);
            store.remove("gt_steal");
            TestPlayers.remove(h, killer, victim);
        }
        h.succeed();
    }
}
