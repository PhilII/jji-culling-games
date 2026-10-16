package net.jji.cullinggames.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Server → client: Kogane announces something on screen (title card, rule banner, popup) and plays its sound.
 * Chat still gets the text too. sound names a file in assets/cullinggames/sounds; empty = the kind's default.
 */
public record AnnouncePacket(Kind kind, String title, String text, String sound) {

    public enum Kind {
        /** Event start: title card plus Kogane. */
        START,
        /** Event end: title card plus Kogane. */
        END,
        /** A rule was added: talisman banner. */
        RULE,
        /** Timer or end-of-event warning: annoyed Kogane. */
        WARNING,
        /** Seal, lost life, out: annoyed Kogane. */
        PENALTY,
        /** Good news (unsealed). */
        GOOD,
        /** Neutral info (placed in a colony). */
        INFO,
        /** Pushed back by the barrier: veil icon in the middle of the screen. */
        BARRIER,
        /** Sound only, nothing on screen (points earned). */
        SOUND
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(kind);
        buf.writeUtf(title, 128);
        buf.writeUtf(text, 256);
        buf.writeUtf(sound, 32);
    }

    public static AnnouncePacket decode(FriendlyByteBuf buf) {
        return new AnnouncePacket(buf.readEnum(Kind.class), buf.readUtf(128), buf.readUtf(256), buf.readUtf(32));
    }
}
