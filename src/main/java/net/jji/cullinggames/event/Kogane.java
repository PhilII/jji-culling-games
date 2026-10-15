package net.jji.cullinggames.event;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Kogane's voice. Placeholder: chat only until step 5 adds bossbar and titles. */
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
}
