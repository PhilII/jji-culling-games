package net.jji.cullinggames.client;

import java.util.Arrays;
import java.util.List;
import net.jji.cullinggames.event.CanonRules;
import net.jji.cullinggames.network.CgNetwork;
import net.jji.cullinggames.network.ShopActionPacket;
import net.jji.cullinggames.network.ShopSyncPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Kogane's shop, drawn as Kogane with its menu open: head and wings on top, the body as a green screen,
 * the tail below. Tabs for services, rules and admin-stocked items. Every button only sends a request;
 * the server decides and sends the refreshed shop back.
 */
public final class ShopScreen extends Screen {
    private enum Tab { SERVICES, RULES, ITEMS, REWARDS, FRAGMENTS, COINS }

    private static final Tab[] EVENT_TABS = {Tab.SERVICES, Tab.RULES, Tab.ITEMS};
    private static final Tab[] REWARD_TABS = {Tab.REWARDS, Tab.FRAGMENTS, Tab.COINS};
    /** Coin amounts offered on the Coins tab. */
    private static final int[] COIN_AMOUNTS = {1, 5, 10};

    // Texture sizes and slice borders, in texture pixels (see scripts/process_art.py).
    private static final int HEAD_W = 602, HEAD_H = 164, HEAD_FRAME_TOP = 162;
    private static final int FRAME_W = 212, FRAME_H = 299;
    private static final int SLICE_L = 27, SLICE_R = 25, SLICE_T = 26, SLICE_B = 60;
    private static final int GREEN_L = 24, GREEN_R = 25, GREEN_T = 8, GREEN_B = 43;
    /** The tail texture starts TAIL_ABOVE rows above the frame's bottom edge (its tip rises beside the frame). */
    private static final int TAIL_W = 313, TAIL_H = 190, TAIL_ABOVE = 60;
    private static final float TAIL_CENTER = 86.5f;

    private static final int ROW = 18;
    private static final int ERROR = 0xFF7A0000;

    private static Tab lastTab = Tab.SERVICES;
    /** Lightman's copper coin when installed, else a copper ingot. */
    private static final ItemStack COIN = coinIcon();

    private ShopSyncPacket data;
    private Tab tab = lastTab;
    private int page;

    // layout, computed in init()
    private float k;
    private int frameX, frameY, frameW, frameH;
    private int left, top, right, bottom;

    ShopScreen(ShopSyncPacket data) {
        super(Component.literal("Kogane's Shop"));
        this.data = data;
    }

    void update(ShopSyncPacket fresh) {
        this.data = fresh;
        rebuildWidgets();
    }

    private static void send(ShopActionPacket.Action action, String id) {
        CgNetwork.CHANNEL.sendToServer(new ShopActionPacket(action, id));
    }

    private void layout() {
        k = Math.min(Math.min((width - 8) / (float) HEAD_W, height * 0.24f / HEAD_H), 0.62f);
        frameW = Math.min(width - 16, 270);
        frameX = (width - frameW) / 2;
        frameY = 4 + Math.round(HEAD_FRAME_TOP * k);
        int frameBottom = height - 4 - Math.round((TAIL_H - TAIL_ABOVE) * k);
        frameH = frameBottom - frameY;
        left = frameX + Math.round(GREEN_L * k) + 4;
        right = frameX + frameW - Math.round(GREEN_R * k) - 4;
        top = frameY + Math.round(GREEN_T * k) + 3;
        bottom = frameBottom - Math.round(GREEN_B * k) - 2;
    }

    private int listTop() {
        return top + 28;
    }

    private int rowsPerPage() {
        return Math.max(1, (bottom - 24 - listTop()) / ROW);
    }

    private Tab[] tabs() {
        return data.rewardMode() ? REWARD_TABS : EVENT_TABS;
    }

    /** Rewards shown on the current tab: fragments on their own tab, everything else on Rewards. */
    private List<ShopSyncPacket.RewardOffer> rewardOffers() {
        boolean fragments = tab == Tab.FRAGMENTS;
        return data.rewardOffers().stream().filter(o -> o.fragment() == fragments).toList();
    }

    private int entries() {
        return switch (tab) {
            case SERVICES -> data.services().size();
            case RULES -> data.rules().size() + 1; // row 0: the original rules 1-8
            case ITEMS -> data.items().size();
            case REWARDS, FRAGMENTS -> rewardOffers().size();
            case COINS -> data.cashOut().available() ? COIN_AMOUNTS.length : 0;
        };
    }

    @Override
    protected void init() {
        layout();
        if (!Arrays.asList(tabs()).contains(tab)) {
            tab = tabs()[0];
            page = 0;
        }
        int pages = Math.max(1, (entries() + rowsPerPage() - 1) / rowsPerPage());
        page = Math.min(page, pages - 1);
        boolean open = data.open();

        addRenderableWidget(new LcdButton(right - 12, top, 12, 11, Component.literal("x"), b -> onClose()));

        Tab[] tabs = tabs();
        int tabW = (right - left - 2 * (tabs.length - 1)) / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            addRenderableWidget(new LcdButton(left + i * (tabW + 2), top + 13, tabW, 12, Component.literal(capitalize(t.name().toLowerCase())), b -> {
                tab = t;
                lastTab = t;
                page = 0;
                rebuildWidgets();
            }).selected(t == tab));
        }

        int perPage = rowsPerPage();
        int from = page * perPage;
        int to = Math.min(entries(), from + perPage);
        int bx = right - 56;
        int y = listTop();
        for (int i = from; i < to; i++) {
            LcdButton b;
            switch (tab) {
                case SERVICES -> {
                    ShopSyncPacket.ServiceOffer s = data.services().get(i);
                    boolean recovery = isRecovery(s.id());
                    String label = !s.owned() ? s.price() + " pts" : !recovery ? "Owned" : s.price() == 0 ? "Full" : "Not now";
                    b = new LcdButton(bx, y + 2, 56, 14, Component.literal(label),
                            btn -> send(ShopActionPacket.Action.BUY, s.id()));
                    if (recovery) {
                        b.setTooltip(Tooltip.create(Component.literal(s.id().equals("heal") ? "Full heal" : "Full cursed energy")
                                .append(". Price grows with what is missing and your fatigue. Once per battle.")));
                    }
                    b.active = open && !s.owned() && data.points() >= s.price();
                }
                case RULES -> {
                    if (i == 0) {
                        b = new LcdButton(bx, y + 2, 56, 14, Component.literal("Read"), btn -> { });
                        b.setTooltip(Tooltip.create(canonRules()));
                        break;
                    }
                    ShopSyncPacket.RuleOffer r = data.rules().get(i - 1);
                    b = new LcdButton(bx, y + 2, 56, 14, Component.literal(r.active() ? "In play" : data.ruleCost() + " pts"),
                            btn -> send(ShopActionPacket.Action.RULE, r.id()));
                    b.setTooltip(Tooltip.create(Component.literal(r.description())));
                    b.active = open && !r.active() && data.points() >= data.ruleCost();
                }
                case REWARDS, FRAGMENTS -> {
                    ShopSyncPacket.RewardOffer o = rewardOffers().get(i);
                    boolean blocked = !o.blocked().isEmpty();
                    String label = !blocked ? o.price() + " pts" : shortReason(o.blocked());
                    b = new LcdButton(bx, y + 2, 56, 14, Component.literal(label),
                            btn -> send(ShopActionPacket.Action.REWARD, o.id()));
                    b.setTooltip(Tooltip.create(Component.literal(blocked ? o.blocked() : o.name())));
                    b.active = open && !blocked && data.points() >= o.price();
                }
                case COINS -> {
                    int coins = COIN_AMOUNTS[i];
                    int cost = coins * data.cashOut().pointsPerCoin();
                    int left = data.cashOut().coinsLeft();
                    boolean capped = left >= 0 && coins > left;
                    b = new LcdButton(bx, y + 2, 56, 14, Component.literal(capped ? "Limit" : cost + " pts"),
                            btn -> send(ShopActionPacket.Action.CASHOUT, Integer.toString(coins)));
                    b.setTooltip(Tooltip.create(Component.literal(left < 0 ? "No limit"
                            : left + (left == 1 ? " coin" : " coins") + " left this event")));
                    b.active = open && !capped && data.points() >= cost;
                }
                default -> {
                    ShopSyncPacket.ItemOffer o = data.items().get(i);
                    b = new LcdButton(bx, y + 2, 56, 14, Component.literal(o.price() + " pts"),
                            btn -> send(ShopActionPacket.Action.BUY, Integer.toString(o.id())));
                    b.setTooltip(Tooltip.create(o.stack().getHoverName()));
                    b.active = open && data.points() >= o.price();
                }
            }
            addRenderableWidget(b);
            y += ROW;
        }

        if (pages > 1) {
            int py = bottom - 24;
            LcdButton prev = new LcdButton(left, py, 16, 12, Component.literal("<"), btn -> { page--; rebuildWidgets(); });
            prev.active = page > 0;
            LcdButton next = new LcdButton(right - 16, py, 16, 12, Component.literal(">"), btn -> { page++; rebuildWidgets(); });
            next.active = page < pages - 1;
            addRenderableWidget(prev);
            addRenderableWidget(next);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        drawKogane(g);

        Component head = Component.literal(data.rewardMode() ? "KOGANE'S REWARDS" : "KOGANE'S SHOP").withStyle(ChatFormatting.BOLD);
        g.drawString(font, head, left, top + 2, LcdButton.INK, false);
        String pts = data.points() + " pts";
        int px = right - 16 - font.width(pts);
        CgTextures.icon(g, CgTextures.ICON_POINTS, px - 12, top, 10);
        g.drawString(font, pts, px, top + 2, LcdButton.INK, false);

        int perPage = rowsPerPage();
        int from = page * perPage;
        int to = Math.min(entries(), from + perPage);
        int nameW = right - 56 - 4 - left;
        int y = listTop();
        if (entries() == 0) {
            String empty = tab == Tab.COINS ? "Cash-out is not available." : tab == Tab.ITEMS || data.rewardMode() ? "Nothing stocked yet." : "Nothing here.";
            g.drawString(font, empty, left, y + 5, LcdButton.INK, false);
        }
        for (int i = from; i < to; i++) {
            g.fill(left, y + ROW - 1, right, y + ROW, 0x33143300);
            switch (tab) {
                case SERVICES -> label(g, serviceName(data.services().get(i).id()), left, y, nameW);
                case RULES -> label(g, i == 0 ? "Rules 1-8 (original)" : capitalize(data.rules().get(i - 1).id()), left, y, nameW);
                case COINS -> {
                    g.renderItem(COIN, left, y + 1);
                    label(g, COIN_AMOUNTS[i] + (COIN_AMOUNTS[i] == 1 ? " coin" : " coins"), left + 19, y, nameW - 19);
                }
                case REWARDS, FRAGMENTS -> {
                    ShopSyncPacket.RewardOffer o = rewardOffers().get(i);
                    g.renderItem(o.icon(), left, y + 1);
                    String name = o.fragment() ? o.name() + "  " + o.have() + "/" + o.need() : o.name();
                    label(g, name, left + 19, y, nameW - 19);
                }
                default -> {
                    ShopSyncPacket.ItemOffer o = data.items().get(i);
                    g.renderItem(o.stack(), left, y + 1);
                    g.renderItemDecorations(font, o.stack(), left, y + 1);
                    label(g, o.stack().getHoverName().getString(), left + 19, y, nameW - 19);
                }
            }
            y += ROW;
        }

        int pages = Math.max(1, (entries() + perPage - 1) / perPage);
        if (pages > 1) {
            String p = (page + 1) + "/" + pages;
            g.drawString(font, p, (left + right - font.width(p)) / 2, bottom - 22, LcdButton.INK, false);
        }
        String line = !data.open() ? data.closedReason() : data.message();
        if (!line.isEmpty()) {
            line = font.plainSubstrByWidth(line, right - left);
            g.drawString(font, line, (left + right - font.width(line)) / 2, bottom - 9, data.open() ? LcdButton.INK : ERROR, false);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void label(GuiGraphics g, String text, int x, int y, int maxW) {
        g.drawString(font, font.plainSubstrByWidth(text, maxW), x, y + 5, LcdButton.INK, false);
    }

    /** Tail first (its tip goes behind a wide frame), then the frame (9-slice, any size), then the head on top. */
    private void drawKogane(GuiGraphics g) {
        CgTextures.ensureFiltered();
        int l = Math.round(SLICE_L * k), r = Math.round(SLICE_R * k), t = Math.round(SLICE_T * k), b = Math.round(SLICE_B * k);
        int x0 = frameX, x1 = frameX + l, x2 = frameX + frameW - r, x3 = frameX + frameW;
        int y0 = frameY, y1 = frameY + t, y2 = frameY + frameH - b, y3 = frameY + frameH;
        int tailW = Math.round(TAIL_W * k);
        int tailX = Math.round(width / 2f - TAIL_CENTER * k);
        CgTextures.whole(g, CgTextures.SHOP_TAIL, tailX, y3 - Math.round(TAIL_ABOVE * k), tailW, Math.round(TAIL_H * k), TAIL_W, TAIL_H);
        int[] dx = {x0, x1, x2, x3};
        int[] dy = {y0, y1, y2, y3};
        int[] u = {0, SLICE_L, FRAME_W - SLICE_R, FRAME_W};
        int[] v = {0, SLICE_T, FRAME_H - SLICE_B, FRAME_H};
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                slice(g, CgTextures.SHOP_FRAME, dx[i], dy[j], dx[i + 1] - dx[i], dy[j + 1] - dy[j],
                        u[i], v[j], u[i + 1] - u[i], v[j + 1] - v[j]);
            }
        }
        int headW = Math.round(HEAD_W * k);
        CgTextures.whole(g, CgTextures.SHOP_HEAD, (width - headW) / 2, 4, headW, Math.round(HEAD_H * k), HEAD_W, HEAD_H);
    }

    private static void slice(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h, int u, int v, int uw, int vh) {
        if (w > 0 && h > 0) {
            g.blit(tex, x, y, w, h, u, v, uw, vh, FRAME_W, FRAME_H);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static boolean isRecovery(String id) {
        return id.equals("heal") || id.equals("ce");
    }

    private static String serviceName(String id) {
        return switch (id) {
            case "heal" -> "Heal";
            case "ce" -> "Cursed energy";
            case "killlog" -> "Kill log";
            case "transfer" -> "Point transfers";
            default -> capitalize(id);
        };
    }

    /** Button-sized version of why a reward can't be bought; the tooltip has the full text. */
    private static String shortReason(String reason) {
        if (reason.startsWith("Requires")) {
            return "Locked";
        }
        return switch (reason) {
            case "Already claimed" -> "Claimed";
            case "Already have it" -> "Owned";
            case "Limit reached", "Fragment limit reached" -> "Limit";
            default -> reason;
        };
    }

    private static Component canonRules() {
        MutableComponent text = Component.literal("The Culling Game rules").withStyle(ChatFormatting.BOLD);
        for (int i = 0; i < CanonRules.RULES.size(); i++) {
            text.append(Component.literal("\n" + (i + 1) + ". " + CanonRules.RULES.get(i)).withStyle(ChatFormatting.RESET));
        }
        return text.append(Component.literal("\n" + CanonRules.SERVER_NOTE).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    private static ItemStack coinIcon() {
        Item coin = ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("lightmanscurrency", "coin_copper"));
        return new ItemStack(coin == null || coin == Items.AIR ? Items.COPPER_INGOT : coin);
    }

    private static String capitalize(String id) {
        String s = id.replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
