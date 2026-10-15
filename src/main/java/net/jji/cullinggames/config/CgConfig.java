package net.jji.cullinggames.config;

import java.util.List;
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
    public static final ForgeConfigSpec.IntValue RULE_COST;
    public static final ForgeConfigSpec.BooleanValue WEAK_TARGETS_ALL_HOSTILE;
    public static final ForgeConfigSpec.IntValue LEADERBOARD_PRICE;
    public static final ForgeConfigSpec.IntValue TRANSFER_PRICE;
    public static final ForgeConfigSpec.IntValue KILLLOG_PRICE;
    public static final ForgeConfigSpec.IntValue LONGER_TIMER_MINUTES;
    public static final ForgeConfigSpec.ConfigValue<String> MONEY_ITEM;
    public static final ForgeConfigSpec.IntValue MONEY_PER_POINT;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> WEAK_TARGETS_EXTRA;

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
        PLAYER_KILL_POINTS = b.comment("Points for killing another participant.")
                .defineInRange("playerKill", 5, 0, 1000);
        WEAK_KILL_POINTS = b.comment("Points for killing a weaker target.")
                .defineInRange("weakKill", 1, 0, 1000);
        RULE_COST = b.comment("Points needed to add a rule.")
                .defineInRange("ruleCost", 100, 0, 100000);
        WEAK_TARGETS_ALL_HOSTILE = b.comment("Every hostile mob (zombies, cursed spirits that count as monsters) is a weak target.")
                .define("weakTargetsAllHostile", true);
        WEAK_TARGETS_EXTRA = b.comment("Extra entity ids that count as weak targets, e.g. \"jujutsucraft:some_entity\".")
                .defineListAllowEmpty("weakTargetsExtra", List.of(), o -> o instanceof String);
        b.pop();

        b.push("shop");
        LEADERBOARD_PRICE = b.comment("Points to unlock the leaderboard for the rest of the event.")
                .defineInRange("leaderboardPrice", 10, 0, 100000);
        TRANSFER_PRICE = b.comment("Points to unlock point transfers for the rest of the event.")
                .defineInRange("transferPrice", 10, 0, 100000);
        KILLLOG_PRICE = b.comment("Points to unlock the kill log for the rest of the event.")
                .defineInRange("killLogPrice", 20, 0, 100000);
        LONGER_TIMER_MINUTES = b.comment("Extra timer minutes from the longer_timer rule.")
                .defineInRange("longerTimerMinutes", 10, 1, 24 * 60);
        MONEY_ITEM = b.comment("Item Kogane pays out when points are cashed out (Lightman's Currency coin). Empty disables cash-out.")
                .define("moneyItem", "lightmanscurrency:coin_copper");
        MONEY_PER_POINT = b.comment("How many of moneyItem one point is worth.")
                .defineInRange("moneyPerPoint", 1, 1, 64);
        b.pop();

        SPEC = b.build();
    }

    private CgConfig() {}
}
