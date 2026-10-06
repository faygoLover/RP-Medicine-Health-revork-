package faygolover.rpmedicine.client.screen;

import faygolover.rpmedicine.network.DosePacket;
import faygolover.rpmedicine.network.Network;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Выбор дозы шприц-ручкой (замечание 06.10): как у настоящей ручки — колёсиком мыши вращаешь пятку поршня,
 * на счётчике в окошке растёт доза (шаг 0,1, не больше, чем осталось). ЛКМ или Enter — ввести, Esc — отмена.
 * Опытному медику подсказано, сколько нужно этому пациенту.
 */
public class DoseScreen extends Screen {
    private final DosePacket.Request req;
    @Nullable
    private final Screen back;
    private boolean sent;
    /** Набранная доза, десятые доли. */
    private int tenths;
    /** Угол пятки поршня для отрисовки. */
    private float spin;

    public DoseScreen(DosePacket.Request req, @Nullable Screen back) {
        super(Component.translatable("rpmedicine.dose.title"));
        this.req = req;
        this.back = back;
    }

    private int maxTenths() {
        return Math.max(1, (int) Math.floor(req.max() * 10 + 1e-4));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int before = tenths;
        tenths = Math.max(0, Math.min(maxTenths(), tenths + (delta > 0 ? 1 : -1)));
        if (tenths != before) {
            spin += delta > 0 ? 36 : -36;
            minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 2.0f, 0.15f));
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && tenths > 0) {
            choose();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((key == 257 || key == 335) && tenths > 0) {
            choose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    private void choose() {
        if (sent) return;
        sent = true;
        Network.sendToServer(new DosePacket.Choice(req.targetId(), req.slot(), req.part(), tenths / 10f));
        minecraft.setScreen(back);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int cx = width / 2;
        int cy = height / 2;
        g.drawCenteredString(font, title.copy().withStyle(ChatFormatting.GOLD), cx, cy - 70, 0xFFFFFF);
        g.drawCenteredString(font, req.drug(), cx, cy - 56, 0xFFFFFF);
        // Ручка лежит горизонтально: игла слева, пятка поршня справа.
        int l = cx - 120, r = cx + 90, top = cy - 14, bot = cy + 14;
        g.fill(l - 24, cy - 1, l, cy + 1, 0xFFC8CDD4);                       // игла
        g.fill(l - 4, cy - 6, l, cy + 6, 0xFF8A9098);                         // колпачок иглы
        g.fill(l, top, r, bot, 0xFFD8DCE2);                                   // корпус
        g.renderOutline(l, top, r - l, bot - top, 0xFF4A4E56);
        // Окошко со шкалой: заполнено препаратом, сколько осталось.
        int wl = l + 20, wr = l + 120;
        g.fill(wl, top + 5, wr, bot - 5, 0xFF3A3E44);
        int fill = (int) Math.round((wr - wl) * Math.min(1, req.max() / 4.0));
        g.fill(wl, top + 6, wl + fill, bot - 6, 0xFFE8D27A);
        int dose = (int) Math.round((wr - wl) * (tenths / 40.0));
        g.fill(wl, top + 6, wl + dose, bot - 6, 0xFFE07A3A);                  // набранная доза
        for (int i = 0; i <= 40; i++) {
            int x = wl + (wr - wl) * i / 40;
            g.fill(x, top + 5, x + 1, top + (i % 10 == 0 ? 11 : 8), 0xFF1E2024);
        }
        // Счётчик дозы.
        String counter = String.format(Locale.ROOT, "%.1f", tenths / 10f).replace('.', ',');
        int cl = wr + 10, cw = 40;
        g.fill(cl, top + 3, cl + cw, bot - 3, 0xFF101214);
        g.drawCenteredString(font, counter, cl + cw / 2, cy - 4, 0xFF7CFF7C);
        // Пятка поршня — крутится.
        int kl = r, kw = 22;
        g.fill(kl, top - 4, kl + kw, bot + 4, 0xFF6A7078);
        int stripe = (int) Math.floorMod((int) spin / 12, 4);
        for (int i = 0; i < 6; i++) {
            int y = top - 4 + ((i * 6 + stripe * 2) % 36);
            if (y < bot + 4) g.fill(kl, y, kl + kw, y + 1, 0xFF3E434A);
        }
        g.drawCenteredString(font, Component.translatable("rpmedicine.dose.left", fmt(req.max())).withStyle(ChatFormatting.GRAY), cx, bot + 14, 0xFFFFFF);
        if (req.hint() > 0)
            g.drawCenteredString(font, Component.translatable("rpmedicine.dose.hint", fmt(req.hint()), req.weightKg()).withStyle(ChatFormatting.AQUA),
                    cx, bot + 26, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("rpmedicine.dose.howto").withStyle(ChatFormatting.DARK_GRAY), cx, bot + 42, 0xFFFFFF);
        super.render(g, mx, my, pt);
    }

    private static String fmt(float v) {
        return String.format(Locale.ROOT, "%.1f", v).replace('.', ',');
    }

    @Override
    public void onClose() {
        if (!sent) {
            sent = true;
            Network.sendToServer(new DosePacket.Choice(req.targetId(), req.slot(), req.part(), 0f));
        }
        minecraft.setScreen(back);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
