package net.jji.cullinggames.event;

import java.util.List;
import java.util.UUID;
import net.jji.cullinggames.CullingGames;
import net.jji.cullinggames.compat.JujutsuCraft;
import net.jji.cullinggames.config.CgConfig;
import net.jji.cullinggames.network.AnnouncePacket;
import net.jji.cullinggames.shop.RewardStore;
import net.jji.cullinggames.shop.RewardStore.Reward;
import net.jji.cullinggames.shop.RewardStore.Type;
import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Kogane's reward shop: after an event ends, players spend the points they had left until the next event
 * starts (starting an event clears points). Rewards are items, server commands or weapon fragments.
 */
public final class Rewards {
    /** Special Grade promotion; JJC sets the grade when the advancement is granted. */
    static final String SPECIAL_GRADE = "jujutsucraft:sorcerer_grade_special";
    static final String GRADE_1 = "jujutsucraft:sorcerer_grade_1";

    private Rewards() {}

    /** Why the reward shop is closed for this player, or "" when it is open. */
    public static String closedReason(EventState state, UUID player) {
        if (state.phase().isActive()) {
            return "Rewards open when the Culling Games end. Use the event shop now.";
        }
        if (state.eventNumber() == 0) {
            return "No Culling Games have been played yet.";
        }
        if (!CgConfig.REWARD_SHOP_OPEN.get()) {
            return "Kogane's reward shop is closed.";
        }
        if (!state.allPoints().containsKey(player)) {
            return "You have no points from the last Culling Games.";
        }
        return "";
    }

    /** Why this player can't buy this reward right now (ignoring price), or "" if they can. */
    public static String blocked(MinecraftServer server, RewardStore store, ServerPlayer player, Reward r) {
        UUID id = player.getUUID();
        if (r.once() && store.boughtEver(id, r.id())) {
            return "Already claimed";
        }
        if (r.stock() > 0 && store.soldThisEvent(r.id()) >= r.stock()) {
            return "Sold out";
        }
        if (r.perPlayer() > 0 && store.boughtThisEvent(id, r.id()) >= r.perPlayer()) {
            return "Limit reached";
        }
        if (r.type() == Type.FRAGMENT && store.boughtThisEvent(id, Type.FRAGMENT) >= CgConfig.MAX_FRAGMENTS_PER_EVENT.get()) {
            return "Fragment limit reached";
        }
        if (!r.blockedBy().isEmpty() && hasAdvancement(server, player, r.blockedBy())) {
            return "Already have it";
        }
        if (!r.requires().isEmpty() && !hasAdvancement(server, player, r.requires())) {
            return "Requires " + advancementName(r.requires());
        }
        if (r.type() != Type.COMMAND && r.stack().isEmpty()) {
            return "Unavailable";
        }
        return "";
    }

    public static Shop.Result buy(MinecraftServer server, ServerPlayer player, String rewardId) {
        EventState state = EventState.get(server);
        String closed = closedReason(state, player.getUUID());
        if (!closed.isEmpty()) {
            return Shop.Result.no(closed);
        }
        RewardStore store = RewardStore.get(server);
        store.useEvent(state.eventNumber());
        Reward r = store.get(rewardId);
        if (r == null) {
            return Shop.Result.no("No reward " + rewardId + ".");
        }
        String blocked = blocked(server, store, player, r);
        if (!blocked.isEmpty()) {
            return Shop.Result.no(r.name() + ": " + blocked + ".");
        }
        UUID id = player.getUUID();
        int have = state.points(id);
        if (have < r.price()) {
            return Shop.Result.no(r.name() + " costs " + r.price() + " points. You have " + have + ".");
        }
        if (r.type() == Type.COMMAND && !runCommand(server, player, r.command())) {
            CullingGames.LOGGER.warn("Reward {} for {}: command failed, no points taken: {}",
                    r.id(), player.getGameProfile().getName(), r.command());
            return Shop.Result.no(r.name() + " could not be given. No points were taken; tell an admin.");
        }
        state.setPoints(id, have - r.price());
        store.recordPurchase(id, r.id());
        String extra = "";
        switch (r.type()) {
            case ITEM -> deliver(store, player, r.stack().copy());
            case FRAGMENT -> extra = addFragment(store, player, r);
            case COMMAND -> { }
        }
        CullingGames.LOGGER.info("Reward: {} bought {} for {} points (event {})",
                player.getGameProfile().getName(), r.id(), r.price(), state.eventNumber());
        Kogane.sound(player, "points");
        return Shop.Result.ok("Bought " + r.name() + " for " + r.price() + " points." + extra);
    }

    /** Adds one fragment; a full set is turned into the weapon at once. Returns text for the purchase message. */
    private static String addFragment(RewardStore store, ServerPlayer player, Reward r) {
        String key = r.fragmentKey();
        int count = store.fragments(player.getUUID(), key) + 1;
        if (count < r.fragmentsNeeded()) {
            store.setFragments(player.getUUID(), key, count);
            return " Fragments: " + count + "/" + r.fragmentsNeeded() + ".";
        }
        store.setFragments(player.getUUID(), key, count - r.fragmentsNeeded());
        ItemStack weapon = r.stack().copy();
        weapon.setCount(1);
        deliver(store, player, weapon);
        String weaponName = r.name().replace(" fragment", "");
        Kogane.announce(player, AnnouncePacket.Kind.GOOD, "Weapon assembled!", weaponName, "unseal");
        CullingGames.LOGGER.info("Reward: {} assembled {}", player.getGameProfile().getName(), key);
        return " All fragments collected: " + weaponName + " is yours!";
    }

    private static boolean runCommand(MinecraftServer server, ServerPlayer player, String command) {
        String cmd = command.replace("%player%", player.getGameProfile().getName());
        if (cmd.startsWith("/")) {
            cmd = cmd.substring(1);
        }
        int result = server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput(), cmd);
        return result > 0;
    }

    /** Into the inventory, or kept for later if it's full. Never dropped on the ground. */
    static void deliver(RewardStore store, ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack) || !stack.isEmpty()) {
            store.addPending(player.getUUID(), stack);
            Kogane.tell(player, "Your inventory is full. Kogane is holding " + stack.getHoverName().getString()
                    + " for you; make room to receive it.");
        }
    }

    /** Hands over pending rewards that now fit. */
    static void deliverPending(MinecraftServer server, ServerPlayer player) {
        RewardStore store = RewardStore.get(server);
        if (!store.hasPending(player.getUUID())) {
            return;
        }
        List<ItemStack> stacks = store.takePending(player.getUUID());
        int given = 0;
        for (ItemStack stack : stacks) {
            ItemStack copy = stack.copy();
            if (player.getInventory().add(copy) && copy.isEmpty()) {
                given++;
            } else {
                store.addPending(player.getUUID(), copy);
            }
        }
        if (given > 0) {
            Kogane.tell(player, "Kogane handed over " + given + (given == 1 ? " reward" : " rewards") + " it was holding.");
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 100 != 0) {
            return;
        }
        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
            deliverPending(event.getServer(), p);
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        seedDefaults(event.getServer());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            deliverPending(player.getServer(), player);
        }
    }

    // --- defaults ---

    /** Puts the default rewards in an empty catalogue once. Rewards whose items don't exist are skipped. */
    public static void seedDefaults(MinecraftServer server) {
        RewardStore store = RewardStore.get(server);
        if (store.seeded()) {
            return;
        }
        addDefaults(store);
        store.markSeeded();
    }

    /** Adds every default reward that is missing. Returns how many were added. */
    public static int addDefaults(RewardStore store) {
        int added = 0;
        Item icon = item("jujutsucraft:recommendation_2");
        if (JujutsuCraft.isLoaded()) {
            Reward special = new Reward("special_grade", Type.COMMAND, "Special Grade promotion",
                    new ItemStack(icon != null ? icon : Items.NETHER_STAR),
                    "advancement grant %player% only " + SPECIAL_GRADE, 220)
                    .once(true).requires(GRADE_1).blockedBy(SPECIAL_GRADE).priceFromConfig("specialGrade");
            added += store.add(special) ? 1 : 0;
        }
        // Item names come from client-side lang files, so the server names them here.
        String[][] weapons = {
            {"inverted_spear_of_heaven", "6", "Inverted Spear of Heaven"},
            {"playful_cloud", "4", "Playful Cloud"},
            {"split_soul_katana", "5", "Split Soul Katana"},
            {"black_rope", "4", "Black Rope"},
            {"supreme_martial_solution", "6", "Kamutoke"},
            {"hiten", "5", "Hiten"},
            {"sword_of_extermination", "6", "Sword of Extermination"},
        };
        for (String[] w : weapons) {
            Item weapon = item("jujutsucraft:" + w[0]);
            if (weapon == null) {
                continue;
            }
            ItemStack stack = new ItemStack(weapon);
            Reward fragment = new Reward("frag_" + w[0], Type.FRAGMENT, w[2] + " fragment", stack, "", 60)
                    .fragmentsNeeded(Integer.parseInt(w[1])).priceFromConfig("fragment");
            added += store.add(fragment) ? 1 : 0;
        }
        return added;
    }

    private static Item item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Item item = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
        return item == null || item == Items.AIR ? null : item;
    }

    // --- advancements ---

    static boolean hasAdvancement(MinecraftServer server, ServerPlayer player, String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Advancement adv = rl == null ? null : server.getAdvancements().getAdvancement(rl);
        return adv != null && player.getAdvancements().getOrStartProgress(adv).isDone();
    }

    /** "jujutsucraft:sorcerer_grade_1" → "Sorcerer grade 1". */
    static String advancementName(String id) {
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        String s = path.replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
