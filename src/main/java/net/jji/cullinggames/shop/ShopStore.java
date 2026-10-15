package net.jji.cullinggames.shop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/** Items admins stock in Kogane's shop. Kept between events. Shop ids are 1-based list positions. */
public final class ShopStore extends SavedData {
    private static final String DATA_NAME = "cullinggames_shop";

    public record Entry(ItemStack stack, int price) {}

    private final List<Entry> entries = new ArrayList<>();

    public static ShopStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ShopStore::load, ShopStore::new, DATA_NAME);
    }

    private static ShopStore load(CompoundTag tag) {
        ShopStore s = new ShopStore();
        for (Tag t : tag.getList("items", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            ItemStack stack = ItemStack.of(c.getCompound("stack"));
            if (!stack.isEmpty()) {
                s.entries.add(new Entry(stack, c.getInt("price")));
            }
        }
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Entry e : entries) {
            CompoundTag c = new CompoundTag();
            c.put("stack", e.stack().save(new CompoundTag()));
            c.putInt("price", e.price());
            list.add(c);
        }
        tag.put("items", list);
        return tag;
    }

    /** Returns the new item's shop id. */
    public int add(ItemStack stack, int price) {
        entries.add(new Entry(stack.copy(), price));
        setDirty();
        return entries.size();
    }

    public boolean remove(int id) {
        if (id < 1 || id > entries.size()) {
            return false;
        }
        entries.remove(id - 1);
        setDirty();
        return true;
    }

    public Entry get(int id) {
        return id < 1 || id > entries.size() ? null : entries.get(id - 1);
    }

    public List<Entry> all() {
        return Collections.unmodifiableList(entries);
    }
}
