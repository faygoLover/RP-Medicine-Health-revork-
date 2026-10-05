package faygolover.rpmedicine.client.screen;

import faygolover.rpmedicine.core.BodyPart;
import faygolover.rpmedicine.core.Minigames;
import faygolover.rpmedicine.core.Minigames.Scene;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Мини-игры операции (решения, п. 1.14): одна сцена тела, шаги продолжают друг друга. Разрез, точки
 * кровотечения и прочее строятся из постоянного сида сцены, поэтому в каждом шаге всё на тех же местах:
 * после разреза видна линия, после зажимов — зажимы, после ретракторов — раскрытая рана с органами или
 * мышцей и костью. Инструмент следует за курсором с инерцией и дрожью; внизу — время. Результат —
 * качество 0–1, отказ — −1 (как у обычных мини-игр).
 */
public class SurgeryMinigameScreen extends Screen {
    private static final int W = 320;
    private static final int H = 230;
    private static final int X0 = 70;
    private static final int X1 = 250;
    private static final int CY = 120;
    /** Полуширина раскрытой раны. */
    private static final double OPEN_H = 34;

    private final MinigameStartPacket task;
    private final Minigames.Type type;
    private final Scene scene;
    @Nullable
    private final Screen back;
    private final Random rnd;
    private final double ease;
    private final ItemStack tool;
    private final double timeLimit;
    private final BodyPart part;
    private final boolean torso;
    // Геометрия сцены (из сида сцены).
    private final double amp;
    private final double phaseShift;
    private final double[][] bleedPoints;
    private final double[] sourcePoint;
    private final double[][] foreign;

    private double time;
    private long lastFrame = System.currentTimeMillis();
    private boolean sent;
    private boolean mouseDown;
    private double toolX = W / 2.0;
    private double toolY = H / 2.0;
    private double prevToolX = W / 2.0;
    private double prevToolY = H / 2.0;
    private double mouseX;
    private double mouseY;
    private int errors;
    private double flash;
    private double cooldown;
    private String status = "";

    // Состояние шагов.
    private int phase;
    private double progress;
    private double reach;
    private boolean outside;
    private boolean[] clamped;
    private double openTop;
    private double openBottom;
    private int dragging = -1;
    private double[][] stitches;
    private int stitchIndex;
    private boolean stitchDragging;
    private double channel;
    private double[][] path;
    private int pathIndex;
    private double pressure;
    private double zoneCenter;
    private double zoneHalf;
    private int hole;
    private boolean[] removed;
    private int carried = -1;
    /** Пила: число ровных движений, направление и путь текущего движения. */
    private int strokes;
    private int sawDir;
    private double sawTravel;
    private double lastSawX = Double.NaN;
    /** Орган: где лежит, где его тащат; сосуды, которые нужно пересечь. */
    private double organX;
    private double organY;
    private boolean organHeld;
    private double[][] vessels;
    private boolean[] vesselCut;
    private final List<double[]> drops = new ArrayList<>();

    public SurgeryMinigameScreen(MinigameStartPacket task, @Nullable Screen back) {
        super(Component.translatable("rpmedicine.minigame." + task.type().id()));
        this.task = task;
        this.type = task.type();
        this.scene = task.scene();
        this.back = back;
        this.rnd = new Random(task.seed());
        this.ease = Mth.clamp(task.ease(), 0.1f, 1f);
        this.tool = itemFor(task.itemKey());
        this.timeLimit = baseTime(type) * (0.8 + 0.5 * ease);
        this.part = scene.part() >= 0 && scene.part() < BodyPart.VALUES.length ? BodyPart.VALUES[scene.part()] : BodyPart.ABDOMEN;
        this.torso = part.isTorso();
        Random sr = new Random(scene.seed());
        this.amp = 4 + sr.nextDouble() * 7;
        this.phaseShift = sr.nextDouble() * Math.PI;
        int n = 3 + sr.nextInt(2);
        bleedPoints = new double[n][2];
        for (int i = 0; i < n; i++) {
            double x = Mth.lerp((i + 0.5) / n, X0 + 12, X1 - 12) + (sr.nextDouble() - 0.5) * 10;
            bleedPoints[i] = new double[]{x, incisionY(x) + (sr.nextDouble() - 0.5) * 6};
        }
        sourcePoint = new double[]{Mth.lerp(0.3 + sr.nextDouble() * 0.4, X0, X1), CY + (sr.nextDouble() - 0.5) * 20};
        int nf = Math.min(6, scene.bullets() + scene.fragments());
        foreign = new double[nf][2];
        for (int i = 0; i < nf; i++)
            foreign[i] = new double[]{Mth.lerp(0.15 + sr.nextDouble() * 0.7, X0, X1), CY + (sr.nextDouble() - 0.5) * 36};
        setupGame();
    }

    private static ItemStack itemFor(String descriptionId) {
        for (Item it : ForgeRegistries.ITEMS) if (it.getDescriptionId().equals(descriptionId)) return new ItemStack(it);
        return ItemStack.EMPTY;
    }

    private static double baseTime(Minigames.Type t) {
        return switch (t) {
            case INCISION, CLAMP, BLEED_SUTURE, DRAIN -> 10;
            case RETRACT -> 9;
            case CLOSE, DRILL -> 16;
            case ORGAN_SUTURE -> 14;
            case VESSEL -> 12;
            case EXTRACT -> 15;
            case AMPUTATION -> 14;
            case HARVEST -> 16;
            case PLANT -> 18;
            default -> 10;
        };
    }

    public int session() {
        return task.session();
    }

    // ------------------------------------------------------------------ геометрия

    private double incisionY(double x) {
        return CY + Math.sin((x - X0) / (X1 - X0) * Math.PI * 1.2 + phaseShift) * amp;
    }

    /** Полуширина раны в столбце x при раскрытии h. */
    private double halfOpen(double x, double h) {
        if (x <= X0 || x >= X1) return 0;
        return h * Math.sin(Math.PI * (x - X0) / (X1 - X0));
    }

    private boolean insideOpening(double x, double y, double top, double bottom) {
        double f = halfOpen(x, 1);
        double yc = incisionY(x);
        return f > 0 && y >= yc - top * f && y <= yc + bottom * f;
    }

    /** Насколько рана раскрыта сейчас (сверху, снизу). */
    private double curTop() {
        if (type == Minigames.Type.RETRACT) return openTop;
        return stage() >= 3 && type != Minigames.Type.CLOSE ? OPEN_H : 0;
    }

    private double curBottom() {
        if (type == Minigames.Type.RETRACT) return openBottom;
        return stage() >= 3 && type != Minigames.Type.CLOSE ? OPEN_H : 0;
    }

    /** Стадия операции до шага: 0 — целая кожа, 1 — вскрыто, 2 — зажато, 3 — раскрыто. */
    private int stage() {
        return scene.stage();
    }

    private void setupGame() {
        channel = 4 + 4 * ease;
        switch (type) {
            case INCISION -> {
                path = new double[24][2];
                for (int i = 0; i < path.length; i++) {
                    double x = Mth.lerp(i / (double) (path.length - 1), X0, X1);
                    path[i] = new double[]{x, incisionY(x)};
                }
            }
            case CLAMP -> clamped = new boolean[bleedPoints.length];
            case CLOSE -> {
                int n = 5;
                stitches = new double[n * 2][2];
                for (int i = 0; i < n; i++) {
                    double x = Mth.lerp((i + 0.5) / n, X0 + 6, X1 - 6);
                    double y = incisionY(x);
                    double off = 11 + rnd.nextDouble() * 4;
                    stitches[i * 2] = new double[]{x - 3 + rnd.nextDouble() * 3, y - off};
                    stitches[i * 2 + 1] = new double[]{x + 3 - rnd.nextDouble() * 3, y + off};
                }
                channel = 5 + 4 * ease;
            }
            case ORGAN_SUTURE -> {
                int n = 4;
                stitches = new double[n * 2][2];
                double cx = sourcePoint[0];
                double cy = sourcePoint[1];
                for (int i = 0; i < n; i++) {
                    double x = cx - 24 + i * 16;
                    double y = cy + (i - 1.5) * 3;
                    stitches[i * 2] = new double[]{x - 2, y - 8};
                    stitches[i * 2 + 1] = new double[]{x + 2, y + 8};
                }
                channel = 3 + 3 * ease;
            }
            case VESSEL -> {
                double y = CY - 14;
                path = new double[7][2];
                for (int i = 0; i < path.length; i++) {
                    double x = Mth.lerp(i / (double) (path.length - 1), 130, 190);
                    path[i] = new double[]{x, y + (i == 0 || i == path.length - 1 ? 0 : (rnd.nextDouble() - 0.5) * 14)};
                }
                channel = 2.5 + 2.5 * ease;
            }
            case DRILL -> zoneHalf = 0.07 + 0.06 * ease;
            case DRAIN -> zoneHalf = 0.08 + 0.06 * ease;
            case EXTRACT -> removed = new boolean[foreign.length];
            case HARVEST -> {
                double[] spot = organSpot();
                organX = spot[0];
                organY = spot[1];
                vessels = new double[3][2];
                for (int i = 0; i < 3; i++) {
                    double a = Math.PI * (0.25 + i * 0.25) + rnd.nextDouble() * 0.2;
                    vessels[i] = new double[]{spot[0] + Math.cos(a) * 18, spot[1] - Math.sin(a) * 14};
                }
                vesselCut = new boolean[3];
            }
            case PLANT -> {
                organX = W - 28;
                organY = 70;
                double[] spot = organSpot();
                path = new double[5][2];
                for (int i = 0; i < path.length; i++)
                    path[i] = new double[]{spot[0] - 20 + i * 10, spot[1] - 16 + (i == 0 || i == path.length - 1 ? 0 : (rnd.nextDouble() - 0.5) * 8)};
                channel = 2.5 + 2.5 * ease;
            }
            default -> { }
        }
        zoneCenter = 0.6;
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
        if (cooldown > 0) return;
        errors++;
        flash = 1;
        cooldown = 0.5;
        status = why;
        sound(ModSounds.MINIGAME_SLIP.get(), 0.8f + rnd.nextFloat() * 0.3f);
    }

    // ------------------------------------------------------------------ кадр

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        long now = System.currentTimeMillis();
        double dt = Math.min(0.1, (now - lastFrame) / 1000.0);
        lastFrame = now;
        if (!sent) time += dt;
        cooldown = Math.max(0, cooldown - dt);
        renderBackground(g);
        int l = left();
        int t = top();
        mouseX = mx - l;
        mouseY = my - t;
        prevToolX = toolX;
        prevToolY = toolY;
        double k = 1 - Math.exp(-dt * (10 + 8 * ease));
        toolX += (mouseX - toolX) * k;
        toolY += (mouseY - toolY) * k;
        double tremor = (1 - ease) * 1.6;
        double tx = toolX + Math.sin(time * 17) * tremor;
        double ty = toolY + Math.cos(time * 13) * tremor;
        double speed = dt > 0 ? Math.hypot(toolX - prevToolX, toolY - prevToolY) / dt : 0;

        g.fill(l, t, l + W, t + H, 0xF0101418);
        drawDrape(g, l, t);
        drawBody(g, l, t);
        g.renderOutline(l, t, W, H, flash > 0 ? 0xFFFF4040 : 0xFF4A6A60);
        g.drawCenteredString(font, title.copy().withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), width / 2, t + 5, 0xFFFFFF);
        int hy = t + 16;
        for (var line : font.split(Component.translatable("rpmedicine.minigame.hint_" + type.id()).withStyle(ChatFormatting.GRAY), W - 16)) {
            g.drawCenteredString(font, line, width / 2, hy, 0xFFFFFF);
            hy += 9;
        }
        switch (type) {
            case INCISION -> incision(g, l, t, tx, ty, speed);
            case CLAMP -> clamp(g, l, t);
            case RETRACT -> retract(g, l, t, speed);
            case CLOSE, ORGAN_SUTURE -> stitchGame(g, l, t, tx, ty);
            case BLEED_SUTURE -> bleedSuture(g, l, t, dt, tx, ty);
            case VESSEL -> vessel(g, l, t, tx, ty);
            case DRILL -> drill(g, l, t, dt);
            case DRAIN -> drain(g, l, t, dt);
            case EXTRACT -> extract(g, l, t, tx, ty);
            case AMPUTATION -> saw(g, l, t, tx, ty, speed);
            case HARVEST -> harvest(g, l, t, tx, ty);
            case PLANT -> plant(g, l, t, tx, ty);
            default -> { }
        }
        // Капли крови.
        drops.removeIf(d -> (d[3] -= dt) <= 0);
        for (double[] d : drops) {
            d[1] += dt * 30;
            g.fill(l + (int) d[0], t + (int) d[1], l + (int) d[0] + 2, t + (int) d[1] + 2, 0xFFA01010);
        }
        if (!tool.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(l + tx - 2, t + ty - 14, 200);
            g.renderItem(tool, 0, 0);
            g.pose().popPose();
        }
        if (flash > 0) {
            g.fill(l, t, l + W, t + H, ((int) (flash * 70) << 24) | 0xC01010);
            flash = Math.max(0, flash - dt * 2.5);
        }
        if (!status.isEmpty())
            g.drawCenteredString(font, Component.translatable(status).withStyle(ChatFormatting.RED), width / 2, t + H - 28, 0xFFFFFF);
        g.drawString(font, Component.translatable("rpmedicine.minigame.errors", errors), l + 6, t + H - 26, errors > 0 ? 0xFF8080 : 0x808080, false);
        double leftFrac = Math.max(0, 1 - time / timeLimit);
        g.fill(l + 6, t + H - 12, l + W - 6, t + H - 7, 0xFF2A2420);
        int col = leftFrac > 0.5 ? 0xFF58B35A : leftFrac > 0.25 ? 0xFFE0B040 : 0xFFE04040;
        g.fill(l + 6, t + H - 12, l + 6 + (int) ((W - 12) * leftFrac), t + H - 7, col);
        if (time >= timeLimit) finish(Math.min(0.45, quality() * doneFraction()));
        super.render(g, mx, my, pt);
    }

    // ------------------------------------------------------------------ сцена

    /** Операционное бельё вокруг поля. */
    private void drawDrape(GuiGraphics g, int l, int t) {
        g.fill(l + 4, t + 34, l + W - 4, t + H - 34, 0xFF2F6B66);
        for (int x = 8; x < W - 8; x += 12) g.fill(l + x, t + 34, l + x + 1, t + H - 34, 0xFF2A615C);
    }

    private void drawBody(GuiGraphics g, int l, int t) {
        // Кожа в окне белья: туловище — всё поле, конечность — полоса.
        int sy0 = torso ? 44 : 78;
        int sy1 = torso ? H - 44 : 164;
        g.fill(l + 40, t + sy0, l + W - 40, t + sy1, 0xFFD2A383);
        g.fill(l + 40, t + sy0, l + W - 40, t + sy0 + 2, 0xFFB8886A);
        Random r = new Random(scene.seed() ^ 77);
        for (int i = 0; i < 60; i++) {
            int x = l + 42 + r.nextInt(W - 84);
            int y = t + sy0 + 2 + r.nextInt(sy1 - sy0 - 4);
            g.fill(x, y, x + 1, y + 1, 0xFFBE8F70);
        }
        // Разметка разреза (до разреза) — пунктир хирургическим маркером.
        if (stage() == 0) {
            for (double x = X0; x <= X1; x += 6) g.fill(l + (int) x, t + (int) incisionY(x), l + (int) x + 3, t + (int) incisionY(x) + 1, 0xFF6A4AA0);
        }
        double top = curTop();
        double bottom = curBottom();
        if (top > 0.5 || bottom > 0.5) drawInterior(g, l, t, top, bottom);
        if (stage() >= 1 && top < 0.5) {
            // Разрез.
            for (double x = X0; x <= X1; x += 1) {
                int y = (int) incisionY(x);
                g.fill(l + (int) x, t + y - 1, l + (int) x + 1, t + y + 2, 0xFF7A1010);
            }
        }
        // Зажимы: стоят со своего шага и до закрытия.
        if (stage() >= 2 && type != Minigames.Type.CLOSE) for (int i = 0; i < bleedPoints.length; i++) drawClamp(g, l, t, i, top, bottom);
        // Ретракторы.
        if (stage() >= 3 && type != Minigames.Type.CLOSE) drawRetractors(g, l, t, top, bottom);
        // Открытая рана без зажима кровит.
        if (stage() == 1 && type != Minigames.Type.CLOSE) for (double[] p : bleedPoints) bleedSpot(g, l, t, p[0], p[1], 1.0);
    }

    /** Внутри раскрытой раны: туловище — органы, конечность — мышца, кость, артерия. */
    private void drawInterior(GuiGraphics g, int l, int t, double top, double bottom) {
        for (int x = X0; x <= X1; x++) {
            double f = halfOpen(x, 1);
            if (f <= 0) continue;
            int yc = (int) incisionY(x);
            int y0 = (int) (yc - top * f);
            int y1 = (int) (yc + bottom * f);
            g.fill(l + x, t + y0, l + x + 1, t + y1, torso ? 0xFF8A2A2A : 0xFF9A3A34);
            g.fill(l + x, t + y0, l + x + 1, t + y0 + 1, 0xFF5A1010);
            g.fill(l + x, t + y1 - 1, l + x + 1, t + y1, 0xFF5A1010);
        }
        if (torso) {
            if (part == BodyPart.CHEST) {
                // Рёбра, лёгкие, сердце.
                for (int i = -2; i <= 2; i++) masked(g, l, t, X0, CY + i * 12 - 2, X1, CY + i * 12 + 2, 0xFFE8DCC0, top, bottom);
                masked(g, l, t, X0 + 10, CY - 30, X0 + 70, CY + 30, 0x90D07A80, top, bottom);
                masked(g, l, t, X1 - 70, CY - 30, X1 - 10, CY + 30, 0x90D07A80, top, bottom);
                double beat = 1 + 0.08 * Math.max(0, Math.sin(time * 7));
                int hw = (int) (16 * beat);
                masked(g, l, t, 160 - hw, CY - hw, 160 + hw, CY + hw, 0xFF7A1018, top, bottom);
            } else {
                // Печень и петли кишечника.
                masked(g, l, t, X0 + 6, CY - 34, X0 + 70, CY + 4, 0xFF6A2A1A, top, bottom);
                for (int i = 0; i < 6; i++) {
                    int cx = 140 + (i % 3) * 28;
                    int cy = CY - 10 + (i / 3) * 18;
                    masked(g, l, t, cx - 12, cy - 6, cx + 12, cy + 6, 0xFFD89A90, top, bottom);
                    masked(g, l, t, cx - 10, cy - 1, cx + 10, cy + 1, 0xFFB87A70, top, bottom);
                }
            }
        } else {
            // Мышца с волокнами, кость, артерия.
            for (int y = CY - 34; y < CY + 34; y += 5) masked(g, l, t, X0, y, X1, y + 1, 0xFF7A2A26, top, bottom);
            masked(g, l, t, X0, CY - 6, X1, CY + 6, 0xFFEDE3C8, top, bottom);
            if (scene.has(Scene.FRACTURE) && !(type == Minigames.Type.DRILL && hole >= 4)) {
                for (int i = 0; i < 12; i++) masked(g, l, t, 158 + (i % 2) * 3, CY - 6 + i, 161 + (i % 2) * 3, CY - 5 + i, 0xFF3A2A1A, top, bottom);
            }
            if (scene.has(Scene.FIXATED) || type == Minigames.Type.DRILL) {
                masked(g, l, t, 128, CY - 4, 192, CY + 4, 0xFFA8B0B8, top, bottom);
                for (int i = 0; i < 4; i++) {
                    int hx = holeX(i);
                    boolean done = scene.has(Scene.FIXATED) || type == Minigames.Type.DRILL && i < hole;
                    masked(g, l, t, hx - 2, CY - 2, hx + 2, CY + 2, done ? 0xFF505860 : 0xFF202020, top, bottom);
                }
            }
            if (part != BodyPart.HEAD) {
                boolean torn = scene.has(Scene.ARTERIAL) && type != Minigames.Type.VESSEL;
                for (int x = X0; x <= X1; x++) {
                    if (torn && x > 145 && x < 175) continue;
                    masked(g, l, t, x, CY - 15, x + 1, CY - 12, 0xFFC01818, top, bottom);
                }
                if (torn) {
                    bleedSpot(g, l, t, 145, CY - 14, 1.3);
                    bleedSpot(g, l, t, 175, CY - 14, 1.3);
                }
            }
        }
        // Пули и осколки.
        for (int i = 0; i < foreign.length; i++) {
            if (type == Minigames.Type.EXTRACT && (removed[i] || carried == i)) continue;
            double[] p = foreign[i];
            if (insideOpening(p[0], p[1], top, bottom))
                g.fill(l + (int) p[0] - 2, t + (int) p[1] - 2, l + (int) p[0] + 2, t + (int) p[1] + 2, i < scene.bullets() ? 0xFFB0A060 : 0xFF707880);
        }
        // Внутреннее кровотечение — лужа.
        if (scene.has(Scene.INTERNAL) && type != Minigames.Type.BLEED_SUTURE)
            masked(g, l, t, (int) sourcePoint[0] - 10, (int) sourcePoint[1] - 6, (int) sourcePoint[0] + 10, (int) sourcePoint[1] + 6, 0xC0700000, top, bottom);
        // Разрыв органа.
        if (scene.has(Scene.ORGAN) && torso && type != Minigames.Type.ORGAN_SUTURE)
            masked(g, l, t, (int) sourcePoint[0] - 26, (int) sourcePoint[1] - 1, (int) sourcePoint[0] + 26, (int) sourcePoint[1] + 1, 0xFF3A0A0A, top, bottom);
    }

    /** Прямоугольник, обрезанный по раскрытой ране. */
    private void masked(GuiGraphics g, int l, int t, int x0, int y0, int x1, int y1, int color, double top, double bottom) {
        for (int x = Math.max(x0, X0); x < Math.min(x1, X1); x++) {
            double f = halfOpen(x, 1);
            if (f <= 0) continue;
            double yc = incisionY(x);
            int a = (int) Math.max(y0, yc - top * f + 1);
            int b = (int) Math.min(y1, yc + bottom * f - 1);
            if (b > a) g.fill(l + x, t + a, l + x + 1, t + b, color);
        }
    }

    private void drawClamp(GuiGraphics g, int l, int t, int i, double top, double bottom) {
        double[] p = bleedPoints[i];
        double f = halfOpen(p[0], 1);
        double y = top > 0.5 ? incisionY(p[0]) - top * f - 2 : p[1];
        int x = l + (int) p[0];
        int yy = t + (int) y;
        line(g, x - 3, yy - 3, x + 3, yy + 3, 0xFFD8DCE0);
        line(g, x + 3, yy - 3, x - 3, yy + 3, 0xFFD8DCE0);
        line(g, x, yy - 3, x - 6, yy - 16, 0xFFB8BCC0);
        line(g, x, yy - 3, x + 6, yy - 16, 0xFFB8BCC0);
        circle(g, x - 7, yy - 18, 2, 0xFFB8BCC0);
        circle(g, x + 7, yy - 18, 2, 0xFFB8BCC0);
    }

    private void drawRetractors(GuiGraphics g, int l, int t, double top, double bottom) {
        double xm = (X0 + X1) / 2.0;
        double yc = incisionY(xm);
        int x = l + (int) xm;
        int yt = t + (int) (yc - top) - 1;
        int yb = t + (int) (yc + bottom) + 1;
        g.fill(x - 14, yt - 2, x + 14, yt + 1, 0xFFC8CCD0);
        g.fill(x - 2, yt - 26, x + 2, yt - 2, 0xFFA8ACB0);
        g.fill(x - 14, yb - 1, x + 14, yb + 2, 0xFFC8CCD0);
        g.fill(x - 2, yb + 2, x + 2, yb + 26, 0xFFA8ACB0);
    }

    private void bleedSpot(GuiGraphics g, int l, int t, double x, double y, double strength) {
        double pulse = Math.max(0, Math.sin(time * 7 + x * 0.1));
        int r = (int) (2 + pulse * 3 * strength);
        g.fill(l + (int) x - r, t + (int) y - r, l + (int) x + r, t + (int) y + r, 0xFFB01010);
        if (pulse > 0.95 && rnd.nextFloat() < 0.3 && drops.size() < 60) drops.add(new double[]{x + (rnd.nextDouble() - 0.5) * 6, y, 0, 0.8});
    }

    // ------------------------------------------------------------------ разрез

    private void incision(GuiGraphics g, int l, int t, double tx, double ty, double speed) {
        double[] s0 = path[0];
        double[] e = path[path.length - 1];
        // Сделанная часть разреза.
        for (int i = 0; i + 1 <= pathIndex && i + 1 < path.length; i++) line(g, l + path[i][0], t + path[i][1], l + path[i + 1][0], t + path[i + 1][1], 0xFF8A1010);
        if (phase == 0) {
            circle(g, l + path[pathIndex][0], t + path[pathIndex][1], 4, 0xFF40C040);
            circle(g, l + e[0], t + e[1], 4, 0xFFE0E0E0);
            if (mouseDown && Math.hypot(tx - path[pathIndex][0], ty - path[pathIndex][1]) < channel * 1.6) phase = 1;
            return;
        }
        circle(g, l + e[0], t + e[1], 4, 0xFFE0E0E0);
        follow(tx, ty, "rpmedicine.minigame.tissue_cut");
        if (speed > 80 + 50 * ease) error("rpmedicine.minigame.too_rough");
        if (rnd.nextFloat() < 0.15 && drops.size() < 60) drops.add(new double[]{tx, ty + 2, 0, 0.6});
        if (pathIndex == path.length - 1 && Math.hypot(tx - e[0], ty - e[1]) < channel * 1.4) finish(quality());
    }

    // ------------------------------------------------------------------ зажимы

    /** Точка кровотечения брызжет раз в ~1,6 с: в момент брызга её не видно. */
    private boolean spurting(int i) {
        double p = (time + i * 0.53) % (1.6 - 0.4 * (1 - ease));
        return p < 0.35;
    }

    private void clamp(GuiGraphics g, int l, int t) {
        for (int i = 0; i < bleedPoints.length; i++) {
            if (clamped[i]) {
                drawClamp(g, l, t, i, 0, 0);
                continue;
            }
            double[] p = bleedPoints[i];
            bleedSpot(g, l, t, p[0], p[1], 1.0);
            if (spurting(i)) g.fill(l + (int) p[0] - 9, t + (int) p[1] - 9, l + (int) p[0] + 9, t + (int) p[1] + 9, 0xD0A01010);
        }
    }

    private void clampClick() {
        for (int i = 0; i < bleedPoints.length; i++) {
            if (clamped[i]) continue;
            if (Math.hypot(toolX - bleedPoints[i][0], toolY - bleedPoints[i][1]) < 5 + 4 * ease) {
                if (spurting(i)) {
                    error("rpmedicine.minigame.blood_hidden");
                    return;
                }
                clamped[i] = true;
                sound(ModSounds.SURGERY_CLAMP.get(), 1.0f + rnd.nextFloat() * 0.2f);
                for (boolean c : clamped) if (!c) return;
                finish(quality());
                return;
            }
        }
        error("rpmedicine.minigame.missed");
    }

    // ------------------------------------------------------------------ ретракторы

    private void retract(GuiGraphics g, int l, int t, double speed) {
        double xm = (X0 + X1) / 2.0;
        double yc = incisionY(xm);
        // Метки, до которых развести края.
        g.fill(l + (int) xm - 20, t + (int) (yc - OPEN_H) - 1, l + (int) xm + 20, t + (int) (yc - OPEN_H), 0xFF40C040);
        g.fill(l + (int) xm - 20, t + (int) (yc + OPEN_H), l + (int) xm + 20, t + (int) (yc + OPEN_H) + 1, 0xFF40C040);
        if (dragging >= 0 && mouseDown) {
            double target = dragging == 0 ? Math.max(0, yc - toolY) : Math.max(0, toolY - yc);
            double cur = dragging == 0 ? openTop : openBottom;
            double next = cur + Mth.clamp(target - cur, -2, 2);
            if (speed > 70 + 40 * ease && next > cur) error("rpmedicine.minigame.tissue_tear");
            if (next > OPEN_H + 8) error("rpmedicine.minigame.over_pull");
            next = Mth.clamp(next, 0, OPEN_H + 10);
            if (dragging == 0) openTop = next;
            else openBottom = next;
        }
        if (openTop >= OPEN_H - 3 && openBottom >= OPEN_H - 3 && !mouseDown) finish(quality());
    }

    private void retractPress() {
        double xm = (X0 + X1) / 2.0;
        double yc = incisionY(xm);
        if (Math.abs(toolX - xm) < 18) {
            if (Math.abs(toolY - (yc - openTop)) < 8) dragging = 0;
            else if (Math.abs(toolY - (yc + openBottom)) < 8) dragging = 1;
        }
        if (dragging < 0) error("rpmedicine.minigame.missed");
    }

    // ------------------------------------------------------------------ швы (закрытие и орган)

    private void stitchGame(GuiGraphics g, int l, int t, double tx, double ty) {
        if (type == Minigames.Type.ORGAN_SUTURE)
            masked(g, l, t, (int) sourcePoint[0] - 26, (int) sourcePoint[1] - 1, (int) sourcePoint[0] + 26, (int) sourcePoint[1] + 1, 0xFF3A0A0A, OPEN_H, OPEN_H);
        int n = stitches.length / 2;
        for (int i = 0; i < n; i++) {
            double[] a = stitches[i * 2];
            double[] b = stitches[i * 2 + 1];
            boolean done = i < stitchIndex;
            int col = done ? 0xFF2A2A6A : i == stitchIndex ? 0xFFFFD040 : 0xFF707070;
            circle(g, l + a[0], t + a[1], 2.5, col);
            circle(g, l + b[0], t + b[1], 2.5, col);
            if (done) line(g, l + a[0], t + a[1], l + b[0], t + b[1], 0xFF2A2A6A);
        }
        if (stitchIndex >= n || !stitchDragging) return;
        double[] a = stitches[stitchIndex * 2];
        double[] b = stitches[stitchIndex * 2 + 1];
        line(g, l + a[0], t + a[1], l + tx, t + ty, 0xFF3A3A8A);
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

    private void stitchPress() {
        if (stitchIndex >= stitches.length / 2) return;
        double[] a = stitches[stitchIndex * 2];
        if (Math.hypot(toolX - a[0], toolY - a[1]) < channel * 1.3) {
            stitchDragging = true;
            outside = false;
        } else {
            error("rpmedicine.minigame.missed");
        }
    }

    private void stitchRelease() {
        if (!stitchDragging) return;
        stitchDragging = false;
        double[] b = stitches[stitchIndex * 2 + 1];
        if (Math.hypot(toolX - b[0], toolY - b[1]) < channel * 1.3) {
            stitchIndex++;
            sound(ModSounds.SURGERY_STITCH.get(), 1.0f + rnd.nextFloat() * 0.2f);
            if (stitchIndex >= stitches.length / 2) finish(quality());
        } else {
            error("rpmedicine.minigame.missed");
        }
    }

    // ------------------------------------------------------------------ внутреннее кровотечение

    private void bleedSuture(GuiGraphics g, int l, int t, double dt, double tx, double ty) {
        // Источник смещается с каждым ударом сердца.
        double sx = sourcePoint[0] + Math.sin(time * 1.1) * 8 * (1.2 - ease);
        double sy = sourcePoint[1] + Math.sin(time * 7) * 2;
        int pool = (int) (14 * (1 - progress) + 3);
        masked(g, l, t, (int) sx - pool, (int) sy - pool / 2, (int) sx + pool, (int) sy + pool / 2, 0xC0700000, OPEN_H, OPEN_H);
        bleedSpot(g, l, t, sx, sy, 1.4 * (1 - progress));
        double r = 4 + 4 * ease;
        circle(g, l + sx, t + sy, r, 0xFFFFD040);
        if (mouseDown) {
            if (Math.hypot(tx - sx, ty - sy) <= r) {
                progress += dt / 2.2;
                outside = false;
            } else {
                if (!outside) error("rpmedicine.minigame.lost_source");
                outside = true;
            }
        }
        bar(g, l + 40, t + H - 46, W - 80, progress, 0xFF6090E0, "rpmedicine.minigame.sutured");
        if (progress >= 1) finish(quality());
    }

    // ------------------------------------------------------------------ сосудистый шов

    private void vessel(GuiGraphics g, int l, int t, double tx, double ty) {
        // Концы артерии и канал, по которому вести нить.
        for (int i = 0; i + 1 < path.length; i++) line(g, l + path[i][0], t + path[i][1], l + path[i + 1][0], t + path[i + 1][1], 0x80E0E0E0);
        for (int i = 0; i + 1 <= pathIndex && i + 1 < path.length && phase > 0; i++)
            line(g, l + path[i][0], t + path[i][1], l + path[i + 1][0], t + path[i + 1][1], 0xFF2A2A8A);
        double[] s0 = path[0];
        double[] e = path[path.length - 1];
        bleedSpot(g, l, t, s0[0], s0[1], 1.2 * (1 - pathIndex / (double) path.length));
        bleedSpot(g, l, t, e[0], e[1], 1.2 * (1 - pathIndex / (double) path.length));
        circle(g, l + s0[0], t + s0[1], 3, 0xFF40C040);
        circle(g, l + e[0], t + e[1], 3, 0xFFE0E0E0);
        if (phase == 0) {
            if (mouseDown && Math.hypot(tx - s0[0], ty - s0[1]) < channel * 1.6) phase = 1;
            return;
        }
        follow(tx, ty, "rpmedicine.minigame.wall");
        if (pathIndex == path.length - 1 && Math.hypot(tx - e[0], ty - e[1]) < channel * 1.5) finish(quality());
    }

    // ------------------------------------------------------------------ остеосинтез

    private int holeX(int i) {
        return 136 + i * 16;
    }

    private void drill(GuiGraphics g, int l, int t, double dt) {
        if (hole >= 4) return;
        double breath = Math.sin(time * 1.4) * 3 * (1.2 - ease);
        int hx = holeX(hole);
        circle(g, l + hx, t + CY + breath, 5, 0xFFFFD040);
        if (phase == 0) return;
        // Сверлить: усилие в плывущей зоне; пережал — сверло соскальзывает.
        zoneCenter = 0.55 + Math.sin(time * (1.0 + (1 - ease)) + hole) * 0.2;
        pressure += (mouseDown ? 0.9 : -0.7) * dt;
        pressure = Mth.clamp(pressure, 0, 1);
        if (Math.abs(pressure - zoneCenter) <= zoneHalf) progress += dt / 0.9;
        else if (pressure > zoneCenter + zoneHalf + 0.15 && mouseDown) error("rpmedicine.minigame.drill_slip");
        gauge(g, l + W - 34, t + 50, 110, pressure, zoneCenter, zoneHalf);
        bar(g, l + 40, t + H - 46, W - 90, progress, 0xFFA8B0B8, "rpmedicine.minigame.drilling");
        if (progress >= 1) {
            hole++;
            phase = 0;
            progress = 0;
            pressure = 0;
            sound(ModSounds.BONE_DRILL.get(), 1.2f);
            if (hole >= 4) finish(quality());
        }
    }

    private void drillClick() {
        if (phase != 0 || hole >= 4) return;
        double breath = Math.sin(time * 1.4) * 3 * (1.2 - ease);
        if (Math.hypot(toolX - holeX(hole), toolY - (CY + breath)) < 4 + 3 * ease) {
            phase = 1;
            sound(ModSounds.BONE_DRILL.get(), 0.9f);
        } else {
            error("rpmedicine.minigame.missed");
        }
    }

    // ------------------------------------------------------------------ дренаж

    private double gapY() {
        return CY + 6 + Math.sin(time * 1.6) * 5 * (1.2 - ease);
    }

    private void drain(GuiGraphics g, int l, int t, double dt) {
        double gy = gapY();
        g.fill(l + 150, t + (int) gy - 3, l + 170, t + (int) gy + 3, 0x80FFD040);
        if (phase == 0) return;
        zoneCenter = 0.5 + Math.sin(time * 1.3) * 0.2;
        pressure += (mouseDown ? 0.9 : -0.7) * dt;
        pressure = Mth.clamp(pressure, 0, 1);
        if (Math.abs(pressure - zoneCenter) <= zoneHalf) {
            progress += dt / 1.6;
            if (rnd.nextFloat() < 0.2) circle(g, l + 160 + rnd.nextInt(10) - 5, t + gy - 6 - rnd.nextInt(8), 1.5, 0xFFE0F0FF);
        } else if (pressure > zoneCenter + zoneHalf + 0.15 && mouseDown) {
            error("rpmedicine.minigame.too_hard");
        }
        gauge(g, l + W - 34, t + 50, 110, pressure, zoneCenter, zoneHalf);
        bar(g, l + 40, t + H - 46, W - 90, progress, 0xFF80B0E0, "rpmedicine.minigame.draining");
        if (progress >= 1) finish(quality());
    }

    private void drainClick() {
        if (phase != 0) return;
        if (Math.abs(toolX - 160) < 12 && Math.abs(toolY - gapY()) < 3 + 3 * ease) {
            phase = 1;
            sound(ModSounds.SURGERY_SUCTION.get(), 1.0f);
        } else {
            error("rpmedicine.minigame.rib_hit");
        }
    }

    // ------------------------------------------------------------------ пули и осколки

    private void extract(GuiGraphics g, int l, int t, double tx, double ty) {
        // Лоток справа.
        g.fill(l + W - 46, t + 54, l + W - 10, t + 84, 0xFFB8BCC0);
        g.fill(l + W - 44, t + 56, l + W - 12, t + 82, 0xFF8A9096);
        int done = 0;
        for (int i = 0; i < foreign.length; i++) {
            if (!removed[i]) continue;
            g.fill(l + W - 40 + (done % 4) * 7, t + 60 + (done / 4) * 8, l + W - 36 + (done % 4) * 7, t + 64 + (done / 4) * 8,
                    i < scene.bullets() ? 0xFFB0A060 : 0xFF707880);
            done++;
        }
        if (carried >= 0) g.fill(l + (int) tx - 2, t + (int) ty - 2, l + (int) tx + 2, t + (int) ty + 2, carried < scene.bullets() ? 0xFFB0A060 : 0xFF707880);
        if (foreign.length == 0 || done == foreign.length) finish(quality());
    }

    private void extractPress() {
        for (int i = 0; i < foreign.length; i++) {
            if (removed[i]) continue;
            if (Math.hypot(toolX - foreign[i][0], toolY - foreign[i][1]) < 5 + 2 * ease) {
                carried = i;
                return;
            }
        }
    }

    private void extractRelease() {
        if (carried < 0) return;
        if (toolX > W - 48 && toolX < W - 8 && toolY > 52 && toolY < 86) {
            removed[carried] = true;
            sound(ModSounds.SURGERY_RETRACT.get(), 1.3f);
        } else if (insideOpening(toolX, toolY, OPEN_H, OPEN_H)) {
            foreign[carried] = new double[]{toolX, toolY};
        }
        carried = -1;
    }

    // ------------------------------------------------------------------ ампутация

    /** Распил поперёк конечности: ровные движения влево-вправо в полосе распила; 12 движений. */
    private static final int STROKES = 12;

    private void saw(GuiGraphics g, int l, int t, double tx, double ty, double speed) {
        int cx = 160;
        // Полоса распила и углубляющийся пропил.
        g.fill(l + cx - 30, t + CY - 1, l + cx + 30, t + CY + 1, 0x60FFFFFF);
        double depth = strokes / (double) STROKES;
        int dh = (int) (6 * depth) + 1;
        g.fill(l + cx - 2, t + CY - dh, l + cx + 2, t + CY + dh, 0xFF3A1010);
        bar(g, l + 40, t + H - 46, W - 80, depth, 0xFFB8BCC0, "rpmedicine.minigame.sawing");
        if (!mouseDown) {
            lastSawX = Double.NaN;
            return;
        }
        if (Math.abs(ty - CY) > channel * 2.5 || Math.abs(tx - cx) > 40) {
            if (!outside) error("rpmedicine.minigame.tissue_cut");
            outside = true;
            return;
        }
        outside = false;
        if (speed > 160 + 60 * ease) error("rpmedicine.minigame.too_rough");
        if (!Double.isNaN(lastSawX)) {
            double dx = tx - lastSawX;
            int dir = dx > 0.5 ? 1 : dx < -0.5 ? -1 : 0;
            if (dir != 0) {
                if (dir != sawDir) {
                    // Сменил направление: засчитать движение, если оно было достаточно длинным.
                    if (sawTravel >= 22) {
                        strokes++;
                        sound(ModSounds.BONE_SAW.get(), 0.9f + rnd.nextFloat() * 0.2f);
                        if (rnd.nextFloat() < 0.6 && drops.size() < 60) drops.add(new double[]{cx, CY + 4, 0, 0.7});
                    }
                    sawDir = dir;
                    sawTravel = 0;
                }
                sawTravel += Math.abs(dx);
            }
        }
        lastSawX = tx;
        if (strokes >= STROKES) finish(quality());
    }

    // ------------------------------------------------------------------ органы

    /** Где лежит орган сцены. */
    private double[] organSpot() {
        int o = scene.organ();
        return switch (o) {
            case 0 -> new double[]{160, CY};          // сердце
            case 1 -> new double[]{X0 + 40, CY};      // лёгкие
            case 2 -> new double[]{X0 + 38, CY - 14}; // печень
            case 3 -> new double[]{X1 - 40, CY + 8};  // почки
            default -> new double[]{168, CY};         // кишечник
        };
    }

    private int organColor() {
        return switch (scene.organ()) {
            case 0 -> 0xFF7A1018;
            case 1 -> 0xFFD07A80;
            case 2 -> 0xFF6A2A1A;
            case 3 -> 0xFF8A3A2A;
            default -> 0xFFD89A90;
        };
    }

    private void drawOrgan(GuiGraphics g, int l, int t, double x, double y) {
        g.fill(l + (int) x - 12, t + (int) y - 9, l + (int) x + 12, t + (int) y + 9, organColor());
        g.fill(l + (int) x - 10, t + (int) y - 7, l + (int) x - 4, t + (int) y - 4, 0x40FFFFFF);
    }

    private void tray(GuiGraphics g, int l, int t) {
        g.fill(l + W - 46, t + 54, l + W - 10, t + 86, 0xFFB8BCC0);
        g.fill(l + W - 44, t + 56, l + W - 12, t + 84, 0xFF5A8AA0);
    }

    /** Изъятие: пересечь три сосуда между толчками крови, затем вынуть орган в контейнер. */
    private void harvest(GuiGraphics g, int l, int t, double tx, double ty) {
        tray(g, l, t);
        if (phase == 0) {
            drawOrgan(g, l, t, organX, organY);
            for (int i = 0; i < vessels.length; i++) {
                double[] v = vessels[i];
                if (vesselCut[i]) {
                    g.fill(l + (int) v[0] - 2, t + (int) v[1] - 1, l + (int) v[0] + 2, t + (int) v[1] + 1, 0xFF3A0A0A);
                    continue;
                }
                line(g, l + v[0], t + v[1], l + organX, t + organY, 0xFFC01818);
                circle(g, l + v[0], t + v[1], 3, 0xFFFFD040);
                if (spurting(i)) g.fill(l + (int) v[0] - 7, t + (int) v[1] - 7, l + (int) v[0] + 7, t + (int) v[1] + 7, 0xD0A01010);
            }
            return;
        }
        double x = organHeld ? tx : organX;
        double y = organHeld ? ty : organY;
        drawOrgan(g, l, t, x, y);
    }

    private void harvestPress() {
        if (phase == 0) {
            for (int i = 0; i < vessels.length; i++) {
                if (vesselCut[i] || Math.hypot(toolX - vessels[i][0], toolY - vessels[i][1]) >= 4 + 3 * ease) continue;
                if (spurting(i)) {
                    error("rpmedicine.minigame.blood_hidden");
                    return;
                }
                vesselCut[i] = true;
                sound(ModSounds.SURGERY_VESSEL_CUT.get(), 1.0f);
                for (boolean c : vesselCut) if (!c) return;
                phase = 1;
                return;
            }
            error("rpmedicine.minigame.missed");
            return;
        }
        if (Math.hypot(toolX - organX, toolY - organY) < 12) organHeld = true;
    }

    private void harvestRelease() {
        if (phase != 1 || !organHeld) return;
        organHeld = false;
        if (toolX > W - 48 && toolX < W - 8 && toolY > 52 && toolY < 88) {
            sound(ModSounds.ORGAN_MOVE.get(), 1.0f);
            finish(quality());
        } else {
            organX = toolX;
            organY = toolY;
        }
    }

    /** Пересадка: уложить орган на место, затем сшить сосуд по каналу. */
    private void plant(GuiGraphics g, int l, int t, double tx, double ty) {
        double[] spot = organSpot();
        if (phase == 0) {
            tray(g, l, t);
            circle(g, l + spot[0], t + spot[1], 12, 0x80FFD040);
            drawOrgan(g, l, t, organHeld ? tx : organX, organHeld ? ty : organY);
            return;
        }
        drawOrgan(g, l, t, spot[0], spot[1]);
        for (int i = 0; i + 1 < path.length; i++) line(g, l + path[i][0], t + path[i][1], l + path[i + 1][0], t + path[i + 1][1], 0x80E0E0E0);
        for (int i = 0; i + 1 <= pathIndex && i + 1 < path.length && phase == 2; i++)
            line(g, l + path[i][0], t + path[i][1], l + path[i + 1][0], t + path[i + 1][1], 0xFF2A2A8A);
        double[] s0 = path[0];
        double[] e = path[path.length - 1];
        circle(g, l + s0[0], t + s0[1], 3, 0xFF40C040);
        circle(g, l + e[0], t + e[1], 3, 0xFFE0E0E0);
        if (phase == 1) {
            if (mouseDown && Math.hypot(tx - s0[0], ty - s0[1]) < channel * 1.6) phase = 2;
            return;
        }
        follow(tx, ty, "rpmedicine.minigame.wall");
        if (pathIndex == path.length - 1 && Math.hypot(tx - e[0], ty - e[1]) < channel * 1.5) finish(quality());
    }

    private void plantPress() {
        if (phase == 0 && Math.hypot(toolX - organX, toolY - organY) < 12) organHeld = true;
    }

    private void plantRelease() {
        if (phase == 0 && organHeld) {
            organHeld = false;
            double[] spot = organSpot();
            if (Math.hypot(toolX - spot[0], toolY - spot[1]) < 8 + 4 * ease) {
                phase = 1;
                sound(ModSounds.ORGAN_MOVE.get(), 1.0f);
            } else {
                organX = toolX;
                organY = toolY;
                error("rpmedicine.minigame.missed");
            }
        } else if (phase == 2) {
            phase = 1;
            error("rpmedicine.minigame.dropped");
        }
    }

    // ------------------------------------------------------------------ общее

    private void follow(double tx, double ty, String wallKey) {
        double d = distanceToPath(tx, ty);
        if (d > channel) {
            if (!outside) error(wallKey);
            outside = true;
        } else {
            outside = false;
        }
        for (int i = pathIndex; i < path.length; i++) {
            if (Math.hypot(tx - path[i][0], ty - path[i][1]) < channel * 2.2) pathIndex = Math.max(pathIndex, i);
            else if (i > pathIndex) break;
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

    private void gauge(GuiGraphics g, int x, int y, int h, double value, double center, double half) {
        g.fill(x, y, x + 20, y + h, 0xFF2A2A2A);
        g.fill(x, y + (int) ((1 - (center + half)) * h), x + 20, y + (int) ((1 - (center - half)) * h), 0xFF2E8B57);
        int vy = y + (int) ((1 - value) * h);
        g.fill(x - 4, vy - 1, x + 24, vy + 2, 0xFFFFFFFF);
    }

    private void bar(GuiGraphics g, int x, int y, int w, double frac, int color, String key) {
        g.fill(x, y, x + w, y + 5, 0xFF2A2A2A);
        g.fill(x, y, x + (int) (w * Mth.clamp(frac, 0, 1)), y + 5, color);
        g.drawString(font, Component.translatable(key), x, y - 10, 0xCCCCCC, false);
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

    private double quality() {
        return Mth.clamp(1 - errors * 0.2 - Math.max(0, time / timeLimit - 0.6) * 0.3, 0, 1);
    }

    /** Доля сделанного — если время вышло. */
    private double doneFraction() {
        return switch (type) {
            case INCISION, VESSEL -> path == null ? 0 : pathIndex / (double) (path.length - 1);
            case CLAMP -> {
                int c = 0;
                for (boolean b : clamped) if (b) c++;
                yield c / (double) clamped.length;
            }
            case RETRACT -> Math.min(openTop, openBottom) / OPEN_H;
            case CLOSE, ORGAN_SUTURE -> stitchIndex / (double) (stitches.length / 2);
            case DRILL -> hole / 4.0;
            case AMPUTATION -> strokes / (double) STROKES;
            case HARVEST -> phase == 0 ? 0.3 : 0.6;
            case PLANT -> phase == 0 ? 0 : path == null ? 0.5 : 0.4 + 0.6 * pathIndex / (double) (path.length - 1);
            case EXTRACT -> {
                int c = 0;
                for (boolean b : removed) if (b) c++;
                yield foreign.length == 0 ? 1 : c / (double) foreign.length;
            }
            default -> Mth.clamp(progress, 0, 1);
        };
    }

    // ------------------------------------------------------------------ ввод

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button != 0 || sent) return false;
        mouseDown = true;
        switch (type) {
            case CLAMP -> clampClick();
            case RETRACT -> retractPress();
            case CLOSE, ORGAN_SUTURE -> stitchPress();
            case DRILL -> drillClick();
            case DRAIN -> drainClick();
            case EXTRACT -> extractPress();
            case HARVEST -> harvestPress();
            case PLANT -> plantPress();
            default -> { }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0) {
            mouseDown = false;
            dragging = -1;
            switch (type) {
                case CLOSE, ORGAN_SUTURE -> stitchRelease();
                case EXTRACT -> extractRelease();
                case HARVEST -> harvestRelease();
                case PLANT -> plantRelease();
                case INCISION, VESSEL -> {
                    // Отпустил инструмент посреди шага — продолжить с того же места.
                    if (phase == 1 && !sent) {
                        phase = 0;
                        if (type == Minigames.Type.VESSEL) error("rpmedicine.minigame.dropped");
                    }
                }
                default -> { }
            }
        }
        return super.mouseReleased(mx, my, button);
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

    public void cancelledByServer() {
        if (sent) return;
        sent = true;
        minecraft.setScreen(back);
    }

    @Override
    public void onClose() {
        refuse();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
