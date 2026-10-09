package faygolover.rpmedicine.client.screen;

import faygolover.rpmedicine.core.Minigames;
import faygolover.rpmedicine.network.MinigameResultPacket;
import faygolover.rpmedicine.network.MinigameStartPacket;
import faygolover.rpmedicine.network.Network;
import faygolover.rpmedicine.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

/**
 * Мини-игры лечения (ТЗ второго этапа, п. 9; переделаны по итогам проверки в духе BodyControl).
 * Инструмент — иконка предмета — следует за курсором с инерцией; стенки раны и вены нельзя задевать;
 * внизу полоска оставшегося времени. Сервер присылает вид, сид и сложность; результат — качество 0–1,
 * отказ — −1 (тогда прогресс-бар). Проверяет результат сервер.
 */
public class MinigameScreen extends Screen {
    private static final int W = 300;
    private static final int H = 196;

    private final MinigameStartPacket task;
    @Nullable
    private final Screen back;
    private final Random rnd;
    private final double ease;
    private final ItemStack tool;
    private final double timeLimit;
    private double time;
    private long lastFrame = System.currentTimeMillis();
    private boolean sent;
    private boolean mouseDown;
    /** Инструмент в координатах рамки (с инерцией). */
    private double toolX = W / 2.0;
    private double toolY = H / 2.0;
    private double mouseX;
    private double mouseY;
    /** Ошибки (касания стенок, промахи) и вспышка после ошибки. */
    private int errors;
    private double flash;
    private int phase;
    private double progress;
    private String status = "";

    // Укол, вена
    private double targetX;
    private double targetY;
    private double targetR;
    private double pressure;
    private double zoneCenter;
    private double zoneHalf;
    private double zonePhase;
    // Канал (вена, пинцет, швы)
    private double[][] path;
    private double channel;
    private boolean outside;
    private int pathIndex;
    private boolean carrying;
    /** След иглы в вене: пройденные точки (замечание 36). */
    private final java.util.List<double[]> trail = new java.util.ArrayList<>();
    // Перевязка
    private double angleSum;
    private double lastAngle = Double.NaN;
    private int direction = 1;
    private double nextSwitch;
    private double segStart;
    /** После смены направления — короткое окно, когда ход в старую сторону не ошибка (замечание 49). */
    private double switchGraceUntil;
    private long lastErrorSound;
    private double deviationSum;
    private int deviationCount;
    private double wrongSum;
    // Швы
    private double[][] stitches;
    private int stitchIndex;
    private boolean stitchDragging;
    // Вправление
    private double marker;

    public MinigameScreen(MinigameStartPacket task, @Nullable Screen back) {
        super(Component.translatable("rpmedicine.minigame." + task.type().id()));
        this.task = task;
        this.back = back;
        this.rnd = new Random(task.seed());
        this.ease = Mth.clamp(task.ease(), 0.1f, 1f);
        this.tool = itemFor(task.itemKey());
        this.timeLimit = baseTime(task.type()) * (0.8 + 0.5 * ease);
        setupGame();
    }

    private static ItemStack itemFor(String descriptionId) {
        for (Item it : ForgeRegistries.ITEMS) if (it.getDescriptionId().equals(descriptionId)) return new ItemStack(it);
        return ItemStack.EMPTY;
    }

    /** Время на игру до поправки на уровень, секунды. */
    private static double baseTime(Minigames.Type t) {
        return switch (t) {
            case INJECTION -> 9;
            case VEIN -> 11;
            case BANDAGE -> 11;
            case TOURNIQUET -> 9;
            case TWEEZERS -> 16;
            case SUTURE -> 16;
            case REDUCE -> 8;
            case INTUBATION -> 12;
            case AUSCULTATION -> 22;
            case BP_CUFF -> 40;
            default -> 10;
        };
    }

    public int session() {
        return task.session();
    }

    private void setupGame() {
        switch (task.type()) {
            case INJECTION -> {
                targetR = 7 + 6 * ease;
                targetX = 70 + rnd.nextDouble() * (W - 140);
                targetY = 70 + rnd.nextDouble() * 60;
                zoneHalf = 0.08 + 0.07 * ease;
            }
            case VEIN -> {
                path = winding(5, 0.35);
                channel = 4.5 + 3.5 * ease;
            }
            case BANDAGE -> nextSwitch = Math.PI * (2.2 + rnd.nextDouble() * 1.4);
            case TOURNIQUET -> {
                zoneHalf = 0.045 + 0.075 * ease;
                zonePhase = rnd.nextDouble() * Math.PI * 2;
            }
            case TWEEZERS -> {
                path = winding(7, 0.9);
                channel = 5 + 4 * ease;
            }
            case SUTURE -> {
                int n = 4 + (ease < 0.5 ? 1 : 0);
                stitches = new double[n * 2][2];
                double x0 = 60, x1 = W - 60;
                for (int i = 0; i < n; i++) {
                    double t = (i + 0.5) / n;
                    double cx = Mth.lerp(t, x0, x1);
                    double cy = 95 + Math.sin(t * Math.PI * 1.3 + rnd.nextDouble()) * 12;
                    double off = 13 + rnd.nextDouble() * 4;
                    stitches[i * 2] = new double[]{cx - 4 + rnd.nextDouble() * 3, cy - off};
                    stitches[i * 2 + 1] = new double[]{cx + 4 - rnd.nextDouble() * 3, cy + off};
                }
                channel = 6 + 5 * ease;
            }
            case REDUCE -> zoneHalf = 0.05 + 0.05 * ease;
            case INTUBATION -> {
                targetR = 6 + 5 * ease;
                zonePhase = rnd.nextDouble() * Math.PI * 2;
            }
            case AUSCULTATION -> {
                // Точки: спереди — сердце и лёгкие (верх, низ), сзади — лёгкие.
                points = new double[][]{{80, 82}, {64, 70}, {96, 70}, {66, 110}, {94, 110}, {204, 74}, {236, 74}, {206, 112}, {234, 112}};
                channel = 6 + 4 * ease;
            }
            case BP_CUFF -> {
                cuff = 0;
                channel = 6 + 8 * ease;
            }
            default -> { }
        }
    }

    // ------------------------------------------------------------------ стетоскоп

    private double[][] points;
    private int pointIndex;
    private double listen;

    private void auscultation(GuiGraphics g, int l, int t, double dt, double tx, double ty) {
        // Торс спереди и сзади.
        torso(g, l + 40, t + 46, false);
        torso(g, l + 180, t + 46, true);
        g.drawCenteredString(font, Component.translatable("rpmedicine.minigame.front"), l + 80, t + 150, 0xFFAAAAAA);
        g.drawCenteredString(font, Component.translatable("rpmedicine.minigame.back"), l + 220, t + 150, 0xFFAAAAAA);
        for (int i = 0; i < points.length; i++) {
            int col = i < pointIndex ? 0xFF50C060 : i == pointIndex ? 0xFFFFD040 : 0xFF707070;
            circle(g, l + points[i][0], t + points[i][1], 4, col);
        }
        if (pointIndex >= points.length) return;
        double[] p = points[pointIndex];
        boolean on = mouseDown && Math.hypot(tx - p[0], ty - p[1]) < channel;
        if (on) {
            double before = listen;
            listen += dt / (1.0 + 0.5 * (1 - ease));
            // Слышно: сердце — стук, лёгкие — вдох.
            if ((int) (before * 2.5) != (int) (listen * 2.5))
                sound(pointIndex == 0 ? ModSounds.HEARTBEAT.get() : ModSounds.HEAVY_BREATHING.get(), 1.0f);
            int r = (int) (8 * Math.min(1, listen));
            circle(g, l + p[0], t + p[1], 4 + r, 0x8050C060);
            if (listen >= 1) {
                pointIndex++;
                listen = 0;
                if (pointIndex >= points.length) finish(quality());
            }
        } else if (listen > 0) {
            // Сорвался с точки — выслушивать заново.
            if (mouseDown) error("rpmedicine.minigame.slipped");
            listen = 0;
        }
    }

    private void torso(GuiGraphics g, int x, int y, boolean back) {
        int skin = 0xFFC99A7A, shade = 0xFFB5876A, dark = 0xFF8A5E48;
        // Шея и плечи, торс сужается к поясу.
        g.fill(x + 31, y - 40, x + 49, y - 24, skin);
        g.fill(x + 8, y - 24, x + 72, y - 14, skin);
        g.fill(x + 10, y - 14, x + 70, y + 50, skin);
        g.fill(x + 13, y + 50, x + 67, y + 92, skin);
        g.fill(x + 31, y - 40, x + 33, y - 24, shade);
        g.fill(x + 47, y - 40, x + 49, y - 24, shade);
        g.fill(x + 10, y - 14, x + 12, y + 50, shade);
        g.fill(x + 68, y - 14, x + 70, y + 50, shade);
        if (!back) {
            // Ключицы, соски, рёберная дуга, пупок.
            g.fill(x + 16, y - 20, x + 36, y - 19, dark);
            g.fill(x + 44, y - 20, x + 64, y - 19, dark);
            g.fill(x + 24, y + 22, x + 27, y + 25, 0xFFA0644E);
            g.fill(x + 53, y + 22, x + 56, y + 25, 0xFFA0644E);
            line(g, x + 18, y + 46, x + 40, y + 34, shade);
            line(g, x + 62, y + 46, x + 40, y + 34, shade);
            g.fill(x + 39, y + 68, x + 41, y + 71, dark);
        } else {
            // Позвоночник и лопатки.
            g.fill(x + 39, y - 22, x + 41, y + 92, shade);
            for (int v = y - 18; v < y + 90; v += 8) g.fill(x + 38, v, x + 42, v + 2, dark);
            line(g, x + 18, y - 10, x + 30, y + 22, shade);
            line(g, x + 30, y + 22, x + 34, y - 8, shade);
            line(g, x + 62, y - 10, x + 50, y + 22, shade);
            line(g, x + 50, y + 22, x + 46, y - 8, shade);
        }
        // Пояс брюк.
        g.fill(x + 13, y + 88, x + 67, y + 92, 0xFF3A4A66);
    }

    // ------------------------------------------------------------------ тонометр

    private double cuff;
    private long lastPump;
    private int sysMark = -1, diaMark = -1;
    private double beatTimer;

    private void bpCuff(GuiGraphics g, int l, int t, double dt) {
        int sys = task.scene().bullets(), dia = task.scene().fragments(), hr = Math.max(40, task.scene().organ());
        // Стравливание: само понемногу, ЛКМ — быстрее.
        if (phase == 1) cuff = Math.max(0, cuff - dt * (mouseDown ? 14 : 6));
        // Тоны слышны, пока давление в манжете между верхним и нижним.
        boolean tones = sys > 0 && cuff <= sys && cuff >= dia;
        beatTimer += dt;
        if (beatTimer >= 60.0 / hr) {
            beatTimer = 0;
            if (tones && phase == 1) sound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASEDRUM.get(), 1.4f);
        }
        // Манометр: корпус, шкала 0–260 с делениями и цифрами, стрелка. Справа — груша и шланг.
        int cx = l + W / 2 - 30, cy = t + 100, r = 50;
        disc(g, cx, cy, r + 4, 0xFF5A5E66);
        disc(g, cx, cy, r + 2, 0xFF2A2C30);
        disc(g, cx, cy, r, 0xFFEDE9DC);
        // Зона тонов не подсвечивается — её надо услышать.
        for (int v = 0; v <= 260; v += 10) {
            double a = Math.toRadians(-225 + v / 260.0 * 270);
            double r0 = v % 20 == 0 ? r - 8 : r - 5;
            line(g, cx + Math.cos(a) * r0, cy + Math.sin(a) * r0, cx + Math.cos(a) * (r - 2), cy + Math.sin(a) * (r - 2), 0xFF303030);
            if (v % 40 == 0) {
                String n = String.valueOf(v);
                int nx = (int) (cx + Math.cos(a) * (r - 16)), ny = (int) (cy + Math.sin(a) * (r - 16));
                g.pose().pushPose();
                g.pose().translate(nx, ny - 2, 0);
                g.pose().scale(0.6f, 0.6f, 1);
                g.drawString(font, n, -font.width(n) / 2, 0, 0xFF303030, false);
                g.pose().popPose();
            }
        }
        double a = Math.toRadians(-225 + Mth.clamp(cuff, 0, 260) / 260.0 * 270);
        for (int w = -1; w <= 1; w++)
            line(g, cx + w * Math.sin(a), cy - w * Math.cos(a), cx + Math.cos(a) * (r - 9), cy + Math.sin(a) * (r - 9), 0xFFB02020);
        disc(g, cx, cy, 3, 0xFF202020);
        g.pose().pushPose();
        g.pose().translate(cx, cy + 20, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        String mm = Component.translatable("rpmedicine.minigame.mmhg").getString();
        g.drawString(font, mm, -font.width(mm) / 2, 0, 0xFF707070, false);
        g.pose().popPose();
        // Шланг и груша (сжимается, пока качаем или держим ЛКМ).
        int bx = l + W / 2 + 70, by = t + 112;
        line(g, cx + r + 2, cy + 10, bx - 18, by - 22, 0xFF202020);
        line(g, cx + r + 2, cy + 11, bx - 18, by - 21, 0xFF202020);
        g.fill(bx - 20, by - 26, bx - 12, by - 18, 0xFF8A8A8A);
        int squeeze = System.currentTimeMillis() - lastPump < 150 ? 3 : 0;
        disc(g, bx, by, 16 - squeeze, 0xFF1E1E22);
        disc(g, bx - 4, by - 5, 4, 0xFF3A3A42);
        // Отметки и показание цифрами.
        g.drawCenteredString(font, String.valueOf((int) cuff), cx, cy - 22, 0xFFFFFFFF);
        String marks = (sysMark >= 0 ? String.valueOf(sysMark) : "—") + " / " + (diaMark >= 0 ? String.valueOf(diaMark) : "—");
        g.drawCenteredString(font, marks, bx, t + H - 40, 0xFFFFFFFF);
        if (phase == 1 && cuff <= 0.5 && !sent) finishCuff(sys, dia);
    }

    /** Отметка тона (пробел или ПКМ): сначала верхнее, потом нижнее. */
    private void markTone() {
        if (phase != 1 || sent) return;
        if (sysMark < 0) sysMark = (int) cuff;
        else if (diaMark < 0) {
            diaMark = (int) cuff;
            finishCuff(task.scene().bullets(), task.scene().fragments());
        }
    }

    private void finishCuff(int sys, int dia) {
        if (sys <= 0) {
            finish(quality());
            return;
        }
        // Точность отметок: каждые channel мм рт. ст. мимо — ошибка.
        int miss = 0;
        miss += sysMark < 0 ? 3 : (int) (Math.abs(sysMark - sys) / channel);
        miss += diaMark < 0 ? 3 : (int) (Math.abs(diaMark - dia) / channel);
        errors += Math.min(4, miss);
        finish(quality());
    }

    /** Извилистый путь слева направо: {@code bend} — насколько сильно гнётся. */
    private double[][] winding(int n, double bend) {
        double[][] p = new double[n][2];
        for (int i = 0; i < n; i++) {
            p[i][0] = 30 + i * (W - 60) / (double) (n - 1);
            p[i][1] = 100 + (i == 0 || i == n - 1 ? 0 : (rnd.nextDouble() - 0.5) * 90 * bend);
        }
        return p;
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

    private void sound(SoundEvent e, float pitch) {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(e, pitch, 0.7f));
    }

    private void error(String why) {
        errors++;
        flash = 1;
        status = why;
        // Тихий глухой щелчок, не чаще раза в полсекунды.
        long now = System.currentTimeMillis();
        if (now - lastErrorSound > 500) {
            lastErrorSound = now;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.get(), 0.6f, 0.25f));
        }
    }

    // ------------------------------------------------------------------ кадр

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        long now = System.currentTimeMillis();
        double dt = Math.min(0.1, (now - lastFrame) / 1000.0);
        lastFrame = now;
        if (!sent) time += dt;
        renderBackground(g);
        int l = left();
        int t = top();
        mouseX = mx - l;
        mouseY = my - t;
        // Инструмент догоняет курсор: резко дёрнуть — проскочит мимо.
        double k = 1 - Math.exp(-dt * (10 + 8 * ease));
        toolX += (mouseX - toolX) * k;
        toolY += (mouseY - toolY) * k;
        // Дрожь рук у неопытного.
        double tremor = (1 - ease) * 1.6;
        double tx = toolX + Math.sin(time * 17) * tremor;
        double ty = toolY + Math.cos(time * 13) * tremor;

        g.fill(l, t, l + W, t + H, 0xF0161210);
        // Стетоскоп и тонометр — не на коже: торс и прибор рисуются сами, фон — простынь кушетки.
        if (task.type() == Minigames.Type.AUSCULTATION || task.type() == Minigames.Type.BP_CUFF) drawSheet(g, l, t);
        else drawSkin(g, l, t);
        g.renderOutline(l, t, W, H, flash > 0 ? 0xFFFF4040 : 0xFF6A5040);
        // Заголовок.
        g.drawCenteredString(font, title.copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), width / 2, t + 5, 0xFFFFFF);
        int hy = t + 16;
        for (var line : font.split(Component.translatable("rpmedicine.minigame.hint_" + task.type().id()).withStyle(ChatFormatting.GRAY), W - 16)) {
            g.drawCenteredString(font, line, width / 2, hy, 0xFFFFFF);
            hy += 9;
        }
        frameDt = dt;
        switch (task.type()) {
            case INJECTION -> injection(g, l, t, dt, tx, ty);
            case VEIN -> vein(g, l, t, dt, tx, ty);
            case BANDAGE -> bandage(g, l, t, dt);
            case TOURNIQUET -> tourniquet(g, l, t, dt);
            case TWEEZERS -> tweezers(g, l, t, tx, ty);
            case SUTURE -> suture(g, l, t, tx, ty);
            case REDUCE -> reduce(g, l, t, dt);
            case INTUBATION -> intubation(g, l, t, dt, tx, ty);
            case AUSCULTATION -> auscultation(g, l, t, dt, tx, ty);
            case BP_CUFF -> bpCuff(g, l, t, dt);
            default -> { }
        }
        // Инструмент у курсора (кроме игр без инструмента).
        if (!tool.isEmpty() && task.type() != Minigames.Type.BANDAGE && task.type() != Minigames.Type.TOURNIQUET
                && task.type() != Minigames.Type.REDUCE && task.type() != Minigames.Type.BP_CUFF) {
            g.pose().pushPose();
            g.pose().translate(l + tx - 2, t + ty - 14, 200);
            g.renderItem(tool, 0, 0);
            g.pose().popPose();
        }
        // Вспышка ошибки, сообщение, ошибки.
        if (flash > 0) {
            g.fill(l, t, l + W, t + H, ((int) (flash * 80) << 24) | 0xC01010);
            flash = Math.max(0, flash - dt * 2.5);
        }
        if (!status.isEmpty())
            g.drawCenteredString(font, Component.translatable(status).withStyle(ChatFormatting.RED), width / 2, t + H - 28, 0xFFFFFF);
        g.drawString(font, Component.translatable("rpmedicine.minigame.errors", errors), l + 6, t + H - 26, errors > 0 ? 0xFF8080 : 0x808080, false);
        // Полоска оставшегося времени.
        double leftFrac = Math.max(0, 1 - time / timeLimit);
        g.fill(l + 6, t + H - 12, l + W - 6, t + H - 7, 0xFF2A2420);
        int col = leftFrac > 0.5 ? 0xFF58B35A : leftFrac > 0.25 ? 0xFFE0B040 : 0xFFE04040;
        g.fill(l + 6, t + H - 12, l + 6 + (int) ((W - 12) * leftFrac), t + H - 7, col);
        if (time >= timeLimit) finish(timeoutQuality());
        super.render(g, mx, my, pt);
    }

    /** Фон: кожа с волосками и порами. */
    private void drawSheet(GuiGraphics g, int l, int t) {
        g.fill(l + 4, t + 38, l + W - 4, t + H - 34, 0xFF33414A);
        // Редкая клетка простыни.
        for (int x = l + 4; x < l + W - 4; x += 12) g.fill(x, t + 38, x + 1, t + H - 34, 0xFF2E3A42);
        for (int y = t + 38; y < t + H - 34; y += 12) g.fill(l + 4, y, l + W - 4, y + 1, 0xFF2E3A42);
    }

    private void drawSkin(GuiGraphics g, int l, int t) {
        g.fill(l + 4, t + 38, l + W - 4, t + H - 34, 0xFFC99A7A);
        Random r = new Random(task.seed() ^ 77);
        for (int i = 0; i < 70; i++) {
            int x = l + 6 + r.nextInt(W - 12);
            int y = t + 40 + r.nextInt(H - 76);
            g.fill(x, y, x + 1, y + 1, 0xFFB5876A);
        }
    }

    // ------------------------------------------------------------------ укол

    private void injection(GuiGraphics g, int l, int t, double dt, double tx, double ty) {
        if (phase == 0) {
            // Место укола медленно «плывёт»: пациент дышит и дёргается.
            double ox = Math.sin(time * 1.3) * 6 * (1.2 - ease);
            double oy = Math.cos(time * 1.7) * 4 * (1.2 - ease);
            circle(g, l + targetX + ox, t + targetY + oy, targetR, 0xFF3A70B0);
            circle(g, l + targetX + ox, t + targetY + oy, 2, 0xFF3A70B0);
            g.drawCenteredString(font, Component.translatable("rpmedicine.minigame.inj_aim").withStyle(ChatFormatting.WHITE), width / 2, t + H - 40, 0xFFFFFF);
            return;
        }
        // Поршень: держать давление в плывущей зелёной зоне, пока препарат не введён.
        zoneCenter = 0.5 + Math.sin(time * (0.9 + (1 - ease) * 0.8) + zonePhase) * 0.28;
        pressure += (mouseDown ? 0.9 : -0.7) * dt;
        pressure = Mth.clamp(pressure, 0, 1);
        boolean in = Math.abs(pressure - zoneCenter) <= zoneHalf;
        if (in) progress += dt / (1.8 + (1 - ease));
        else if (pressure > zoneCenter + zoneHalf + 0.12 && mouseDown) {
            progress -= dt * 0.3;
            if (flash <= 0) error("rpmedicine.minigame.inj_too_fast");
        }
        verticalGauge(g, l + W - 40, t + 44, 110, pressure, zoneCenter, zoneHalf);
        bar(g, l + 40, t + H - 46, W - 110, progress, 0xFF6090E0, "rpmedicine.minigame.injected");
        if (progress >= 1) finish(quality());
    }

    // ------------------------------------------------------------------ вена

    private void vein(GuiGraphics g, int l, int t, double dt, double tx, double ty) {
        drawChannel(g, l, t, 0xFF3A5A9A, 0xFF5878C0);
        double[] s0 = path[0];
        double[] e = path[path.length - 1];
        circle(g, l + s0[0], t + s0[1], 4, 0xFF40C040);
        circle(g, l + e[0], t + e[1], 5, 0xFFE0E0E0);
        // Пройденный путь иглы — светлой линией поверх вены.
        for (int i = 1; i < trail.size(); i++) {
            double[] a = trail.get(i - 1), b = trail.get(i);
            int steps = (int) Math.max(1, Math.hypot(b[0] - a[0], b[1] - a[1]));
            for (int k = 0; k <= steps; k++) {
                int px = (int) (l + a[0] + (b[0] - a[0]) * k / steps), py = (int) (t + a[1] + (b[1] - a[1]) * k / steps);
                g.fill(px - 1, py - 1, px + 1, py + 1, 0xFFE8F0FF);
            }
        }
        if (phase == 0) {
            if (mouseDown && Math.hypot(tx - s0[0], ty - s0[1]) < channel * 1.4) {
                phase = 1;
                pathIndex = 0;
                trail.clear();
            }
            return;
        }
        if (phase == 1) {
            followChannel(tx, ty);
            if (mouseDown && (trail.isEmpty() || Math.hypot(trail.get(trail.size() - 1)[0] - tx, trail.get(trail.size() - 1)[1] - ty) > 1.5))
                trail.add(new double[]{tx, ty});
            if (pathIndex == path.length - 1 && Math.hypot(tx - e[0], ty - e[1]) < channel * 1.2) {
                phase = 2;
                progress = 0;
                sound(ModSounds.INJECTION.get(), 1.0f);
            }
            return;
        }
        // Держать иглу в вене неподвижно.
        if (Math.hypot(tx - e[0], ty - e[1]) > channel * 1.3) {
            error("rpmedicine.minigame.vein_lost");
            phase = 1;
            pathIndex = Math.max(0, path.length - 2);
            return;
        }
        progress += dt / 1.2;
        bar(g, l + 40, t + H - 46, W - 80, progress, 0xFF6090E0, "rpmedicine.minigame.hold_still");
        if (progress >= 1) finish(quality());
    }

    // ------------------------------------------------------------------ перевязка

    private void bandage(GuiGraphics g, int l, int t, double dt) {
        int cx = l + W / 2;
        int cy = t + 108;
        int r = 46;
        // Цвет кольца и подсказки: зелёный в начале витка, к смене направления — жёлтый, потом оранжевый.
        double seg = Mth.clamp((angleSum - segStart) / Math.max(1e-3, nextSwitch - segStart), 0, 1);
        int ringColor = seg < 0.6 ? lerpColor(0xFF2E8B57, 0xFFD8C040, seg / 0.6) : lerpColor(0xFFD8C040, 0xFFE06A20, (seg - 0.6) / 0.4);
        for (int i = 0; i < 90; i++) {
            double a = i * Math.PI * 2 / 90;
            int x = cx + (int) (Math.cos(a) * r);
            int y = cy + (int) (Math.sin(a) * r);
            g.fill(x, y, x + 2, y + 2, ringColor);
        }
        for (int i = 0; i < 60; i++) {
            double a = i * Math.PI * 2 / 60;
            int x = cx + (int) (Math.cos(a) * (r * 0.62));
            int y = cy + (int) (Math.sin(a) * (r * 0.62));
            g.fill(x, y, x + 1, y + 1, 0xFF7A6A5A);
        }
        g.fill(cx - 7, cy - 7, cx + 7, cy + 7, 0xFF8A3A3A);
        String dir = direction > 0 ? "rpmedicine.minigame.dir_cw" : "rpmedicine.minigame.dir_ccw";
        g.drawCenteredString(font, Component.translatable(dir), width / 2, t + 44, ringColor & 0xFFFFFF);
        double turns = Math.abs(angleSum) / (Math.PI * 2);
        bar(g, l + 40, t + H - 46, W - 80, Math.min(1, turns / 3), 0xFFE0E0D0, "rpmedicine.minigame.wrapped");
        if (mouseDown) {
            double dx = mouseX - W / 2.0;
            double dy = mouseY - 108;
            double dist = Math.sqrt(dx * dx + dy * dy);
            double a = Math.atan2(dy, dx);
            if (!Double.isNaN(lastAngle)) {
                double d = a - lastAngle;
                if (d > Math.PI) d -= Math.PI * 2;
                if (d < -Math.PI) d += Math.PI * 2;
                if (Math.abs(d) < 1.0) {
                    // Только в указанную сторону; против — ошибка.
                    if (Math.signum(d) == direction) {
                        angleSum += Math.abs(d);
                        if (angleSum >= nextSwitch) {
                            direction = -direction;
                            segStart = angleSum;
                            switchGraceUntil = time + 0.6;
                            nextSwitch = angleSum + Math.PI * (2.0 + rnd.nextDouble() * 1.4);
                            sound(ModSounds.BANDAGE.get(), 1.1f);
                        }
                    } else if (Math.abs(d) > 0.02 && time >= switchGraceUntil) {
                        wrongSum += Math.abs(d);
                        if (wrongSum > 0.8) {
                            wrongSum = 0;
                            error("rpmedicine.minigame.wrong_direction");
                        }
                    }
                    // Слишком быстро — бинт соскальзывает.
                    if (Math.abs(d) / Math.max(1e-3, dt) > 14 - 4 * (1 - ease) && flash <= 0) error("rpmedicine.minigame.too_fast");
                }
            }
            lastAngle = a;
            deviationSum += Math.abs(dist - r) / r;
            deviationCount++;
            if (Math.abs(dist - r) > r * 0.35 && flash <= 0) error("rpmedicine.minigame.off_line");
            if (angleSum >= Math.PI * 6) finish(quality() * bandageEvenness());
        } else {
            lastAngle = Double.NaN;
        }
    }

    private static int lerpColor(int a, int b, double k) {
        k = Mth.clamp(k, 0, 1);
        int r = (int) Mth.lerp(k, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int gg = (int) Mth.lerp(k, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bb = (int) Mth.lerp(k, a & 0xFF, b & 0xFF);
        return 0xFF000000 | r << 16 | gg << 8 | bb;
    }

    private double bandageEvenness() {
        double dev = deviationCount > 0 ? deviationSum / deviationCount : 1;
        return Mth.clamp(1 - dev * 2.0, 0, 1);
    }

    // ------------------------------------------------------------------ жгут

    private void tourniquet(GuiGraphics g, int l, int t, double dt) {
        // Зона давления гуляет: чем ниже навык, тем быстрее и неровнее.
        double speed = 1.2 + (1 - ease) * 2.2;
        zoneCenter = 0.6 + Math.sin(time * speed + zonePhase) * 0.17 + Math.sin(time * speed * 2.3 + zonePhase * 1.7) * 0.07 * (1 - ease);
        pressure += (mouseDown ? 0.75 : -0.55) * dt;
        pressure = Mth.clamp(pressure, 0, 1);
        boolean in = Math.abs(pressure - zoneCenter) <= zoneHalf;
        if (in) progress += dt / 3.0;
        if (pressure > 0.95 && flash <= 0) error("rpmedicine.minigame.too_tight");
        verticalGauge(g, l + W / 2 - 12, t + 44, 110, pressure, zoneCenter, zoneHalf);
        bar(g, l + 40, t + H - 46, W - 80, progress, 0xFF58B35A, "rpmedicine.minigame.held");
        if (progress >= 1) finish(quality());
    }

    // ------------------------------------------------------------------ пинцет

    private void tweezers(GuiGraphics g, int l, int t, double tx, double ty) {
        drawChannel(g, l, t, 0xFF5A2020, 0xFF8A3030);
        double[] mouth = path[0];
        double[] deep = path[path.length - 1];
        circle(g, l + mouth[0], t + mouth[1], 5, 0xFF40C040);
        // Пуля в глубине раны или в пинцете.
        if (!carrying) circle(g, l + deep[0], t + deep[1], 4, 0xFFB0A060);
        if (phase == 0) {
            if (mouseDown && Math.hypot(tx - mouth[0], ty - mouth[1]) < channel * 1.4) {
                phase = 1;
                pathIndex = 0;
            }
            return;
        }
        if (phase == 1) {
            // Внутрь: от устья к пуле.
            followChannel(tx, ty);
            if (pathIndex == path.length - 1 && Math.hypot(tx - deep[0], ty - deep[1]) < channel * 1.1) {
                carrying = true;
                phase = 2;
                pathIndex = path.length - 1;
                sound(ModSounds.SURGERY_RETRACT.get(), 1.2f);
            }
            return;
        }
        // Наружу с пулей: обратно по каналу, не задевая стенок.
        double d = distanceToPath(tx, ty);
        if (d > channel) {
            if (!outside) error("rpmedicine.minigame.wall");
            outside = true;
        } else {
            outside = false;
        }
        for (int i = pathIndex; i >= 0; i--) {
            if (Math.hypot(tx - path[i][0], ty - path[i][1]) < channel * 2.2) pathIndex = Math.min(pathIndex, i);
            else if (i < pathIndex) break;
        }
        g.fill((int) (l + tx) - 2, (int) (t + ty) - 2, (int) (l + tx) + 3, (int) (t + ty) + 3, 0xFFB0A060);
        if (pathIndex == 0 && Math.hypot(tx - mouth[0], ty - mouth[1]) < channel * 1.2) finish(quality());
    }

    // ------------------------------------------------------------------ швы

    /** Швы, вторая фаза (замечание 85): стянуть края нитками — не перетянуть, иначе ткань рвётся. */
    private boolean tightening;
    private double tension;
    private double frameDt;

    private void suture(GuiGraphics g, int l, int t, double tx, double ty) {
        // Рана: края разошлись; стягиваются по мере натяжения ниток.
        double gap = 12 * (1 - Mth.clamp(tension, 0, 1));
        for (int x = 50; x < W - 50; x++) {
            double f = (x - 50) / (double) (W - 100);
            int y = (int) (95 + Math.sin(f * Math.PI * 1.3 + 0.5) * 12);
            int h = (int) Math.round(gap / 2) + 2;
            g.fill(l + x, t + y - h, l + x + 1, t + y + h + 1, 0xFF6A1515);
            g.fill(l + x, t + y - h - 1, l + x + 1, t + y - h, 0xFFB07060);
            g.fill(l + x, t + y + h + 1, l + x + 1, t + y + h + 2, 0xFFB07060);
        }
        if (tightening) {
            // Держать ЛКМ — тянуть нитки; перетянул — рвёт ткань (у новичка тянется рывками).
            if (mouseDown && !sent) tension += frameDt * (0.35 + 0.25 * (1 - ease)) * (1 + Math.sin(time * 9) * 0.35 * (1 - ease));
            if (tension > 1.0) {
                error("rpmedicine.minigame.tissue_torn");
                tension = 0.55;
            }
            int bx = l + W / 2 - 60, by = t + H - 44;
            g.fill(bx, by, bx + 120, by + 6, 0xFF302020);
            g.fill(bx + (int) (120 * 0.85), by, bx + 120 - 2, by + 6, 0xFF2E7A3A);
            g.fill(bx, by + 1, bx + (int) (120 * Mth.clamp(tension, 0, 1)), by + 5, 0xFFE8E0C0);
        }
        int n = stitches.length / 2;
        for (int i = 0; i < n; i++) {
            double[] a = stitches[i * 2];
            double[] b = stitches[i * 2 + 1];
            boolean done = i < stitchIndex;
            int col = done ? 0xFFE8E8E0 : i == stitchIndex ? 0xFFFFD040 : 0xFF707070;
            circle(g, l + a[0], t + a[1], 3, col);
            circle(g, l + b[0], t + b[1], 3, col);
            if (done) line(g, l + a[0], t + a[1], l + b[0], t + b[1], 0xFFE8E8E0);
        }
        if (stitchIndex >= n) return;
        double[] a = stitches[stitchIndex * 2];
        double[] b = stitches[stitchIndex * 2 + 1];
        if (stitchDragging) {
            // Игла тянет нить от входа к выходу; уйти в сторону — рвёт кожу.
            line(g, l + a[0], t + a[1], l + tx, t + ty, 0xFFE8E8E0);
            double vx = b[0] - a[0], vy = b[1] - a[1];
            double f = Mth.clamp(((tx - a[0]) * vx + (ty - a[1]) * vy) / (vx * vx + vy * vy), 0, 1);
            double dev = Math.hypot(tx - (a[0] + vx * f), ty - (a[1] + vy * f));
            if (dev > channel) {
                if (!outside) error("rpmedicine.minigame.skin_torn");
                outside = true;
            } else {
                outside = false;
            }
        }
    }

    private void stitchPress() {
        if (tightening) return;
        if (stitches == null || stitchIndex >= stitches.length / 2) return;
        double[] a = stitches[stitchIndex * 2];
        if (Math.hypot(toolX - a[0], toolY - a[1]) < channel * 1.2) {
            stitchDragging = true;
            outside = false;
        } else {
            error("rpmedicine.minigame.missed");
        }
    }

    private void stitchRelease() {
        // Отпустил, когда края сошлись, — шов завязан.
        if (tightening) {
            if (tension >= 0.85 && !sent) {
                sound(ModSounds.SURGERY_STITCH.get(), 0.8f);
                finish(Mth.clamp(quality() - Math.max(0, 0.92 - tension), 0, 1));
            }
            return;
        }
        if (!stitchDragging) return;
        stitchDragging = false;
        double[] b = stitches[stitchIndex * 2 + 1];
        if (Math.hypot(toolX - b[0], toolY - b[1]) < channel * 1.2) {
            stitchIndex++;
            sound(ModSounds.SURGERY_STITCH.get(), 1.0f + rnd.nextFloat() * 0.2f);
            // Все стежки — теперь стянуть края.
            if (stitchIndex >= stitches.length / 2) {
                tightening = true;
                status = "rpmedicine.minigame.tighten";
            }
        } else {
            error("rpmedicine.minigame.missed");
        }
    }

    // ------------------------------------------------------------------ вправление

    private void reduce(GuiGraphics g, int l, int t, double dt) {
        if (phase == 0) {
            // Вытяжение: держать натяжение в зелёной зоне.
            zoneCenter = 0.6;
            pressure += (mouseDown ? 0.8 : -0.6) * dt;
            pressure = Mth.clamp(pressure, 0, 1);
            if (Math.abs(pressure - zoneCenter) <= 0.12) progress += dt / 1.4;
            if (pressure > 0.92 && flash <= 0) error("rpmedicine.minigame.too_hard");
            verticalGauge(g, l + W / 2 - 12, t + 44, 110, pressure, zoneCenter, 0.12);
            bar(g, l + 40, t + H - 46, W - 80, progress, 0xFF58B35A, "rpmedicine.minigame.traction");
            if (progress >= 1) {
                phase = 1;
                progress = 0;
                zoneCenter = 0.25 + rnd.nextDouble() * 0.5;
            }
            return;
        }
        // Рывок: точно в окне.
        marker = (Math.sin(time * (3.2 + (1 - ease) * 2.4)) + 1) / 2;
        int bx = l + 30;
        int by = t + 100;
        int bw = W - 60;
        g.fill(bx, by, bx + bw, by + 12, 0xFF303030);
        g.fill(bx + (int) ((zoneCenter - zoneHalf) * bw), by, bx + (int) ((zoneCenter + zoneHalf) * bw), by + 12, 0xFF2E8B57);
        int mx = bx + (int) (marker * bw);
        g.fill(mx - 1, by - 4, mx + 2, by + 16, 0xFFFFFFFF);
        g.drawCenteredString(font, Component.translatable("rpmedicine.minigame.jerk").withStyle(ChatFormatting.GOLD), width / 2, by - 16, 0xFFFFFF);
    }

    private void jerk() {
        if (phase != 1) return;
        double d = Math.abs(marker - zoneCenter);
        if (d <= zoneHalf) {
            sound(ModSounds.SURGERY_BONE_SET.get(), 1.0f);
            finish(quality() * Mth.clamp(1 - d / (zoneHalf * 2), 0.5, 1));
        } else {
            error("rpmedicine.minigame.missed");
            if (errors >= 3) finish(0);
        }
    }

    // ------------------------------------------------------------------ интубация

    /** Насколько раскрыты голосовые связки 0–1: в ритме дыхания. */
    private double glottisOpen() {
        return Math.max(0, Math.sin(time * (1.6 + (1 - ease) * 0.8) + zonePhase));
    }

    private void intubation(GuiGraphics g, int l, int t, double dt, double tx, double ty) {
        int cx = l + W / 2;
        int cy = t + 105;
        // Глотка: тёмное кольцо, надгортанник, связки — раскрывающаяся щель.
        g.fill(cx - 70, cy - 52, cx + 70, cy + 52, 0xFF7A2A2A);
        g.fill(cx - 56, cy - 42, cx + 56, cy + 42, 0xFF4A1414);
        g.fill(cx - 14, cy - 50, cx + 14, cy - 38, 0xFFC07070);
        double open = glottisOpen();
        int half = (int) (2 + open * targetR * 1.6);
        g.fill(cx - 24, cy - half - 3, cx + 24, cy - half, 0xFFE8D8C8);
        g.fill(cx - 24, cy + half, cx + 24, cy + half + 3, 0xFFE8D8C8);
        g.fill(cx - 22, cy - half, cx + 22, cy + half, 0xFF100606);
        bar(g, l + 40, t + H - 46, W - 80, progress, 0xFF6090E0, "rpmedicine.minigame.tube_depth");
        if (phase == 0) return;
        // Трубка введена — продвигать, пока связки открыты, и держать по центру.
        if (Math.abs(tx - W / 2.0) > targetR * 1.5 || Math.abs(ty - 105) > targetR * 1.5) {
            if (!outside) error("rpmedicine.minigame.esophagus");
            outside = true;
            return;
        }
        outside = false;
        if (mouseDown) {
            if (open > 0.4) progress += dt / 1.2;
            else if (flash <= 0) error("rpmedicine.minigame.cords_closed");
        }
        if (progress >= 1) finish(quality());
    }

    private void intubationClick() {
        if (phase != 0) return;
        if (Math.hypot(toolX - W / 2.0, toolY - 105) <= targetR * 1.3 && glottisOpen() > 0.4) {
            phase = 1;
            sound(ModSounds.SURGERY_TRACHEA.get(), 1.0f);
        } else {
            error(glottisOpen() <= 0.4 ? "rpmedicine.minigame.cords_closed" : "rpmedicine.minigame.missed");
        }
    }

    // ------------------------------------------------------------------ общее

    /** Продвижение по каналу внутрь; выход за стенку — ошибка. */
    private void followChannel(double tx, double ty) {
        double d = distanceToPath(tx, ty);
        if (d > channel) {
            if (!outside) error("rpmedicine.minigame.wall");
            outside = true;
        } else {
            outside = false;
        }
        for (int i = pathIndex; i < path.length; i++) {
            if (Math.hypot(tx - path[i][0], ty - path[i][1]) < channel * 2.2) pathIndex = Math.max(pathIndex, i);
            else if (i > pathIndex) break;
        }
    }

    private void drawChannel(GuiGraphics g, int l, int t, int wall, int inner) {
        int c = (int) channel;
        for (int i = 0; i + 1 < path.length; i++) {
            for (int k = 0; k <= 40; k++) {
                double f = k / 40.0;
                int x = l + (int) Mth.lerp(f, path[i][0], path[i + 1][0]);
                int y = t + (int) Mth.lerp(f, path[i][1], path[i + 1][1]);
                g.fill(x - c - 2, y - c - 2, x + c + 2, y + c + 2, wall);
            }
        }
        for (int i = 0; i + 1 < path.length; i++) {
            for (int k = 0; k <= 40; k++) {
                double f = k / 40.0;
                int x = l + (int) Mth.lerp(f, path[i][0], path[i + 1][0]);
                int y = t + (int) Mth.lerp(f, path[i][1], path[i + 1][1]);
                g.fill(x - c, y - c, x + c, y + c, inner);
            }
        }
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

    private void verticalGauge(GuiGraphics g, int x, int y, int h, double value, double center, double half) {
        g.fill(x, y, x + 24, y + h, 0xFF2A2A2A);
        g.fill(x, y + (int) ((1 - (center + half)) * h), x + 24, y + (int) ((1 - (center - half)) * h), 0xFF2E8B57);
        g.fill(x, y, x + 24, y + (int) (h * 0.05), 0xFF7A2020);
        int vy = y + (int) ((1 - value) * h);
        g.fill(x - 5, vy - 1, x + 29, vy + 2, 0xFFFFFFFF);
    }

    private void bar(GuiGraphics g, int x, int y, int w, double frac, int color, String key) {
        g.fill(x, y, x + w, y + 5, 0xFF2A2A2A);
        g.fill(x, y, x + (int) (w * Mth.clamp(frac, 0, 1)), y + 5, color);
        g.drawString(font, Component.translatable(key), x, y - 10, 0xCCCCCC, false);
    }

    /** Закрашенный круг (по строкам). */
    private void disc(GuiGraphics g, double cx, double cy, double r, int color) {
        for (int dy = (int) -r; dy <= (int) r; dy++) {
            int hw = (int) Math.sqrt(Math.max(0, r * r - dy * dy));
            g.fill((int) cx - hw, (int) cy + dy, (int) cx + hw + 1, (int) cy + dy + 1, color);
        }
    }

    private void circle(GuiGraphics g, double cx, double cy, double r, int color) {
        int n = Math.max(12, (int) (r * 4));
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            int x = (int) (cx + Math.cos(a) * r);
            int y = (int) (cy + Math.sin(a) * r);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    private void line(GuiGraphics g, double x0, double y0, double x1, double y1, int color) {
        int n = (int) Math.max(2, Math.hypot(x1 - x0, y1 - y0));
        for (int i = 0; i <= n; i++) {
            double f = i / (double) n;
            int x = (int) Mth.lerp(f, x0, x1);
            int y = (int) Mth.lerp(f, y0, y1);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    /** Качество: каждая ошибка −0,22, к концу времени чуть хуже. */
    private double quality() {
        double q = 1 - errors * 0.22 - Math.max(0, time / timeLimit - 0.6) * 0.3;
        return Mth.clamp(q, 0, 1);
    }

    private double timeoutQuality() {
        // Не успел — засчитывается доля сделанного, но не больше «плохо».
        double done = switch (task.type()) {
            case BANDAGE -> Math.min(1, Math.abs(angleSum) / (Math.PI * 6));
            case SUTURE -> stitches == null ? 0 : stitchIndex / (double) (stitches.length / 2);
            case TOURNIQUET, VEIN, INJECTION -> Mth.clamp(progress, 0, 1) * (phase > 0 || task.type() == Minigames.Type.TOURNIQUET ? 1 : 0);
            default -> 0;
        };
        return Math.min(0.45, done * quality());
    }

    // ------------------------------------------------------------------ ввод

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button == 1 && task.type() == Minigames.Type.BP_CUFF) {
            markTone();
            return true;
        }
        if (button != 0 || sent) return false;
        mouseDown = true;
        switch (task.type()) {
            case INJECTION -> {
                if (phase == 0) {
                    double ox = Math.sin(time * 1.3) * 6 * (1.2 - ease);
                    double oy = Math.cos(time * 1.7) * 4 * (1.2 - ease);
                    if (Math.hypot(toolX - (targetX + ox), toolY - (targetY + oy)) <= targetR) {
                        phase = 1;
                        sound(ModSounds.INJECTION.get(), 1.1f);
                    } else {
                        error("rpmedicine.minigame.missed");
                    }
                }
            }
            case SUTURE -> stitchPress();
            case INTUBATION -> intubationClick();
            case REDUCE -> {
                if (phase == 1) jerk();
            }
            default -> { }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        // Тонометр: колёсико вверх — качать грушу; потом стравливать.
        if (task.type() == Minigames.Type.BP_CUFF && !sent) {
            if (delta > 0 && phase == 0) {
                cuff = Math.min(260, cuff + 12);
                lastPump = System.currentTimeMillis();
                sound(net.minecraft.sounds.SoundEvents.WOOL_PLACE, 1.6f);
                if (cuff >= Math.max(140, task.scene().bullets() + 20)) phase = 1;
            }
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0) {
            mouseDown = false;
            if (task.type() == Minigames.Type.SUTURE) stitchRelease();
            if ((task.type() == Minigames.Type.TWEEZERS || task.type() == Minigames.Type.VEIN) && phase >= 1 && !sent) {
                // Отпустил инструмент в ране — сорвалось, начинать заново.
                error("rpmedicine.minigame.dropped");
                phase = 0;
                carrying = false;
                pathIndex = 0;
            }
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        // Тонометр: пробел — отметить тон.
        if (key == 32 && task.type() == Minigames.Type.BP_CUFF) {
            markTone();
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE && !sent && task.type() == Minigames.Type.REDUCE) {
            jerk();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    // ------------------------------------------------------------------ итог

    private void finish(double quality) {
        if (sent) return;
        sent = true;
        sound(quality >= 0.5 ? ModSounds.MINIGAME_OK.get() : ModSounds.SURGERY_ERROR.get(), 1.0f);
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
