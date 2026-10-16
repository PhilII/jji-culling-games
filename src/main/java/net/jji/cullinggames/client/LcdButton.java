package net.jji.cullinggames.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Flat dark-green button for Kogane's green screen. */
final class LcdButton extends Button {
    static final int INK = 0xFF143300;
    private static final int FILL = 0xFF1F4A08;
    private static final int FILL_HOVER = 0xFF2F6E0C;
    private static final int FILL_OFF = 0x5531600A;
    private static final int TEXT = 0xFFD4FF5A;
    private static final int TEXT_OFF = 0xFF4E7A16;
    private static final int SELECTED = 0xFF0C2A02;

    private boolean selected;

    LcdButton(int x, int y, int w, int h, Component label, OnPress onPress) {
        super(x, y, w, h, label, onPress, DEFAULT_NARRATION);
    }

    LcdButton selected(boolean selected) {
        this.selected = selected;
        return this;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int fill = !active ? FILL_OFF : selected ? SELECTED : isHoveredOrFocused() ? FILL_HOVER : FILL;
        g.fill(getX(), getY(), getX() + width, getY() + height, fill);
        if (selected) {
            g.fill(getX(), getY() + height - 1, getX() + width, getY() + height, TEXT);
        }
        Font font = Minecraft.getInstance().font;
        String text = font.plainSubstrByWidth(getMessage().getString(), width - 4);
        g.drawString(font, text, getX() + (width - font.width(text)) / 2, getY() + (height - 8) / 2,
                active ? TEXT : TEXT_OFF, false);
    }
}
