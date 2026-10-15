package net.jji.cullinggames.event;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import net.jji.cullinggames.network.CgNetwork;
import net.jji.cullinggames.network.HudSyncPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Kogane's status for each participant, once a second. Players with the mod get a HUD packet;
 * players without it get a private bossbar with the same information.
 */
public final class KoganeBar {
    private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();
    /** Players whose client HUD is currently showing, so it can be hidden when they drop out. */
    private static final Set<UUID> HUD_SHOWN = new HashSet<>();

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
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            UUID id = p.getUUID();
            boolean shown = state.phase().isActive() && state.isParticipant(id) && !state.isEliminated(id);
            if (CgNetwork.hasMod(p)) {
                remove(id);
                if (shown) {
                    CgNetwork.send(p, Views.hud(server, p));
                    HUD_SHOWN.add(id);
                } else if (HUD_SHOWN.remove(id)) {
                    CgNetwork.send(p, HudSyncPacket.INACTIVE);
                }
                continue;
            }
            if (!shown) {
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
        HUD_SHOWN.removeIf(id -> server.getPlayerList().getPlayer(id) == null);
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

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        remove(event.getEntity().getUUID());
        HUD_SHOWN.remove(event.getEntity().getUUID());
    }
}
