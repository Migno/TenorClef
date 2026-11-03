package adris.altoclef.tradecycle.mc;

import adris.altoclef.multiversion.DrawContextWrapper;
import adris.altoclef.tradecycle.TradeCycleConfig;
import adris.altoclef.tradecycle.TradeStore;
import adris.altoclef.tradecycle.TradeView;
import adris.altoclef.tradecycle.VillagerRecord;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Index table (key P): sortable by enchantment, price, distance, status; owned / wanted / missing summary on top. */
class TradeIndexScreen extends Screen {

    private static TradeView.Sort sort = TradeView.Sort.STATUS;
    private static final String[] HEADERS = {"Enchantment", "Price", "Distance", "Status"};
    private static final int ROW_H = 11;

    private int scroll;
    private final int[][] headerHits = new int[HEADERS.length][];
    private List<TradeView.Row> rows = List.of();
    private long rowsVersion = -1;
    private TradeView.Sort rowsSort;

    TradeIndexScreen() {
        super(Component.literal("Librarian trades"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void refreshRows() {
        TradeStore store = TradeStore.get();
        if (store.version() == rowsVersion && rowsSort == sort) return;
        double[] p = TradeMc.playerPos();
        rows = TradeView.rows(store.index().all(), p == null ? 0 : p[0], p == null ? 0 : p[1], p == null ? 0 : p[2], sort);
        rowsVersion = store.version();
        rowsSort = sort;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        DrawContextWrapper g = DrawContextWrapper.of(context);
        refreshRows();
        TradeStore store = TradeStore.get();
        TradeCycleConfig c = store.config();
        int x0 = Math.max(8, width / 2 - 230), x1 = Math.min(width - 8, width / 2 + 230);
        g.fill(x0 - 4, 6, x1 + 4, height - 6, 0xE0101018);
        g.drawText(font, "Librarian trades  " + store.worldKey(), x0, 12, 0xFFFFD27F, true);
        g.drawText(font, TradeView.summary(c.wanted, store.index().all(), c.bestTolerance)
                + "   tolerance " + c.bestTolerance + "   " + store.index().villagers.size() + " villager(s)", x0, 24, 0xFFCCCCCC, false);

        int hx = x0;
        g.drawText(font, "Sort:", hx, 38, 0xFF888888, false);
        hx += font.width("Sort: ") + 2;
        for (int i = 0; i < HEADERS.length; i++) {
            int w = font.width(HEADERS[i]) + 8;
            boolean on = TradeView.Sort.values()[i] == sort;
            boolean hover = mouseX >= hx && mouseX < hx + w && mouseY >= 35 && mouseY < 48;
            g.fill(hx, 35, hx + w, 48, on ? 0xFF6A4A1A : hover ? 0xFF3A3A48 : 0xFF24242E);
            g.drawText(font, HEADERS[i], hx + 4, 38, on ? 0xFFFFE0A0 : 0xFFDDDDDD, false);
            headerHits[i] = new int[]{hx, 35, w, 13};
            hx += w + 4;
        }

        int y = 54;
        int cStatus = x0, cBook = x0 + 72, cBest = x0 + 230, cPos = x0 + 262, cDist = x0 + 362, cId = x0 + 404;
        g.drawText(font, "Status", cStatus, y, 0xFF888888, false);
        g.drawText(font, "Book / price", cBook, y, 0xFF888888, false);
        g.drawText(font, "Position", cPos, y, 0xFF888888, false);
        g.drawText(font, "Dist", cDist, y, 0xFF888888, false);
        g.drawText(font, "UUID", cId, y, 0xFF888888, false);
        y += ROW_H + 2;
        int visible = Math.max(1, (height - 12 - y) / ROW_H);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - visible)));
        if (rows.isEmpty()) {
            g.drawText(font, "Index is empty. Run @trades scan near your librarians.", cStatus, y, 0xFFAAAAAA, false);
        }
        for (int i = scroll; i < rows.size() && i < scroll + visible; i++) {
            TradeView.Row r = rows.get(i);
            VillagerRecord v = r.villager();
            int tint = TradeView.tint(v, c.wanted, c.bestTolerance).argb;
            if ((i & 1) == 0) g.fill(x0 - 2, y - 1, x1 + 2, y + ROW_H - 1, 0x30FFFFFF);
            g.fill(cStatus, y + 1, cStatus + 4, y + 8, tint);
            g.drawText(font, v.status.name(), cStatus + 7, y, tint, false);
            String book = r.book() == null ? (v.lastScanned > 0 ? "no book" : "-") : r.book().shortLabel();
            g.drawText(font, book, cBook, y, 0xFFFFFFFF, false);
            if (r.book() != null && TradeView.isBest(r.book(), c.bestTolerance)) g.drawText(font, "BEST", cBest, y, 0xFF55FF55, false);
            g.drawText(font, TradeView.pos(v), cPos, y, 0xFFBBBBBB, false);
            g.drawText(font, String.format("%.0fm", r.distance()), cDist, y, 0xFFBBBBBB, false);
            g.drawText(font, v.shortId(), cId, y, 0xFF777777, false);
            y += ROW_H;
        }
        if (rows.size() > visible) {
            g.drawText(font, (scroll + 1) + "-" + Math.min(rows.size(), scroll + visible) + " of " + rows.size() + " (scroll)",
                    x1 - 110, 12, 0xFF888888, false);
        }
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() == 0) {
            for (int i = 0; i < headerHits.length; i++) {
                int[] b = headerHits[i];
                if (b != null && click.x() >= b[0] && click.x() < b[0] + b[2] && click.y() >= b[1] && click.y() < b[1] + b[3]) {
                    sort = TradeView.Sort.values()[i];
                    scroll = 0;
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scroll -= (int) Math.signum(vertical) * 3;
        if (scroll < 0) scroll = 0;
        return true;
    }

    static void open() {
        Minecraft.getInstance().setScreenAndShow(new TradeIndexScreen());
    }
}
