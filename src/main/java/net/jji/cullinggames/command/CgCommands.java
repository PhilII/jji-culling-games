package net.jji.cullinggames.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;
import net.jji.cullinggames.colony.Colony;
import net.jji.cullinggames.colony.ColonyService;
import net.jji.cullinggames.colony.ColonyStore;
import net.jji.cullinggames.compat.JujutsuCraft;
import net.jji.cullinggames.config.CgConfig;
import net.jji.cullinggames.event.CanonRules;
import net.jji.cullinggames.event.EventManager;
import net.jji.cullinggames.event.EventRule;
import net.jji.cullinggames.event.EventState;
import net.jji.cullinggames.event.KillRecord;
import net.jji.cullinggames.event.Kogane;
import net.jji.cullinggames.event.PenaltyMode;
import net.jji.cullinggames.event.Points;
import net.jji.cullinggames.event.Recovery;
import net.jji.cullinggames.event.Shop;
import net.jji.cullinggames.event.Views;
import net.jji.cullinggames.network.CgNetwork;
import net.jji.cullinggames.shop.ShopStore;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.coordinates.ColumnPosArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
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
                .executes(CgCommands::help)
                .then(Commands.literal("help").executes(CgCommands::help))
                .then(admin("start")
                        .executes(c -> start(c, CgConfig.DEFAULT_PENALTY.get(), CgConfig.EVENT_LIVES.get()))
                        .then(Commands.argument("penalty", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(PenaltyMode.values()).map(m -> m.name().toLowerCase()), b))
                                .executes(c -> start(c, parsePenalty(c), CgConfig.EVENT_LIVES.get()))
                                .then(Commands.argument("lives", IntegerArgumentType.integer(1, 100))
                                        .executes(c -> start(c, parsePenalty(c), IntegerArgumentType.getInteger(c, "lives"))))))
                .then(admin("stop").executes(c -> report(c, EventManager.stop(server(c)), "Event stopped."))
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 24 * 60))
                                .executes(c -> report(c, EventManager.stopIn(server(c), IntegerArgumentType.getInteger(c, "minutes")),
                                        "Event will end in " + minutes(IntegerArgumentType.getInteger(c, "minutes")) + "."))))
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
                .then(ConfigCommand.build())
                .then(RewardCommand.rewards())
                .then(RewardCommand.reward())
                .then(Commands.literal("killlog")
                        .executes(c -> killLog(c, 10))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                                .executes(c -> killLog(c, IntegerArgumentType.getInteger(c, "count")))))
                .then(shopCommands())
                .then(Commands.literal("buy").then(Commands.argument("what", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(buyIds(c.getSource().getServer()), b))
                        .executes(CgCommands::buy)))
                .then(Commands.literal("rules").executes(CgCommands::listRules))
                .then(Commands.literal("addrule").then(Commands.argument("rule", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(EventRule.values()).map(EventRule::id), b))
                        .executes(CgCommands::addRule)))
                .then(Commands.literal("transfer").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 1_000_000))
                                .executes(CgCommands::transfer))))
                .then(Commands.literal("leaderboard").executes(CgCommands::leaderboard))
                .then(Commands.literal("cashout").then(Commands.argument("coins", IntegerArgumentType.integer(1, 1_000_000))
                        .executes(c -> shopResult(c, Shop.cashOut(server(c), c.getSource().getPlayerOrException(),
                                IntegerArgumentType.getInteger(c, "coins")))))));
    }

    private static String minutes(int n) {
        return n + (n == 1 ? " minute" : " minutes");
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

    private static int start(CommandContext<CommandSourceStack> c, PenaltyMode penalty, int lives) {
        return report(c, EventManager.start(server(c), penalty, lives),
                "Event started, penalty " + penalty.name().toLowerCase() + ", " + lives + " lives.");
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
              .append(" | lives ").append(s.startingLives())
              .append(" | sealed ").append(s.sealedPlayers().size())
              .append(" | out ").append(s.eliminatedPlayers().size())
              .append(" | running time ").append(String.format("%d:%02d:%02d", t.toHours(), t.toMinutesPart(), t.toSecondsPart()));
            if (s.endAtTicks() >= 0) {
                long left = Math.max(0, s.endAtTicks() - s.runningTicks()) / 20;
                sb.append(" | ends in ").append(String.format("%d:%02d", left / 60, left % 60));
            }
            if (!s.rules().isEmpty()) {
                sb.append(" | rules ").append(String.join(", ", s.rules()));
            }
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

    /** Admins always; players only after buying killlog this event. */
    private static int killLog(CommandContext<CommandSourceStack> c, int count) throws CommandSyntaxException {
        EventState state = EventState.get(server(c));
        if (!c.getSource().hasPermission(2)) {
            ServerPlayer player = c.getSource().getPlayerOrException();
            if (!Shop.hasAccess(state, player.getUUID(), Shop.Service.KILLLOG)) {
                c.getSource().sendFailure(Component.literal("Buy the kill log first: /cg buy killlog ("
                        + Shop.Service.KILLLOG.price() + " points)."));
                return 0;
            }
        }
        List<KillRecord> kills = state.kills();
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

    // --- shop, rules, transfers ---

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> shopCommands() {
        return Commands.literal("shop")
                .executes(CgCommands::showShop)
                .then(admin("add").then(Commands.argument("price", IntegerArgumentType.integer(0, 1_000_000))
                        .executes(CgCommands::shopAdd)))
                .then(admin("remove").then(Commands.argument("id", IntegerArgumentType.integer(1))
                        .executes(CgCommands::shopRemove)));
    }

    private static Stream<String> buyIds(MinecraftServer server) {
        int items = ShopStore.get(server).all().size();
        return Stream.of(Arrays.stream(Shop.Service.values()).map(Shop.Service::id),
                Arrays.stream(Recovery.values()).map(Recovery::id),
                java.util.stream.IntStream.rangeClosed(1, items).mapToObj(Integer::toString)).flatMap(x -> x);
    }

    private static MutableComponent buyLine(String label, int price, String buyId) {
        return Component.literal("\n [" + price + " pts] " + label)
                .withStyle(s -> s.withColor(ChatFormatting.AQUA)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cg buy " + buyId))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to buy"))));
    }

    private static int showShop(CommandContext<CommandSourceStack> c) {
        MinecraftServer server = server(c);
        if (c.getSource().getEntity() instanceof ServerPlayer player && CgNetwork.hasMod(player)) {
            CgNetwork.send(player, Views.shop(server, player, ""));
            return 1;
        }
        MutableComponent msg = Kogane.line("Shop. Click to buy.").copy();
        msg.append(buyLine("Leaderboard (rest of event)", Shop.Service.LEADERBOARD.price(), "leaderboard"));
        msg.append(buyLine("Point transfers (rest of event)", Shop.Service.TRANSFER.price(), "transfer"));
        msg.append(buyLine("Kill log (rest of event)", Shop.Service.KILLLOG.price(), "killlog"));
        if (CgConfig.RECOVERY_ENABLED.get() && c.getSource().getEntity() instanceof ServerPlayer player) {
            msg.append(buyLine("Full heal (once per battle)", Recovery.HEAL.price(player), "heal"));
            if (JujutsuCraft.isLoaded()) {
                msg.append(buyLine("Full cursed energy (once per battle)", Recovery.CE.price(player), "ce"));
            }
        }
        List<ShopStore.Entry> items = ShopStore.get(server).all();
        for (int i = 0; i < items.size(); i++) {
            ItemStack s = items.get(i).stack();
            msg.append(buyLine("#" + (i + 1) + " " + s.getCount() + "x " + s.getHoverName().getString(), items.get(i).price(), Integer.toString(i + 1)));
        }
        msg.append(Component.literal("\n Add a rule: /cg rules (" + CgConfig.RULE_COST.get() + " pts). Cash out: /cg cashout <coins> (" + CgConfig.POINTS_PER_COIN.get() + " pts each).")
                .withStyle(ChatFormatting.GRAY));
        c.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    private static int shopAdd(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            c.getSource().sendFailure(Component.literal("Hold the item (and stack size) you want to sell."));
            return 0;
        }
        int price = IntegerArgumentType.getInteger(c, "price");
        int id = ShopStore.get(server(c)).add(held, price);
        String name = held.getCount() + "x " + held.getHoverName().getString();
        c.getSource().sendSuccess(() -> Component.literal("Added shop item #" + id + ": " + name + " for " + price + " points."), true);
        return id;
    }

    private static int shopRemove(CommandContext<CommandSourceStack> c) {
        int id = IntegerArgumentType.getInteger(c, "id");
        if (!ShopStore.get(server(c)).remove(id)) {
            c.getSource().sendFailure(Component.literal("No shop item #" + id + "."));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal("Removed shop item #" + id + ". Later items moved up one number."), true);
        return 1;
    }

    private static int buy(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        String what = StringArgumentType.getString(c, "what");
        Shop.Service service = Shop.Service.byId(what);
        Recovery recovery = Recovery.byId(what);
        if (recovery != null) {
            return shopResult(c, Shop.buyRecovery(server(c), player, recovery));
        }
        if (service != null) {
            return shopResult(c, Shop.buyService(server(c), player, service));
        }
        try {
            return shopResult(c, Shop.buyItem(server(c), player, Integer.parseInt(what)));
        } catch (NumberFormatException e) {
            c.getSource().sendFailure(Component.literal("Unknown: " + what + ". See /cg shop."));
            return 0;
        }
    }

    private static int shopResult(CommandContext<CommandSourceStack> c, Shop.Result result) {
        if (result.ok()) {
            c.getSource().sendSuccess(() -> Kogane.line(result.message()), false);
            return 1;
        }
        c.getSource().sendFailure(Component.literal(result.message()));
        return 0;
    }

    private static int listRules(CommandContext<CommandSourceStack> c) {
        EventState state = EventState.get(server(c));
        MutableComponent msg = Kogane.line("The Culling Game rules:").copy();
        for (int i = 0; i < CanonRules.RULES.size(); i++) {
            msg.append(Component.literal("\n " + (i + 1) + ". " + CanonRules.RULES.get(i)).withStyle(ChatFormatting.GRAY));
        }
        msg.append(Component.literal("\n " + CanonRules.SERVER_NOTE).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        msg.append(Component.literal("\n New rules cost " + CgConfig.RULE_COST.get() + " points and continue from 9. Click to add:")
                .withStyle(ChatFormatting.YELLOW));
        for (EventRule r : EventRule.values()) {
            boolean active = state.hasRule(r.id());
            msg.append(Component.literal("\n " + (active ? "[in play] " : "") + r.id() + ": " + r.description())
                    .withStyle(s -> active ? s.withColor(ChatFormatting.GRAY)
                            : s.withColor(ChatFormatting.AQUA).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cg addrule " + r.id()))));
        }
        c.getSource().sendSuccess(() -> msg, false);
        return state.rules().size();
    }

    private static int addRule(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        EventRule rule = EventRule.byId(StringArgumentType.getString(c, "rule"));
        if (rule == null) {
            c.getSource().sendFailure(Component.literal("Unknown rule. See /cg rules."));
            return 0;
        }
        return shopResult(c, Shop.addRule(server(c), c.getSource().getPlayerOrException(), rule));
    }

    private static int transfer(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer to = EntityArgument.getPlayer(c, "player");
        return shopResult(c, Shop.transfer(server(c), c.getSource().getPlayerOrException(), to.getUUID(),
                to.getGameProfile().getName(), IntegerArgumentType.getInteger(c, "amount")));
    }

    /** Admins always; players with the leaderboard unlocked or the reveal_points rule in play. */
    private static int leaderboard(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        EventState state = EventState.get(server(c));
        if (!c.getSource().hasPermission(2)) {
            ServerPlayer player = c.getSource().getPlayerOrException();
            if (!Shop.hasAccess(state, player.getUUID(), Shop.Service.LEADERBOARD)) {
                c.getSource().sendFailure(Component.literal("Buy the leaderboard first: /cg buy leaderboard ("
                        + Shop.Service.LEADERBOARD.price() + " points)."));
                return 0;
            }
        }
        String text = Shop.leaderboard(server(c), state, 10);
        c.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    // --- help ---

    private static final String[][] PLAYER_HELP = {
        {"/cg points", "your points"},
        {"/cg shop  (or K)", "Kogane's shop"},
        {"/cg buy <item|service>", "buy from the shop"},
        {"/cg buy heal, /cg buy ce", "full heal or cursed energy for points (once per battle)"},
        {"/cg rules, /cg addrule <rule>", "rules you can add"},
        {"/cg transfer <player> <amount>", "send points (buy transfer first)"},
        {"/cg leaderboard", "standings (buy it first)"},
        {"/cg killlog", "kill log (buy it first)"},
        {"/cg cashout <coins>", "points to coins"},
        {"/cg rewards", "Kogane's reward shop after the event"},
    };

    private static final String[][] ADMIN_HELP = {
        {"/cg colony add <name> circle <x z> <r>", "or box <x1 z1> <x2 z2>; remove, list, tp"},
        {"/cg start [seal_ct|lose_life] [lives]", "start the event"},
        {"/cg pause, resume, status", ""},
        {"/cg stop [minutes]", "end now, or after minutes with a warning"},
        {"/cg participant add|remove <player>", ""},
        {"/cg points get|set|add|remove <player> [n]", ""},
        {"/cg shop add <price>", "sell the item in your hand; /cg shop remove <id>"},
        {"/cg killlog [count]", "kill proof"},
        {"/cg config [key] [value]", "show or change settings in game"},
        {"/cg reward add item|command <id> <price>", "reward shop; also list, set, remove, defaults"},
    };

    private static int help(CommandContext<CommandSourceStack> c) {
        MutableComponent msg = Kogane.line("Culling Games commands").copy();
        appendHelp(msg, PLAYER_HELP);
        if (c.getSource().hasPermission(2)) {
            msg.append(Component.literal("\n Admin:").withStyle(ChatFormatting.GOLD));
            appendHelp(msg, ADMIN_HELP);
        }
        c.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    private static void appendHelp(MutableComponent msg, String[][] lines) {
        for (String[] line : lines) {
            msg.append(Component.literal("\n " + line[0]).withStyle(ChatFormatting.AQUA));
            if (!line[1].isEmpty()) {
                msg.append(Component.literal(" - " + line[1]).withStyle(ChatFormatting.GRAY));
            }
        }
    }
}
