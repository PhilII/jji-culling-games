package net.jji.cullinggames.event;

import java.util.UUID;
import net.jji.cullinggames.config.CgConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Kogane's tracker (Red, 20 Oct): every participant carries a compass that points to the nearest other participant.
 * It is a vanilla lodestone compass whose target the server moves, so players without the mod see it work too.
 * It can't be dropped or kept: tossing or dying removes it, the next update gives it back, and the event end takes it.
 */
public final class Tracker {
    static final String TAG = "cullinggamesTracker";
    /** A dimension that doesn't exist: a lodestone compass pointing there spins, which means "nobody to track". */
    private static final String NOWHERE = "cullinggames:nowhere";

    private Tracker() {}

    public static boolean isTracker(ItemStack stack) {
        return stack.is(Items.COMPASS) && stack.getTag() != null && stack.getTag().getBoolean(TAG);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || event.getServer().getTickCount() % (20 * CgConfig.TRACKER_UPDATE_SECONDS.get()) != 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        EventState state = EventState.get(server);
        if (state.phase().isActive()) {
            update(server, state);
        }
    }

    /** Gives, points or takes away every online participant's tracker. Package-visible for tests. */
    static void update(MinecraftServer server, EventState state) {
        boolean on = CgConfig.TRACKER_COMPASS.get();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!on || !canTrack(state, p)) {
                if (!p.isDeadOrDying()) {
                    remove(p);
                }
                continue;
            }
            ServerPlayer target = nearest(server, state, p);
            boolean found = false;
            Inventory inv = p.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (isTracker(stack)) {
                    if (found) {
                        inv.setItem(i, ItemStack.EMPTY);
                    } else {
                        point(stack, target);
                        found = true;
                    }
                }
            }
            if (!found) {
                ItemStack stack = create();
                point(stack, target);
                if (inv.add(stack)) {
                    Kogane.tell(p, "Kogane's tracker points to the nearest player.");
                }
            }
        }
    }

    /** Alive participant still in the game. Eliminated players and spectators don't carry one. */
    private static boolean canTrack(EventState state, ServerPlayer p) {
        return state.isParticipant(p.getUUID()) && !state.isEliminated(p.getUUID())
                && p.gameMode.getGameModeForPlayer() != GameType.SPECTATOR;
    }

    /** The closest other trackable participant in the same dimension, or null. */
    static ServerPlayer nearest(MinecraftServer server, EventState state, ServerPlayer from) {
        ServerPlayer best = null;
        double bestDist = Double.MAX_VALUE;
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other == from || other.level() != from.level() || other.isDeadOrDying() || !canTrack(state, other)) {
                continue;
            }
            double d = other.distanceToSqr(from);
            if (d < bestDist) {
                bestDist = d;
                best = other;
            }
        }
        return best;
    }

    static ItemStack create() {
        ItemStack stack = new ItemStack(Items.COMPASS);
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean(TAG, true);
        stack.setHoverName(Component.literal("Kogane's Tracker").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GOLD)));
        ListTag lore = new ListTag();
        lore.add(StringTag.valueOf(Component.Serializer.toJson(
                Component.literal("Points to the nearest player").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY)))));
        stack.getOrCreateTagElement("display").put("Lore", lore);
        return stack;
    }

    /**
     * Moves the compass target. Untracked (LodestoneTracked false), so vanilla never checks for a real lodestone.
     * Small moves are skipped: every change resends the item and replays the hand animation.
     */
    private static void point(ItemStack stack, ServerPlayer target) {
        CompoundTag tag = stack.getOrCreateTag();
        String dim = target == null ? NOWHERE : target.level().dimension().location().toString();
        BlockPos pos = target == null ? BlockPos.ZERO : target.blockPosition();
        BlockPos old = tag.contains("LodestonePos") ? NbtUtils.readBlockPos(tag.getCompound("LodestonePos")) : null;
        if (dim.equals(tag.getString("LodestoneDimension")) && old != null && old.distSqr(pos) <= 4) {
            return;
        }
        tag.putBoolean("LodestoneTracked", false);
        tag.put("LodestonePos", NbtUtils.writeBlockPos(pos));
        tag.putString("LodestoneDimension", dim);
    }

    /** The compass target as "dimension x y z", for tests. */
    static String targetOf(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("LodestonePos")) {
            return "";
        }
        BlockPos pos = NbtUtils.readBlockPos(tag.getCompound("LodestonePos"));
        return tag.getString("LodestoneDimension") + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    static ItemStack find(ServerPlayer p) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (isTracker(inv.getItem(i))) {
                return inv.getItem(i);
            }
        }
        return ItemStack.EMPTY;
    }

    static void remove(ServerPlayer p) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (isTracker(inv.getItem(i))) {
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    static void removeAll(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            remove(p);
        }
    }

    /** Tossed trackers vanish; the next update gives a fresh one. */
    @SubscribeEvent
    public static void onToss(ItemTossEvent event) {
        if (isTracker(event.getEntity().getItem())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        event.getDrops().removeIf(item -> isTracker(item.getItem()));
    }

    /** Someone who was offline when the event ended still has one: take it at login. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            EventState state = EventState.get(player.getServer());
            UUID id = player.getUUID();
            if (!state.phase().isActive() || !state.isParticipant(id)) {
                remove(player);
            }
        }
    }
}
