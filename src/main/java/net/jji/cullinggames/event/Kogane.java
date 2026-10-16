package net.jji.cullinggames.event;

import net.jji.cullinggames.network.AnnouncePacket;
import net.jji.cullinggames.network.CgNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Kogane's voice: chat lines and placeholder sounds (vanilla sounds until custom ones exist). */
public final class Kogane {
    private Kogane() {}

    private static MutableComponent prefix() {
        return Component.literal("[Kogane] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
    }

    public static Component line(String text) {
        return prefix().append(Component.literal(text).withStyle(ChatFormatting.YELLOW));
    }

    public static void broadcast(MinecraftServer server, String text) {
        server.getPlayerList().broadcastSystemMessage(line(text), false);
    }

    public static void tell(ServerPlayer player, String text) {
        player.sendSystemMessage(line(text));
    }

    /** On-screen announcement for players with the mod. Chat messages are sent separately and still reach everyone. */
    public static void announce(ServerPlayer player, AnnouncePacket.Kind kind, String title, String text) {
        if (CgNetwork.hasMod(player)) {
            CgNetwork.send(player, new AnnouncePacket(kind, title, text));
        }
    }

    public static void announceAll(MinecraftServer server, AnnouncePacket.Kind kind, String title, String text) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            announce(player, kind, title, text);
        }
    }

    public enum Cue {
        WARNING(SoundEvents.NOTE_BLOCK_BELL.value(), 1.0f),
        PENALTY(SoundEvents.ANVIL_LAND, 0.8f),
        GOOD(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2f),
        OUT(SoundEvents.WITHER_SPAWN, 1.0f);

        private final SoundEvent sound;
        private final float pitch;

        Cue(SoundEvent sound, float pitch) {
            this.sound = sound;
            this.pitch = pitch;
        }
    }

    /** Plays a sound only this player hears. */
    public static void cue(ServerPlayer player, Cue cue) {
        player.playNotifySound(cue.sound, SoundSource.MASTER, 0.8f, cue.pitch);
    }
}
