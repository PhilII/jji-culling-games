package net.jji.cullinggames.event;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Kogane's bossbar: one private bar per participant with their own points and time left to score.
 * Placeholder until the step 6 HUD. Updated once a second.
 */
public final class KoganeBar {
    private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();

    private KoganeBar() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 20 != 0) {
            return;
        }
        update(event.getServer());
    }

    static void update(MinecraftServer server) {
        EventState state = EventState.get(server);
        if (!state.phase().isActive()) {
            clearAll();
            return;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            UUID id = p.getUUID();
            if (!state.isParticipant(id) || state.isEliminated(id)) {
                remove(id);
                continue;
            }
            ServerBossEvent bar = BARS.computeIfAbsent(id, k -> new ServerBossEvent(Component.empty(),
                    BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS));
            if (!bar.getPlayers().contains(p)) {
                bar.addPlayer(p);
            }
            render(state, bar, id);
        }
        // Bars of players who logged out are dropped; they get a new one at login.
        BARS.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
    }

    private static void render(EventState state, ServerBossEvent bar, UUID id) {
        StringBuilder text = new StringBuilder("Kogane | ").append(state.points(id)).append(" pts | ");
        if (state.isSealed(id)) {
            text.append("CT SEALED - score to unseal");
            bar.setColor(BossEvent.BossBarColor.PURPLE);
            bar.setProgress(0);
        } else {
            long limit = Penalties.timerLimitTicks(state);
            long left = Math.max(0, limit - state.ticksSincePointChange(id));
            text.append("score within ").append(clock(left));
            bar.setProgress(limit == 0 ? 0 : (float) left / limit);
            bar.setColor(left <= 20 * 60 ? BossEvent.BossBarColor.RED
                    : left <= 5 * 20 * 60 ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.GREEN);
        }
        if (state.phase() == EventPhase.PAUSED) {
            text.append(" | PAUSED");
        } else if (state.endAtTicks() >= 0) {
            text.append(" | ends in ").append(clock(Math.max(0, state.endAtTicks() - state.runningTicks())));
        }
        bar.setName(Component.literal(text.toString()));
    }

    private static String clock(long ticks) {
        long s = ticks / 20;
        return String.format("%d:%02d", s / 60, s % 60);
    }

    static String textFor(UUID id) {
        ServerBossEvent bar = BARS.get(id);
        return bar == null ? null : bar.getName().getString();
    }

    private static void remove(UUID id) {
        ServerBossEvent bar = BARS.remove(id);
        if (bar != null) {
            bar.removeAllPlayers();
        }
    }

    static void clearAll() {
        for (Iterator<ServerBossEvent> it = BARS.values().iterator(); it.hasNext(); ) {
            it.next().removeAllPlayers();
            it.remove();
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        remove(event.getEntity().getUUID());
    }
}
