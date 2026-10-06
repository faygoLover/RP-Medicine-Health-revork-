package faygolover.rpmedicine.client.screen;

import faygolover.rpmedicine.network.GmActionPacket;
import faygolover.rpmedicine.network.GmEditPacket;
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
 * Панель ГМа (ТЗ второго этапа, п. 11.3): слева игроки онлайн и тела с цветом состояния, в середине —
 * силуэт (щелчок по части: нанести рану, вылечить часть, ввести препарат), справа — полный осмотр
 * цифрами или история; кнопки действий.
 */
public class GmPanelScreen extends Screen {
    private static final int W = 520;
    private static final int H = 262;
    private static final int LIST_W = 120;
    /** Силуэт: клетка в пикселях и ширина колонки. */
    private static final int UNIT = 4;
    private static final int BODY_W = faygolover.rpmedicine.client.Silhouette.GRID_W * UNIT + 16;
    private static final double[] SEVERITIES = {10, 20, 40, 70, 100};
    private static final double[] DOSES = {0.5, 1, 1.5, 2, 3};
    private static final int ROW_H = 12;
    private static final int[] COLORS = {0xFF55FF55, 0xFFFFFF55, 0xFFFFAA00, 0xFFFF5555, 0xFFAA00AA};

    private GmPanelPacket panel;
    @Nullable
    private UUID selected;
    private List<Component> report = List.of();
    private int scroll;
    private int reportScroll;
    private byte[] colors = new byte[0];
    @Nullable
    private faygolover.rpmedicine.core.BodyPart part;
    private int woundType = 3;
    private int severity = 1;
    private int drug;
    private int dose = 1;
    /** Вкладка справа: 0 — осмотр, 1 — тело (силуэт и правка), 2 — показатели (замечание 57). */
    private int tab;
    private final java.util.Map<String, net.minecraft.client.gui.components.EditBox> values = new java.util.HashMap<>();
    private static final String[] TABS = {"exam", "body", "values"};

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
            if (p.colors().length > 0) colors = p.colors();
            reportScroll = 0;
        }
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    private int contentX() {
        return left() + LIST_W + 10;
    }

    private int bodyX() {
        return contentX();
    }

    private int bodyY() {
        return top() + 36;
    }

    private void edit(GmEditPacket.Op op, String arg, double value) {
        if (selected != null && part != null) Network.sendToServer(new GmEditPacket(selected, op, part, arg, (float) value));
    }

    @Override
    protected void init() {
        int cx = contentX();
        // Вкладки.
        for (int k = 0; k < TABS.length; k++) {
            int idx = k;
            addRenderableWidget(Button.builder(Component.translatable("rpmedicine.gm.tab." + TABS[k])
                    .withStyle(tab == k ? ChatFormatting.YELLOW : ChatFormatting.WHITE), b -> {
                tab = idx;
                rebuildWidgets();
            }).bounds(cx + k * 96, top() + 18, 92, 14).build());
        }
        if (selected != null && tab == 1) initBody();
        if (selected != null && tab == 2) initValues();
        // Действия — внизу.
        int x = cx;
        int y = top() + H - 24;
        GmActionPacket.Action[] actions = {GmActionPacket.Action.HEAL, GmActionPacket.Action.REVIVE, GmActionPacket.Action.KILL,
                GmActionPacket.Action.TELEPORT, GmActionPacket.Action.HISTORY, GmActionPacket.Action.INSPECT};
        int aw = (W - LIST_W - 16) / actions.length;
        for (int i = 0; i < actions.length; i++) {
            GmActionPacket.Action a = actions[i];
            addRenderableWidget(Button.builder(Component.translatable("rpmedicine.gm." + a.name().toLowerCase(java.util.Locale.ROOT)), b -> {
                if (selected == null) return;
                if (a == GmActionPacket.Action.HISTORY || a == GmActionPacket.Action.INSPECT) {
                    tab = 0;
                    rebuildWidgets();
                }
                Network.sendToServer(new GmActionPacket(selected, a));
            }).bounds(x + i * aw, y, aw - 2, 18).build());
        }
    }

    /** Тело: силуэт слева, правка выбранной части — справа, кнопки во всю ширину. */
    private void initBody() {
        if (part == null) return;
        int bx = contentX() + BODY_W + 10;
        int by = bodyY();
        int bw = W - LIST_W - BODY_W - 30;
        var types = faygolover.rpmedicine.core.WoundType.VALUES;
        addRenderableWidget(Button.builder(Component.translatable("rpmedicine.wound." + types[woundType].id), b -> {
            woundType = (woundType + 1) % types.length;
            b.setMessage(Component.translatable("rpmedicine.wound." + types[woundType].id));
        }).bounds(bx, by, bw, 14).build());
        addRenderableWidget(Button.builder(sevLabel(), b -> {
            severity = (severity + 1) % SEVERITIES.length;
            b.setMessage(sevLabel());
        }).bounds(bx, by + 16, bw, 14).build());
        addRenderableWidget(Button.builder(Component.translatable("rpmedicine.gm.injure_btn"),
                b -> edit(GmEditPacket.Op.INJURE, types[woundType].id, SEVERITIES[severity])).bounds(bx, by + 32, bw, 14).build());
        addRenderableWidget(Button.builder(Component.translatable("rpmedicine.gm.heal_part_btn"),
                b -> edit(GmEditPacket.Op.HEAL_PART, "", 0)).bounds(bx, by + 48, bw, 14).build());
        List<String> drugs = panel.drugs();
        if (!drugs.isEmpty()) {
            drug = Math.min(drug, drugs.size() - 1);
            addRenderableWidget(Button.builder(Component.literal(drugs.get(drug)), b -> {
                drug = (drug + (hasShiftDown() ? drugs.size() - 1 : 1)) % drugs.size();
                b.setMessage(Component.literal(drugs.get(drug)));
            }).bounds(bx, by + 72, bw, 14).build());
            addRenderableWidget(Button.builder(doseLabel(), b -> {
                dose = (dose + 1) % DOSES.length;
                b.setMessage(doseLabel());
            }).bounds(bx, by + 88, bw, 14).build());
            addRenderableWidget(Button.builder(Component.translatable("rpmedicine.gm.drug_btn"),
                    b -> edit(GmEditPacket.Op.DRUG, drugs.get(drug), DOSES[dose])).bounds(bx, by + 104, bw, 14).build());
        }
    }

    /** Показатели: поле и «Поставить» для крови, SpO2, мозга и прочего (замечание 58). */
    private void initValues() {
        values.clear();
        int x = contentX();
        int y = bodyY();
        for (String key : faygolover.rpmedicine.server.GmPanelService.SETTABLE) {
            var box = addRenderableWidget(new net.minecraft.client.gui.components.EditBox(font, x + 150, y, 70, 14, Component.empty()));
            box.setMaxLength(8);
            box.setFilter(t -> t.matches("[0-9.]*"));
            values.put(key, box);
            addRenderableWidget(Button.builder(Component.translatable("rpmedicine.gm.set_btn"), b -> {
                try {
                    double v = Double.parseDouble(box.getValue());
                    if (selected != null) Network.sendToServer(new GmEditPacket(selected, GmEditPacket.Op.SET,
                            faygolover.rpmedicine.core.BodyPart.CHEST, key, (float) v));
                } catch (NumberFormatException ignored) {
                    // Пустое поле — ничего.
                }
            }).bounds(x + 224, y, 70, 14).build());
            y += 18;
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
        GmPanelPacket.Row sel = selectedRow();
        if (sel != null) {
            g.drawString(font, Component.literal(sel.name() + "  " + sel.dimension() + " " + sel.x() + " " + sel.y() + " " + sel.z())
                    .withStyle(ChatFormatting.GRAY), contentX(), t + 6, 0xFFFFFF, false);
        }
        if (selected == null) {
            g.drawString(font, Component.translatable("rpmedicine.gm.pick"), contentX(), bodyY(), 0x808080, false);
        } else if (tab == 0) {
            // Осмотр: по строке на показатель, с прокруткой.
            List<FormattedCharSequence> lines = new ArrayList<>();
            for (Component c : report) lines.addAll(font.split(c, W - LIST_W - 22));
            int maxLines = (H - 36 - 30) / 10;
            for (int i = 0; i < maxLines && reportScroll + i < lines.size(); i++)
                g.drawString(font, lines.get(reportScroll + i), contentX(), bodyY() + i * 10, 0xFFFFFF, false);
        } else if (tab == 1) {
            byte[] c = colors.length == faygolover.rpmedicine.core.BodyPart.VALUES.length ? colors : new byte[faygolover.rpmedicine.core.BodyPart.VALUES.length];
            var hover = faygolover.rpmedicine.client.Silhouette.partAt(bodyX() + 6, bodyY(), UNIT, mx, my);
            faygolover.rpmedicine.client.Silhouette.draw(g, bodyX() + 6, bodyY(), UNIT, c, part, hover);
            Component cap = part != null ? Component.translatable(part.translationKey()) : Component.translatable("rpmedicine.gm.body_pick");
            g.drawCenteredString(font, cap, bodyX() + BODY_W / 2 - 2, bodyY() + faygolover.rpmedicine.client.Silhouette.GRID_H * UNIT + 3,
                    part != null ? 0xFFD700 : 0x808080);
        } else {
            int y = bodyY() + 3;
            for (String key : faygolover.rpmedicine.server.GmPanelService.SETTABLE) {
                g.drawString(font, Component.translatable("rpmedicine.gm.value." + key), contentX(), y, 0xC0C0C0, false);
                y += 18;
            }
        }
        super.render(g, mx, my, pt);
    }

    private Component sevLabel() {
        return Component.translatable("rpmedicine.gm.severity", (int) SEVERITIES[severity]);
    }

    private Component doseLabel() {
        double d = DOSES[dose];
        return Component.translatable("rpmedicine.gm.dose", d == Math.floor(d) ? String.valueOf((int) d) : String.valueOf(d));
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
        if (button == 0 && selected != null && tab == 1) {
            var p = faygolover.rpmedicine.client.Silhouette.partAt(bodyX() + 6, bodyY(), UNIT, mx, my);
            if (p != null) {
                part = part == p ? null : p;
                rebuildWidgets();
                return true;
            }
        }
        if (button == 0 && mx >= l + 3 && mx < l + LIST_W && my >= t + 20) {
            int i = (int) ((my - t - 20) / ROW_H) + scroll;
            if (i >= 0 && i < panel.rows().size()) {
                selected = panel.rows().get(i).uuid();
                report = List.of();
                colors = new byte[0];
                rebuildWidgets();
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
