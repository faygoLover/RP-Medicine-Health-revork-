package faygolover.rpmedicine.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import faygolover.rpmedicine.medcard.Medcard;
import faygolover.rpmedicine.network.MedcardActionPacket;
import faygolover.rpmedicine.network.MedcardDataPacket;
import faygolover.rpmedicine.network.Network;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Экран медкарты — раскрытая книга (решения, п. 1.13; проверка 8.7). Слева титульный лист: фото,
 * Ф. И. О., возраст, пол, рост, вес, группа, аллергии, хронические. Справа — история травм и лечения:
 * дата строкой, под ней время и записи дня, длинные записи переносятся. Предложения мода — щелчком по
 * записи: принять, отклонить, изменить.
 */
public class MedcardScreen extends Screen {
    private static final int W = 460;
    private static final int H = 256;
    private static final int PAGE = W / 2;
    private static final int LINE = 10;
    private static final int PAPER = 0xFFEDE6D6;
    private static final int INK = 0x2A2A2A;
    private static final int FADED = 0x6E675A;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    /** Строка правой страницы: дата, либо строка записи (первая строка записи помнит её id). */
    private record Row(FormattedCharSequence text, int entryId, boolean header, boolean first) {}

    private MedcardDataPacket card;
    private int scroll;
    /** Выбранная запись (−1 — нет). */
    private int selected = -1;
    /** Какую запись правим (−1 — новая). */
    private int editing = -1;
    private String gender;
    private EditBox fullName;
    private EditBox age;
    private EditBox allergies;
    private EditBox chronic;
    private EditBox entryText;
    private List<Row> rows = List.of();

    public MedcardScreen(MedcardDataPacket card) {
        super(Component.translatable("rpmedicine.medcard.title"));
        this.card = card;
        this.gender = card.gender();
    }

    public UUID uuid() {
        return card.uuid();
    }

    public void update(MedcardDataPacket p) {
        this.card = p;
        this.gender = p.gender();
        if (selected >= 0 && find(selected) == null) selected = -1;
        // Поля титульного листа — с сервера, если игрок их не правит прямо сейчас.
        fullName = null;
        age = null;
        allergies = null;
        chronic = null;
        rebuildWidgets();
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    private int histTop() {
        return top() + 24;
    }

    private int histLines() {
        return (H - 24 - 52) / LINE;
    }

    private Medcard.Entry find(int id) {
        for (Medcard.Entry e : card.entries()) if (e.id == id) return e;
        return null;
    }

    /** Записи от новых к старым, сгруппированные по дням; длинные — переносом. */
    private List<Row> buildRows() {
        List<Medcard.Entry> list = new ArrayList<>(card.entries());
        list.sort((a, b) -> Long.compare(b.time, a.time));
        List<Row> out = new ArrayList<>();
        String day = null;
        int width = PAGE - 22;
        for (Medcard.Entry e : list) {
            String d = DAY.format(Instant.ofEpochMilli(e.time));
            if (!d.equals(day)) {
                day = d;
                out.add(new Row(Component.literal(d).withStyle(ChatFormatting.BOLD, ChatFormatting.UNDERLINE).getVisualOrderText(), -1, true, false));
            }
            MutableComponent line = Component.literal(TIME.format(Instant.ofEpochMilli(e.time)) + "  ").withStyle(s -> s.withColor(FADED))
                    .append(entryComponent(e).withStyle(s -> s.withColor(e.proposed ? 0x8A6A00 : INK)));
            if (!e.author.isEmpty()) line.append(Component.literal(" — " + e.author).withStyle(s -> s.withColor(FADED).withItalic(true)));
            if (e.proposed) line.append(Component.translatable("rpmedicine.medcard.proposed").withStyle(s -> s.withColor(0xB07800)));
            List<FormattedCharSequence> split = font.split(line, width);
            for (int i = 0; i < split.size(); i++) out.add(new Row(split.get(i), e.id, false, i == 0));
        }
        return out;
    }

    @Override
    protected void init() {
        rows = buildRows();
        scroll = Math.max(0, Math.min(scroll, rows.size() - histLines()));
        int l = left();
        int t = top();
        int fx = l + 82;
        int fw = PAGE - 82 - 10;

        // Титульный лист.
        fullName = box(fullName, fx, t + 34, fw, card.fullName().isEmpty() ? card.name() : card.fullName(), 64);
        age = box(age, fx, t + 58, 34, card.age() > 0 ? String.valueOf(card.age()) : "", 3);
        addRenderableWidget(Button.builder(genderLabel(), b -> {
            gender = gender.equals("m") ? "f" : gender.equals("f") ? "" : "m";
            b.setMessage(genderLabel());
        }).bounds(fx + 66, t + 57, fw - 66, 14).build());
        allergies = box(allergies, l + 10, t + 150, PAGE - 20, card.allergies(), 300);
        chronic = box(chronic, l + 10, t + 184, PAGE - 20, card.chronic(), 300);
        addRenderableWidget(Button.builder(Component.translatable("rpmedicine.medcard.save"), b -> saveTitle())
                .bounds(l + PAGE - 80, t + H - 26, 70, 16).build());

        // История: правка выбранной записи и своя запись.
        int rx = l + PAGE + 10;
        Medcard.Entry sel = selected >= 0 ? find(selected) : null;
        if (sel != null) {
            int id = sel.id;
            int bx = l + W - 10;
            bx -= 16;
            addRenderableWidget(Button.builder(Component.literal("✎"), b -> {
                editing = id;
                entryText.setValue(sel.text.isEmpty() ? entryComponent(sel).getString() : sel.text);
                rebuildWidgets();
            }).bounds(bx, t + H - 48, 15, 14).tooltip(Tooltip.create(Component.translatable("rpmedicine.medcard.edit"))).build());
            if (sel.proposed) {
                bx -= 16;
                addRenderableWidget(Button.builder(Component.literal("✗"), b -> {
                    send(MedcardActionPacket.Op.DECLINE, id, "");
                    selected = -1;
                }).bounds(bx, t + H - 48, 15, 14).tooltip(Tooltip.create(Component.translatable("rpmedicine.medcard.decline"))).build());
                bx -= 16;
                addRenderableWidget(Button.builder(Component.literal("✓"), b -> send(MedcardActionPacket.Op.ACCEPT, id, ""))
                        .bounds(bx, t + H - 48, 15, 14).tooltip(Tooltip.create(Component.translatable("rpmedicine.medcard.accept"))).build());
            }
        }
        entryText = box(entryText, rx, t + H - 26, PAGE - 20 - 74, "", 300);
        addRenderableWidget(Button.builder(Component.translatable(editing >= 0 ? "rpmedicine.medcard.save" : "rpmedicine.medcard.add"), b -> {
            if (editing >= 0) send(MedcardActionPacket.Op.EDIT, editing, entryText.getValue());
            else send(MedcardActionPacket.Op.ADD, -1, entryText.getValue());
            editing = -1;
            entryText.setValue("");
        }).bounds(l + W - 82, t + H - 27, 72, 18).build());
    }

    /** Поле ввода; при перестройке экрана сохраняет набранное. */
    private EditBox box(EditBox old, int x, int y, int w, String value, int max) {
        EditBox b = addRenderableWidget(new EditBox(font, x, y, w, 14, Component.empty()));
        b.setMaxLength(max);
        b.setValue(old != null ? old.getValue() : value);
        return b;
    }

    private Component genderLabel() {
        return Component.translatable("rpmedicine.medcard.gender." + (gender.isEmpty() ? "none" : gender));
    }

    private void saveTitle() {
        String name = fullName.getValue().trim();
        if (!name.equals(card.fullName()) && !(card.fullName().isEmpty() && name.equals(card.name())))
            send(MedcardActionPacket.Op.FULL_NAME, -1, name);
        String a = age.getValue().trim();
        if (!a.equals(card.age() > 0 ? String.valueOf(card.age()) : "")) send(MedcardActionPacket.Op.AGE, -1, a.isEmpty() ? "0" : a);
        if (!gender.equals(card.gender())) send(MedcardActionPacket.Op.GENDER, -1, gender);
        if (!allergies.getValue().equals(card.allergies())) send(MedcardActionPacket.Op.ALLERGIES, -1, allergies.getValue());
        if (!chronic.getValue().equals(card.chronic())) send(MedcardActionPacket.Op.CHRONIC, -1, chronic.getValue());
    }

    private void send(MedcardActionPacket.Op op, int id, String text) {
        Network.sendToServer(new MedcardActionPacket(card.uuid(), op, id, text));
    }

    /** Текст записи: своя — как есть; от мода — перевод с аргументами ({@code #ключ} — тоже перевод). */
    static MutableComponent entryComponent(Medcard.Entry e) {
        if (!e.text.isEmpty()) return Component.literal(e.text);
        Object[] args = new Object[e.args.size()];
        for (int i = 0; i < args.length; i++) {
            String a = e.args.get(i);
            args[i] = a.startsWith("#") ? Component.translatable(a.substring(1)) : a;
        }
        return Component.translatable("rpmedicine.medcard.entry." + e.key, args);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int l = left();
        int t = top();
        // Обложка и два листа.
        g.fill(l - 4, t - 4, l + W + 4, t + H + 4, 0xFF5A3A28);
        g.fill(l, t, l + PAGE - 1, t + H, PAPER);
        g.fill(l + PAGE + 1, t, l + W, t + H, PAPER);
        g.fill(l + PAGE - 1, t, l + PAGE + 1, t + H, 0xFFB8AD96);

        // --- Титульный лист. Текст без тени: тень на светлой бумаге делала заголовок нечитаемым.
        text(g, Component.translatable("rpmedicine.medcard.page_title").withStyle(ChatFormatting.BOLD), l + PAGE / 2, t + 8, INK, true);
        drawPhoto(g, l + 10, t + 24);
        text(g, Component.translatable("rpmedicine.medcard.full_name"), l + 82, t + 24, FADED, false);
        text(g, Component.translatable("rpmedicine.medcard.age"), l + 82, t + 50, FADED, false);
        text(g, Component.translatable("rpmedicine.medcard.gender"), l + 148, t + 50, FADED, false);
        String blood = card.bloodType().isEmpty() ? "?" : faygolover.rpmedicine.core.BloodType.byId(card.bloodType()).map(b -> b.label).orElse("?");
        int vy = t + 80;
        field(g, "rpmedicine.medcard.height", String.valueOf(Math.round(card.height())), l + 82, vy);
        field(g, "rpmedicine.medcard.weight", String.valueOf(Math.round(card.weight())), l + 82, vy + 11);
        field(g, "rpmedicine.medcard.blood", blood, l + 82, vy + 22);
        if (mx >= l + 82 && mx < l + PAGE - 10 && my >= vy && my < vy + 33)
            g.renderTooltip(font, Component.translatable("rpmedicine.medcard.gm_only"), mx, my);
        text(g, Component.translatable("rpmedicine.medcard.allergies"), l + 10, t + 140, FADED, false);
        text(g, Component.translatable("rpmedicine.medcard.chronic"), l + 10, t + 174, FADED, false);
        if (card.created() > 0)
            text(g, Component.translatable("rpmedicine.medcard.created", DAY.format(Instant.ofEpochMilli(card.created()))), l + 10, t + H - 22, FADED, false);

        // --- История.
        int rx = l + PAGE + 10;
        text(g, Component.translatable("rpmedicine.medcard.page_history").withStyle(ChatFormatting.BOLD), l + PAGE + PAGE / 2, t + 8, INK, true);
        if (rows.isEmpty()) text(g, Component.translatable("rpmedicine.medcard.no_entries"), rx, histTop(), FADED, false);
        int n = histLines();
        for (int i = 0; i < n && scroll + i < rows.size(); i++) {
            Row r = rows.get(scroll + i);
            int y = histTop() + i * LINE;
            if (!r.header && r.entryId == selected) g.fill(rx - 2, y - 1, l + W - 8, y + LINE - 1, 0x40B08A40);
            g.drawString(font, r.text, r.header ? rx : rx + 4, y, INK, false);
        }
        if (rows.size() > n) {
            // Полоса прокрутки.
            int barH = n * LINE;
            int th = Math.max(10, barH * n / rows.size());
            int ty = histTop() + (barH - th) * scroll / Math.max(1, rows.size() - n);
            g.fill(l + W - 6, histTop(), l + W - 4, histTop() + barH, 0x30000000);
            g.fill(l + W - 6, ty, l + W - 4, ty + th, 0xA0806040);
        }
        if (editing >= 0) text(g, Component.translatable("rpmedicine.medcard.editing"), rx, t + H - 38, 0x8A6A00, false);
        else if (selected < 0 && card.entries().stream().anyMatch(e -> e.proposed))
            text(g, Component.translatable("rpmedicine.medcard.select_hint"), rx, t + H - 38, FADED, false);
        super.render(g, mx, my, pt);
    }

    private void text(GuiGraphics g, Component c, int x, int y, int color, boolean center) {
        g.drawString(font, c, center ? x - font.width(c) / 2 : x, y, color, false);
    }

    private void field(GuiGraphics g, String key, String value, int x, int y) {
        Component k = Component.translatable(key);
        g.drawString(font, k, x, y, FADED, false);
        g.drawString(font, value, x + font.width(k) + 4, y, INK, false);
    }

    /**
     * Фото: персонаж, если он рядом (виден клиенту), в приглушённом «снимочном» цвете; иначе пустая рамка.
     * Отдельный снимок при создании карты не хранится — это заглушка до решения автора (вопрос 12).
     */
    private void drawPhoto(GuiGraphics g, int x, int y) {
        int w = 64;
        int h = 80;
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFFFFFFFF);
        g.fill(x, y, x + w, y + h, 0xFFB9B6AE);
        Player p = minecraft.level != null ? minecraft.level.getPlayerByUUID(card.uuid()) : null;
        if (p == null) {
            text(g, Component.translatable("rpmedicine.medcard.no_photo"), x + w / 2, y + h / 2 - 4, 0x5A5A5A, true);
            return;
        }
        g.enableScissor(x, y, x + w, y + h);
        RenderSystem.setShaderColor(0.62f, 0.6f, 0.56f, 1f);
        // Взгляд прямо в камеру: «мышь» чуть выше лица.
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, x + w / 2, y + h + 34, 42, x + w / 2f, y + 10, p);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        g.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int l = left();
        int rx = l + PAGE + 8;
        if (mx >= rx && mx < l + W - 8 && my >= histTop() && my < histTop() + histLines() * LINE) {
            int i = scroll + (int) ((my - histTop()) / LINE);
            if (i < rows.size() && !rows.get(i).header) {
                int id = rows.get(i).entryId;
                selected = selected == id ? -1 : id;
                rebuildWidgets();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int max = Math.max(0, rows.size() - histLines());
        int ns = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta) * 2));
        if (ns != scroll) scroll = ns;
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
