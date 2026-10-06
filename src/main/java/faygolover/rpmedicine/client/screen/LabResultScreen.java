package faygolover.rpmedicine.client.screen;

import faygolover.rpmedicine.network.LabResultPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Бланк анализа крови: показатель, результат, единицы, норма; вне нормы — красным со стрелкой
 * (замечание 06.10: результаты в окне с настоящими значениями, а не в чате).
 */
public class LabResultScreen extends Screen {
    private static final int W = 320;
    private static final int PAPER = 0xFFF3EFE4;
    private static final int INK = 0xFF2A2A30;
    private static final int GRAY = 0xFF7A7568;
    private static final int RED = 0xFFB0202A;
    private static final int GREEN = 0xFF1F7A3A;
    private static final int LINE = 0xFFD6CFBE;

    private final LabResultPacket r;
    private final String date = new java.text.SimpleDateFormat("dd.MM.yy HH:mm").format(new java.util.Date());

    public LabResultScreen(LabResultPacket r) {
        super(Component.translatable("rpmedicine.labform.title"));
        this.r = r;
    }

    private int formH() {
        return 92 + LabResultPacket.ROWS.length * 12 + (r.compat() >= 0 ? 12 : 0) + 30;
    }

    @Override
    protected void init() {
        int t = (height - formH()) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(width / 2 - 40, t + formH() - 24, 80, 18).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int h = formH();
        int l = (width - W) / 2;
        int t = (height - h) / 2;
        g.fill(l + 3, t + 3, l + W + 3, t + h + 3, 0x60000000);
        g.fill(l, t, l + W, t + h, PAPER);
        g.renderOutline(l, t, W, h, 0xFF9A9080);
        // Шапка
        g.drawCenteredString(font, Component.translatable("rpmedicine.labform.lab").withStyle(s -> s.withItalic(true)), width / 2, t + 6, 0xFF8A8270);
        g.drawString(font, title, l + (W - font.width(title)) / 2, t + 17, INK, false);
        g.fill(l + 10, t + 29, l + W - 10, t + 30, INK);
        g.drawString(font, Component.translatable("rpmedicine.labform.patient", r.patient().isEmpty() ? "—" : r.patient()), l + 12, t + 35, INK, false);
        g.drawString(font, date, l + W - 12 - font.width(date), t + 35, GRAY, false);
        g.drawString(font, Component.translatable("rpmedicine.labform.blood_type", r.bloodType().isEmpty() ? "?" : r.bloodType()), l + 12, t + 47, INK, false);
        // Таблица
        int cName = l + 12, cVal = l + 150, cUnit = l + 184, cNorm = l + 254;
        int y = t + 63;
        g.drawString(font, Component.translatable("rpmedicine.labform.col_name"), cName, y, GRAY, false);
        g.drawString(font, Component.translatable("rpmedicine.labform.col_result"), cVal, y, GRAY, false);
        g.drawString(font, Component.translatable("rpmedicine.labform.col_norm"), cNorm, y, GRAY, false);
        g.fill(l + 10, y + 10, l + W - 10, y + 11, LINE);
        y += 15;
        for (int i = 0; i < LabResultPacket.ROWS.length; i++) {
            LabResultPacket.Row row = LabResultPacket.ROWS[i];
            double v = r.values()[i];
            boolean low = v < row.low(), high = v > row.high();
            int col = low || high ? RED : INK;
            g.drawString(font, Component.translatable("rpmedicine.labform." + row.key()), cName, y, INK, false);
            String val = fmt(v, row.digits()) + (high ? " ↑" : low ? " ↓" : "");
            g.drawString(font, val, cVal, y, col, false);
            g.drawString(font, Component.translatable("rpmedicine.labform." + row.key() + ".unit"), cUnit, y, GRAY, false);
            String norm = row.low() <= 0 ? "< " + fmt(row.high(), row.digits()) : fmt(row.low(), row.digits()) + "–" + fmt(row.high(), row.digits());
            g.drawString(font, norm, cNorm, y, GRAY, false);
            y += 12;
        }
        g.fill(l + 10, y + 1, l + W - 10, y + 2, LINE);
        y += 6;
        g.drawString(font, Component.translatable(r.sepsis() ? "rpmedicine.labform.sepsis_yes" : "rpmedicine.labform.sepsis_no"),
                cName, y, r.sepsis() ? RED : INK, false);
        if (r.compat() >= 0) {
            y += 12;
            Component c = switch (r.compat()) {
                case 1 -> Component.translatable("rpmedicine.labform.compat_yes", r.bag());
                case 2 -> Component.translatable("rpmedicine.labform.compat_no", r.bag());
                default -> Component.translatable("rpmedicine.labform.compat_unknown");
            };
            g.drawString(font, c, cName, y, r.compat() == 1 ? GREEN : r.compat() == 2 ? RED : GRAY, false);
        }
        super.render(g, mx, my, pt);
    }

    private static String fmt(double v, int digits) {
        return digits == 0 ? String.valueOf(Math.round(v)) : String.format(Locale.ROOT, "%." + digits + "f", v).replace('.', ',');
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
