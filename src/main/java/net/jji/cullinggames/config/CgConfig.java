package net.jji.cullinggames.config;

import net.jji.cullinggames.event.PenaltyMode;
import net.minecraftforge.common.ForgeConfigSpec;

/** Server config (serverconfig/cullinggames-server.toml). All event tunables live here. */
public final class CgConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ALLOW_LATE_JOIN;
    public static final ForgeConfigSpec.IntValue TIMER_MINUTES;
    public static final ForgeConfigSpec.IntValue FIRST_WARNING_MINUTES;
    public static final ForgeConfigSpec.IntValue FINAL_WARNING_MINUTES;
    public static final ForgeConfigSpec.EnumValue<PenaltyMode> DEFAULT_PENALTY;
    public static final ForgeConfigSpec.IntValue EVENT_LIVES;
    public static final ForgeConfigSpec.IntValue PLAYER_KILL_POINTS;
    public static final ForgeConfigSpec.IntValue WEAK_KILL_POINTS;
    public static final ForgeConfigSpec.IntValue RULE_COST;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("event");
        ALLOW_LATE_JOIN = b.comment("Players who log in while the event runs are added as participants.")
                .define("allowLateJoin", true);
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
        b.pop();

        SPEC = b.build();
    }

    private CgConfig() {}
}
