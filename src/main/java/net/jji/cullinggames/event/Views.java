package net.jji.cullinggames.event;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.jji.cullinggames.config.CgConfig;
import net.jji.cullinggames.network.HudSyncPacket;
import net.jji.cullinggames.network.ShopActionPacket;
import net.jji.cullinggames.network.ShopSyncPacket;
import net.jji.cullinggames.shop.RewardStore;
import net.jji.cullinggames.shop.ShopStore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Builds what a player's client is allowed to see, and runs shop actions sent from the client. */
public final class Views {
    private Views() {}

    public static HudSyncPacket hud(MinecraftServer server, ServerPlayer player) {
        EventState state = EventState.get(server);
        UUID id = player.getUUID();
        if (!state.phase().isActive() || !state.isParticipant(id) || state.isEliminated(id)) {
            return HudSyncPacket.INACTIVE;
        }
        long limit = Penalties.timerLimitTicks(state);
        long left = Math.max(0, limit - state.ticksSincePointChange(id));
        long endsIn = state.endAtTicks() < 0 ? -1 : Math.max(0, state.endAtTicks() - state.runningTicks());
        String colony = state.colonyOf(id);
        return new HudSyncPacket(true, state.points(id), left, limit, state.isSealed(id),
                state.phase() == EventPhase.PAUSED, endsIn, colony == null ? "" : colony,
                state.lives(id), state.startingLives());
    }

    /** The event shop while an event runs; Kogane's reward shop after it. */
    public static ShopSyncPacket shop(MinecraftServer server, ServerPlayer player, String message) {
        EventState state = EventState.get(server);
        UUID id = player.getUUID();
        if (!state.phase().isActive() && state.eventNumber() > 0) {
            return rewards(server, state, player, message);
        }
        String closed = "";
        if (state.phase() != EventPhase.RUNNING) {
            closed = "The shop is only open while the Culling Games run.";
        } else if (!state.isParticipant(id) || state.isEliminated(id)) {
            closed = "Only players still in the Culling Games can use the shop.";
        }
        List<ShopSyncPacket.ItemOffer> items = new ArrayList<>();
        List<ShopStore.Entry> entries = ShopStore.get(server).all();
        for (int i = 0; i < entries.size(); i++) {
            items.add(new ShopSyncPacket.ItemOffer(i + 1, entries.get(i).stack(), entries.get(i).price()));
        }
        List<ShopSyncPacket.ServiceOffer> services = new ArrayList<>();
        for (Shop.Service s : Shop.Service.values()) {
            services.add(new ShopSyncPacket.ServiceOffer(s.id(), s.price(), Shop.hasAccess(state, id, s)));
        }
        List<ShopSyncPacket.RuleOffer> rules = new ArrayList<>();
        for (EventRule r : EventRule.values()) {
            rules.add(new ShopSyncPacket.RuleOffer(r.id(), r.description(), state.hasRule(r.id())));
        }
        return new ShopSyncPacket(state.points(id), CgConfig.RULE_COST.get(), closed, message, items, services, rules);
    }

    static ShopSyncPacket rewards(MinecraftServer server, EventState state, ServerPlayer player, String message) {
        RewardStore store = RewardStore.get(server);
        store.useEvent(state.eventNumber());
        UUID id = player.getUUID();
        List<ShopSyncPacket.RewardOffer> offers = new ArrayList<>();
        for (RewardStore.Reward r : store.all()) {
            boolean fragment = r.type() == RewardStore.Type.FRAGMENT;
            int have = fragment ? store.fragments(id, r.fragmentKey()) : 0;
            offers.add(new ShopSyncPacket.RewardOffer(r.id(), r.name(), r.stack(), r.price(),
                    Rewards.blocked(server, store, player, r), fragment, have, fragment ? r.fragmentsNeeded() : 0));
        }
        int max = CgConfig.MAX_COINS_PER_EVENT.get();
        ShopSyncPacket.CashOut cashOut = new ShopSyncPacket.CashOut(Shop.moneyItem() != null, CgConfig.POINTS_PER_COIN.get(),
                max == 0 ? -1 : Math.max(0, max - store.coinsThisEvent(id)));
        if (message.isEmpty()) {
            message = "Welcome to the reward exchange!";
        }
        return new ShopSyncPacket(state.points(id), 0, Rewards.closedReason(state, id), message,
                List.of(), List.of(), List.of(), true, offers, cashOut);
    }

    /** Runs a client shop action and returns the refreshed shop for that player. */
    public static ShopSyncPacket handle(ServerPlayer player, ShopActionPacket action) {
        MinecraftServer server = player.getServer();
        String message = "";
        if (server == null) {
            return new ShopSyncPacket(0, 0, "Unavailable.", "", List.of(), List.of(), List.of());
        }
        switch (action.action()) {
            case BUY -> {
                Shop.Service service = Shop.Service.byId(action.id());
                Shop.Result result;
                if (service != null) {
                    result = Shop.buyService(server, player, service);
                } else {
                    try {
                        result = Shop.buyItem(server, player, Integer.parseInt(action.id()));
                    } catch (NumberFormatException e) {
                        result = Shop.Result.no("Unknown item.");
                    }
                }
                message = result.message();
            }
            case RULE -> {
                EventRule rule = EventRule.byId(action.id());
                message = rule == null ? "Unknown rule." : Shop.addRule(server, player, rule).message();
            }
            case REWARD -> message = Rewards.buy(server, player, action.id()).message();
            case CASHOUT -> {
                try {
                    message = Shop.cashOut(server, player, Integer.parseInt(action.id())).message();
                } catch (NumberFormatException e) {
                    message = "Unknown amount.";
                }
            }
            case OPEN -> Rewards.deliverPending(server, player);
        }
        return shop(server, player, message);
    }
}
