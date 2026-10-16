package net.jji.cullinggames.client;

import net.jji.cullinggames.CullingGames;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * The mod's GUI art (made from the originals by scripts/process_art.py). Textures are larger than they are
 * drawn, so they get linear filtering to stay smooth when scaled down.
 */
final class CgTextures {
    static final ResourceLocation HUD_ICONS = gui("hud_icons");
    static final ResourceLocation KOGANE = gui("kogane");
    static final ResourceLocation SHOP_HEAD = gui("shop_head");
    static final ResourceLocation SHOP_FRAME = gui("shop_frame");
    static final ResourceLocation SHOP_TAIL = gui("shop_tail");
    static final ResourceLocation TITLE_CARD = gui("title_card");
    static final ResourceLocation RULE_BANNER = gui("rule_banner");
    static final ResourceLocation BARRIER = gui("barrier");

    /** HUD icon index in hud_icons.png (48x48 each). */
    static final int ICON_POINTS = 0, ICON_TIMER = 1, ICON_COLONY = 2, ICON_LIVES = 3, ICON_SEALED = 4;
    /** Kogane frame index in kogane.png (128x128 each). */
    static final int KOGANE_NEUTRAL = 0, KOGANE_SHOUT = 1, KOGANE_ANNOYED = 2;

    private static boolean filtered;

    private CgTextures() {}

    private static ResourceLocation gui(String name) {
        return ResourceLocation.fromNamespaceAndPath(CullingGames.MOD_ID, "textures/gui/" + name + ".png");
    }

    /** Linear filtering for every texture above. Runs once, on first draw (textures load lazily). */
    static void ensureFiltered() {
        if (filtered) {
            return;
        }
        filtered = true;
        var textures = Minecraft.getInstance().getTextureManager();
        for (ResourceLocation id : new ResourceLocation[] {HUD_ICONS, KOGANE, SHOP_HEAD, SHOP_FRAME, SHOP_TAIL,
                TITLE_CARD, RULE_BANNER, BARRIER}) {
            textures.getTexture(id).setFilter(true, false);
        }
    }

    static void icon(GuiGraphics g, int index, int x, int y, int size) {
        ensureFiltered();
        g.blit(HUD_ICONS, x, y, size, size, index * 48, 0, 48, 48, 240, 48);
    }

    static void kogane(GuiGraphics g, int frame, int x, int y, int size) {
        ensureFiltered();
        g.blit(KOGANE, x, y, size, size, frame * 128, 0, 128, 128, 384, 128);
    }

    /** Draws a whole texture of size texW x texH scaled to w x h. */
    static void whole(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h, int texW, int texH) {
        ensureFiltered();
        g.blit(tex, x, y, w, h, 0, 0, texW, texH, texW, texH);
    }
}
