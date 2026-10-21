package net.jji.cullinggames.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import net.jji.cullinggames.CullingGames;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Soft link to Jujutsu Craft's player variables (capability "jujutsucraft:player_variables").
 * Reached by reflection so the mod runs without Jujutsu Craft; every call is a no-op when it is missing.
 * Only cursed energy is changed. The CT fields are never written: Jujutsu Craft re-rolls CT values of 0 or below
 * on respawn and other mods (Alm ownership, ReBalanced) key off the CT, so touching it is unsafe.
 */
public final class JujutsuCraft {
    private static final String VARS_CLASS = "net.mcreator.jujutsucraft.network.JujutsucraftModVariables";
    private static boolean resolved;
    private static Capability<?> capability;
    private static Field cursePower;
    private static Field cursePowerMax;
    private static Field technique;
    private static Field technique2;
    private static Method sync;

    private JujutsuCraft() {}

    public static boolean isLoaded() {
        return ModList.get().isLoaded("jujutsucraft") && resolve();
    }

    private static synchronized boolean resolve() {
        if (resolved) {
            return capability != null;
        }
        resolved = true;
        try {
            Class<?> vars = Class.forName(VARS_CLASS);
            capability = (Capability<?>) vars.getField("PLAYER_VARIABLES_CAPABILITY").get(null);
            Class<?> pv = Class.forName(VARS_CLASS + "$PlayerVariables");
            cursePower = pv.getField("PlayerCursePower");
            cursePowerMax = pv.getField("PlayerCursePowerMAX");
            technique = pv.getField("PlayerCurseTechnique");
            technique2 = pv.getField("PlayerCurseTechnique2");
            sync = pv.getMethod("syncPlayerVariables", Entity.class);
            return true;
        } catch (ReflectiveOperationException | ClassCastException e) {
            CullingGames.LOGGER.error("Jujutsu Craft is loaded but its player variables could not be reached; CT seal is disabled", e);
            capability = null;
            return false;
        }
    }

    private static Object vars(ServerPlayer player) {
        if (!isLoaded()) {
            return null;
        }
        return player.getCapability(capability).resolve().orElse(null);
    }

    public static double cursedEnergy(ServerPlayer player) {
        return getDouble(player, cursePower);
    }

    public static double maxCursedEnergy(ServerPlayer player) {
        return getDouble(player, cursePowerMax);
    }

    public static double technique(ServerPlayer player) {
        return getDouble(player, technique);
    }

    public static double secondTechnique(ServerPlayer player) {
        return getDouble(player, technique2);
    }

    /** Sets cursed energy and syncs to the client. Returns false if Jujutsu Craft is missing. */
    public static boolean setCursedEnergy(ServerPlayer player, double value) {
        Object v = vars(player);
        if (v == null) {
            return false;
        }
        try {
            cursePower.setDouble(v, value);
            sync.invoke(v, player);
            return true;
        } catch (ReflectiveOperationException e) {
            CullingGames.LOGGER.error("Could not set cursed energy for {}", player.getGameProfile().getName(), e);
            return false;
        }
    }

    /** Fatigue as a level (0 = none, 1 = Fatigue I...), from Jujutsu Craft's "jujutsucraft:fatigue" effect. */
    public static int fatigueLevel(LivingEntity entity) {
        MobEffect fatigue = effect("jujutsucraft:fatigue");
        MobEffectInstance instance = fatigue == null ? null : entity.getEffect(fatigue);
        return instance == null ? 0 : instance.getAmplifier() + 1;
    }

    /** A registered effect by id (any mod), or null if there is none. Plain registry lookup, no reflection. */
    public static MobEffect effect(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key == null ? null : ForgeRegistries.MOB_EFFECTS.getValue(key);
    }

    private static double getDouble(ServerPlayer player, Field field) {
        Object v = vars(player);
        if (v == null) {
            return Double.NaN;
        }
        try {
            return field.getDouble(v);
        } catch (IllegalAccessException e) {
            return Double.NaN;
        }
    }

    /**
     * The player who summoned this entity (shikigami, Mahoraga, dolls...), from the "OWNER_UUID" string that
     * Jujutsu Craft writes into a summon's persistent data. Null if it is not a summon. Plain NBT, so it
     * works without reflection.
     */
    public static UUID summonOwner(Entity entity) {
        String raw = entity.getPersistentData().getString("OWNER_UUID");
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
