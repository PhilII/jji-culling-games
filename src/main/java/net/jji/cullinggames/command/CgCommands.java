package net.jji.cullinggames.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.time.Duration;
import com.mojang.authlib.GameProfile;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import net.jji.cullinggames.colony.Colony;
import net.jji.cullinggames.colony.ColonyService;
import net.jji.cullinggames.colony.ColonyStore;
import net.jji.cullinggames.config.CgConfig;
import net.jji.cullinggames.event.EventManager;
import net.jji.cullinggames.event.EventState;
import net.jji.cullinggames.event.KillRecord;
import net.jji.cullinggames.event.Kogane;
import net.jji.cullinggames.event.Points;
import net.jji.cullinggames.event.PenaltyMode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.coordinates.ColumnPosArgument;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** /cg commands. Admin subcommands need permission level 2; "/cg points" alone is for every player. */
public final class CgCommands {
    private CgCommands() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("cg")
                .then(admin("start")
                        .executes(c -> start(c, CgConfig.DEFAULT_PENALTY.get()))
                        .then(Commands.argument("penalty", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(PenaltyMode.values()).map(m -> m.name().toLowerCase()), b))
                                .executes(c -> start(c, parsePenalty(c)))))
                .then(admin("stop").executes(c -> report(c, EventManager.stop(server(c)), "Event stopped.")))
                .then(admin("pause").executes(c -> report(c, EventManager.pause(server(c)), "Event paused.")))
                .then(admin("resume").executes(c -> report(c, EventManager.resume(server(c)), "Event resumed.")))
                .then(admin("status").executes(CgCommands::status))
                .then(colonyCommands())
                .then(admin("participant")
                        .then(Commands.literal("add").then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> participant(c, true))))
                        .then(Commands.literal("remove").then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> participant(c, false)))))
                .then(pointsCommands())
                .then(admin("killlog")
                        .executes(c -> killLog(c, 10))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                                .executes(c -> killLog(c, IntegerArgumentType.getInteger(c, "count"))))));
    }

    /** Admin-only literal (permission level 2). */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> admin(String name) {
        return Commands.literal(name).requires(s -> s.hasPermission(2));
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
            case NO_COLONIES -> "No colonies set up. Add one with /cg colony add first.";
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

    // --- colonies ---

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> colonyCommands() {
        return admin("colony")
                .then(Commands.literal("add").then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.literal("circle").then(Commands.argument("center", ColumnPosArgument.columnPos())
                                .then(Commands.argument("radius", IntegerArgumentType.integer(3, 5000))
                                        .executes(CgCommands::addCircle))))
                        .then(Commands.literal("box").then(Commands.argument("from", ColumnPosArgument.columnPos())
                                .then(Commands.argument("to", ColumnPosArgument.columnPos())
                                        .executes(CgCommands::addBox))))))
                .then(Commands.literal("remove").then(colonyName().executes(CgCommands::removeColony)))
                .then(Commands.literal("list").executes(CgCommands::listColonies))
                .then(Commands.literal("tp").then(colonyName().executes(CgCommands::tpColony)));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> colonyName() {
        return Commands.argument("name", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                        ColonyStore.get(c.getSource().getServer()).all().stream().map(Colony::name), b));
    }

    private static int addCircle(CommandContext<CommandSourceStack> c) {
        ColumnPos center = ColumnPosArgument.getColumnPos(c, "center");
        Colony colony = Colony.circle(StringArgumentType.getString(c, "name"), c.getSource().getLevel().dimension(),
                center.x(), center.z(), IntegerArgumentType.getInteger(c, "radius"));
        return addColony(c, colony);
    }

    private static int addBox(CommandContext<CommandSourceStack> c) {
        ColumnPos from = ColumnPosArgument.getColumnPos(c, "from");
        ColumnPos to = ColumnPosArgument.getColumnPos(c, "to");
        if (Math.abs(from.x() - to.x()) < 4 || Math.abs(from.z() - to.z()) < 4) {
            c.getSource().sendFailure(Component.literal("A box colony must be at least 5x5 blocks."));
            return 0;
        }
        Colony colony = Colony.box(StringArgumentType.getString(c, "name"), c.getSource().getLevel().dimension(),
                from.x(), from.z(), to.x(), to.z());
        return addColony(c, colony);
    }

    private static int addColony(CommandContext<CommandSourceStack> c, Colony colony) {
        if (!ColonyStore.get(server(c)).add(colony)) {
            c.getSource().sendFailure(Component.literal("A colony named " + colony.name() + " already exists."));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal("Added colony " + colony.describe() + "."), true);
        return 1;
    }

    private static int removeColony(CommandContext<CommandSourceStack> c) {
        String name = StringArgumentType.getString(c, "name");
        if (EventState.get(server(c)).phase().isActive()) {
            c.getSource().sendFailure(Component.literal("Stop the event before removing colonies."));
            return 0;
        }
        if (!ColonyStore.get(server(c)).remove(name)) {
            c.getSource().sendFailure(Component.literal("No colony named " + name + "."));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal("Removed colony " + name + "."), true);
        return 1;
    }

    private static int listColonies(CommandContext<CommandSourceStack> c) {
        ColonyStore store = ColonyStore.get(server(c));
        if (store.isEmpty()) {
            c.getSource().sendSuccess(() -> Component.literal("No colonies set up."), false);
            return 0;
        }
        EventState state = EventState.get(server(c));
        StringBuilder sb = new StringBuilder("Colonies:");
        for (Colony colony : store.all()) {
            sb.append("\n - ").append(colony.describe());
            if (state.phase().isActive()) {
                sb.append(" | ").append(state.countIn(colony.name())).append(" players");
            }
        }
        String text = sb.toString();
        c.getSource().sendSuccess(() -> Component.literal(text), false);
        return store.all().size();
    }

    private static int tpColony(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String name = StringArgumentType.getString(c, "name");
        Colony colony = ColonyStore.get(server(c)).get(name);
        if (colony == null) {
            c.getSource().sendFailure(Component.literal("No colony named " + name + "."));
            return 0;
        }
        ServerPlayer player = c.getSource().getPlayerOrException();
        ColonyService.teleportRandom(server(c), player, colony);
        return 1;
    }

    // --- points and kill log ---

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> pointsCommands() {
        return Commands.literal("points")
                .executes(CgCommands::ownPoints)
                .then(admin("get").then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .executes(c -> changePoints(c, "get", 0))))
                .then(pointChange("set", 0))
                .then(pointChange("add", 1))
                .then(pointChange("remove", 1));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> pointChange(String op, int min) {
        return admin(op).then(Commands.argument("player", GameProfileArgument.gameProfile())
                .then(Commands.argument("amount", IntegerArgumentType.integer(min, 1_000_000))
                        .executes(c -> changePoints(c, op, IntegerArgumentType.getInteger(c, "amount")))));
    }

    private static int ownPoints(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        EventState state = EventState.get(server(c));
        if (!state.isParticipant(player.getUUID())) {
            c.getSource().sendFailure(Component.literal("You are not in the Culling Games."));
            return 0;
        }
        int pts = state.points(player.getUUID());
        c.getSource().sendSuccess(() -> Kogane.line("You have " + pts + " points."), false);
        return pts;
    }

    private static int changePoints(CommandContext<CommandSourceStack> c, String op, int amount) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(c, "player");
        if (profiles.size() != 1) {
            c.getSource().sendFailure(Component.literal("Name exactly one player."));
            return 0;
        }
        GameProfile profile = profiles.iterator().next();
        MinecraftServer server = server(c);
        int total = switch (op) {
            case "set" -> Points.set(server, profile.getId(), amount);
            case "add" -> Points.add(server, profile.getId(), amount);
            case "remove" -> Points.add(server, profile.getId(), -amount);
            default -> EventState.get(server).points(profile.getId());
        };
        String text = profile.getName() + " has " + total + " points.";
        c.getSource().sendSuccess(() -> Component.literal(text), !"get".equals(op));
        return total;
    }

    private static int killLog(CommandContext<CommandSourceStack> c, int count) {
        List<KillRecord> kills = EventState.get(server(c)).kills();
        if (kills.isEmpty()) {
            c.getSource().sendSuccess(() -> Component.literal("No kills logged."), false);
            return 0;
        }
        SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss");
        StringBuilder sb = new StringBuilder("Kill log (" + kills.size() + " total, newest last):");
        for (KillRecord k : kills.subList(Math.max(0, kills.size() - count), kills.size())) {
            sb.append("\n ").append(fmt.format(new Date(k.epochMillis())))
              .append(" ").append(k.killerName()).append(" killed ").append(k.victimName())
              .append(" at ").append(k.x()).append(" ").append(k.y()).append(" ").append(k.z())
              .append(" (").append(k.dimension()).append(") +").append(k.points());
        }
        String text = sb.toString();
        c.getSource().sendSuccess(() -> Component.literal(text), false);
        return kills.size();
    }
}
