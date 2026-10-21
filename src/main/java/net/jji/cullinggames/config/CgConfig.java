package net.jji.cullinggames.config;

import java.util.List;
import net.jji.cullinggames.event.KillSteal;
import net.jji.cullinggames.event.PenaltyMode;
import net.minecraftforge.common.ForgeConfigSpec;

/** Server config (serverconfig/cullinggames-server.toml). All event tunables live here. */
public final class CgConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ALLOW_LATE_JOIN;
    public static final ForgeConfigSpec.BooleanValue TELEPORT_TO_SPAWN_ON_END;
    public static final ForgeConfigSpec.IntValue TIMER_MINUTES;
    public static final ForgeConfigSpec.IntValue FIRST_WARNING_MINUTES;
    public static final ForgeConfigSpec.IntValue FINAL_WARNING_MINUTES;
    public static final ForgeConfigSpec.EnumValue<PenaltyMode> DEFAULT_PENALTY;
    public static final ForgeConfigSpec.IntValue EVENT_LIVES;
    public static final ForgeConfigSpec.IntValue PLAYER_KILL_POINTS;
    public static final ForgeConfigSpec.IntValue WEAK_KILL_POINTS;
    public static final ForgeConfigSpec.EnumValue<KillSteal> KILL_STEAL_MODE;
    public static final ForgeConfigSpec.IntValue KILL_STEAL_FLAT;
    public static final ForgeConfigSpec.IntValue KILL_STEAL_PERCENT;
    public static final ForgeConfigSpec.IntValue RULE_COST;
    public static final ForgeConfigSpec.BooleanValue WEAK_TARGETS_ALL_HOSTILE;
    public static final ForgeConfigSpec.IntValue LEADERBOARD_PRICE;
    public static final ForgeConfigSpec.IntValue TRANSFER_PRICE;
    public static final ForgeConfigSpec.IntValue KILLLOG_PRICE;
    public static final ForgeConfigSpec.IntValue LONGER_TIMER_MINUTES;
    public static final ForgeConfigSpec.ConfigValue<String> MONEY_ITEM;
    public static final ForgeConfigSpec.IntValue POINTS_PER_COIN;
    public static final ForgeConfigSpec.IntValue MAX_COINS_PER_EVENT;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> WEAK_TARGETS_EXTRA;
    public static final ForgeConfigSpec.BooleanValue REWARD_SHOP_OPEN;
    public static final ForgeConfigSpec.IntValue MAX_FRAGMENTS_PER_EVENT;
    public static final ForgeConfigSpec.IntValue SPECIAL_GRADE_PRICE;
    public static final ForgeConfigSpec.IntValue FRAGMENT_PRICE;
    public static final ForgeConfigSpec.BooleanValue TRACKER_COMPASS;
    public static final ForgeConfigSpec.IntValue TRACKER_UPDATE_SECONDS;
    public static final ForgeConfigSpec.IntValue KILL_BONUS_CE;
    public static final ForgeConfigSpec.BooleanValue KILL_BONUS_CE_OVER_MAX;
    public static final ForgeConfigSpec.IntValue KILL_ZONE_SECONDS;
    public static final ForgeConfigSpec.IntValue KILL_ZONE_LEVEL;
    public static final ForgeConfigSpec.ConfigValue<String> KILL_ZONE_EFFECT;
    public static final ForgeConfigSpec.BooleanValue KILL_BONUS_WEAK_KILLS;
    public static final ForgeConfigSpec.IntValue REVEAL_TOP_COUNT;
    public static final ForgeConfigSpec.IntValue REVEAL_INTERVAL_MINUTES;
    public static final ForgeConfigSpec.BooleanValue HUNTED_ENABLED;
    public static final ForgeConfigSpec.IntValue HUNTED_GLOW_SECONDS;
    public static final ForgeConfigSpec.IntValue HUNTED_INTERVAL_MINUTES;
    public static final ForgeConfigSpec.IntValue ASSIST_SECONDS;
    public static final ForgeConfigSpec.BooleanValue RECOVERY_ENABLED;
    public static final ForgeConfigSpec.IntValue HEAL_POINTS_PER_HEART;
    public static final ForgeConfigSpec.IntValue CE_PER_POINT;
    public static final ForgeConfigSpec.IntValue FATIGUE_COST_PERCENT;
    public static final ForgeConfigSpec.IntValue BATTLE_SECONDS;
    public static final ForgeConfigSpec.IntValue RECOVERIES_PER_BATTLE;
    public static final ForgeConfigSpec.IntValue RECOVERY_COOLDOWN_SECONDS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("event");
        ALLOW_LATE_JOIN = b.comment("Players who log in while the event runs are added as participants.")
                .define("allowLateJoin", true);
        TELEPORT_TO_SPAWN_ON_END = b.comment("Send participants to world spawn when the event ends.")
                .define("teleportToSpawnOnEnd", true);
        b.pop();

        b.push("penalty");
        TIMER_MINUTES = b.comment("Minutes a participant may go without a point change before the penalty applies.")
                .defineInRange("timerMinutes", 19, 1, 24 * 60);
        FIRST_WARNING_MINUTES = b.comment("Kogane warns when this many minutes are left on the timer.")
                .defineInRange("firstWarningMinutes", 5, 0, 24 * 60);
        FINAL_WARNING_MINUTES = b.comment("Kogane gives a final warning when this many minutes are left.")
                .defineInRange("finalWarningMinutes", 1, 0, 24 * 60);
        DEFAULT_PENALTY = b.comment("Penalty used when /cg start does not name one.")
                .defineEnum("defaultPenalty", PenaltyMode.SEAL_CT);
        EVENT_LIVES = b.comment("Event lives per participant. Event deaths never cost real server lives.")
                .defineInRange("eventLives", 3, 1, 100);
        b.pop();

        b.push("points");
        PLAYER_KILL_POINTS = b.comment("Points Kogane gives for killing another participant.")
                .defineInRange("playerKill", 5, 0, 1000);
        WEAK_KILL_POINTS = b.comment("Points for killing a weaker target.")
                .defineInRange("weakKill", 1, 0, 1000);
        KILL_STEAL_MODE = b.comment("On a participant kill the killer also takes points from the victim:",
                        "FLAT = killStealFlat points, PERCENT = killStealPercent % of the victim's points, NONE = nothing.",
                        "The victim loses what is taken (never below 0).")
                .defineEnum("killStealMode", KillSteal.FLAT);
        KILL_STEAL_FLAT = b.comment("Points taken from the victim in FLAT mode.")
                .defineInRange("killStealFlat", 5, 0, 100000);
        KILL_STEAL_PERCENT = b.comment("Percent of the victim's points taken in PERCENT mode.")
                .defineInRange("killStealPercent", 25, 0, 100);
        RULE_COST = b.comment("Points needed to add a rule.")
                .defineInRange("ruleCost", 100, 0, 100000);
        WEAK_TARGETS_ALL_HOSTILE = b.comment("Every hostile mob (zombies, cursed spirits that count as monsters) is a weak target.")
                .define("weakTargetsAllHostile", true);
        WEAK_TARGETS_EXTRA = b.comment("Extra entity ids that count as weak targets, e.g. \"jujutsucraft:some_entity\".")
                .defineListAllowEmpty("weakTargetsExtra", List.of(), o -> o instanceof String);
        b.pop();

        b.push("shop");
        LEADERBOARD_PRICE = b.comment("Points to unlock the leaderboard for the rest of the event.")
                .defineInRange("leaderboardPrice", 100, 0, 100000);
        TRANSFER_PRICE = b.comment("Points to unlock point transfers for the rest of the event.")
                .defineInRange("transferPrice", 100, 0, 100000);
        KILLLOG_PRICE = b.comment("Points to unlock the kill log for the rest of the event.")
                .defineInRange("killLogPrice", 100, 0, 100000);
        LONGER_TIMER_MINUTES = b.comment("Extra timer minutes from the longer_timer rule.")
                .defineInRange("longerTimerMinutes", 10, 1, 24 * 60);
        MONEY_ITEM = b.comment("Item Kogane pays out when points are cashed out (Lightman's Currency coin). Empty disables cash-out.")
                .define("moneyItem", "lightmanscurrency:coin_copper");
        POINTS_PER_COIN = b.comment("Points per moneyItem when cashing out. Kept high so coins don't replace the rewards.")
                .defineInRange("pointsPerCoin", 5, 1, 100000);
        MAX_COINS_PER_EVENT = b.comment("Coins one player may cash out per event (during it and in the reward shop after it). 0 = no limit.")
                .defineInRange("maxCoinsPerEvent", 10, 0, 1000000);
        b.pop();

        b.push("rewards");
        REWARD_SHOP_OPEN = b.comment("Kogane's reward shop is open after an event ends, until the next event starts.",
                        "Players spend the points they had left. Set false to close it.")
                .define("rewardShopOpen", true);
        MAX_FRAGMENTS_PER_EVENT = b.comment("Weapon fragments one player may buy from the rewards of one event (all weapons together).")
                .defineInRange("maxFragmentsPerEvent", 2, 0, 1000);
        SPECIAL_GRADE_PRICE = b.comment("Price of the default Special Grade promotion (unless changed with /cg reward set).")
                .defineInRange("specialGradePrice", 220, 0, 1000000);
        FRAGMENT_PRICE = b.comment("Price of each default weapon fragment (unless changed with /cg reward set).")
                .defineInRange("fragmentPrice", 60, 0, 1000000);
        b.pop();

        // Red's rules from 20 Oct 2026 (0.13.0).
        b.push("tracker");
        TRACKER_COMPASS = b.comment("Every participant gets Kogane's tracker: a compass that points to the nearest other participant.")
                .define("trackerCompass", true);
        TRACKER_UPDATE_SECONDS = b.comment("Seconds between tracker updates.")
                .defineInRange("trackerUpdateSeconds", 2, 1, 60);
        b.pop();

        b.push("killBonus");
        KILL_BONUS_CE = b.comment("Cursed energy the killer gets for killing another participant. 0 = off.")
                .defineInRange("killBonusCe", 500, 0, 1000000);
        KILL_BONUS_CE_OVER_MAX = b.comment("The cursed energy bonus may go above the killer's max. If false it stops at the max.")
                .define("killBonusCeOverMax", false);
        KILL_ZONE_SECONDS = b.comment("Seconds of the zone effect the killer gets. 0 = off.")
                .defineInRange("killZoneSeconds", 45, 0, 3600);
        KILL_ZONE_LEVEL = b.comment("Level of the zone effect (1 = level I).")
                .defineInRange("killZoneLevel", 1, 1, 10);
        KILL_ZONE_EFFECT = b.comment("Effect given as \"zone\". Jujutsu Craft's Zone (the Black Flash state) is jujutsucraft:zone.")
                .define("killZoneEffect", "jujutsucraft:zone");
        KILL_BONUS_WEAK_KILLS = b.comment("Weak-target kills (mobs) also give the cursed energy and zone bonus.")
                .define("killBonusWeakKills", false);
        b.pop();

        b.push("reveal");
        REVEAL_TOP_COUNT = b.comment("How many top players by points have their colony announced to everyone.")
                .defineInRange("revealTopCount", 3, 1, 50);
        REVEAL_INTERVAL_MINUTES = b.comment("Minutes of event time between announcements. 0 = off.")
                .defineInRange("revealIntervalMinutes", 10, 0, 24 * 60);
        b.pop();

        b.push("hunted");
        HUNTED_ENABLED = b.comment("Participants with 0 points are hunted: they glow until they get a kill or an assist.")
                .define("huntedEnabled", true);
        HUNTED_GLOW_SECONDS = b.comment("Seconds a hunted player glows each time.")
                .defineInRange("huntedGlowSeconds", 30, 1, 3600);
        HUNTED_INTERVAL_MINUTES = b.comment("Minutes of event time between glows. 20 = one Minecraft day.")
                .defineInRange("huntedIntervalMinutes", 20, 1, 24 * 60);
        ASSIST_SECONDS = b.comment("Damage to a participant this many seconds before someone else kills them counts as an assist.")
                .defineInRange("assistSeconds", 15, 1, 600);
        b.pop();

        b.push("recovery");
        RECOVERY_ENABLED = b.comment("Participants can buy a heal or a cursed energy refill with points in the event shop.")
                .define("recoveryEnabled", true);
        HEAL_POINTS_PER_HEART = b.comment("Points per missing heart (2 health) for a full heal.")
                .defineInRange("healPointsPerHeart", 1, 0, 100000);
        CE_PER_POINT = b.comment("Missing cursed energy refilled per point for a full refill.")
                .defineInRange("cePerPoint", 200, 1, 1000000);
        FATIGUE_COST_PERCENT = b.comment("Extra cost in percent per level of Jujutsu Craft fatigue.")
                .defineInRange("fatigueCostPercent", 25, 0, 10000);
        BATTLE_SECONDS = b.comment("A player is in battle while they hit or were hit by another participant within this many seconds.")
                .defineInRange("battleSeconds", 15, 1, 600);
        RECOVERIES_PER_BATTLE = b.comment("Heals plus refills one player may buy during one battle.")
                .defineInRange("recoveriesPerBattle", 1, 0, 100);
        RECOVERY_COOLDOWN_SECONDS = b.comment("Seconds between recoveries outside battle. 0 = no wait.")
                .defineInRange("recoveryCooldownSeconds", 10, 0, 3600);
        b.pop();

        SPEC = b.build();
    }

    private CgConfig() {}
}
