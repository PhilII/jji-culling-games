package net.jji.cullinggames.colony;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** All colonies, for every dimension, saved with the overworld. Names are case-insensitive. */
public final class ColonyStore extends SavedData {
    private static final String DATA_NAME = "cullinggames_colonies";

    private final Map<String, Colony> colonies = new LinkedHashMap<>();

    public static ColonyStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ColonyStore::load, ColonyStore::new, DATA_NAME);
    }

    private static ColonyStore load(CompoundTag tag) {
        ColonyStore s = new ColonyStore();
        for (Tag t : tag.getList("colonies", Tag.TAG_COMPOUND)) {
            Colony c = Colony.load((CompoundTag) t);
            if (c != null) {
                s.colonies.put(key(c.name()), c);
            }
        }
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Colony c : colonies.values()) {
            list.add(c.save());
        }
        tag.put("colonies", list);
        return tag;
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public boolean add(Colony colony) {
        if (colonies.containsKey(key(colony.name()))) {
            return false;
        }
        colonies.put(key(colony.name()), colony);
        setDirty();
        return true;
    }

    public boolean remove(String name) {
        boolean removed = colonies.remove(key(name)) != null;
        if (removed) {
            setDirty();
        }
        return removed;
    }

    public Colony get(String name) {
        return name == null ? null : colonies.get(key(name));
    }

    public Collection<Colony> all() {
        return Collections.unmodifiableCollection(colonies.values());
    }

    public boolean isEmpty() {
        return colonies.isEmpty();
    }
}
