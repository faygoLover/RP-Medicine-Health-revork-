package faygolover.rpmedicine.client.screen;

import faygolover.rpmedicine.network.GmActionPacket;
import faygolover.rpmedicine.network.GmPanelPacket;
import faygolover.rpmedicine.network.GmReportPacket;
import faygolover.rpmedicine.network.Network;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Панель ГМа (ТЗ второго этапа, п. 11.3): слева игроки онлайн и тела с цветом состояния, справа —
 * полный осмотр цифрами или история; кнопки действий.
 */
public class GmPanelScreen extends Screen {
    private static final int W = 420;
    private static final int H = 250;
    private static final int LIST_W = 130;
    private static final int ROW_H = 12;
    private static final int[] COLORS = {0xFF55FF55, 0xFFFFFF55, 0xFFFFAA00, 0xFFFF5555, 0xFFAA00AA};

    private GmPanelPacket panel;
    @Nullable
    private UUID selected;
    private List<Component> report = List.of();
    private int scroll;
    private int reportScroll;

    public GmPanelScreen(GmPanelPacket panel) {
        super(Component.translatable("rpmedicine.gm.title"));
        this.panel = panel;
    }

    public void update(GmPanelPacket p) {
        this.panel = p;
    }

    public void report(GmReportPacket p) {
        if (p.uuid().equals(selected)) {
            report = p.lines();
            reportScroll = 0;
        }
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    @Override
    protected void init() {
        int x = left() + LIST_W + 10;
        int y = top() + H - 24;
        GmActionPacket.Action[] actions = {GmActionPacket.Action.HEAL, GmActionPacket.Action.REVIVE, GmActionPacket.Action.KILL,
                GmActionPacket.Action.TELEPORT, GmActionPacket.Action.HISTORY, GmActionPacket.Action.INSPECT};
        int bw = (W - LIST_W - 20) / actions.length;
        for (int i = 0; i < actions.length; i++) {
            GmActionPacket.Action a = actions[i];
            addRenderableWidget(Button.builder(Component.translatable("rpmedicine.gm." + a.name().toLowerCase(java.util.Locale.ROOT)), b -> {
                if (selected != null) Network.sendToServer(new GmActionPacket(selected, a));
            }).bounds(x + i * bw, y, bw - 2, 18).build());
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int l = left();
        int t = top();
        g.fill(l, t, l + W, t + H, 0xE0101418);
        g.renderOutline(l, t, W, H, 0xFF505860);
        g.drawString(font, title, l + 6, t + 6, 0xFFD700, false);
        // Список.
        List<GmPanelPacket.Row> rows = panel.rows();
        int visible = (H - 30) / ROW_H;
        for (int i = 0; i < visible && scroll + i < rows.size(); i++) {
            GmPanelPacket.Row r = rows.get(scroll + i);
            int y = t + 20 + i * ROW_H;
            if (r.uuid().equals(selected)) g.fill(l + 3, y - 1, l + LIST_W, y + ROW_H - 1, 0x40FFFFFF);
            g.fill(l + 6, y + 2, l + 12, y + 8, COLORS[Math.max(0, Math.min(4, r.color()))]);
            String name = (r.stub() ? "† " : "") + r.name();
            g.drawString(font, font.plainSubstrByWidth(name, LIST_W - 20), l + 16, y + 1, r.stub() ? 0xAAAAAA : 0xFFFFFF, false);
        }
        if (rows.isEmpty()) g.drawString(font, Component.translatable("rpmedicine.gm.empty"), l + 6, t + 20, 0x808080, false);
        g.fill(l + LIST_W + 4, t + 18, l + LIST_W + 5, t + H - 6, 0xFF404850);
        // Отчёт.
        int rx = l + LIST_W + 10;
        int ry = t + 20;
        GmPanelPacket.Row sel = selectedRow();
        if (sel != null) {
            g.drawString(font, Component.literal(sel.name() + "  " + sel.dimension() + " " + sel.x() + " " + sel.y() + " " + sel.z())
                    .withStyle(ChatFormatting.GRAY), rx, t + 6, 0xFFFFFF, false);
        }
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (Component c : report) lines.addAll(font.split(c, W - LIST_W - 18));
        int maxLines = (H - 54) / 10;
        for (int i = 0; i < maxLines && reportScroll + i < lines.size(); i++) g.drawString(font, lines.get(reportScroll + i), rx, ry + i * 10, 0xFFFFFF, false);
        if (selected == null) g.drawString(font, Component.translatable("rpmedicine.gm.pick"), rx, ry, 0x808080, false);
        super.render(g, mx, my, pt);
    }

    @Nullable
    private GmPanelPacket.Row selectedRow() {
        for (GmPanelPacket.Row r : panel.rows()) if (r.uuid().equals(selected)) return r;
        return null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int l = left();
        int t = top();
        if (button == 0 && mx >= l + 3 && mx < l + LIST_W && my >= t + 20) {
            int i = (int) ((my - t - 20) / ROW_H) + scroll;
            if (i >= 0 && i < panel.rows().size()) {
                selected = panel.rows().get(i).uuid();
                report = List.of();
                Network.sendToServer(new GmActionPacket(selected, GmActionPacket.Action.INSPECT));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx < left() + LIST_W) scroll = Math.max(0, Math.min(Math.max(0, panel.rows().size() - 5), scroll - (int) Math.signum(delta)));
        else reportScroll = Math.max(0, reportScroll - (int) Math.signum(delta));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
