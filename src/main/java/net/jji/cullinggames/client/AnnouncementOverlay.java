package net.jji.cullinggames.client;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import net.jji.cullinggames.network.AnnouncePacket;
import net.jji.cullinggames.network.AnnouncePacket.Kind;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Kogane's on-screen announcements: the 死滅回游 title card at start and end, the talisman banner when a rule
 * is added, a Kogane popup for warnings and penalties, and the veil icon when the barrier pushes you back.
 */
public final class AnnouncementOverlay implements IGuiOverlay {
    private static final long TITLE_MS = 5500;
    private static final long BANNER_MS = 6000;
    private static final long POPUP_MS = 4500;
    private static final long BARRIER_MS = 1300;
    private static final long FADE_IN = 300;
    private static final long FADE_OUT = 700;

    private static Shown title;
    private static Shown banner;
    private static Shown popup;
    private static Shown barrier;

    private record Shown(AnnouncePacket msg, long since, long duration) {
        float age() {
            return Util.getMillis() - since;
        }

        boolean over() {
            return age() > duration;
        }

        float alpha() {
            float t = age();
            float in = Mth.clamp(t / FADE_IN, 0, 1);
            float out = Mth.clamp((duration - t) / FADE_OUT, 0, 1);
            return Math.min(in, out);
        }

        /** 0 → 1 with ease-out over the first `ms`. */
        float ease(float ms) {
            float k = Mth.clamp(age() / ms, 0, 1);
            return 1 - (1 - k) * (1 - k);
        }
    }

    static void show(AnnouncePacket msg) {
        long now = Util.getMillis();
        String sound = msg.sound().isEmpty() ? defaultSound(msg.kind()) : msg.sound();
        switch (msg.kind()) {
            case START, END -> {
                title = new Shown(msg, now, TITLE_MS);
                popup = new Shown(msg, now + 600, POPUP_MS);
            }
            case RULE -> banner = new Shown(msg, now, BANNER_MS);
            case BARRIER -> {
                boolean fresh = barrier == null || barrier.over();
                barrier = new Shown(msg, fresh ? now : barrier.since(), fresh ? BARRIER_MS : now - barrier.since() + BARRIER_MS);
                if (!fresh) {
                    return; // still showing: no new sound while the player keeps pushing
                }
            }
            case SOUND -> { }
            default -> popup = new Shown(msg, now, POPUP_MS);
        }
        CgSounds.play(sound);
    }

    private static String defaultSound(Kind kind) {
        return switch (kind) {
            case START -> "start";
            case END -> "end";
            case RULE -> "rule";
            case WARNING -> "warning";
            case GOOD -> "unseal";
            case BARRIER -> "barrier";
            case INFO, PENALTY -> "kogane";
            case SOUND -> "";
        };
    }

    static void clear() {
        title = banner = popup = barrier = null;
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) {
            return;
        }
        if (title != null && title.over()) title = null;
        if (banner != null && banner.over()) banner = null;
        if (popup != null && popup.over()) popup = null;
        if (barrier != null && barrier.over()) barrier = null;
        if (title == null && banner == null && popup == null && barrier == null) {
            return;
        }
        RenderSystem.enableBlend();
        // In front of the chat, which draws at a raised depth and would otherwise cover the popups.
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        Font font = mc.font;
        int titleBottom = 0;
        if (title != null) {
            titleBottom = drawTitle(g, title, width, height);
        }
        if (banner != null) {
            drawBanner(g, font, banner, width);
        }
        if (popup != null && popup.age() >= 0) {
            drawPopup(g, font, popup, width, titleBottom);
        }
        if (barrier != null) {
            drawBarrier(g, font, barrier, width, height);
        }
        g.setColor(1, 1, 1, 1);
        g.pose().popPose();
        RenderSystem.disableBlend();
    }

    /** Darkens the screen and drops the title card in. Returns its bottom edge. */
    private static int drawTitle(GuiGraphics g, Shown s, int width, int height) {
        float a = s.alpha();
        g.fill(0, 0, width, height, (int) (a * 0x70) << 24);
        int w = (int) Math.min(width * 0.75f, 440);
        int h = w * 270 / 1024;
        int x = (width - w) / 2;
        int y = (int) (height * 0.14f);
        float scale = 1.3f - 0.3f * s.ease(450);
        g.pose().pushPose();
        g.pose().translate(width / 2f, y + h / 2f, 0);
        g.pose().scale(scale, scale, 1);
        g.pose().translate(-width / 2f, -(y + h / 2f), 0);
        g.setColor(1, 1, 1, a);
        CgTextures.whole(g, CgTextures.TITLE_CARD, x, y, w, h, 1024, 270);
        g.setColor(1, 1, 1, 1);
        g.pose().popPose();
        return y + h;
    }

    private static void drawBanner(GuiGraphics g, Font font, Shown s, int width) {
        float a = s.alpha();
        int w = (int) Math.min(width * 0.62f, 340);
        int h = w * 158 / 768;
        int x = (width - w) / 2;
        // Below the HUD panel when the banner (and Kogane on its left) would overlap it on narrow screens.
        int y = (x - 34 < HudOverlay.RIGHT_EDGE + 4 ? HudOverlay.BOTTOM_EDGE + 6 : 26) - (int) ((1 - s.ease(400)) * 24);
        g.setColor(1, 1, 1, a);
        CgTextures.whole(g, CgTextures.RULE_BANNER, x, y, w, h, 768, 158);
        CgTextures.kogane(g, CgTextures.KOGANE_SHOUT, x - 34, y + h / 2 - 22 + bob(s), 44);
        g.setColor(1, 1, 1, 1);
        if (a < 0.05f) {
            return;
        }
        int alpha = (int) (a * 255) << 24;
        int innerX = x + (int) (w * 0.17f);
        int innerW = (int) (w * 0.72f);
        Component head = Component.literal(s.msg().title().toUpperCase()).withStyle(ChatFormatting.BOLD);
        g.drawString(font, head, innerX + (innerW - font.width(head)) / 2, y + (int) (h * 0.2f), alpha | 0x8A1010, false);
        List<FormattedCharSequence> lines = font.split(Component.literal(s.msg().text()), innerW);
        int ly = y + (int) (h * 0.2f) + 12;
        for (FormattedCharSequence line : lines.subList(0, Math.min(2, lines.size()))) {
            g.drawString(font, line, innerX + (innerW - font.width(line)) / 2, ly, alpha | 0x2A1A0A, false);
            ly += 10;
        }
    }

    /** Kogane with a speech box: under the title card at start/end, otherwise top right. */
    private static void drawPopup(GuiGraphics g, Font font, Shown s, int width, int titleBottom) {
        float a = s.alpha();
        Kind kind = s.msg().kind();
        int frame = switch (kind) {
            case WARNING, PENALTY -> CgTextures.KOGANE_ANNOYED;
            case START, END -> CgTextures.KOGANE_SHOUT;
            default -> CgTextures.KOGANE_NEUTRAL;
        };
        int titleColor = switch (kind) {
            case WARNING -> 0xFFFF55;
            case PENALTY -> 0xFF5555;
            case GOOD -> 0x7CFC5A;
            default -> 0xFFC94A;
        };
        Component head = Component.literal(s.msg().title()).withStyle(ChatFormatting.BOLD);
        List<FormattedCharSequence> lines = s.msg().text().isEmpty() ? List.of()
                : font.split(Component.literal(s.msg().text()), 170);
        int textW = font.width(head);
        for (FormattedCharSequence line : lines) {
            textW = Math.max(textW, font.width(line));
        }
        int boxW = textW + 12;
        int boxH = 14 + lines.size() * 10 + 4;
        int portrait = 40;
        int total = portrait + boxW;
        int x;
        int y;
        if (titleBottom > 0) {
            x = (width - total) / 2;
            y = titleBottom + 8;
        } else {
            x = width - total - 6;
            y = 40;
        }
        int slide = (int) ((1 - s.ease(300)) * 30);
        x += titleBottom > 0 ? 0 : slide;
        int bx = x + portrait - 4;
        int by = y + (portrait - boxH) / 2;
        int alpha = (int) (a * 255) << 24;
        g.fill(bx, by, bx + boxW, by + boxH, (int) (a * 0xC0) << 24 | 0x100810);
        g.renderOutline(bx, by, boxW, boxH, alpha | 0x5A2A6A);
        g.setColor(1, 1, 1, a);
        CgTextures.kogane(g, frame, x, y + bob(s), portrait);
        g.setColor(1, 1, 1, 1);
        if (a < 0.05f) {
            return;
        }
        g.drawString(font, head, bx + 6, by + 4, alpha | titleColor);
        int ly = by + 16;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, bx + 6, ly, alpha | 0xFFFFFF);
            ly += 10;
        }
    }

    private static void drawBarrier(GuiGraphics g, Font font, Shown s, int width, int height) {
        float a = s.alpha();
        int size = 30;
        int cx = width / 2;
        int y = height / 2 - 56;
        // red pulse around the screen edge
        int edge = (int) (a * 0x50) << 24 | 0x400010;
        g.fillGradient(0, 0, width, 24, edge, 0);
        g.fillGradient(0, height - 24, width, height, 0, edge);
        g.setColor(1, 1, 1, a);
        CgTextures.whole(g, CgTextures.BARRIER, cx - size / 2, y, size, size, 64, 64);
        g.setColor(1, 1, 1, 1);
        if (a >= 0.05f) {
            Component text = Component.literal(s.msg().title()).withStyle(ChatFormatting.BOLD);
            g.drawString(font, text, cx - font.width(text) / 2, y + size + 3, (int) (a * 255) << 24 | 0xFF4040);
        }
    }

    /** Small up-and-down float, like Kogane hovering. */
    private static int bob(Shown s) {
        return Math.round(Mth.sin(s.age() / 220f) * 1.5f);
    }
}
