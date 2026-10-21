package net.jji.cullinggames.event;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.jji.cullinggames.CullingGames;
import net.jji.cullinggames.compat.JujutsuCraft;
import net.jji.cullinggames.config.CgConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Fights between participants (Red, 20 Oct): who is in a battle (limits heal/CE purchases), assists (end the
 * hunted state), and the kill bonus of cursed energy and Zone.
 *
 * <p>Kept in memory only: a restart ends every battle, which only frees up a recovery purchase.
 * Registered after {@link Points}, so a sealed killer is unsealed by the kill's points before the bonus lands.
 */
public final class Combat {
    /** Victim -> attacker -> server tick of the attacker's last hit. */
    private static final Map<UUID, Map<UUID, Long>> HITS = new HashMap<>();
    /** Server tick of a player's last hit given or taken in a fight with another participant. */
    private static final Map<UUID, Long> LAST_FIGHT = new HashMap<>();
    /** Recoveries bought in the current battle. */
    private static final Map<UUID, Integer> BATTLE_USES = new HashMap<>();
    /** Server tick of the last recovery purchase. */
    private static final Map<UUID, Long> LAST_RECOVERY = new HashMap<>();

    private Combat() {}

    static void reset() {
        HITS.clear();
        LAST_FIGHT.clear();
        BATTLE_USES.clear();
        LAST_RECOVERY.clear();
    }

    /** Lowest priority and not when cancelled: only damage that actually lands counts. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim) || victim.getServer() == null || event.getAmount() <= 0) {
            return;
        }
        MinecraftServer server = victim.getServer();
        EventState state = EventState.get(server);
        if (state.phase() != EventPhase.RUNNING) {
            return;
        }
        ServerPlayer attacker = Points.resolveKiller(server, event.getSource());
        if (attacker != null && attacker != victim
                && state.isParticipant(attacker.getUUID()) && state.isParticipant(victim.getUUID())) {
            recordHit(server, attacker, victim);
        }
    }

    /** Package-visible for tests. */
    static void recordHit(MinecraftServer server, ServerPlayer attacker, ServerPlayer victim) {
        long now = server.getTickCount();
        HITS.computeIfAbsent(victim.getUUID(), k -> new HashMap<>()).put(attacker.getUUID(), now);
        fight(server, attacker.getUUID(), now);
        fight(server, victim.getUUID(), now);
    }

    /** A hit after a quiet spell starts a new battle with fresh recovery uses. */
    private static void fight(MinecraftServer server, UUID id, long now) {
        if (!inBattle(server, id)) {
            BATTLE_USES.remove(id);
        }
        LAST_FIGHT.put(id, now);
    }

    public static boolean inBattle(MinecraftServer server, UUID id) {
        Long last = LAST_FIGHT.get(id);
        return last != null && server.getTickCount() - last < 20L * CgConfig.BATTLE_SECONDS.get();
    }

    static int battleUses(UUID id) {
        return BATTLE_USES.getOrDefault(id, 0);
    }

    /** Ticks until the next recovery is allowed outside battle (0 = now). */
    static long cooldownLeft(MinecraftServer server, UUID id) {
        Long last = LAST_RECOVERY.get(id);
        if (last == null) {
            return 0;
        }
        return Math.max(0, last + 20L * CgConfig.RECOVERY_COOLDOWN_SECONDS.get() - server.getTickCount());
    }

    static void recordRecovery(MinecraftServer server, UUID id) {
        LAST_RECOVERY.put(id, (long) server.getTickCount());
        if (inBattle(server, id)) {
            BATTLE_USES.merge(id, 1, Integer::sum);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        MinecraftServer server = victim.getServer();
        if (server == null || victim.level().isClientSide) {
            return;
        }
        EventState state = EventState.get(server);
        Map<UUID, Long> hits = HITS.remove(victim.getUUID());
        if (state.phase() != EventPhase.RUNNING) {
            return;
        }
        ServerPlayer killer = Points.resolveKiller(server, event.getSource());
        boolean playerVictim = victim instanceof ServerPlayer && state.isParticipant(victim.getUUID());
        if (killer != null && killer != victim && state.isParticipant(killer.getUUID()) && !state.isEliminated(killer.getUUID())) {
            if (playerVictim) {
                state.clearHunt(killer.getUUID());
            }
            if (playerVictim || CgConfig.KILL_BONUS_WEAK_KILLS.get() && Points.pointsFor(state, victim) > 0) {
                killBonus(state, killer);
            }
        }
        if (playerVictim && hits != null) {
            long since = server.getTickCount() - 20L * CgConfig.ASSIST_SECONDS.get();
            String name = ((ServerPlayer) victim).getGameProfile().getName();
            hits.forEach((id, tick) -> {
                if (tick >= since && (killer == null || !id.equals(killer.getUUID())) && state.isParticipant(id)) {
                    ServerPlayer helper = server.getPlayerList().getPlayer(id);
                    boolean wasHunted = state.isHunted(id);
                    state.clearHunt(id);
                    if (helper != null) {
                        Kogane.tell(helper, "Assist on " + name + "." + (wasHunted ? " You are no longer hunted." : ""));
                    }
                }
            });
        }
    }

    /**
     * Logging out mid-battle would dodge a death, and with the timer held while offline it would cost nothing.
     * The player dies where they stand, credited to the last participant who hit them, so points, lives, the
     * kill log and the kill bonus all follow the normal death path. {@code die} skips damage, so Infinity and
     * armour can't block it.
     */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            combatLog(player.getServer(), player);
        }
    }

    /** Returns true if the player was killed for leaving a battle. Package-visible for tests. */
    static boolean combatLog(MinecraftServer server, ServerPlayer player) {
        UUID id = player.getUUID();
        EventState state = EventState.get(server);
        if (!CgConfig.COMBAT_LOG_KILLS.get() || !player.isAlive() || state.phase() != EventPhase.RUNNING
                || !state.isParticipant(id) || state.isEliminated(id) || !inBattle(server, id)) {
            return false;
        }
        ServerPlayer attacker = lastAttacker(server, state, id);
        DamageSource source = attacker != null
                ? player.damageSources().playerAttack(attacker)
                : player.damageSources().generic();
        Kogane.broadcast(server, player.getGameProfile().getName() + " fled a battle and paid with a life.");
        player.setHealth(0);
        player.die(source);
        return true;
    }

    /** The online participant who hit this player most recently within the battle window, or null. */
    private static ServerPlayer lastAttacker(MinecraftServer server, EventState state, UUID victim) {
        Map<UUID, Long> hits = HITS.get(victim);
        if (hits == null) {
            return null;
        }
        long since = server.getTickCount() - 20L * CgConfig.BATTLE_SECONDS.get();
        ServerPlayer best = null;
        long bestTick = Long.MIN_VALUE;
        for (Map.Entry<UUID, Long> e : hits.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (e.getValue() >= since && e.getValue() > bestTick && p != null && state.isParticipant(p.getUUID())) {
                best = p;
                bestTick = e.getValue();
            }
        }
        return best;
    }

    /** Cursed energy (capped at max unless killBonusCeOverMax) and Jujutsu Craft's Zone for the killer. */
    static void killBonus(EventState state, ServerPlayer killer) {
        StringBuilder msg = new StringBuilder();
        int ce = CgConfig.KILL_BONUS_CE.get();
        // A sealed killer's energy is drained every tick, so a bonus would be lost.
        if (ce > 0 && JujutsuCraft.isLoaded() && !state.isSealed(killer.getUUID())) {
            double now = JujutsuCraft.cursedEnergy(killer);
            double max = JujutsuCraft.maxCursedEnergy(killer);
            double next = now + ce;
            if (!CgConfig.KILL_BONUS_CE_OVER_MAX.get() && !Double.isNaN(max)) {
                next = Math.max(now, Math.min(next, max));
            }
            if (!Double.isNaN(now) && JujutsuCraft.setCursedEnergy(killer, next)) {
                msg.append("+").append((int) Math.round(next - now)).append(" cursed energy");
            }
        }
        int seconds = CgConfig.KILL_ZONE_SECONDS.get();
        MobEffect zone = seconds > 0 ? JujutsuCraft.effect(CgConfig.KILL_ZONE_EFFECT.get()) : null;
        if (zone != null) {
            killer.addEffect(new MobEffectInstance(zone, seconds * 20, CgConfig.KILL_ZONE_LEVEL.get() - 1));
            msg.append(msg.length() > 0 ? " and " : "").append(seconds).append(" s of ").append(zone.getDisplayName().getString());
        } else if (seconds > 0) {
            CullingGames.LOGGER.debug("Kill zone effect {} is not registered", CgConfig.KILL_ZONE_EFFECT.get());
        }
        if (msg.length() > 0) {
            Kogane.tell(killer, "Kill bonus: " + msg + ".");
        }
    }
}
