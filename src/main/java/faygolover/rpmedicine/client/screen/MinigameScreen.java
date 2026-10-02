package faygolover.rpmedicine.client.screen;

import faygolover.rpmedicine.core.Minigames;
import faygolover.rpmedicine.network.MinigameResultPacket;
import faygolover.rpmedicine.network.MinigameStartPacket;
import faygolover.rpmedicine.network.Network;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

/**
 * Мини-игры лечения (ТЗ второго этапа, п. 9). Сервер присылает вид, сид и сложность; результат —
 * качество 0–1, отказ — −1 (тогда прогресс-бар). Проверяет результат сервер.
 */
public class MinigameScreen extends Screen {
    private static final int W = 240;
    private static final int H = 150;
    /** Мини-игра не дольше, секунды (дальше засчитывается то, что успели). */
    private static final double TIME_LIMIT = 30;

    private final MinigameStartPacket task;
    @Nullable
    private final Screen back;
    private final Random rnd;
    private final double ease;
    private final long startMs = System.currentTimeMillis();
    private boolean sent;
    private boolean mouseDown;

    // Тайминг (укол, вена, вправление)
    private double windowCenter;
    private double windowHalf;
    private double speed;
    private int hitsNeeded;
    private int hits;
    private double hitQualitySum;

    // Перевязка
    private double angleSum;
    private double lastAngle = Double.NaN;
    private double deviationSum;
    private int deviationCount;

    // Жгут
    private double tension;
    private double zoneLow;
    private double zoneHigh;
    private double inZone;
    private long lastFrameMs = System.currentTimeMillis();

    // Пинцет
    private double[][] path;
    private double channel;
    private int touches;
    private boolean outside;
    private int progressIndex;
    private boolean dragging;

    // Швы
    private double[][] points;
    private int pointIndex;
    private double pointRadius;

    public MinigameScreen(MinigameStartPacket task, @Nullable Screen back) {
        super(Component.translatable("rpmedicine.minigame." + task.type().id()));
        this.task = task;
        this.back = back;
        this.rnd = new Random(task.seed());
        this.ease = Mth.clamp(task.ease(), 0.1f, 1f);
        setupGame();
    }

    public int session() {
        return task.session();
    }

    private void setupGame() {
        switch (task.type()) {
            case INJECTION, VEIN, REDUCE -> {
                windowHalf = (0.04 + 0.08 * ease) * (task.type() == Minigames.Type.REDUCE ? 0.7 : task.type() == Minigames.Type.VEIN ? 0.8 : 1.0);
                speed = (task.type() == Minigames.Type.REDUCE ? 3.4 : 2.2) * (1.6 - 0.6 * ease);
                hitsNeeded = task.type() == Minigames.Type.VEIN ? 2 : 1;
                windowCenter = 0.2 + 0.6 * rnd.nextDouble();
            }
            case TOURNIQUET -> {
                double width = 0.12 + 0.12 * ease;
                zoneLow = 0.45 + rnd.nextDouble() * (0.4 - width);
                zoneHigh = zoneLow + width;
            }
            case TWEEZERS -> {
                int n = 7;
                path = new double[n][2];
                for (int i = 0; i < n; i++) {
                    path[i][0] = 20 + i * (W - 40) / (double) (n - 1);
                    path[i][1] = 75 + (i == 0 || i == n - 1 ? 0 : (rnd.nextDouble() - 0.5) * 70);
                }
                channel = 5 + 6 * ease;
            }
            case SUTURE -> {
                int n = 6;
                points = new double[n][2];
                double y0 = 50 + rnd.nextDouble() * 20;
                double y1 = 80 + rnd.nextDouble() * 30;
                for (int i = 0; i < n; i++) {
                    double t = i / (double) (n - 1);
                    points[i][0] = 40 + t * (W - 80) + (rnd.nextDouble() - 0.5) * 8;
                    points[i][1] = Mth.lerp(t, y0, y1) + (rnd.nextDouble() - 0.5) * 10;
                }
                pointRadius = 5 + 6 * ease;
            }
            default -> { }
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
        if (task.refuse()) {
            addRenderableWidget(Button.builder(Component.translatable("rpmedicine.minigame.refuse"), b -> refuse())
                    .bounds(width / 2 - 80, top() + H + 6, 160, 20).build());
        }
    }

    private double elapsed() {
        return (System.currentTimeMillis() - startMs) / 1000.0;
    }

    /** Положение стрелки 0–1 в тайминговых играх. */
    private double marker() {
        return (Math.sin(elapsed() * speed) + 1) / 2;
    }

    // ------------------------------------------------------------------ отрисовка

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int l = left();
        int t = top();
        g.fill(l, t, l + W, t + H, 0xE0101418);
        g.renderOutline(l, t, W, H, 0xFF505860);
        g.drawCenteredString(font, Component.translatable(task.itemKey()).append(" — ").append(title), width / 2, t + 6, 0xFFFFFF);
        // Подсказка переносится внутри рамки.
        int hy = t + 18;
        for (var line : font.split(Component.translatable("rpmedicine.minigame.hint_" + task.type().id()).withStyle(ChatFormatting.GRAY), W - 12)) {
            g.drawCenteredString(font, line, width / 2, hy, 0xFFFFFF);
            hy += 9;
        }
        long now = System.currentTimeMillis();
        double dt = Math.min(0.1, (now - lastFrameMs) / 1000.0);
        lastFrameMs = now;
        switch (task.type()) {
            case INJECTION, VEIN, REDUCE -> renderTiming(g, l, t);
            case BANDAGE -> renderBandage(g, l, t, mx, my);
            case TOURNIQUET -> renderTourniquet(g, l, t, dt);
            case TWEEZERS -> renderTweezers(g, l, t, mx, my);
            case SUTURE -> renderSuture(g, l, t);
        }
        // Время.
        double left = TIME_LIMIT - elapsed();
        g.drawString(font, String.format(java.util.Locale.ROOT, "%.0f", Math.max(0, left)), l + W - 18, t + H - 12, 0x808080, false);
        if (left <= 0) finish(timeoutQuality());
        super.render(g, mx, my, pt);
    }

    private void renderTiming(GuiGraphics g, int l, int t) {
        int bx = l + 20;
        int by = t + 70;
        int bw = W - 40;
        g.fill(bx, by, bx + bw, by + 12, 0xFF303840);
        int wx0 = bx + (int) ((windowCenter - windowHalf) * bw);
        int wx1 = bx + (int) ((windowCenter + windowHalf) * bw);
        g.fill(wx0, by, wx1, by + 12, 0xFF2E8B57);
        int mx = bx + (int) (marker() * bw);
        g.fill(mx - 1, by - 4, mx + 2, by + 16, 0xFFFFFFFF);
        if (hitsNeeded > 1)
            g.drawCenteredString(font, Component.translatable("rpmedicine.minigame.hits", hits, hitsNeeded), width / 2, by + 24, 0xAAAAAA);
    }

    private void renderBandage(GuiGraphics g, int l, int t, int mx, int my) {
        int cx = l + W / 2;
        int cy = t + 85;
        int r = 40;
        // Окружность, по которой вести мышь.
        for (int i = 0; i < 72; i++) {
            double a = i * Math.PI * 2 / 72;
            int x = cx + (int) (Math.cos(a) * r);
            int y = cy + (int) (Math.sin(a) * r);
            g.fill(x, y, x + 2, y + 2, 0xFF2E8B57);
        }
        g.fill(cx - 6, cy - 6, cx + 6, cy + 6, 0xFF884444);
        double turns = Math.abs(angleSum) / (Math.PI * 2);
        g.drawCenteredString(font, Component.translatable("rpmedicine.minigame.turns", String.format(java.util.Locale.ROOT, "%.1f", Math.min(3, turns))),
                width / 2, t + H - 14, 0xAAAAAA);
        if (mouseDown) {
            double dx = mx - cx;
            double dy = my - cy;
            double dist = Math.sqrt(dx * dx + dy * dy);
            double a = Math.atan2(dy, dx);
            if (!Double.isNaN(lastAngle)) {
                double d = a - lastAngle;
                if (d > Math.PI) d -= Math.PI * 2;
                if (d < -Math.PI) d += Math.PI * 2;
                if (Math.abs(d) < 1.0) angleSum += d;
            }
            lastAngle = a;
            deviationSum += Math.abs(dist - r) / r;
            deviationCount++;
            if (Math.abs(angleSum) >= Math.PI * 6) finish(bandageQuality());
        } else {
            lastAngle = Double.NaN;
        }
    }

    private double bandageQuality() {
        double dev = deviationCount > 0 ? deviationSum / deviationCount : 1;
        return Mth.clamp(1 - dev * 2.5 * (1.2 - 0.4 * ease), 0, 1);
    }

    private void renderTourniquet(GuiGraphics g, int l, int t, double dt) {
        tension += (mouseDown ? 0.55 : -0.45) * dt;
        tension = Mth.clamp(tension, 0, 1);
        if (tension >= zoneLow && tension <= zoneHigh) inZone += dt;
        int bx = l + W / 2 - 10;
        int by = t + 34;
        int bh = 100;
        g.fill(bx, by, bx + 20, by + bh, 0xFF303840);
        int zy0 = by + bh - (int) (zoneHigh * bh);
        int zy1 = by + bh - (int) (zoneLow * bh);
        g.fill(bx, zy0, bx + 20, zy1, 0xFF2E8B57);
        int ty = by + bh - (int) (tension * bh);
        g.fill(bx - 6, ty - 1, bx + 26, ty + 2, 0xFFFFFFFF);
        g.drawString(font, Component.translatable("rpmedicine.minigame.hold", String.format(java.util.Locale.ROOT, "%.1f", Math.min(3, inZone))),
                l + W / 2 + 20, t + 80, 0xAAAAAA, false);
        if (inZone >= 3) finish(Mth.clamp(inZone / Math.max(3, elapsed()) * 1.4, 0, 1));
    }

    private void renderTweezers(GuiGraphics g, int l, int t, int mx, int my) {
        // Канал: толстая линия по точкам.
        for (int i = 0; i + 1 < path.length; i++) {
            for (int k = 0; k <= 30; k++) {
                double f = k / 30.0;
                int x = l + (int) Mth.lerp(f, path[i][0], path[i + 1][0]);
                int y = t + (int) Mth.lerp(f, path[i][1], path[i + 1][1]);
                int c = (int) channel;
                g.fill(x - c, y - c, x + c, y + c, 0xFF5A3030);
            }
        }
        int sx = l + (int) path[0][0];
        int sy = t + (int) path[0][1];
        int ex = l + (int) path[path.length - 1][0];
        int ey = t + (int) path[path.length - 1][1];
        g.fill(sx - 4, sy - 4, sx + 4, sy + 4, 0xFF2E8B57);
        g.fill(ex - 4, ey - 4, ex + 4, ey + 4, 0xFFC0C0C0);
        g.drawCenteredString(font, Component.translatable("rpmedicine.minigame.touches", touches), width / 2, t + H - 14, 0xAAAAAA);
        if (!dragging) return;
        double lx = mx - l;
        double ly = my - t;
        double d = distanceToPath(lx, ly);
        if (d > channel) {
            if (!outside) touches++;
            outside = true;
        } else {
            outside = false;
        }
        // Продвижение по каналу: нельзя перескочить через участки.
        for (int i = progressIndex; i < path.length; i++) {
            if (Math.hypot(lx - path[i][0], ly - path[i][1]) < channel * 2.2) progressIndex = Math.max(progressIndex, i);
            else if (i > progressIndex) break;
        }
        if (progressIndex == path.length - 1 && Math.hypot(lx - path[path.length - 1][0], ly - path[path.length - 1][1]) < channel * 1.5)
            finish(Mth.clamp(1 - touches * 0.25, 0, 1));
    }

    private double distanceToPath(double x, double y) {
        double best = Double.MAX_VALUE;
        for (int i = 0; i + 1 < path.length; i++) {
            double ax = path[i][0], ay = path[i][1], bx = path[i + 1][0], by = path[i + 1][1];
            double vx = bx - ax, vy = by - ay;
            double f = Mth.clamp(((x - ax) * vx + (y - ay) * vy) / (vx * vx + vy * vy), 0, 1);
            best = Math.min(best, Math.hypot(x - (ax + vx * f), y - (ay + vy * f)));
        }
        return best;
    }

    private void renderSuture(GuiGraphics g, int l, int t) {
        // Линия раны.
        for (int i = 0; i + 1 < points.length; i++) {
            for (int k = 0; k <= 20; k++) {
                double f = k / 20.0;
                int x = l + (int) Mth.lerp(f, points[i][0], points[i + 1][0]);
                int y = t + (int) Mth.lerp(f, points[i][1], points[i + 1][1]);
                g.fill(x, y, x + 2, y + 2, 0xFF8B2020);
            }
        }
        for (int i = 0; i < points.length; i++) {
            int x = l + (int) points[i][0];
            int y = t + (int) points[i][1];
            int r = (int) pointRadius;
            int color = i < pointIndex ? 0xFF2E8B57 : i == pointIndex ? 0xFFFFD700 : 0xFF606060;
            g.renderOutline(x - r, y - r, r * 2, r * 2, color);
        }
    }

    private double timeoutQuality() {
        return switch (task.type()) {
            case BANDAGE -> bandageQuality() * Math.min(1, Math.abs(angleSum) / (Math.PI * 6));
            case TOURNIQUET -> Mth.clamp(inZone / 3 * 0.5, 0, 0.5);
            case SUTURE -> pointIndex == 0 ? 0 : hitQualitySum / points.length;
            case INJECTION, VEIN, REDUCE -> hits == 0 ? 0 : hitQualitySum / hitsNeeded;
            default -> 0;
        };
    }

    // ------------------------------------------------------------------ ввод

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button != 0 || sent) return false;
        mouseDown = true;
        switch (task.type()) {
            case INJECTION, VEIN, REDUCE -> timingHit();
            case TWEEZERS -> {
                double lx = mx - left();
                double ly = my - top();
                if (Math.hypot(lx - path[0][0], ly - path[0][1]) < channel * 1.5) {
                    dragging = true;
                    progressIndex = 0;
                    outside = false;
                }
            }
            case SUTURE -> {
                double lx = mx - left();
                double ly = my - top();
                double d = Math.hypot(lx - points[pointIndex][0], ly - points[pointIndex][1]);
                hitQualitySum += Mth.clamp(1 - d / (pointRadius * 1.5), 0, 1);
                pointIndex++;
                if (pointIndex >= points.length) finish(hitQualitySum / points.length);
            }
            default -> { }
        }
        return true;
    }

    private void timingHit() {
        double d = Math.abs(marker() - windowCenter);
        hitQualitySum += Mth.clamp(1 - d / (windowHalf * 1.5), 0, 1);
        hits++;
        if (hits >= hitsNeeded) finish(hitQualitySum / hitsNeeded);
        else windowCenter = 0.2 + 0.6 * rnd.nextDouble();
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0) {
            mouseDown = false;
            if (dragging) {
                // Отпустил пинцет на полпути — касание и заново от начала.
                dragging = false;
                touches++;
                progressIndex = 0;
            }
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        // Пробел — то же, что клик, в тайминговых играх.
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE && !sent
                && (task.type() == Minigames.Type.INJECTION || task.type() == Minigames.Type.VEIN || task.type() == Minigames.Type.REDUCE)) {
            timingHit();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    // ------------------------------------------------------------------ итог

    private void finish(double quality) {
        if (sent) return;
        sent = true;
        Network.sendToServer(new MinigameResultPacket(task.session(), (float) Mth.clamp(quality, 0, 1)));
        minecraft.setScreen(back);
    }

    private void refuse() {
        if (sent) return;
        sent = true;
        Network.sendToServer(new MinigameResultPacket(task.session(), -1f));
        minecraft.setScreen(back);
    }

    /** Сервер отменил действие (урон, ушёл от пациента) — закрыть без ответа. */
    public void cancelledByServer() {
        if (sent) return;
        sent = true;
        minecraft.setScreen(back);
    }

    @Override
    public void onClose() {
        // Esc — отказ от мини-игры (прогресс-бар), если можно.
        refuse();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
