package net.jji.cullinggames.client;

import java.util.List;
import net.jji.cullinggames.network.CgNetwork;
import net.jji.cullinggames.network.ShopActionPacket;
import net.jji.cullinggames.network.ShopSyncPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Kogane's shop. Left: services and rules. Right: admin-stocked items, a page at a time.
 * Every button only sends a request; the server decides and sends the refreshed shop back.
 */
public final class ShopScreen extends Screen {
    private static final int ROW = 20;
    private static final int TOP = 44;

    private ShopSyncPacket data;
    private int page;

    ShopScreen(ShopSyncPacket data) {
        super(Component.literal("Kogane's Shop"));
        this.data = data;
    }

    /** As many item rows as fit above the message line, at least 2. */
    private int itemsPerPage() {
        return Math.max(2, (height - 70 - (TOP + 12) - 24) / ROW);
    }

    void update(ShopSyncPacket fresh) {
        this.data = fresh;
        int pages = Math.max(1, (data.items().size() + itemsPerPage() - 1) / itemsPerPage());
        page = Math.min(page, pages - 1);
        rebuildWidgets();
    }

    private static void send(ShopActionPacket.Action action, String id) {
        CgNetwork.CHANNEL.sendToServer(new ShopActionPacket(action, id));
    }

    @Override
    protected void init() {
        int left = width / 2 - 200;
        int right = width / 2 + 10;
        int top = TOP;
        int perPage = itemsPerPage();
        boolean open = data.open();

        int y = top + 12;
        for (ShopSyncPacket.ServiceOffer s : data.services()) {
            String label = s.owned() ? capitalize(s.id()) + " (owned)" : capitalize(s.id()) + " - " + s.price() + " pts";
            Button b = Button.builder(Component.literal(label), btn -> send(ShopActionPacket.Action.BUY, s.id()))
                    .bounds(left, y, 190, 18).build();
            b.active = open && !s.owned() && data.points() >= s.price();
            addRenderableWidget(b);
            y += ROW;
        }

        y += 12;
        for (ShopSyncPacket.RuleOffer r : data.rules()) {
            String label = r.active() ? capitalize(r.id()) + " (in play)" : capitalize(r.id()) + " - " + data.ruleCost() + " pts";
            Button b = Button.builder(Component.literal(label), btn -> send(ShopActionPacket.Action.RULE, r.id()))
                    .bounds(left, y, 190, 18).tooltip(Tooltip.create(Component.literal(r.description()))).build();
            b.active = open && !r.active() && data.points() >= data.ruleCost();
            addRenderableWidget(b);
            y += ROW;
        }

        List<ShopSyncPacket.ItemOffer> items = data.items();
        int from = page * perPage;
        y = top + 12;
        for (int i = from; i < Math.min(items.size(), from + perPage); i++) {
            ShopSyncPacket.ItemOffer o = items.get(i);
            Button b = Button.builder(Component.literal("Buy - " + o.price() + " pts"),
                            btn -> send(ShopActionPacket.Action.BUY, Integer.toString(o.id())))
                    .bounds(right + 110, y, 80, 18)
                    .tooltip(Tooltip.create(o.stack().getHoverName())).build();
            b.active = open && data.points() >= o.price();
            addRenderableWidget(b);
            y += ROW;
        }
        int pages = Math.max(1, (items.size() + perPage - 1) / perPage);
        if (pages > 1) {
            int py = top + 12 + perPage * ROW + 4;
            Button prev = Button.builder(Component.literal("<"), btn -> { page--; rebuildWidgets(); })
                    .bounds(right, py, 20, 20).build();
            prev.active = page > 0;
            Button next = Button.builder(Component.literal(">"), btn -> { page++; rebuildWidgets(); })
                    .bounds(right + 170, py, 20, 20).build();
            next.active = page < pages - 1;
            addRenderableWidget(prev);
            addRenderableWidget(next);
        }

        addRenderableWidget(Button.builder(Component.literal("Close"), btn -> onClose())
                .bounds(width - 66, 8, 58, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int left = width / 2 - 200;
        int right = width / 2 + 10;
        int top = TOP;
        int perPage = itemsPerPage();
        g.drawCenteredString(font, title, width / 2, 14, 0xFFAA00);
        g.drawCenteredString(font, "Your points: " + data.points(), width / 2, 28, 0xFFFFFF);

        g.drawString(font, "Services (rest of event)", left, top, 0xAAAAAA);
        g.drawString(font, "Rules", left, top + 12 + data.services().size() * ROW + 2, 0xAAAAAA);
        g.drawString(font, "Items", right, top, 0xAAAAAA);

        List<ShopSyncPacket.ItemOffer> items = data.items();
        int from = page * perPage;
        int y = top + 12;
        if (items.isEmpty()) {
            g.drawString(font, "Nothing stocked yet.", right, y + 6, 0x777777);
        }
        for (int i = from; i < Math.min(items.size(), from + perPage); i++) {
            ShopSyncPacket.ItemOffer o = items.get(i);
            g.renderItem(o.stack(), right, y + 2);
            g.renderItemDecorations(font, o.stack(), right, y + 2);
            String name = font.plainSubstrByWidth(o.stack().getHoverName().getString(), 84);
            g.drawString(font, name, right + 20, y + 6, 0xFFFFFF);
            y += ROW;
        }

        if (!data.open()) {
            g.drawCenteredString(font, data.closedReason(), width / 2, height - 14, 0xFF5555);
        } else if (!data.message().isEmpty()) {
            g.drawCenteredString(font, data.message(), width / 2, height - 14, 0xFFFF55);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String capitalize(String id) {
        String s = id.replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
