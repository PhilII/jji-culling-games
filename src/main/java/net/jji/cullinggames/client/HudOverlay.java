package net.jji.cullinggames.client;

import net.jji.cullinggames.network.HudSyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** Top-left Kogane panel: own points, time left to score, colony, lives. Placeholder look (plain panel). */
public final class HudOverlay implements IGuiOverlay {
    private static final int GOLD = 0xFFFFAA00;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREY = 0xFFAAAAAA;
    private static final int GREEN = 0xFF55FF55;
    private static final int YELLOW = 0xFFFFFF55;
    private static final int RED = 0xFFFF5555;
    private static final int PURPLE = 0xFFAA55FF;
    private static final int PANEL = 0x90000000;

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        HudSyncPacket hud = ClientPackets.hud;
        Minecraft mc = Minecraft.getInstance();
        if (!hud.active() || mc.options.hideGui || mc.options.renderDebug) {
            return;
        }
        Font font = mc.font;
        int x = 6;
        int y = 6;
        int w = 132;
        int lines = hud.paused() || hud.endsInTicks() >= 0 ? 5 : 4;
        g.fill(x - 3, y - 3, x + w, y + lines * 10 + 6, PANEL);

        g.drawString(font, "KOGANE", x, y, GOLD);
        y += 10;
        g.drawString(font, "Points: " + hud.points(), x, y, WHITE);
        y += 10;
        if (hud.sealed()) {
            g.drawString(font, "CT SEALED - score to unseal", x, y, PURPLE);
        } else {
            int color = hud.ticksLeft() <= 20 * 60 ? RED : hud.ticksLeft() <= 5 * 20 * 60 ? YELLOW : GREEN;
            g.drawString(font, "Score within " + clock(hud.ticksLeft()), x, y, color);
        }
        y += 10;
        g.drawString(font, hud.colony() + "  |  Lives " + hud.lives() + "/" + hud.startingLives(), x, y, GREY);
        y += 10;
        if (hud.paused()) {
            g.drawString(font, "PAUSED", x, y, YELLOW);
            y += 10;
        } else if (hud.endsInTicks() >= 0) {
            g.drawString(font, "Ends in " + clock(hud.endsInTicks()), x, y, RED);
            y += 10;
        }
        // timer bar
        float frac = hud.sealed() || hud.limitTicks() == 0 ? 0 : (float) hud.ticksLeft() / hud.limitTicks();
        g.fill(x, y + 1, x + w - 6, y + 3, 0xFF333333);
        g.fill(x, y + 1, x + Math.round((w - 6) * frac), y + 3, hud.sealed() ? PURPLE : GOLD);
    }

    static String clock(long ticks) {
        long s = ticks / 20;
        return String.format("%d:%02d", s / 60, s % 60);
    }
}
