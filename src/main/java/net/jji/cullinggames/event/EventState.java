package net.jji.cullinggames.event;

import java.util.Collections;
import java.util.LinkedHashSet;
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

    public static EventState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(EventState::load, EventState::new, DATA_NAME);
    }

    private static EventState load(CompoundTag tag) {
        EventState s = new EventState();
        s.phase = parseEnum(EventPhase.class, tag.getString("phase"), EventPhase.IDLE);
        s.penalty = parseEnum(PenaltyMode.class, tag.getString("penalty"), PenaltyMode.SEAL_CT);
        s.startedAtMillis = tag.getLong("startedAtMillis");
        s.runningTicks = tag.getLong("runningTicks");
        s.eventNumber = tag.getInt("eventNumber");
        for (Tag t : tag.getList("participants", Tag.TAG_INT_ARRAY)) {
            s.participants.add(NbtUtils.loadUUID(t));
        }
        return s;
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
        setDirty();
    }

    void setPhase(EventPhase phase) {
        this.phase = phase;
        setDirty();
    }

    void end() {
        this.phase = EventPhase.IDLE;
        this.participants.clear();
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
            setDirty();
        }
        return added;
    }

    boolean removeParticipant(UUID id) {
        boolean removed = participants.remove(id);
        if (removed) {
            setDirty();
        }
        return removed;
    }

    // --- reads ---

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
