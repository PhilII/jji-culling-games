package net.jji.cullinggames.event;

import java.util.Locale;
import net.jji.cullinggames.compat.JujutsuCraft;
import net.jji.cullinggames.config.CgConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Heal and cursed energy refill for points (Red, 20 Oct). The price grows with what is missing and with Jujutsu
 * Craft fatigue. In battle a player gets recoveriesPerBattle purchases; outside battle only a short cooldown applies.
 */
public enum Recovery {
    HEAL, CE;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Recovery byId(String id) {
        for (Recovery r : values()) {
            if (r.id().equalsIgnoreCase(id)) {
                return r;
            }
        }
        return null;
    }

    /** What a full heal or refill would restore now: health points, or cursed energy. NaN if unknown. */
    double missing(ServerPlayer player) {
        if (this == HEAL) {
            return Math.max(0, player.getMaxHealth() - player.getHealth());
        }
        double max = JujutsuCraft.maxCursedEnergy(player);
        double now = JujutsuCraft.cursedEnergy(player);
        return Double.isNaN(max) || Double.isNaN(now) ? Double.NaN : Math.max(0, max - now);
    }

    /** Points for a full heal or refill now; 0 when nothing is missing. */
    public int price(ServerPlayer player) {
        double missing = missing(player);
        if (Double.isNaN(missing) || missing <= 0) {
            return 0;
        }
        double base = this == HEAL
                ? Math.ceil(missing / 2) * CgConfig.HEAL_POINTS_PER_HEART.get()
                : Math.ceil(missing / CgConfig.CE_PER_POINT.get());
        double factor = 1 + JujutsuCraft.fatigueLevel(player) * CgConfig.FATIGUE_COST_PERCENT.get() / 100.0;
        return (int) Math.min(Integer.MAX_VALUE, Math.ceil(base * factor));
    }

    /** Why this can't be bought now, or "" if it can (points aside). */
    public String blocked(MinecraftServer server, EventState state, ServerPlayer player) {
        if (!CgConfig.RECOVERY_ENABLED.get()) {
            return "Recovery is turned off.";
        }
        if (this == CE && !JujutsuCraft.isLoaded()) {
            return "Cursed energy needs Jujutsu Craft.";
        }
        if (this == CE && state.isSealed(player.getUUID())) {
            return "Your technique is sealed. Score to unseal it.";
        }
        double missing = missing(player);
        if (Double.isNaN(missing) || missing <= 0) {
            return this == HEAL ? "Your health is full." : "Your cursed energy is full.";
        }
        if (Combat.inBattle(server, player.getUUID())) {
            int max = CgConfig.RECOVERIES_PER_BATTLE.get();
            if (Combat.battleUses(player.getUUID()) >= max) {
                return max == 0 ? "No recovery during a battle." : "Once per battle. Get out of the fight first.";
            }
        } else {
            long wait = Combat.cooldownLeft(server, player.getUUID());
            if (wait > 0) {
                return "Wait " + ((wait + 19) / 20) + " s.";
            }
        }
        return "";
    }

    void apply(ServerPlayer player) {
        if (this == HEAL) {
            player.setHealth(player.getMaxHealth());
        } else {
            JujutsuCraft.setCursedEnergy(player, JujutsuCraft.maxCursedEnergy(player));
        }
    }

    public String displayName() {
        return this == HEAL ? "a full heal" : "a cursed energy refill";
    }
}
