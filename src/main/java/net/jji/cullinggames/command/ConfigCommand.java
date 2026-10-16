package net.jji.cullinggames.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.jji.cullinggames.config.CgConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * /cg config [key] [value]: read or change server config values in game. Changes are validated against the
 * same ranges as the config file and saved to serverconfig/cullinggames-server.toml immediately.
 */
final class ConfigCommand {
    private ConfigCommand() {}

    /** Every value that can be changed in game, by its key in the config file. */
    static Map<String, ForgeConfigSpec.ConfigValue<?>> values() {
        Map<String, ForgeConfigSpec.ConfigValue<?>> m = new LinkedHashMap<>();
        for (ForgeConfigSpec.ConfigValue<?> v : new ForgeConfigSpec.ConfigValue<?>[] {
                CgConfig.ALLOW_LATE_JOIN, CgConfig.TELEPORT_TO_SPAWN_ON_END,
                CgConfig.TIMER_MINUTES, CgConfig.FIRST_WARNING_MINUTES, CgConfig.FINAL_WARNING_MINUTES,
                CgConfig.DEFAULT_PENALTY, CgConfig.EVENT_LIVES,
                CgConfig.PLAYER_KILL_POINTS, CgConfig.WEAK_KILL_POINTS,
                CgConfig.KILL_STEAL_MODE, CgConfig.KILL_STEAL_FLAT, CgConfig.KILL_STEAL_PERCENT,
                CgConfig.RULE_COST, CgConfig.WEAK_TARGETS_ALL_HOSTILE,
                CgConfig.LEADERBOARD_PRICE, CgConfig.TRANSFER_PRICE, CgConfig.KILLLOG_PRICE,
                CgConfig.LONGER_TIMER_MINUTES, CgConfig.MONEY_ITEM, CgConfig.POINTS_PER_COIN,
                CgConfig.MAX_COINS_PER_EVENT, CgConfig.REWARD_SHOP_OPEN, CgConfig.MAX_FRAGMENTS_PER_EVENT,
                CgConfig.SPECIAL_GRADE_PRICE, CgConfig.FRAGMENT_PRICE}) {
            m.put(v.getPath().get(v.getPath().size() - 1), v);
        }
        return m;
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("config").requires(s -> s.hasPermission(2))
                .executes(ConfigCommand::list)
                .then(Commands.argument("key", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(values().keySet(), b))
                        .executes(ConfigCommand::show)
                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                .executes(ConfigCommand::set)));
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        StringBuilder sb = new StringBuilder("Culling Games config (change with /cg config <key> <value>):");
        values().forEach((k, v) -> sb.append("\n ").append(k).append(" = ").append(v.get()));
        String text = sb.toString();
        c.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int show(CommandContext<CommandSourceStack> c) {
        String key = StringArgumentType.getString(c, "key");
        ForgeConfigSpec.ConfigValue<?> v = values().get(key);
        if (v == null) {
            c.getSource().sendFailure(Component.literal("Unknown key " + key + ". See /cg config."));
            return 0;
        }
        ForgeConfigSpec.ValueSpec spec = CgConfig.SPEC.getSpec().get(v.getPath());
        String comment = spec.getComment() == null ? "" : "\n " + spec.getComment().replace('\n', ' ');
        String text = key + " = " + v.get() + comment;
        c.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int set(CommandContext<CommandSourceStack> c) {
        String key = StringArgumentType.getString(c, "key");
        String raw = StringArgumentType.getString(c, "value").trim();
        ForgeConfigSpec.ConfigValue v = values().get(key);
        if (v == null) {
            c.getSource().sendFailure(Component.literal("Unknown key " + key + ". See /cg config."));
            return 0;
        }
        Object parsed = parse(v.get(), raw);
        ForgeConfigSpec.ValueSpec spec = CgConfig.SPEC.getSpec().get((java.util.List<String>) v.getPath());
        if (parsed == null || !spec.test(parsed)) {
            c.getSource().sendFailure(Component.literal("Invalid value for " + key + ": " + raw + ". Current: " + v.get() + "."));
            return 0;
        }
        Object old = v.get();
        v.set(parsed);
        CgConfig.SPEC.save();
        c.getSource().sendSuccess(() -> Component.literal(key + ": " + old + " -> " + parsed), true);
        return 1;
    }

    /** Parses the raw text to the type of the current value, or null if it doesn't fit. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static Object parse(Object current, String raw) {
        try {
            if (current instanceof Integer) {
                return Integer.parseInt(raw);
            }
            if (current instanceof Boolean) {
                if (raw.equalsIgnoreCase("true") || raw.equalsIgnoreCase("false")) {
                    return Boolean.parseBoolean(raw);
                }
                return null;
            }
            if (current instanceof Enum<?> e) {
                return Enum.valueOf((Class) e.getDeclaringClass(), raw.toUpperCase(Locale.ROOT));
            }
            if (current instanceof String) {
                return raw;
            }
        } catch (IllegalArgumentException ignored) {
            // NumberFormatException and unknown enum names both land here
        }
        return null;
    }
}
