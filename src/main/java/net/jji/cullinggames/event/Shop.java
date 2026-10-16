package net.jji.cullinggames.event;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.jji.cullinggames.config.CgConfig;
import net.jji.cullinggames.network.AnnouncePacket;
import net.jji.cullinggames.shop.RewardStore;
import net.jji.cullinggames.shop.ShopStore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Everything bought with points: admin-stocked items, the three services, rules, transfers and cash-out.
 * Each method returns a message for the player; {@link Result#ok} says whether it went through.
 */
public final class Shop {
    private Shop() {}

    public record Result(boolean ok, String message) {
        static Result ok(String m) {
            return new Result(true, m);
        }

        static Result no(String m) {
            return new Result(false, m);
        }
    }

    /** Services unlocked for the rest of the event. */
    public enum Service {
        LEADERBOARD, TRANSFER, KILLLOG;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public int price() {
            return switch (this) {
                case LEADERBOARD -> CgConfig.LEADERBOARD_PRICE.get();
                case TRANSFER -> CgConfig.TRANSFER_PRICE.get();
                case KILLLOG -> CgConfig.KILLLOG_PRICE.get();
            };
        }

        public static Service byId(String id) {
            for (Service s : values()) {
                if (s.id().equalsIgnoreCase(id)) {
                    return s;
                }
            }
            return null;
        }
    }

    /** Participant in a running event, not out. Returns an error result or null when allowed. */
    private static Result gate(EventState state, ServerPlayer player) {
        if (state.phase() != EventPhase.RUNNING) {
            return Result.no("The shop is only open while the Culling Games run.");
        }
        if (!state.isParticipant(player.getUUID()) || state.isEliminated(player.getUUID())) {
            return Result.no("Only players still in the Culling Games can use the shop.");
        }
        return null;
    }

    /** Takes points without resetting the timer. False if the player can't afford it. */
    private static boolean pay(EventState state, UUID id, int price) {
        int have = state.points(id);
        if (have < price) {
            return false;
        }
        state.setPoints(id, have - price);
        return true;
    }

    public static Result buyService(MinecraftServer server, ServerPlayer player, Service service) {
        EventState state = EventState.get(server);
        Result denied = gate(state, player);
        if (denied != null) {
            return denied;
        }
        if (hasAccess(state, player.getUUID(), service)) {
            return Result.no("You already have " + service.id() + ".");
        }
        if (!pay(state, player.getUUID(), service.price())) {
            return Result.no(service.id() + " costs " + service.price() + " points. You have " + state.points(player.getUUID()) + ".");
        }
        state.unlock(player.getUUID(), service.id());
        return Result.ok("Unlocked " + service.id() + " for the rest of the event.");
    }

    public static Result buyItem(MinecraftServer server, ServerPlayer player, int shopId) {
        EventState state = EventState.get(server);
        Result denied = gate(state, player);
        if (denied != null) {
            return denied;
        }
        ShopStore.Entry entry = ShopStore.get(server).get(shopId);
        if (entry == null) {
            return Result.no("No shop item #" + shopId + ".");
        }
        if (!pay(state, player.getUUID(), entry.price())) {
            return Result.no("That costs " + entry.price() + " points. You have " + state.points(player.getUUID()) + ".");
        }
        give(player, entry.stack().copy());
        return Result.ok("Bought " + entry.stack().getHoverName().getString() + " for " + entry.price() + " points.");
    }

    public static Result addRule(MinecraftServer server, ServerPlayer player, EventRule rule) {
        EventState state = EventState.get(server);
        Result denied = gate(state, player);
        if (denied != null) {
            return denied;
        }
        if (state.hasRule(rule.id())) {
            return Result.no("That rule is already in play.");
        }
        int cost = CgConfig.RULE_COST.get();
        if (!pay(state, player.getUUID(), cost)) {
            return Result.no("A rule costs " + cost + " points. You have " + state.points(player.getUUID()) + ".");
        }
        state.addRule(rule.id());
        Kogane.broadcast(server, player.getGameProfile().getName() + " added a rule: " + rule.description());
        // The original game has 8 rules, so bought rules continue from 9.
        Kogane.announceAll(server, AnnouncePacket.Kind.RULE, "Rule " + (8 + state.rules().size()) + " added", rule.description());
        return Result.ok("Rule added.");
    }

    public static Result transfer(MinecraftServer server, ServerPlayer from, UUID to, String toName, int amount) {
        EventState state = EventState.get(server);
        Result denied = gate(state, from);
        if (denied != null) {
            return denied;
        }
        if (!hasAccess(state, from.getUUID(), Service.TRANSFER)) {
            return Result.no("Buy transfer first (" + Service.TRANSFER.price() + " points).");
        }
        if (to.equals(from.getUUID()) || !state.isParticipant(to) || state.isEliminated(to)) {
            return Result.no(toName + " is not someone you can transfer to.");
        }
        if (!pay(state, from.getUUID(), amount)) {
            return Result.no("You only have " + state.points(from.getUUID()) + " points.");
        }
        // A received transfer is a gain: it resets the receiver's timer and unseals them.
        Points.add(server, to, amount);
        ServerPlayer receiver = server.getPlayerList().getPlayer(to);
        if (receiver != null) {
            Kogane.tell(receiver, from.getGameProfile().getName() + " sent you " + amount + " points.");
        }
        return Result.ok("Sent " + amount + " points to " + toName + ".");
    }

    /**
     * Points to Lightman's Currency coins (one-way), pointsPerCoin points each, at most maxCoinsPerEvent per player
     * per event. Open to players still in a running event, and in the reward shop after it. Disabled if the coin
     * item doesn't exist.
     */
    public static Result cashOut(MinecraftServer server, ServerPlayer player, int coins) {
        EventState state = EventState.get(server);
        UUID id = player.getUUID();
        if (state.phase().isActive()) {
            Result denied = gate(state, player);
            if (denied != null) {
                return denied;
            }
        } else {
            String closed = Rewards.closedReason(state, id);
            if (!closed.isEmpty()) {
                return Result.no(closed);
            }
        }
        Item coin = moneyItem();
        if (coin == null) {
            return Result.no("Cash-out is not available on this server.");
        }
        RewardStore store = RewardStore.get(server);
        store.useEvent(state.eventNumber());
        int max = CgConfig.MAX_COINS_PER_EVENT.get();
        if (max > 0 && store.coinsThisEvent(id) + coins > max) {
            int left = Math.max(0, max - store.coinsThisEvent(id));
            return Result.no("You can cash out " + left + " more coin" + (left == 1 ? "" : "s") + " this event (max " + max + ").");
        }
        int cost = coins * CgConfig.POINTS_PER_COIN.get();
        if (!pay(state, id, cost)) {
            return Result.no(coins + (coins == 1 ? " coin costs " : " coins cost ") + cost + " points. You have " + state.points(id) + ".");
        }
        store.addCoins(id, coins);
        int total = coins;
        while (total > 0) {
            int n = Math.min(total, new ItemStack(coin).getMaxStackSize());
            Rewards.deliver(store, player, new ItemStack(coin, n));
            total -= n;
        }
        return Result.ok("Cashed out " + cost + " points for " + coins + (coins == 1 ? " coin." : " coins."));
    }

    static Item moneyItem() {
        ResourceLocation id = ResourceLocation.tryParse(CgConfig.MONEY_ITEM.get());
        if (id == null) {
            return null;
        }
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return item == null || item == Items.AIR ? null : item;
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    // --- access checks used by commands ---

    public static boolean hasAccess(EventState state, UUID id, Service service) {
        return switch (service) {
            case LEADERBOARD -> state.hasRule(EventRule.REVEAL_POINTS.id()) || state.hasUnlocked(id, service.id());
            case TRANSFER -> state.hasRule(EventRule.FREE_TRANSFERS.id()) || state.hasUnlocked(id, service.id());
            case KILLLOG -> state.hasUnlocked(id, service.id());
        };
    }

    /** Leaderboard lines, highest first. */
    public static String leaderboard(MinecraftServer server, EventState state, int top) {
        List<Map.Entry<UUID, Integer>> sorted = state.allPoints().entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue(Comparator.reverseOrder()))
                .limit(top)
                .toList();
        StringBuilder sb = new StringBuilder("Leaderboard:");
        int rank = 1;
        for (Map.Entry<UUID, Integer> e : sorted) {
            String name = server.getProfileCache() == null ? e.getKey().toString()
                    : server.getProfileCache().get(e.getKey()).map(p -> p.getName()).orElse(e.getKey().toString());
            sb.append("\n ").append(rank++).append(". ").append(name).append(" - ").append(e.getValue());
        }
        return sb.toString();
    }
}
