package net.jji.cullinggames.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Locale;
import java.util.Map;
import net.jji.cullinggames.event.EventState;
import net.jji.cullinggames.event.Kogane;
import net.jji.cullinggames.event.Rewards;
import net.jji.cullinggames.event.Shop;
import net.jji.cullinggames.event.Views;
import net.jji.cullinggames.network.CgNetwork;
import net.jji.cullinggames.shop.RewardStore;
import net.jji.cullinggames.shop.RewardStore.Reward;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Kogane's reward shop. Players: /cg rewards, /cg reward buy &lt;id&gt;.
 * Admins: /cg reward add item|command, remove, set, list, fragments, defaults.
 */
final class RewardCommand {
    private static final String[] FIELDS = {"name", "price", "stock", "perplayer", "once", "requires", "blockedby",
            "fragments", "command", "icon"};

    private RewardCommand() {}

    /** /cg rewards */
    static LiteralArgumentBuilder<CommandSourceStack> rewards() {
        return Commands.literal("rewards").executes(RewardCommand::show);
    }

    /** /cg reward ... */
    static LiteralArgumentBuilder<CommandSourceStack> reward() {
        return Commands.literal("reward")
                .then(Commands.literal("buy").then(rewardId().executes(RewardCommand::buy)))
                .then(admin("list").executes(RewardCommand::list))
                .then(admin("add")
                        .then(Commands.literal("item").then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("price", IntegerArgumentType.integer(0, 1_000_000))
                                        .executes(RewardCommand::addItem))))
                        .then(Commands.literal("command").then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("price", IntegerArgumentType.integer(0, 1_000_000))
                                        .then(Commands.argument("command", StringArgumentType.greedyString())
                                                .executes(RewardCommand::addCommand))))))
                .then(admin("remove").then(rewardId().executes(RewardCommand::remove)))
                .then(admin("set").then(rewardId()
                        .then(Commands.argument("field", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(FIELDS, b))
                                .executes(RewardCommand::setHeld)
                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                        .executes(RewardCommand::set)))))
                .then(admin("fragments").then(Commands.argument("player", EntityArgument.player())
                        .executes(RewardCommand::showFragments)
                        .then(Commands.argument("weapon", StringArgumentType.string())
                                .then(Commands.argument("count", IntegerArgumentType.integer(0, 1000))
                                        .executes(RewardCommand::setFragments)))))
                .then(admin("defaults").executes(RewardCommand::defaults));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> admin(String name) {
        return Commands.literal(name).requires(s -> s.hasPermission(2));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> rewardId() {
        return Commands.argument("id", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                        RewardStore.get(c.getSource().getServer()).all().stream().map(Reward::id), b));
    }

    private static MinecraftServer server(CommandContext<CommandSourceStack> c) {
        return c.getSource().getServer();
    }

    private static Reward find(CommandContext<CommandSourceStack> c) {
        String id = StringArgumentType.getString(c, "id");
        Reward r = RewardStore.get(server(c)).get(id);
        if (r == null) {
            c.getSource().sendFailure(Component.literal("No reward " + id + ". See /cg reward list."));
        }
        return r;
    }

    // --- players ---

    private static int show(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        MinecraftServer server = server(c);
        ServerPlayer player = c.getSource().getPlayerOrException();
        if (CgNetwork.hasMod(player)) {
            CgNetwork.send(player, Views.shop(server, player, ""));
            return 1;
        }
        EventState state = EventState.get(server);
        String closed = Rewards.closedReason(state, player.getUUID());
        if (!closed.isEmpty()) {
            c.getSource().sendFailure(Component.literal(closed));
            return 0;
        }
        RewardStore store = RewardStore.get(server);
        store.useEvent(state.eventNumber());
        MutableComponent msg = Kogane.line("Rewards. You have " + state.points(player.getUUID()) + " points. Click to buy.").copy();
        for (Reward r : store.all()) {
            String blocked = Rewards.blocked(server, store, player, r);
            String progress = r.type() == RewardStore.Type.FRAGMENT
                    ? " (" + store.fragments(player.getUUID(), r.fragmentKey()) + "/" + r.fragmentsNeeded() + ")" : "";
            MutableComponent line = Component.literal("\n [" + r.price() + " pts] " + r.name() + progress
                    + (blocked.isEmpty() ? "" : " - " + blocked));
            if (blocked.isEmpty()) {
                line.withStyle(s -> s.withColor(ChatFormatting.AQUA)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cg reward buy " + r.id()))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to buy"))));
            } else {
                line.withStyle(ChatFormatting.DARK_GRAY);
            }
            msg.append(line);
        }
        c.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    private static int buy(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Shop.Result result = Rewards.buy(server(c), c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "id"));
        if (result.ok()) {
            c.getSource().sendSuccess(() -> Kogane.line(result.message()), false);
            return 1;
        }
        c.getSource().sendFailure(Component.literal(result.message()));
        return 0;
    }

    // --- admins ---

    private static int list(CommandContext<CommandSourceStack> c) {
        RewardStore store = RewardStore.get(server(c));
        if (store.all().isEmpty()) {
            c.getSource().sendSuccess(() -> Component.literal("No rewards. Add some with /cg reward add, or /cg reward defaults."), false);
            return 0;
        }
        StringBuilder sb = new StringBuilder("Rewards:");
        for (Reward r : store.all()) {
            sb.append("\n ").append(r.id()).append(" - ").append(r.name()).append(", ").append(r.type().name().toLowerCase(Locale.ROOT))
                    .append(", ").append(r.price()).append(" pts");
            if (r.stock() > 0) {
                sb.append(", stock ").append(r.stock()).append("/event");
            }
            if (r.perPlayer() > 0) {
                sb.append(", max ").append(r.perPlayer()).append("/player/event");
            }
            if (r.once()) {
                sb.append(", once per player");
            }
            if (!r.requires().isEmpty()) {
                sb.append(", requires ").append(r.requires());
            }
            if (!r.blockedBy().isEmpty()) {
                sb.append(", not if ").append(r.blockedBy());
            }
            if (r.type() == RewardStore.Type.FRAGMENT) {
                sb.append(", ").append(r.fragmentsNeeded()).append(" for ").append(r.fragmentKey());
            }
            if (r.type() == RewardStore.Type.COMMAND) {
                sb.append(", runs: ").append(r.command());
            }
        }
        String text = sb.toString();
        c.getSource().sendSuccess(() -> Component.literal(text), false);
        return store.all().size();
    }

    private static int addItem(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ItemStack held = c.getSource().getPlayerOrException().getMainHandItem();
        if (held.isEmpty()) {
            c.getSource().sendFailure(Component.literal("Hold the item (and stack size) the reward gives."));
            return 0;
        }
        String name = held.getCount() + "x " + itemName(held);
        return add(c, new Reward(StringArgumentType.getString(c, "id"), RewardStore.Type.ITEM, name, held, "",
                IntegerArgumentType.getInteger(c, "price")));
    }

    private static int addCommand(CommandContext<CommandSourceStack> c) {
        ItemStack icon = c.getSource().getEntity() instanceof ServerPlayer p && !p.getMainHandItem().isEmpty()
                ? p.getMainHandItem().copyWithCount(1) : new ItemStack(net.minecraft.world.item.Items.PAPER);
        String id = StringArgumentType.getString(c, "id");
        return add(c, new Reward(id, RewardStore.Type.COMMAND, capitalize(id), icon,
                StringArgumentType.getString(c, "command"), IntegerArgumentType.getInteger(c, "price")));
    }

    private static int add(CommandContext<CommandSourceStack> c, Reward r) {
        if (r.id().length() > 32) {
            c.getSource().sendFailure(Component.literal("Reward ids can be at most 32 characters."));
            return 0;
        }
        if (!RewardStore.get(server(c)).add(r)) {
            c.getSource().sendFailure(Component.literal("A reward called " + r.id() + " already exists."));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal("Added reward " + r.id() + " (" + r.name() + ") for "
                + r.price() + " points. Change details with /cg reward set " + r.id() + " <field> <value>."), true);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> c) {
        String id = StringArgumentType.getString(c, "id");
        if (!RewardStore.get(server(c)).remove(id)) {
            c.getSource().sendFailure(Component.literal("No reward " + id + "."));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal("Removed reward " + id + "."), true);
        return 1;
    }

    /** /cg reward set &lt;id&gt; icon: uses the held item as the icon (or the item for item/fragment rewards). */
    private static int setHeld(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String field = StringArgumentType.getString(c, "field").toLowerCase(Locale.ROOT);
        if (!field.equals("icon")) {
            c.getSource().sendFailure(Component.literal("Give a value: /cg reward set <id> " + field + " <value>."));
            return 0;
        }
        Reward r = find(c);
        ItemStack held = c.getSource().getPlayerOrException().getMainHandItem();
        if (r == null || held.isEmpty()) {
            if (r != null) {
                c.getSource().sendFailure(Component.literal("Hold the item to use."));
            }
            return 0;
        }
        r.stack(r.type() == RewardStore.Type.FRAGMENT ? held.copyWithCount(1) : held);
        RewardStore.get(server(c)).changed();
        c.getSource().sendSuccess(() -> Component.literal(r.id() + ": item set to " + itemName(held) + "."), true);
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> c) {
        Reward r = find(c);
        if (r == null) {
            return 0;
        }
        String field = StringArgumentType.getString(c, "field").toLowerCase(Locale.ROOT);
        String value = StringArgumentType.getString(c, "value").trim();
        try {
            switch (field) {
                case "name" -> r.name(value);
                case "price" -> r.price(nonNegative(value));
                case "stock" -> r.stock(nonNegative(value));
                case "perplayer" -> r.perPlayer(nonNegative(value));
                case "fragments" -> r.fragmentsNeeded(nonNegative(value));
                case "once" -> r.once(bool(value));
                case "requires" -> r.requires(value.equalsIgnoreCase("none") ? "" : value);
                case "blockedby" -> r.blockedBy(value.equalsIgnoreCase("none") ? "" : value);
                case "command" -> r.command(value);
                default -> {
                    c.getSource().sendFailure(Component.literal("Unknown field " + field + ". Fields: " + String.join(", ", FIELDS) + "."));
                    return 0;
                }
            }
        } catch (IllegalArgumentException e) {
            c.getSource().sendFailure(Component.literal("Invalid value for " + field + ": " + value + "."));
            return 0;
        }
        RewardStore.get(server(c)).changed();
        c.getSource().sendSuccess(() -> Component.literal(r.id() + ": " + field + " = " + value), true);
        return 1;
    }

    private static int nonNegative(String v) {
        int n = Integer.parseInt(v);
        if (n < 0) {
            throw new IllegalArgumentException();
        }
        return n;
    }

    private static boolean bool(String v) {
        if (v.equalsIgnoreCase("true") || v.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(v);
        }
        throw new IllegalArgumentException();
    }

    private static int showFragments(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(c, "player");
        Map<String, Integer> frags = RewardStore.get(server(c)).fragments(target.getUUID());
        String text = frags.isEmpty() ? target.getGameProfile().getName() + " has no fragments."
                : target.getGameProfile().getName() + "'s fragments: " + frags;
        c.getSource().sendSuccess(() -> Component.literal(text), false);
        return frags.size();
    }

    private static int setFragments(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(c, "player");
        String weapon = StringArgumentType.getString(c, "weapon");
        int count = IntegerArgumentType.getInteger(c, "count");
        RewardStore.get(server(c)).setFragments(target.getUUID(), weapon, count);
        c.getSource().sendSuccess(() -> Component.literal(target.getGameProfile().getName() + ": " + weapon + " fragments = " + count), true);
        return 1;
    }

    private static int defaults(CommandContext<CommandSourceStack> c) {
        int added = Rewards.addDefaults(RewardStore.get(server(c)));
        c.getSource().sendSuccess(() -> Component.literal(added == 0 ? "All default rewards are already there."
                : "Added " + added + " default rewards."), true);
        return added;
    }

    /** Item names live in client-side lang files; on a server, fall back to the item id. */
    private static String itemName(ItemStack stack) {
        if (stack.hasCustomHoverName()) {
            return stack.getHoverName().getString();
        }
        String name = stack.getHoverName().getString();
        if (name.contains(".") && !name.contains(" ")) {
            var key = ForgeRegistries.ITEMS.getKey(stack.getItem());
            return key == null ? name : capitalize(key.getPath());
        }
        return name;
    }

    private static String capitalize(String id) {
        String s = id.replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
