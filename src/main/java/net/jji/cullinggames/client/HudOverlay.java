package net.jji.cullinggames.client;

import net.jji.cullinggames.network.HudSyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** Top-left Kogane panel: own points, time left to score, colony, lives. */
public final class HudOverlay implements IGuiOverlay {
    private static final int GOLD = 0xFFFFC94A;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREY = 0xFFBBBBBB;
    private static final int GREEN = 0xFF7CFC5A;
    private static final int YELLOW = 0xFFFFFF55;
    private static final int RED = 0xFFFF5555;
    private static final int PURPLE = 0xFFC08CFF;
    private static final int PANEL = 0xA0100810;
    private static final int EDGE = 0xFF5A2A6A;

    private static final int ICON = 11;
    private static final int ROW = 12;
    private static final int PORTRAIT = 30;
    private static final int X0 = 4, Y0 = 4, W = 150, H = 4 * ROW + 8;
    /** Where the panel ends, so announcements can stay clear of it. */
    static final int RIGHT_EDGE = X0 + W, BOTTOM_EDGE = Y0 + H;

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        HudSyncPacket hud = ClientPackets.hud;
        Minecraft mc = Minecraft.getInstance();
        if (!hud.active() || mc.options.hideGui || mc.options.renderDebug) {
            return;
        }
        Font font = mc.font;
        boolean urgent = hud.sealed() || hud.ticksLeft() <= 20 * 60;
        int x0 = X0;
        int y0 = Y0;
        int w = W;
        int h = H;

        g.fill(x0, y0, x0 + w, y0 + h, PANEL);
        g.renderOutline(x0, y0, w, h, EDGE);
        CgTextures.kogane(g, urgent ? CgTextures.KOGANE_ANNOYED : CgTextures.KOGANE_NEUTRAL, x0 + 2, y0 + 4, PORTRAIT);

        int x = x0 + PORTRAIT + 6;
        int tx = x + ICON + 3;
        int y = y0 + 4;

        CgTextures.icon(g, CgTextures.ICON_POINTS, x, y, ICON);
        g.drawString(font, hud.points() + " pts", tx, y + 2, GOLD);
        y += ROW;

        if (hud.sealed()) {
            CgTextures.icon(g, CgTextures.ICON_SEALED, x, y, ICON);
            g.drawString(font, "SEALED - score!", tx, y + 2, PURPLE);
        } else {
            int color = hud.ticksLeft() <= 20 * 60 ? RED : hud.ticksLeft() <= 5 * 20 * 60 ? YELLOW : GREEN;
            CgTextures.icon(g, CgTextures.ICON_TIMER, x, y, ICON);
            g.drawString(font, clock(hud.ticksLeft()), tx, y + 2, color);
        }
        y += ROW;

        CgTextures.icon(g, CgTextures.ICON_COLONY, x, y, ICON);
        g.drawString(font, font.plainSubstrByWidth(hud.colony(), x0 + w - tx - 3), tx, y + 2, WHITE);
        y += ROW;

        CgTextures.icon(g, CgTextures.ICON_LIVES, x, y, ICON);
        g.drawString(font, hud.lives() + "/" + hud.startingLives(), tx, y + 2, GREY);
        if (hud.paused()) {
            g.drawString(font, "PAUSED", tx + 34, y + 2, YELLOW);
        } else if (hud.endsInTicks() >= 0) {
            g.drawString(font, "ends " + clock(hud.endsInTicks()), tx + 34, y + 2, RED);
        }

        // timer bar along the bottom edge
        float frac = hud.sealed() || hud.limitTicks() == 0 ? 0 : (float) hud.ticksLeft() / hud.limitTicks();
        int by = y0 + h - 3;
        g.fill(x0 + 1, by, x0 + w - 1, by + 2, 0xFF2A2030);
        g.fill(x0 + 1, by, x0 + 1 + Math.round((w - 2) * frac), by + 2, hud.sealed() ? PURPLE : urgent ? RED : GOLD);
    }

    static String clock(long ticks) {
        long s = ticks / 20;
        return String.format("%d:%02d", s / 60, s % 60);
    }
}
