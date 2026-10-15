package net.jji.cullinggames.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.time.Duration;
import java.util.Arrays;
import net.jji.cullinggames.config.CgConfig;
import net.jji.cullinggames.event.EventManager;
import net.jji.cullinggames.event.EventState;
import net.jji.cullinggames.event.PenaltyMode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** /cg admin commands (permission level 2). */
public final class CgCommands {
    private CgCommands() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("cg")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("start")
                        .executes(c -> start(c, CgConfig.DEFAULT_PENALTY.get()))
                        .then(Commands.argument("penalty", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(PenaltyMode.values()).map(m -> m.name().toLowerCase()), b))
                                .executes(c -> start(c, parsePenalty(c)))))
                .then(Commands.literal("stop").executes(c -> report(c, EventManager.stop(server(c)), "Event stopped.")))
                .then(Commands.literal("pause").executes(c -> report(c, EventManager.pause(server(c)), "Event paused.")))
                .then(Commands.literal("resume").executes(c -> report(c, EventManager.resume(server(c)), "Event resumed.")))
                .then(Commands.literal("status").executes(CgCommands::status))
                .then(Commands.literal("participant")
                        .then(Commands.literal("add").then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> participant(c, true))))
                        .then(Commands.literal("remove").then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> participant(c, false))))));
    }

    private static MinecraftServer server(CommandContext<CommandSourceStack> c) {
        return c.getSource().getServer();
    }

    private static PenaltyMode parsePenalty(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String raw = StringArgumentType.getString(c, "penalty");
        try {
            return PenaltyMode.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new SimpleCommandExceptionType(
                    Component.literal("Unknown penalty: " + raw + " (use seal_ct or lose_life)")).create();
        }
    }

    private static int start(CommandContext<CommandSourceStack> c, PenaltyMode penalty) {
        return report(c, EventManager.start(server(c), penalty), "Event started, penalty " + penalty.name().toLowerCase() + ".");
    }

    private static int report(CommandContext<CommandSourceStack> c, EventManager.Result result, String okText) {
        if (result == EventManager.Result.OK) {
            c.getSource().sendSuccess(() -> Component.literal(okText), true);
            return 1;
        }
        String why = switch (result) {
            case ALREADY_RUNNING -> "An event is already active. Stop it first.";
            case NOT_ACTIVE -> "No event is active.";
            case NOT_RUNNING -> "The event is not running.";
            case NOT_PAUSED -> "The event is not paused.";
            default -> "Failed.";
        };
        c.getSource().sendFailure(Component.literal(why));
        return 0;
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        EventState s = EventState.get(server(c));
        StringBuilder sb = new StringBuilder("Culling Games: ").append(s.phase().name().toLowerCase());
        if (s.phase().isActive()) {
            Duration t = Duration.ofSeconds(s.runningTicks() / 20);
            sb.append(" | event #").append(s.eventNumber())
              .append(" | penalty ").append(s.penalty().name().toLowerCase())
              .append(" | participants ").append(s.participants().size())
              .append(" | running time ").append(String.format("%d:%02d:%02d", t.toHours(), t.toMinutesPart(), t.toSecondsPart()));
        }
        String text = sb.toString();
        c.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int participant(CommandContext<CommandSourceStack> c, boolean add) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(c, "player");
        boolean changed = add ? EventManager.addParticipant(server(c), player) : EventManager.removeParticipant(server(c), player);
        String name = player.getGameProfile().getName();
        if (changed) {
            c.getSource().sendSuccess(() -> Component.literal((add ? "Added " : "Removed ") + name + "."), true);
            return 1;
        }
        c.getSource().sendFailure(Component.literal(add ? "No active event, or " + name + " is already in it." : name + " is not a participant."));
        return 0;
    }
}
