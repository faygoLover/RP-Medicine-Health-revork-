package faygolover.rpmedicine.client.screen;

import faygolover.rpmedicine.network.DrawPacket;
import faygolover.rpmedicine.network.Network;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Locale;

/**
 * Мини-игра набора из флакона (решения, п. 1.16): флакон вверх дном, резиновая пробка гуляет — ЛКМ, когда она
 * над иглой (проткнуть). Потом колёсиком тянуть поршень и набирать мл (шаг 0,1). Подсказка, сколько нужно, —
 * только опытному; неопытный набирает на глаз (сколько выйдет).
 */
public class DrawScreen extends Screen {
    private static final float SYRINGE_ML = 10f;
    private final DrawPacket.Request req;
    private int stage;
    private int misses;
    private int tenths;
    private float shake;
    private long start = System.currentTimeMillis();
    private boolean sent;

    public DrawScreen(DrawPacket.Request req) {
        super(Component.translatable("rpmedicine.draw.title"));
        this.req = req;
    }

    private float t() {
        return (System.currentTimeMillis() - start) / 1000f;
    }

    /** Смещение пробки от центра, px: у новичка гуляет быстрее и шире. */
    private float stopperX() {
        float speed = 1.4f + (10 - req.level()) * 0.18f;
        float amp = 34 + (10 - req.level()) * 3;
        return Mth.sin(t() * speed) * amp + Mth.sin(t() * speed * 2.3f) * amp * 0.25f;
    }

    private int maxTenths() {
        return (int) Math.floor(Math.min(req.left(), SYRINGE_ML) * 10 + 1e-4);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        if (stage == 0) {
            if (Math.abs(stopperX()) < 9) {
                stage = 1;
                start = System.currentTimeMillis();
                if (!req.skilled()) tenths = 10;
            } else {
                misses++;
                shake = 1;
            }
            return true;
        }
        if (stage == 1 && req.skilled() && tenths > 0) {
            send(tenths / 10f);
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (stage == 1 && req.skilled()) tenths = Math.max(0, Math.min(maxTenths(), tenths + (delta > 0 ? 1 : -1)));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((key == 257 || key == 335) && stage == 1 && req.skilled() && tenths > 0) {
            send(tenths / 10f);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void tick() {
        // Неопытный: поршень тянется сам «на глаз», потом готово.
        if (stage == 1 && !req.skilled() && t() > 1.6f) send(1f);
        shake = Math.max(0, shake - 0.1f);
    }

    private void send(float ml) {
        if (sent) return;
        sent = true;
        Network.sendToServer(new DrawPacket.Choice(ml, misses));
        onClose();
    }

    @Override
    public void onClose() {
        if (!sent) {
            sent = true;
            Network.sendToServer(new DrawPacket.Choice(0, misses));
        }
        super.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int cx = width / 2, cy = height / 2;
        g.drawCenteredString(font, title.copy().withStyle(ChatFormatting.GOLD), cx, cy - 100, 0xFFFFFF);
        g.drawCenteredString(font, req.drug().copy().append(" ").append(req.label()), cx, cy - 88, 0xFFFFFF);
        // Флакон вверх дном.
        int vx = cx + (stage == 0 ? Math.round(stopperX()) : 0) + Math.round(Mth.sin(shake * 20) * 3 * shake);
        int vTop = cy - 76, vBot = cy - 30;
        g.fill(vx - 16, vTop, vx + 16, vBot, 0xC0D8E4EC);
        float full = Math.min(1, req.left() / 10f);
        int liq = (int) ((vBot - vTop - 4) * full);
        g.fill(vx - 14, vBot - 2 - liq, vx + 14, vBot - 2, 0xC0E8E0A0);
        g.fill(vx - 9, vBot, vx + 9, vBot + 8, 0xFFA0A4AA);                     // колпачок
        g.fill(vx - 6, vBot + 2, vx + 6, vBot + 8, 0xFF7A2A30);                 // резиновая пробка
        // Шприц иглой вверх.
        int sTop = cy - 10, sBot = cy + 70, sl = cx - 8, sr = cx + 8;
        int needleTop = stage == 0 ? sTop - 14 : vBot + 3;
        g.fill(cx - 1, needleTop, cx + 1, sTop, 0xFFC8CDD4);
        g.fill(sl, sTop, sr, sBot, 0xC0EEF2F6);
        g.renderOutline(sl, sTop, sr - sl, sBot - sTop, 0xFF6A7078);
        float shown = stage == 1 && !req.skilled() ? Math.min(1, t() / 1.5f) * 1f : tenths / 10f;
        int h = (int) ((sBot - sTop) * (shown / SYRINGE_ML));
        g.fill(sl + 1, sTop + 1, sr - 1, sTop + 1 + h, 0xD0F4F0E0);
        g.fill(sl - 2, sTop + 1 + h, sr + 2, sTop + 4 + h, 0xFF30343A);         // поршень
        g.fill(cx - 2, sTop + 4 + h, cx + 2, sBot + 18, 0xFF8A9098);             // шток
        for (int i = 0; i <= 10; i++) {
            int y = sTop + (sBot - sTop) * i / 10;
            g.fill(sr, y, sr + (i % 5 == 0 ? 6 : 3), y + 1, 0xFF30343A);
            if (i % 5 == 0) g.drawString(font, String.valueOf(i), sr + 8, y - 4, 0xFFFFFFFF, false);
        }
        int ty = sBot + 26;
        if (stage == 0) {
            g.drawCenteredString(font, Component.translatable("rpmedicine.draw.pierce").withStyle(ChatFormatting.GRAY), cx, ty, 0xFFFFFF);
        } else if (req.skilled()) {
            String ml = String.format(Locale.ROOT, "%.1f", tenths / 10f).replace('.', ',');
            g.drawCenteredString(font, Component.translatable("rpmedicine.draw.amount", ml), cx, ty, 0xFF7CFF7C);
            if (req.hint() > 0)
                g.drawCenteredString(font, Component.translatable("rpmedicine.draw.hint",
                        String.format(Locale.ROOT, "%.1f", req.hint()).replace('.', ',')).withStyle(ChatFormatting.AQUA), cx, ty + 12, 0xFFFFFF);
            g.drawCenteredString(font, Component.translatable("rpmedicine.draw.howto").withStyle(ChatFormatting.DARK_GRAY), cx, ty + 24, 0xFFFFFF);
        } else {
            g.drawCenteredString(font, Component.translatable("rpmedicine.draw.by_eye").withStyle(ChatFormatting.GRAY), cx, ty, 0xFFFFFF);
        }
        g.drawCenteredString(font, Component.translatable("rpmedicine.draw.left",
                String.format(Locale.ROOT, "%.1f", req.left()).replace('.', ',')).withStyle(ChatFormatting.DARK_GRAY), cx, cy - 22 + 0, 0xFFFFFF);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
