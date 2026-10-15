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

            LivingDeathEvent cancelled = new LivingDeathEvent(zombie, p1.damageSources().playerAttack(p1));
            cancelled.setCanceled(true);
            MinecraftForge.EVENT_BUS.post(cancelled);
            check(h, state.points(p1.getUUID()) == 6, "cancelled death (totem) gives nothing");

            outsider = TestPlayers.create(h, "gt_k3");
            EventManager.removeParticipant(server, outsider);
            kill(zombie, outsider);
            check(h, state.points(outsider.getUUID()) == 0, "non-participant gets nothing");
            kill(outsider, p1);
            check(h, state.points(p1.getUUID()) == 6, "killing a non-participant gives nothing");

            EventManager.pause(server);
            kill(zombie, p1);
            check(h, state.points(p1.getUUID()) == 6, "no points while paused");
            EventManager.resume(server);

            check(h, Points.add(server, p2.getUUID(), 10) == 10, "admin add");
            check(h, Points.set(server, p2.getUUID(), -5) == 0, "points never go below 0");

            EventState copy = EventState.load(state.save(new CompoundTag()));
            check(h, copy.points(p1.getUUID()) == 6 && copy.kills().equals(state.kills())
                    && copy.ticksSincePointChange(p2.getUUID()) == state.ticksSincePointChange(p2.getUUID()),
                    "points, timer and kill log survive save/load");

            EventManager.stop(server);
            check(h, state.points(p1.getUUID()) == 6 && state.kills().size() == 2, "results kept after stop");
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
}
