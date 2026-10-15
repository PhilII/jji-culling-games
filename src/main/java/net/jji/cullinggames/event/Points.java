package net.jji.cullinggames.event;

import java.util.UUID;
import net.jji.cullinggames.CullingGames;
import net.jji.cullinggames.config.CgConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** Point awards and admin point changes. Points are private: only the player and admins see them. */
public final class Points {
    private Points() {}

    /** Admin change. Returns the new total. Any actual change resets the player's timer. */
    public static int set(MinecraftServer server, UUID id, int value) {
        EventState state = EventState.get(server);
        if (state.setPoints(id, Math.max(0, value))) {
            Penalties.unseal(server, state, id, true);
        }
        return state.points(id);
    }

    public static int add(MinecraftServer server, UUID id, int delta) {
        return set(server, id, EventState.get(server).points(id) + delta);
    }

    /** Lowest priority and not when cancelled, so totems and other mods that prevent death don't count. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        MinecraftServer server = victim.getServer();
        if (server == null || victim.level().isClientSide) {
            return;
        }
        EventState state = EventState.get(server);
        if (state.phase() != EventPhase.RUNNING) {
            return;
        }
        ServerPlayer killer = resolveKiller(event.getSource());
        if (killer == null || killer == victim || !state.isParticipant(killer.getUUID())) {
            return;
        }
        int award = pointsFor(state, victim);
        if (award <= 0) {
            return;
        }
        boolean victimIsPlayer = victim instanceof ServerPlayer;
        String victimName = victimIsPlayer
                ? ((ServerPlayer) victim).getGameProfile().getName()
                : entityId(victim);
        state.recordKill(new KillRecord(killer.getUUID(), killer.getGameProfile().getName(),
                victimIsPlayer ? victim.getUUID() : null, victimName,
                System.currentTimeMillis(), state.runningTicks(), victim.level().dimension().location().toString(),
                victim.getBlockX(), victim.getBlockY(), victim.getBlockZ(), award));
        int total = add(server, killer.getUUID(), award);
        Kogane.tell(killer, "+" + award + " points. You have " + total + ".");
        CullingGames.LOGGER.debug("{} killed {} for {} points", killer.getGameProfile().getName(), victimName, award);
    }

    /** The player behind the damage: direct attacker, projectile owner, or owner of a tamed/summoned entity. */
    static ServerPlayer resolveKiller(DamageSource source) {
        Entity entity = source.getEntity();
        if (entity instanceof ServerPlayer player) {
            return player;
        }
        if (entity instanceof OwnableEntity ownable && ownable.getOwner() instanceof ServerPlayer owner) {
            return owner;
        }
        return null;
    }

    static int pointsFor(EventState state, LivingEntity victim) {
        if (victim instanceof ServerPlayer player) {
            return state.isParticipant(player.getUUID()) ? CgConfig.PLAYER_KILL_POINTS.get() : 0;
        }
        if (CgConfig.WEAK_TARGETS_ALL_HOSTILE.get() && victim instanceof Enemy) {
            return CgConfig.WEAK_KILL_POINTS.get();
        }
        String id = entityId(victim);
        return CgConfig.WEAK_TARGETS_EXTRA.get().contains(id) ? CgConfig.WEAK_KILL_POINTS.get() : 0;
    }

    static String entityId(LivingEntity entity) {
        var key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return key == null ? "unknown" : key.toString();
    }
}
