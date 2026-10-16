package net.jji.cullinggames.shop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Kogane's reward shop: the catalogue admins set up, and the ledger of what each player has bought.
 * Per-event counters are keyed to the event number and reset when a new event's rewards open.
 * Weapon fragments, once-only purchases and pending deliveries persist across events.
 */
public final class RewardStore extends SavedData {
    private static final String DATA_NAME = "cullinggames_rewards";

    public enum Type {
        /** Gives a copy of the stored stack. */
        ITEM,
        /** Runs a command as the server; %player% is replaced with the buyer's name. */
        COMMAND,
        /** Adds one fragment of the stored weapon; a full set gives the weapon. */
        FRAGMENT
    }

    /** One reward. Mutable so admins can change single fields with /cg reward set. */
    public static final class Reward {
        private final String id;
        private final Type type;
        private String name;
        private ItemStack stack;
        private String command;
        private int price;
        /** 0 = unlimited. */
        private int stock;
        /** 0 = unlimited. */
        private int perPlayer;
        private boolean once;
        /** Advancement id the buyer must have, or "". */
        private String requires;
        /** Advancement id that makes the reward pointless (already has it), or "". */
        private String blockedBy;
        private int fragmentsNeeded;

        public Reward(String id, Type type, String name, ItemStack stack, String command, int price) {
            this.id = id;
            this.type = type;
            this.name = name;
            this.stack = stack.copy();
            this.command = command;
            this.price = price;
            this.requires = "";
            this.blockedBy = "";
            this.fragmentsNeeded = 1;
        }

        public String id() { return id; }
        public Type type() { return type; }
        public String name() { return name; }
        public ItemStack stack() { return stack; }
        public String command() { return command; }
        public int price() { return price; }
        public int stock() { return stock; }
        public int perPlayer() { return perPlayer; }
        public boolean once() { return once; }
        public String requires() { return requires; }
        public String blockedBy() { return blockedBy; }
        public int fragmentsNeeded() { return fragmentsNeeded; }

        public Reward name(String v) { name = v; return this; }
        public Reward stack(ItemStack v) { stack = v.copy(); return this; }
        public Reward command(String v) { command = v; return this; }
        public Reward price(int v) { price = v; return this; }
        public Reward stock(int v) { stock = v; return this; }
        public Reward perPlayer(int v) { perPlayer = v; return this; }
        public Reward once(boolean v) { once = v; return this; }
        public Reward requires(String v) { requires = v; return this; }
        public Reward blockedBy(String v) { blockedBy = v; return this; }
        public Reward fragmentsNeeded(int v) { fragmentsNeeded = Math.max(1, v); return this; }

        /** Fragments are counted per weapon item, so two rewards for the same weapon share progress. */
        public String fragmentKey() {
            return net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("id", id);
            t.putString("type", type.name());
            t.putString("name", name);
            t.put("stack", stack.save(new CompoundTag()));
            t.putString("command", command);
            t.putInt("price", price);
            t.putInt("stock", stock);
            t.putInt("perPlayer", perPlayer);
            t.putBoolean("once", once);
            t.putString("requires", requires);
            t.putString("blockedBy", blockedBy);
            t.putInt("fragmentsNeeded", fragmentsNeeded);
            return t;
        }

        static Reward load(CompoundTag t) {
            Type type;
            try {
                type = Type.valueOf(t.getString("type"));
            } catch (IllegalArgumentException e) {
                return null;
            }
            return new Reward(t.getString("id"), type, t.getString("name"), ItemStack.of(t.getCompound("stack")),
                    t.getString("command"), t.getInt("price"))
                    .stock(t.getInt("stock")).perPlayer(t.getInt("perPlayer")).once(t.getBoolean("once"))
                    .requires(t.getString("requires")).blockedBy(t.getString("blockedBy"))
                    .fragmentsNeeded(t.getInt("fragmentsNeeded"));
        }
    }

    private final List<Reward> rewards = new ArrayList<>();
    private boolean seeded;
    /** Event the per-event counters belong to. */
    private int ledgerEvent;
    private final Map<UUID, Map<String, Integer>> boughtThisEvent = new HashMap<>();
    private final Map<String, Integer> soldThisEvent = new HashMap<>();
    private final Map<UUID, Set<String>> boughtEver = new HashMap<>();
    /** Player → weapon item id → fragments held. */
    private final Map<UUID, Map<String, Integer>> fragments = new HashMap<>();
    /** Rewards that didn't fit in the inventory. */
    private final Map<UUID, List<ItemStack>> pending = new HashMap<>();

    public static RewardStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(RewardStore::load, RewardStore::new, DATA_NAME);
    }

    private static RewardStore load(CompoundTag tag) {
        RewardStore s = new RewardStore();
        for (Tag t : tag.getList("rewards", Tag.TAG_COMPOUND)) {
            Reward r = Reward.load((CompoundTag) t);
            if (r != null) {
                s.rewards.add(r);
            }
        }
        s.seeded = tag.getBoolean("seeded");
        s.ledgerEvent = tag.getInt("ledgerEvent");
        readCounts(tag.getCompound("boughtThisEvent"), s.boughtThisEvent);
        CompoundTag sold = tag.getCompound("soldThisEvent");
        for (String k : sold.getAllKeys()) {
            s.soldThisEvent.put(k, sold.getInt(k));
        }
        CompoundTag ever = tag.getCompound("boughtEver");
        for (String k : ever.getAllKeys()) {
            Set<String> ids = new HashSet<>();
            for (Tag t : ever.getList(k, Tag.TAG_STRING)) {
                ids.add(t.getAsString());
            }
            s.boughtEver.put(UUID.fromString(k), ids);
        }
        readCounts(tag.getCompound("fragments"), s.fragments);
        CompoundTag pend = tag.getCompound("pending");
        for (String k : pend.getAllKeys()) {
            List<ItemStack> stacks = new ArrayList<>();
            for (Tag t : pend.getList(k, Tag.TAG_COMPOUND)) {
                ItemStack st = ItemStack.of((CompoundTag) t);
                if (!st.isEmpty()) {
                    stacks.add(st);
                }
            }
            s.pending.put(UUID.fromString(k), stacks);
        }
        return s;
    }

    private static void readCounts(CompoundTag tag, Map<UUID, Map<String, Integer>> into) {
        for (String k : tag.getAllKeys()) {
            CompoundTag inner = tag.getCompound(k);
            Map<String, Integer> m = new HashMap<>();
            for (String r : inner.getAllKeys()) {
                m.put(r, inner.getInt(r));
            }
            into.put(UUID.fromString(k), m);
        }
    }

    private static CompoundTag writeCounts(Map<UUID, Map<String, Integer>> from) {
        CompoundTag tag = new CompoundTag();
        from.forEach((id, m) -> {
            CompoundTag inner = new CompoundTag();
            m.forEach(inner::putInt);
            tag.put(id.toString(), inner);
        });
        return tag;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        rewards.forEach(r -> list.add(r.save()));
        tag.put("rewards", list);
        tag.putBoolean("seeded", seeded);
        tag.putInt("ledgerEvent", ledgerEvent);
        tag.put("boughtThisEvent", writeCounts(boughtThisEvent));
        CompoundTag sold = new CompoundTag();
        soldThisEvent.forEach(sold::putInt);
        tag.put("soldThisEvent", sold);
        CompoundTag ever = new CompoundTag();
        boughtEver.forEach((id, ids) -> {
            ListTag l = new ListTag();
            ids.forEach(r -> l.add(StringTag.valueOf(r)));
            ever.put(id.toString(), l);
        });
        tag.put("boughtEver", ever);
        tag.put("fragments", writeCounts(fragments));
        CompoundTag pend = new CompoundTag();
        pending.forEach((id, stacks) -> {
            ListTag l = new ListTag();
            stacks.forEach(st -> l.add(st.save(new CompoundTag())));
            pend.put(id.toString(), l);
        });
        tag.put("pending", pend);
        return tag;
    }

    // --- catalogue ---

    public List<Reward> all() {
        return Collections.unmodifiableList(rewards);
    }

    public Reward get(String id) {
        for (Reward r : rewards) {
            if (r.id().equalsIgnoreCase(id)) {
                return r;
            }
        }
        return null;
    }

    /** False if the id is taken. */
    public boolean add(Reward reward) {
        if (get(reward.id()) != null) {
            return false;
        }
        rewards.add(reward);
        setDirty();
        return true;
    }

    public boolean remove(String id) {
        boolean removed = rewards.removeIf(r -> r.id().equalsIgnoreCase(id));
        if (removed) {
            setDirty();
        }
        return removed;
    }

    public boolean seeded() {
        return seeded;
    }

    public void markSeeded() {
        seeded = true;
        setDirty();
    }

    /** Call after editing a reward in place. */
    public void changed() {
        setDirty();
    }

    // --- ledger ---

    /** Starts fresh per-event counters when the rewards of a new event open. */
    public void useEvent(int eventNumber) {
        if (ledgerEvent != eventNumber) {
            ledgerEvent = eventNumber;
            boughtThisEvent.clear();
            soldThisEvent.clear();
            setDirty();
        }
    }

    public int boughtThisEvent(UUID player, String rewardId) {
        return boughtThisEvent.getOrDefault(player, Map.of()).getOrDefault(rewardId, 0);
    }

    /** Sum over all rewards of one type bought this event by the player. */
    public int boughtThisEvent(UUID player, Type type) {
        int n = 0;
        for (Map.Entry<String, Integer> e : boughtThisEvent.getOrDefault(player, Map.of()).entrySet()) {
            Reward r = get(e.getKey());
            if (r != null && r.type() == type) {
                n += e.getValue();
            }
        }
        return n;
    }

    public int soldThisEvent(String rewardId) {
        return soldThisEvent.getOrDefault(rewardId, 0);
    }

    public boolean boughtEver(UUID player, String rewardId) {
        return boughtEver.getOrDefault(player, Set.of()).contains(rewardId);
    }

    public void recordPurchase(UUID player, String rewardId) {
        boughtThisEvent.computeIfAbsent(player, k -> new HashMap<>()).merge(rewardId, 1, Integer::sum);
        soldThisEvent.merge(rewardId, 1, Integer::sum);
        boughtEver.computeIfAbsent(player, k -> new HashSet<>()).add(rewardId);
        setDirty();
    }

    public int fragments(UUID player, String weapon) {
        return fragments.getOrDefault(player, Map.of()).getOrDefault(weapon, 0);
    }

    public Map<String, Integer> fragments(UUID player) {
        return Collections.unmodifiableMap(fragments.getOrDefault(player, Map.of()));
    }

    public void setFragments(UUID player, String weapon, int count) {
        Map<String, Integer> m = fragments.computeIfAbsent(player, k -> new HashMap<>());
        if (count <= 0) {
            m.remove(weapon);
        } else {
            m.put(weapon, count);
        }
        setDirty();
    }

    public void addPending(UUID player, ItemStack stack) {
        pending.computeIfAbsent(player, k -> new ArrayList<>()).add(stack.copy());
        setDirty();
    }

    public boolean hasPending(UUID player) {
        return !pending.getOrDefault(player, List.of()).isEmpty();
    }

    /** Removes and returns everything pending for the player. */
    public List<ItemStack> takePending(UUID player) {
        List<ItemStack> stacks = pending.remove(player);
        if (stacks != null) {
            setDirty();
        }
        return stacks == null ? List.of() : stacks;
    }
}
