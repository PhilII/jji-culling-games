package net.jji.cullinggames.event;

import java.util.List;
import net.jji.cullinggames.CullingGames;
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
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Red's rules of 20 Oct: tracker compass, kill bonus, top-3 reveal, hunted glow, heal/CE purchases. */
@GameTestHolder(CullingGames.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RedRulesGameTests {
    private RedRulesGameTests() {}

    private static void check(GameTestHelper h, boolean ok, String what) {
        if (!ok) {
            CullingGames.LOGGER.error("GameTest check failed: {}", what);
            h.fail(what);
        }
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

    private static void colony(GameTestHelper h, String name, int dx) {
        ColonyStore store = ColonyStore.get(h.getLevel().getServer());
        store.remove(name);
        BlockPos c = h.absolutePos(new BlockPos(2 + dx, 1, 2));
        store.add(Colony.circle(name, h.getLevel().dimension(), c.getX(), c.getZ(), 40));
    }

    private static void moveTo(GameTestHelper h, ServerPlayer p, int x, int z) {
        BlockPos at = h.absolutePos(new BlockPos(x, 1, z));
        p.teleportTo(h.getLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
    }

    private static String target(GameTestHelper h, int x, int z) {
        BlockPos at = h.absolutePos(new BlockPos(x, 1, z));
        return h.getLevel().dimension().location() + " " + at.getX() + " " + at.getY() + " " + at.getZ();
    }

    private static void kill(ServerPlayer victim, ServerPlayer killer) {
        MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(victim, killer.damageSources().playerAttack(killer)));
    }

    @GameTest(template = "empty", batch = "tracker")
    public static void trackerCompass(GameTestHelper h) {
        wrap(h, "trackerCompass", () -> runTracker(h));
    }

    private static void runTracker(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        EventManager.stop(server);
        colony(h, "gt_track", 0);
        ServerPlayer p1 = TestPlayers.create(h, "gt_t1");
        ServerPlayer p2 = TestPlayers.create(h, "gt_t2");
        ServerPlayer p3 = TestPlayers.create(h, "gt_t3");
        try {
            check(h, EventManager.start(server, PenaltyMode.SEAL_CT) == EventManager.Result.OK, "start");
            EventState state = EventState.get(server);
            moveTo(h, p1, 0, 0);
            moveTo(h, p2, 6, 0);
            moveTo(h, p3, 20, 0);
            Tracker.update(server, state);
            check(h, Tracker.isTracker(Tracker.find(p1)), "participant gets a tracker");
            check(h, Tracker.targetOf(Tracker.find(p1)).equals(target(h, 6, 0)), "p1 points to nearest (p2)");
            check(h, Tracker.targetOf(Tracker.find(p3)).equals(target(h, 6, 0)), "p3 points to nearest (p2)");
            check(h, Tracker.targetOf(Tracker.find(p2)).equals(target(h, 0, 0)), "p2 points to nearest (p1)");

            moveTo(h, p2, 30, 0);
            Tracker.update(server, state);
            check(h, Tracker.targetOf(Tracker.find(p1)).equals(target(h, 20, 0)), "target follows the nearest after a move");
            int count = 0;
            for (int i = 0; i < p1.getInventory().getContainerSize(); i++) {
                count += Tracker.isTracker(p1.getInventory().getItem(i)) ? 1 : 0;
            }
            check(h, count == 1, "only one tracker per player");

            CgConfig.TRACKER_COMPASS.set(false);
            Tracker.update(server, state);
            check(h, Tracker.find(p1).isEmpty(), "turning the tracker off takes it away");
            CgConfig.TRACKER_COMPASS.set(true);
            Tracker.update(server, state);
            check(h, !Tracker.find(p1).isEmpty(), "turning it on gives it back");

            check(h, EventManager.stop(server) == EventManager.Result.OK, "stop");
            check(h, Tracker.find(p1).isEmpty() && Tracker.find(p2).isEmpty(), "trackers removed at event end");
        } finally {
            CgConfig.TRACKER_COMPASS.set(true);
            EventManager.stop(server);
            ColonyStore.get(server).remove("gt_track");
            TestPlayers.remove(h, p1, p2, p3);
        }
    }

    @GameTest(template = "empty", batch = "combatlog")
    public static void combatLogKills(GameTestHelper h) {
        wrap(h, "combatLogKills", () -> runCombatLog(h));
    }

    /** Logging out mid-battle is a death credited to the last attacker; logging out outside battle is free. */
    private static void runCombatLog(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        EventManager.stop(server);
        colony(h, "gt_cl", 0);
        ServerPlayer p1 = TestPlayers.create(h, "gt_cl1");
        ServerPlayer p2 = TestPlayers.create(h, "gt_cl2");
        ServerPlayer p3 = TestPlayers.create(h, "gt_cl3");
        try {
            check(h, EventManager.start(server, PenaltyMode.SEAL_CT, 3) == EventManager.Result.OK, "start");
            EventState state = EventState.get(server);
            Points.set(server, p2.getUUID(), 20);

            // Outside battle: logging out costs nothing.
            check(h, !Combat.combatLog(server, p3), "no battle, no penalty");
            check(h, state.lives(p3.getUUID()) == 3 && p3.isAlive(), "p3 keeps lives");

            // In battle: real logout (PlayerList.remove fires PlayerLoggedOutEvent).
            Combat.recordHit(server, p1, p2);
            TestPlayers.remove(h, p2);
            check(h, state.lives(p2.getUUID()) == 2, "combat logger loses a life");
            check(h, state.kills().size() == 1 && state.kills().get(0).killerId().equals(p1.getUUID())
                    && state.kills().get(0).victimId().equals(p2.getUUID()), "kill logged for the last attacker");
            check(h, state.points(p1.getUUID()) == 10, "attacker gets kill points (5 + 5 stolen)");
            check(h, state.points(p2.getUUID()) == 15, "logger loses the stolen points");

            // Switched off in config: no penalty.
            CgConfig.COMBAT_LOG_KILLS.set(false);
            Combat.recordHit(server, p1, p3);
            check(h, !Combat.combatLog(server, p3), "config off, no penalty");
        } finally {
            CgConfig.COMBAT_LOG_KILLS.set(true);
            EventManager.stop(server);
            ColonyStore.get(server).remove("gt_cl");
            TestPlayers.remove(h, p1, p3);
        }
    }

    @GameTest(template = "empty", batch = "killbonus")
    public static void killBonusAndAssist(GameTestHelper h) {
        wrap(h, "killBonusAndAssist", () -> runKillBonus(h));
    }

    private static void runKillBonus(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        EventManager.stop(server);
        colony(h, "gt_kb", 0);
        ServerPlayer p1 = TestPlayers.create(h, "gt_kb1");
        ServerPlayer p2 = TestPlayers.create(h, "gt_kb2");
        ServerPlayer p3 = TestPlayers.create(h, "gt_kb3");
        try {
            check(h, EventManager.start(server, PenaltyMode.SEAL_CT) == EventManager.Result.OK, "start");
            EventState state = EventState.get(server);
            boolean jjc = JujutsuCraft.isLoaded();
            double max = jjc ? JujutsuCraft.maxCursedEnergy(p1) : 0;
            if (jjc) {
                JujutsuCraft.setCursedEnergy(p1, 0);
            }
            check(h, state.isHunted(p3.getUUID()), "0 points = hunted");
            Combat.recordHit(server, p3, p2);
            kill(p2, p1);
            check(h, state.points(p1.getUUID()) > 0, "kill gives points");
            check(h, !state.isHunted(p3.getUUID()) && state.points(p3.getUUID()) == 0, "an assist ends hunted at 0 points");
            check(h, state.isHunted(p2.getUUID()), "the victim is still hunted");
            if (jjc) {
                double expected = Math.min(CgConfig.KILL_BONUS_CE.get(), max);
                CullingGames.LOGGER.info("Kill bonus CE: {} (max {})", JujutsuCraft.cursedEnergy(p1), max);
                check(h, Math.abs(JujutsuCraft.cursedEnergy(p1) - expected) < 0.01, "kill gives +500 CE up to max");

                CgConfig.KILL_BONUS_CE_OVER_MAX.set(true);
                JujutsuCraft.setCursedEnergy(p1, max);
                Combat.killBonus(state, p1);
                check(h, JujutsuCraft.cursedEnergy(p1) >= max + CgConfig.KILL_BONUS_CE.get() - 0.01, "over-max option allows more than max");
            }
            MobEffect zone = JujutsuCraft.effect(CgConfig.KILL_ZONE_EFFECT.get());
            if (zone != null) {
                MobEffectInstance active = p1.getEffect(zone);
                check(h, active != null && active.getDuration() > 45 * 20 - 40, "kill gives 45 s of Zone");
            } else {
                CullingGames.LOGGER.warn("{} not registered; Zone part skipped", CgConfig.KILL_ZONE_EFFECT.get());
                check(h, !jjc, "Jujutsu Craft has jujutsucraft:zone");
            }
        } finally {
            CgConfig.KILL_BONUS_CE_OVER_MAX.set(false);
            EventManager.stop(server);
            ColonyStore.get(server).remove("gt_kb");
            TestPlayers.remove(h, p1, p2, p3);
        }
    }

    @GameTest(template = "empty", batch = "reveal")
    public static void revealTopAndHunted(GameTestHelper h) {
        wrap(h, "revealTopAndHunted", () -> runReveal(h));
    }

    private static void runReveal(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        EventManager.stop(server);
        colony(h, "gt_rev", 0);
        ServerPlayer p1 = TestPlayers.create(h, "gt_r1");
        ServerPlayer p2 = TestPlayers.create(h, "gt_r2");
        ServerPlayer p3 = TestPlayers.create(h, "gt_r3");
        ServerPlayer p4 = TestPlayers.create(h, "gt_r4");
        ServerPlayer p5 = TestPlayers.create(h, "gt_r5");
        try {
            check(h, EventManager.start(server, PenaltyMode.SEAL_CT) == EventManager.Result.OK, "start");
            EventState state = EventState.get(server);
            Points.set(server, p1.getUUID(), 10);
            Points.set(server, p2.getUUID(), 40);
            Points.set(server, p3.getUUID(), 25);
            Points.set(server, p4.getUUID(), 5);
            List<String> top = Reveals.topLines(server, state);
            // The test world may hold other colonies, so compare with wherever each player was placed.
            check(h, top.equals(List.of("gt_r2 - " + state.colonyOf(p2.getUUID()), "gt_r3 - " + state.colonyOf(p3.getUUID()),
                    "gt_r1 - " + state.colonyOf(p1.getUUID()))), "top 3 by points with colony: " + top);

            check(h, state.isHunted(p5.getUUID()) && !state.isHunted(p1.getUUID()), "only 0-point players are hunted");
            check(h, Reveals.glowHunted(server, state) == 1, "one hunted player glows");
            MobEffectInstance glow = p5.getEffect(MobEffects.GLOWING);
            check(h, glow != null && glow.getDuration() == 30 * 20, "hunted glow lasts 30 s");
            check(h, p1.getEffect(MobEffects.GLOWING) == null, "players with points don't glow");

            Points.set(server, p4.getUUID(), 0);
            check(h, state.isHunted(p4.getUUID()), "dropping to 0 points makes you hunted");
            EventState copy = EventState.load(state.save(new CompoundTag()));
            state.clearHunt(p5.getUUID());
            check(h, !state.isHunted(p5.getUUID()), "a kill or assist ends hunted");
            check(h, !EventState.load(state.save(new CompoundTag())).isHunted(p5.getUUID()) && copy.isHunted(p5.getUUID()),
                    "hunted state survives save/load");

            // Timing: the glow comes on event time, once per huntedIntervalMinutes (one MC day).
            p4.removeAllEffects();
            long day = CgConfig.HUNTED_INTERVAL_MINUTES.get() * 20L * 60;
            while (state.runningTicks() < day - 1) {
                state.tickRunning();
                Reveals.tick(server, state);
            }
            check(h, p4.getEffect(MobEffects.GLOWING) == null, "no glow before a day has passed");
            state.tickRunning();
            Reveals.tick(server, state);
            check(h, p4.getEffect(MobEffects.GLOWING) != null, "hunted player glows when a day has passed");
        } finally {
            EventManager.stop(server);
            ColonyStore.get(server).remove("gt_rev");
            TestPlayers.remove(h, p1, p2, p3, p4, p5);
        }
    }

    @GameTest(template = "empty", batch = "recovery")
    public static void healAndCursedEnergy(GameTestHelper h) {
        wrap(h, "healAndCursedEnergy", () -> runRecovery(h));
    }

    private static void runRecovery(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        EventManager.stop(server);
        colony(h, "gt_rec", 0);
        ServerPlayer p1 = TestPlayers.create(h, "gt_rc1");
        ServerPlayer p2 = TestPlayers.create(h, "gt_rc2");
        try {
            check(h, EventManager.start(server, PenaltyMode.SEAL_CT) == EventManager.Result.OK, "start");
            EventState state = EventState.get(server);
            Points.set(server, p1.getUUID(), 100);
            p1.setHealth(p1.getMaxHealth());
            check(h, !Shop.buyRecovery(server, p1, Recovery.HEAL).ok(), "nothing to heal at full health");

            p1.setHealth(p1.getMaxHealth() - 10);
            check(h, Recovery.HEAL.price(p1) == 5, "price: 1 point per missing heart");
            check(h, Views.shop(server, p1, "").services().stream().anyMatch(s -> s.id().equals("heal") && s.price() == 5 && !s.owned()),
                    "heal is in the shop at the scaled price");
            check(h, Shop.buyRecovery(server, p1, Recovery.HEAL).ok(), "heal bought");
            check(h, p1.getHealth() == p1.getMaxHealth(), "healed to full");
            check(h, state.points(p1.getUUID()) == 95, "heal paid");

            p1.setHealth(p1.getMaxHealth() - 10);
            check(h, Recovery.HEAL.blocked(server, state, p1).startsWith("Wait"), "short cooldown outside battle");
            CgConfig.RECOVERY_COOLDOWN_SECONDS.set(0);
            check(h, Shop.buyRecovery(server, p1, Recovery.HEAL).ok(), "outside battle it can be used freely");

            Combat.recordHit(server, p2, p1);
            check(h, Combat.inBattle(server, p1.getUUID()), "a hit from a participant starts a battle");
            p1.setHealth(p1.getMaxHealth() - 10);
            check(h, Shop.buyRecovery(server, p1, Recovery.HEAL).ok(), "one heal in battle");
            p1.setHealth(p1.getMaxHealth() - 10);
            Shop.Result second = Shop.buyRecovery(server, p1, Recovery.HEAL);
            check(h, !second.ok() && second.message().startsWith("Once per battle"), "only once per battle");

            MobEffect fatigue = JujutsuCraft.effect("jujutsucraft:fatigue");
            if (fatigue != null) {
                p1.addEffect(new MobEffectInstance(fatigue, 200, 1));
                check(h, JujutsuCraft.fatigueLevel(p1) == 2, "fatigue level read from the effect");
                check(h, Recovery.HEAL.price(p1) == 8, "fatigue II raises 5 to 8 (+25% per level)");
                p1.removeEffect(fatigue);
            }

            if (JujutsuCraft.isLoaded()) {
                double max = JujutsuCraft.maxCursedEnergy(p2);
                CullingGames.LOGGER.info("Test player max CE {}", max);
                Points.set(server, p2.getUUID(), 1000);
                JujutsuCraft.setCursedEnergy(p2, 0);
                if (max > 0) {
                    int price = Recovery.CE.price(p2);
                    check(h, price == (int) Math.ceil(max / CgConfig.CE_PER_POINT.get()), "CE price scales with missing CE");
                    // p2 is in the battle with p1, so this is their one recovery
                    check(h, Shop.buyRecovery(server, p2, Recovery.CE).ok(), "CE refill bought");
                    check(h, JujutsuCraft.cursedEnergy(p2) == max, "CE refilled to max");
                    check(h, state.points(p2.getUUID()) == 1000 - price, "refill paid");
                    JujutsuCraft.setCursedEnergy(p2, 0);
                    check(h, !Shop.buyRecovery(server, p2, Recovery.CE).ok(), "heal and refill share the battle limit");
                }
                Penalties.seal(state, p1);
                check(h, !Recovery.CE.blocked(server, state, p1).isEmpty(), "sealed players can't buy CE");
            }
        } finally {
            CgConfig.RECOVERY_COOLDOWN_SECONDS.set(10);
            EventManager.stop(server);
            ColonyStore.get(server).remove("gt_rec");
            TestPlayers.remove(h, p1, p2);
        }
    }
}
