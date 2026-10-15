package net.jji.cullinggames.colony;

import java.util.Random;
import net.jji.cullinggames.CullingGames;
import net.jji.cullinggames.event.EventManager;
import net.jji.cullinggames.event.EventPhase;
import net.jji.cullinggames.event.EventState;
import net.jji.cullinggames.event.PenaltyMode;
import net.jji.cullinggames.test.TestPlayers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Run with ./gradlew runGameTestServer. Tests in different batches never run at the same time. */
@GameTestHolder(CullingGames.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ColonyGameTests {
    private static final String TEMPLATE = "empty";

    private ColonyGameTests() {}

    private static void check(GameTestHelper h, boolean ok, String what) {
        if (!ok) {
            CullingGames.LOGGER.error("GameTest check failed: {}", what);
            h.fail(what);
        }
    }

    @GameTest(template = TEMPLATE, batch = "geometry")
    public static void circleAndBoxGeometry(GameTestHelper h) {
        Colony circle = Colony.circle("c", Level.OVERWORLD, 0, 0, 10);
        check(h, circle.contains(Level.OVERWORLD, 0.5, 0.5), "circle contains center");
        check(h, circle.contains(Level.OVERWORLD, 9.0, 0.5), "circle contains near edge");
        check(h, !circle.contains(Level.OVERWORLD, 11.5, 0.5), "circle excludes outside");
        check(h, !circle.contains(Level.NETHER, 0.5, 0.5), "circle excludes other dimension");
        Vec2 back = circle.clampInside(30, 0.5);
        check(h, circle.contains(Level.OVERWORLD, back.x, back.y), "circle clamp lands inside");

        Colony box = Colony.box("b", Level.OVERWORLD, 10, 10, -10, -10);
        check(h, box.contains(Level.OVERWORLD, -10.0, 10.9), "box contains corners (normalized)");
        check(h, !box.contains(Level.OVERWORLD, 11.1, 0), "box excludes outside");
        Vec2 boxBack = box.clampInside(-50, 50);
        check(h, box.contains(Level.OVERWORLD, boxBack.x, boxBack.y), "box clamp lands inside");

        Random rnd = new Random(1);
        for (int i = 0; i < 200; i++) {
            Vec2 p = circle.randomPoint(rnd);
            Vec2 q = box.randomPoint(rnd);
            check(h, circle.contains(Level.OVERWORLD, p.x, p.y), "circle random point inside");
            check(h, box.contains(Level.OVERWORLD, q.x, q.y), "box random point inside");
        }

        Colony loaded = Colony.load(box.save());
        check(h, loaded != null && loaded.describe().equals(box.describe()), "colony survives save/load");
        CompoundTag broken = box.save();
        broken.putString("dim", "Not A Valid Id!");
        check(h, Colony.load(broken) == null, "invalid dimension is rejected on load");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "event")
    public static void spreadAndBarrier(GameTestHelper h) {
        try {
            runSpreadAndBarrier(h);
        } catch (RuntimeException e) {
            CullingGames.LOGGER.error("spreadAndBarrier threw", e);
            throw e;
        }
    }

    private static void runSpreadAndBarrier(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        EventManager.stop(server);
        ColonyStore store = ColonyStore.get(server);
        store.remove("gt_a");
        store.remove("gt_b");

        BlockPos a = h.absolutePos(new BlockPos(2, 1, 2));
        Colony colonyA = Colony.circle("gt_a", h.getLevel().dimension(), a.getX(), a.getZ(), 6);
        Colony colonyB = Colony.box("gt_b", h.getLevel().dimension(), a.getX() + 40, a.getZ(), a.getX() + 50, a.getZ() + 10);
        check(h, store.add(colonyA) && store.add(colonyB), "colonies added");
        check(h, !store.add(Colony.circle("GT_A", h.getLevel().dimension(), 0, 0, 5)), "duplicate name rejected");

        ServerPlayer p1 = TestPlayers.create(h, "gt_p1");
        ServerPlayer p2 = TestPlayers.create(h, "gt_p2");
        try {
            check(h, EventManager.start(server, PenaltyMode.SEAL_CT) == EventManager.Result.OK, "event starts");
            EventState state = EventState.get(server);
            check(h, state.phase() == EventPhase.RUNNING, "event running");
            for (ServerPlayer p : new ServerPlayer[] {p1, p2}) {
                check(h, state.isParticipant(p.getUUID()), "player is a participant");
                Colony held = Barrier.heldColony(server, p);
                check(h, held != null, "player is assigned a colony");
                check(h, held.contains(p.level().dimension(), p.getX(), p.getZ()), "player was teleported into their colony");
            }
            check(h, !state.colonyOf(p1.getUUID()).equalsIgnoreCase(state.colonyOf(p2.getUUID())),
                    "two players spread over two colonies");

            Colony held = Barrier.heldColony(server, p1);
            check(h, !Barrier.enforce(server, p1), "no push while inside");
            Vec2 c = held.center();
            p1.teleportTo(c.x + 200, p1.getY(), c.y);
            check(h, !held.contains(p1.level().dimension(), p1.getX(), p1.getZ()), "player moved outside");
            check(h, Barrier.enforce(server, p1), "barrier reacts to player outside");
            check(h, held.contains(p1.level().dimension(), p1.getX(), p1.getZ()), "barrier pushed player back inside");

            check(h, EventManager.pause(server) == EventManager.Result.OK, "pause");
            p1.teleportTo(c.x - 200, p1.getY(), c.y);
            check(h, Barrier.enforce(server, p1), "barrier still holds while paused");

            check(h, EventManager.stop(server) == EventManager.Result.OK, "stop");
            check(h, Barrier.heldColony(server, p1) == null, "no barrier after stop");
            p1.teleportTo(c.x + 200, p1.getY(), c.y);
            check(h, !Barrier.enforce(server, p1), "free to leave after stop");
        } finally {
            EventManager.stop(server);
            store.remove("gt_a");
            store.remove("gt_b");
            TestPlayers.remove(h, p1, p2);
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "nocolonies")
    public static void startNeedsColonies(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        EventManager.stop(server);
        ColonyStore store = ColonyStore.get(server);
        if (!store.isEmpty()) {
            h.succeed();
            return;
        }
        check(h, EventManager.start(server, PenaltyMode.LOSE_LIFE) == EventManager.Result.NO_COLONIES,
                "start refused without colonies");
        h.succeed();
    }
}
