package net.jji.cullinggames.event;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/**
 * One kill during an event. victimId is null for non-player victims (victimName is then the entity type).
 * Kept after the event stops, so admins can use it as kill proof until the next event starts.
 */
public record KillRecord(UUID killerId, String killerName, UUID victimId, String victimName,
                         long epochMillis, long runningTicks, String dimension, int x, int y, int z, int points) {

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("killer", killerId);
        t.putString("killerName", killerName);
        if (victimId != null) {
            t.putUUID("victim", victimId);
        }
        t.putString("victimName", victimName);
        t.putLong("time", epochMillis);
        t.putLong("ticks", runningTicks);
        t.putString("dim", dimension);
        t.putIntArray("pos", new int[] {x, y, z});
        t.putInt("points", points);
        return t;
    }

    static KillRecord load(CompoundTag t) {
        int[] pos = t.getIntArray("pos");
        if (!t.hasUUID("killer") || pos.length != 3) {
            return null;
        }
        return new KillRecord(t.getUUID("killer"), t.getString("killerName"),
                t.hasUUID("victim") ? t.getUUID("victim") : null, t.getString("victimName"),
                t.getLong("time"), t.getLong("ticks"), t.getString("dim"), pos[0], pos[1], pos[2], t.getInt("points"));
    }
}
