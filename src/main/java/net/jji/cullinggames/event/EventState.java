package net.jji.cullinggames.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Event state saved with the overworld, so a running event survives a restart. */
public final class EventState extends SavedData {
    private static final String DATA_NAME = "cullinggames_event";

    private EventPhase phase = EventPhase.IDLE;
    private PenaltyMode penalty = PenaltyMode.SEAL_CT;
    private long startedAtMillis;
    /** Ticks the event has been RUNNING (paused time is not counted). */
    private long runningTicks;
    private int eventNumber;
    private final Set<UUID> participants = new LinkedHashSet<>();
    /** Participant -> colony name. */
    private final Map<UUID, String> colonyOf = new HashMap<>();
    /** Points per player. Kept after the event stops (final standings) until the next start. */
    private final Map<UUID, Integer> points = new HashMap<>();
    /** runningTicks at the player's last point change, or when they joined. Drives the step 4 timer. */
    private final Map<UUID, Long> lastPointChange = new HashMap<>();
    /** Kill log, oldest first. Kept after the event stops until the next start. */
    private final List<KillRecord> kills = new ArrayList<>();

    public static EventState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(EventState::load, EventState::new, DATA_NAME);
    }

    static EventState load(CompoundTag tag) {
        EventState s = new EventState();
        s.phase = parseEnum(EventPhase.class, tag.getString("phase"), EventPhase.IDLE);
        s.penalty = parseEnum(PenaltyMode.class, tag.getString("penalty"), PenaltyMode.SEAL_CT);
        s.startedAtMillis = tag.getLong("startedAtMillis");
        s.runningTicks = tag.getLong("runningTicks");
        s.eventNumber = tag.getInt("eventNumber");
        for (Tag t : tag.getList("participants", Tag.TAG_INT_ARRAY)) {
            s.participants.add(NbtUtils.loadUUID(t));
        }
        CompoundTag colonies = tag.getCompound("colonyOf");
        for (String key : colonies.getAllKeys()) {
            UUID id = parseUuid(key);
            if (id != null) {
                s.colonyOf.put(id, colonies.getString(key));
            }
        }
        CompoundTag pts = tag.getCompound("points");
        for (String key : pts.getAllKeys()) {
            UUID id = parseUuid(key);
            if (id != null) {
                s.points.put(id, pts.getInt(key));
            }
        }
        CompoundTag last = tag.getCompound("lastPointChange");
        for (String key : last.getAllKeys()) {
            UUID id = parseUuid(key);
            if (id != null) {
                s.lastPointChange.put(id, last.getLong(key));
            }
        }
        for (Tag t : tag.getList("kills", Tag.TAG_COMPOUND)) {
            KillRecord k = KillRecord.load((CompoundTag) t);
            if (k != null) {
                s.kills.add(k);
            }
        }
        return s;
    }

    private static UUID parseUuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putString("phase", phase.name());
        tag.putString("penalty", penalty.name());
        tag.putLong("startedAtMillis", startedAtMillis);
        tag.putLong("runningTicks", runningTicks);
        tag.putInt("eventNumber", eventNumber);
        ListTag list = new ListTag();
        for (UUID id : participants) {
            list.add(NbtUtils.createUUID(id));
        }
        tag.put("participants", list);
        CompoundTag colonies = new CompoundTag();
        colonyOf.forEach((id, name) -> colonies.putString(id.toString(), name));
        tag.put("colonyOf", colonies);
        CompoundTag pts = new CompoundTag();
        points.forEach((id, v) -> pts.putInt(id.toString(), v));
        tag.put("points", pts);
        CompoundTag last = new CompoundTag();
        lastPointChange.forEach((id, v) -> last.putLong(id.toString(), v));
        tag.put("lastPointChange", last);
        ListTag killList = new ListTag();
        for (KillRecord k : kills) {
            killList.add(k.save());
        }
        tag.put("kills", killList);
        return tag;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    // --- transitions (EventManager is the only caller) ---

    void begin(PenaltyMode penalty, long nowMillis) {
        this.phase = EventPhase.RUNNING;
        this.penalty = penalty;
        this.startedAtMillis = nowMillis;
        this.runningTicks = 0;
        this.eventNumber++;
        this.participants.clear();
        this.colonyOf.clear();
        this.points.clear();
        this.lastPointChange.clear();
        this.kills.clear();
        setDirty();
    }

    void setPhase(EventPhase phase) {
        this.phase = phase;
        setDirty();
    }

    void end() {
        this.phase = EventPhase.IDLE;
        this.participants.clear();
        this.colonyOf.clear();
        setDirty();
    }

    void tickRunning() {
        runningTicks++;
        if (runningTicks % 20 == 0) {
            setDirty();
        }
    }

    boolean addParticipant(UUID id) {
        boolean added = participants.add(id);
        if (added) {
            points.putIfAbsent(id, 0);
            lastPointChange.put(id, runningTicks);
            setDirty();
        }
        return added;
    }

    boolean removeParticipant(UUID id) {
        boolean removed = participants.remove(id);
        colonyOf.remove(id);
        if (removed) {
            setDirty();
        }
        return removed;
    }

    void assignColony(UUID id, String colony) {
        colonyOf.put(id, colony);
        setDirty();
    }

    /** Sets a player's points. Returns true if the value changed (which resets their timer). */
    boolean setPoints(UUID id, int value) {
        int old = points.getOrDefault(id, 0);
        if (old == value) {
            return false;
        }
        points.put(id, value);
        lastPointChange.put(id, runningTicks);
        setDirty();
        return true;
    }

    void recordKill(KillRecord kill) {
        kills.add(kill);
        setDirty();
    }

    // --- reads ---

    public int points(UUID id) {
        return points.getOrDefault(id, 0);
    }

    /** Ticks of running time since the player's last point change (or since they joined). */
    public long ticksSincePointChange(UUID id) {
        return runningTicks - lastPointChange.getOrDefault(id, runningTicks);
    }

    public Map<UUID, Integer> allPoints() {
        return Collections.unmodifiableMap(points);
    }

    public List<KillRecord> kills() {
        return Collections.unmodifiableList(kills);
    }

    public String colonyOf(UUID id) {
        return colonyOf.get(id);
    }

    /** Participants currently assigned to the named colony (case-insensitive). */
    public long countIn(String colony) {
        return colonyOf.values().stream().filter(colony::equalsIgnoreCase).count();
    }

    public EventPhase phase() {
        return phase;
    }

    public PenaltyMode penalty() {
        return penalty;
    }

    public long startedAtMillis() {
        return startedAtMillis;
    }

    public long runningTicks() {
        return runningTicks;
    }

    public int eventNumber() {
        return eventNumber;
    }

    public Set<UUID> participants() {
        return Collections.unmodifiableSet(participants);
    }

    public boolean isParticipant(UUID id) {
        return participants.contains(id);
    }
}
