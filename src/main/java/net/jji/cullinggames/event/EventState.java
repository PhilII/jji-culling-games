package net.jji.cullinggames.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
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
    /** Lives each participant starts with in this event. */
    private int startingLives = 3;
    private final Map<UUID, Integer> lives = new HashMap<>();
    private final Set<UUID> eliminated = new HashSet<>();
    /** Sealed participant -> cursed energy at the moment of sealing (restored on unseal). */
    private final Map<UUID, Double> sealed = new HashMap<>();
    /** Game mode an eliminated participant had before being made a spectator. */
    private final Map<UUID, String> gameModeBefore = new HashMap<>();
    /**
     * Restores owed to players who were offline when the event ended or when they were unsealed.
     * Survives the next event start; an entry is removed once applied at login.
     */
    private final Map<UUID, CompoundTag> restoreOnLogin = new HashMap<>();
    /** Services each player has bought this event (leaderboard, transfer, killlog). */
    private final Map<UUID, Set<String>> unlocks = new HashMap<>();
    /** Rules added this event (EventRule names). */
    private final Set<String> rules = new LinkedHashSet<>();
    /** Participants who got a kill or an assist since they last had points, so they are not hunted at 0 points. */
    private final Set<UUID> huntCleared = new HashSet<>();
    /** runningTicks at which the event ends by itself, or -1. */
    private long endAtTicks = -1;

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
        s.startingLives = tag.contains("startingLives") ? tag.getInt("startingLives") : 3;
        CompoundTag livesTag = tag.getCompound("lives");
        for (String key : livesTag.getAllKeys()) {
            UUID id = parseUuid(key);
            if (id != null) {
                s.lives.put(id, livesTag.getInt(key));
            }
        }
        for (Tag t : tag.getList("eliminated", Tag.TAG_INT_ARRAY)) {
            s.eliminated.add(NbtUtils.loadUUID(t));
        }
        CompoundTag sealedTag = tag.getCompound("sealed");
        for (String key : sealedTag.getAllKeys()) {
            UUID id = parseUuid(key);
            if (id != null) {
                s.sealed.put(id, sealedTag.getDouble(key));
            }
        }
        CompoundTag modes = tag.getCompound("gameModeBefore");
        for (String key : modes.getAllKeys()) {
            UUID id = parseUuid(key);
            if (id != null) {
                s.gameModeBefore.put(id, modes.getString(key));
            }
        }
        CompoundTag restores = tag.getCompound("restoreOnLogin");
        for (String key : restores.getAllKeys()) {
            UUID id = parseUuid(key);
            if (id != null) {
                s.restoreOnLogin.put(id, restores.getCompound(key));
            }
        }
        CompoundTag unlockTag = tag.getCompound("unlocks");
        for (String key : unlockTag.getAllKeys()) {
            UUID id = parseUuid(key);
            if (id != null) {
                Set<String> set = new HashSet<>();
                for (Tag t : unlockTag.getList(key, Tag.TAG_STRING)) {
                    set.add(t.getAsString());
                }
                s.unlocks.put(id, set);
            }
        }
        for (Tag t : tag.getList("rules", Tag.TAG_STRING)) {
            s.rules.add(t.getAsString());
        }
        for (Tag t : tag.getList("huntCleared", Tag.TAG_INT_ARRAY)) {
            s.huntCleared.add(NbtUtils.loadUUID(t));
        }
        s.endAtTicks = tag.contains("endAtTicks") ? tag.getLong("endAtTicks") : -1;
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
        tag.putInt("startingLives", startingLives);
        CompoundTag livesTag = new CompoundTag();
        lives.forEach((id, v) -> livesTag.putInt(id.toString(), v));
        tag.put("lives", livesTag);
        ListTag elim = new ListTag();
        for (UUID id : eliminated) {
            elim.add(NbtUtils.createUUID(id));
        }
        tag.put("eliminated", elim);
        CompoundTag sealedTag = new CompoundTag();
        sealed.forEach((id, v) -> sealedTag.putDouble(id.toString(), v));
        tag.put("sealed", sealedTag);
        CompoundTag modes = new CompoundTag();
        gameModeBefore.forEach((id, v) -> modes.putString(id.toString(), v));
        tag.put("gameModeBefore", modes);
        CompoundTag restores = new CompoundTag();
        restoreOnLogin.forEach((id, v) -> restores.put(id.toString(), v.copy()));
        tag.put("restoreOnLogin", restores);
        CompoundTag unlockTag = new CompoundTag();
        unlocks.forEach((id, set) -> {
            ListTag l = new ListTag();
            set.forEach(v -> l.add(StringTag.valueOf(v)));
            unlockTag.put(id.toString(), l);
        });
        tag.put("unlocks", unlockTag);
        ListTag ruleList = new ListTag();
        rules.forEach(r -> ruleList.add(StringTag.valueOf(r)));
        tag.put("rules", ruleList);
        ListTag cleared = new ListTag();
        for (UUID id : huntCleared) {
            cleared.add(NbtUtils.createUUID(id));
        }
        tag.put("huntCleared", cleared);
        tag.putLong("endAtTicks", endAtTicks);
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

    void begin(PenaltyMode penalty, int startingLives, long nowMillis) {
        this.startingLives = startingLives;
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
        this.lives.clear();
        this.eliminated.clear();
        this.sealed.clear();
        this.gameModeBefore.clear();
        this.unlocks.clear();
        this.rules.clear();
        this.huntCleared.clear();
        this.endAtTicks = -1;
        setDirty();
    }

    void setPhase(EventPhase phase) {
        this.phase = phase;
        setDirty();
    }

    /** Penalties.onEventEnd must run first: it restores sealed and eliminated players. */
    void end() {
        this.phase = EventPhase.IDLE;
        this.participants.clear();
        this.colonyOf.clear();
        this.sealed.clear();
        this.eliminated.clear();
        this.gameModeBefore.clear();
        this.endAtTicks = -1;
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
            lives.putIfAbsent(id, startingLives);
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

    /**
     * Sets a player's points. Returns true if they went up. Only gains reset the timer:
     * otherwise spending a single point would dodge the penalty.
     */
    boolean setPoints(UUID id, int value) {
        int old = points.getOrDefault(id, 0);
        if (old == value) {
            return false;
        }
        points.put(id, value);
        if (value == 0) {
            huntCleared.remove(id);
        }
        boolean gained = value > old;
        if (gained) {
            lastPointChange.put(id, runningTicks);
        }
        setDirty();
        return gained;
    }

    boolean unlock(UUID id, String service) {
        boolean added = unlocks.computeIfAbsent(id, k -> new HashSet<>()).add(service);
        if (added) {
            setDirty();
        }
        return added;
    }

    boolean addRule(String rule) {
        boolean added = rules.add(rule);
        if (added) {
            setDirty();
        }
        return added;
    }

    /** A kill or an assist: the player is no longer hunted, even at 0 points, until they drop to 0 again. */
    void clearHunt(UUID id) {
        if (huntCleared.add(id)) {
            setDirty();
        }
    }

    void setEndAt(long ticks) {
        endAtTicks = ticks;
        setDirty();
    }

    /**
     * Holds an offline participant's timer for one running tick, so time spent logged out (overnight in a
     * weekend event) never counts towards the penalty.
     */
    void holdTimer(UUID id) {
        lastPointChange.computeIfPresent(id, (k, v) -> v + 1);
    }

    /** Restarts the player's timer without a point change (after a life-loss penalty). */
    void resetTimer(UUID id) {
        lastPointChange.put(id, runningTicks);
        setDirty();
    }

    /** Takes one life. Returns lives left; at 0 the player is marked eliminated. */
    int loseLife(UUID id) {
        int left = Math.max(0, lives.getOrDefault(id, startingLives) - 1);
        lives.put(id, left);
        if (left == 0) {
            eliminated.add(id);
        }
        setDirty();
        return left;
    }

    void markSealed(UUID id, double cursedEnergy) {
        sealed.put(id, cursedEnergy);
        setDirty();
    }

    /** Returns the saved cursed energy, or null if the player was not sealed. */
    Double clearSealed(UUID id) {
        Double ce = sealed.remove(id);
        if (ce != null) {
            setDirty();
        }
        return ce;
    }

    void rememberGameMode(UUID id, String mode) {
        gameModeBefore.putIfAbsent(id, mode);
        setDirty();
    }

    String takeGameMode(UUID id) {
        String mode = gameModeBefore.remove(id);
        if (mode != null) {
            setDirty();
        }
        return mode;
    }

    void addRestore(UUID id, CompoundTag restore) {
        CompoundTag merged = restoreOnLogin.computeIfAbsent(id, k -> new CompoundTag());
        merged.merge(restore);
        setDirty();
    }

    CompoundTag takeRestore(UUID id) {
        CompoundTag tag = restoreOnLogin.remove(id);
        if (tag != null) {
            setDirty();
        }
        return tag;
    }

    void recordKill(KillRecord kill) {
        kills.add(kill);
        setDirty();
    }

    // --- reads ---

    /** At 0 points with no kill or assist since: glows now and then (Red's hunted rule). */
    public boolean isHunted(UUID id) {
        return isParticipant(id) && !isEliminated(id) && points(id) == 0 && !huntCleared.contains(id);
    }

    public boolean hasUnlocked(UUID id, String service) {
        return unlocks.getOrDefault(id, Set.of()).contains(service);
    }

    public boolean hasRule(String rule) {
        return rules.contains(rule);
    }

    public Set<String> rules() {
        return Collections.unmodifiableSet(rules);
    }

    public long endAtTicks() {
        return endAtTicks;
    }

    public int startingLives() {
        return startingLives;
    }

    public int lives(UUID id) {
        return lives.getOrDefault(id, startingLives);
    }

    public boolean isEliminated(UUID id) {
        return eliminated.contains(id);
    }

    public boolean isSealed(UUID id) {
        return sealed.containsKey(id);
    }

    public Set<UUID> sealedPlayers() {
        return Collections.unmodifiableSet(sealed.keySet());
    }

    public Set<UUID> eliminatedPlayers() {
        return Collections.unmodifiableSet(eliminated);
    }

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
