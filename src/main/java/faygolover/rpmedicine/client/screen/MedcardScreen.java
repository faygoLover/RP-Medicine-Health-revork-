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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Медкарта — книга по форме «Медицинская карта пациента» (решения, п. 1.14). Первый разворот: слева
 * регистрационные данные и сведения о пациенте с фото, справа медицинские сведения, особые отметки и
 * комментарий. Дальше — анамнез таблицей через разворот: дата и диагноз слева, обстоятельства и
 * последствия справа. Предложения мода — щелчком по строке: принять, отклонить, изменить.
 */
public class MedcardScreen extends Screen {
    private static final int W = 480;
    private static final int H = 262;
    private static final int PAGE = W / 2;
    private static final int LINE = 10;
    private static final int PAPER = 0xFFEDE6D6;
    private static final int INK = 0x2A2A2A;
    private static final int FADED = 0x6E675A;
    private static final int GOLD = 0x8A6A00;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DAY_SHORT = DateTimeFormatter.ofPattern("dd.MM.yy").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
    /** Анамнез: ширины колонок (дата, диагноз; обстоятельства, последствия). */
    private static final int COL_DATE = 50;
    private static final int TABLE_TOP = 30;
    private static final int TABLE_BOTTOM = H - 62;

    /** Строка таблицы анамнеза: запись и её строки по колонкам. */
    private record Row(Medcard.Entry e, List<FormattedCharSequence> date, List<FormattedCharSequence> diag,
                       List<FormattedCharSequence> circ, List<FormattedCharSequence> cons) {
        int lines() {
            return Math.max(Math.max(date.size(), diag.size()), Math.max(circ.size(), cons.size()));
        }
    }

    private Medcard card;
    /** Разворот: 0 — титул, дальше — анамнез. */
    private int spread;
    private int selected = -1;
    private int editing = -1;
    private final Map<String, EditBox> boxes = new HashMap<>();
    private final Map<String, String> drafts = new HashMap<>();
    private String gender;
    private String department;
    private int marks;
    private boolean closed;
    /** Страницы анамнеза: строки каждого разворота. */
    private List<List<Row>> pages = List.of();

    public MedcardScreen(MedcardDataPacket p) {
        super(Component.translatable("rpmedicine.medcard.title"));
        load(p.card());
    }

    private void load(Medcard c) {
        this.card = c;
        this.gender = c.gender;
        this.department = c.department;
        this.marks = c.marks;
        this.closed = c.closed;
    }

    public UUID uuid() {
        return card.uuid;
    }

    public void update(MedcardDataPacket p) {
        load(p.card());
        if (selected >= 0 && card.entry(selected) == null) selected = -1;
        // Титул — с сервера (набранное, но не сохранённое, пропадёт: сервер уже прислал свежие данные).
        drafts.keySet().removeIf(k -> !k.startsWith("entry_"));
        rebuildWidgets();
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    // ------------------------------------------------------------------ виджеты

    @Override
    protected void init() {
        // Набранный текст переживает перестройку экрана.
        for (var e : boxes.entrySet()) drafts.put(e.getKey(), e.getValue().getValue());
        boxes.clear();
        pages = paginate();
        if (spread > pages.size()) spread = pages.size();
        int l = left();
        int t = top();
        if (spread == 0) initTitle(l, t);
        else initHistory(l, t);
        // Листать.
        int total = 1 + Math.max(1, pages.size());
        if (spread > 0) addRenderableWidget(Button.builder(Component.literal("◀"), b -> turn(-1)).bounds(l + 4, t + H - 16, 16, 12).build());
        if (spread + 1 < total) addRenderableWidget(Button.builder(Component.literal("▶"), b -> turn(1)).bounds(l + W - 20, t + H - 16, 16, 12).build());
    }

    private void turn(int d) {
        spread = Math.max(0, spread + d);
        selected = -1;
        editing = -1;
        rebuildWidgets();
    }

    private EditBox box(String key, int x, int y, int w, String value, int max) {
        EditBox b = addRenderableWidget(new EditBox(font, x, y, w, 12, Component.empty()));
        b.setMaxLength(max);
        b.setBordered(true);
        b.setValue(drafts.getOrDefault(key, value));
        boxes.put(key, b);
        return b;
    }

    private void initTitle(int l, int t) {
        // Левая страница: регистрация и сведения.
        addRenderableWidget(Button.builder(statusLabel(), b -> {
            closed = !closed;
            b.setMessage(statusLabel());
            send(MedcardActionPacket.set(card.uuid, "closed", closed ? "1" : "0"));
        }).bounds(l + 74, t + 66, 96, 12).build());
        int fx = l + 82;
        int fw = PAGE - 92;
        box("fullName", fx, t + 108, fw, card.fullName.isEmpty() ? card.name : card.fullName, 64);
        box("callsign", fx, t + 122, fw, card.callsign, 32);
        box("serviceDate", fx, t + 136, 70, card.serviceDate, 16);
        box("birthDate", fx, t + 150, 70, card.birthDate, 16);
        addRenderableWidget(Button.builder(genderLabel(), b -> {
            gender = gender.equals("m") ? "f" : gender.equals("f") ? "" : "m";
            b.setMessage(genderLabel());
        }).bounds(fx, t + 164, 70, 12).build());
        addRenderableWidget(Button.builder(departmentLabel(), b -> {
            int i = java.util.Arrays.asList(Medcard.DEPARTMENTS).indexOf(department);
            department = Medcard.DEPARTMENTS[(i + 1) % Medcard.DEPARTMENTS.length];
            b.setMessage(departmentLabel());
        }).bounds(fx, t + 178, fw, 12).build());

        // Правая страница: медицинские сведения, отметки, комментарий.
        int rx = l + PAGE + 10;
        int rw = PAGE - 20;
        String[] med = {"allergies", "chronic", "medications", "implants", "disability"};
        String[] vals = {card.allergies, card.chronic, card.medications, card.implants, card.disability};
        for (int i = 0; i < med.length; i++) box(med[i], rx, t + 32 + i * 24, rw, vals[i], 300);
        int[] bits = {Medcard.MARK_PSYCH, Medcard.MARK_INCAPACITY, Medcard.MARK_HIGH_RISK};
        for (int i = 0; i < bits.length; i++) {
            int bit = bits[i];
            addRenderableWidget(Button.builder(markLabel(bit), b -> {
                marks ^= bit;
                b.setMessage(markLabel(bit));
            }).bounds(rx, t + 164 + i * 14, rw, 12).build());
        }
        box("comment", rx, t + 220, rw, card.comment, 300);
        addRenderableWidget(Button.builder(Component.translatable("rpmedicine.medcard.save"), b -> saveTitle())
                .bounds(l + W - 96, t + H - 17, 70, 14).build());
    }

    private void initHistory(int l, int t) {
        int y = t + H - 56;
        // Правка выбранной записи или новая: диагноз слева, обстоятельства и последствия справа.
        Medcard.Entry sel = selected >= 0 ? card.entry(selected) : null;
        boolean edit = editing >= 0 && card.entry(editing) != null;
        Medcard.Entry src = edit ? card.entry(editing) : null;
        String pre = edit ? "entry_edit_" + editing + "_" : "entry_new_";
        box(pre + "diag", l + 10, y + 12, PAGE - 20, src != null ? (src.text.isEmpty() ? entryComponent(src).getString() : src.text) : "", 300);
        box(pre + "circ", l + PAGE + 10, y + 12, PAGE - 20, src != null ? plain(src.circumstances) : "", 300);
        box(pre + "cons", l + PAGE + 10, y + 26, PAGE - 20, src != null ? src.consequences : "", 300);
        addRenderableWidget(Button.builder(Component.translatable(edit ? "rpmedicine.medcard.save" : "rpmedicine.medcard.add"), b -> {
            List<String> v = List.of(boxes.get(pre + "diag").getValue(), boxes.get(pre + "circ").getValue(), boxes.get(pre + "cons").getValue());
            send(new MedcardActionPacket(card.uuid, edit ? MedcardActionPacket.Op.EDIT : MedcardActionPacket.Op.ADD, edit ? editing : -1, "", v));
            drafts.keySet().removeIf(k -> k.startsWith(pre));
            boxes.keySet().removeIf(k -> k.startsWith(pre));
            editing = -1;
            rebuildWidgets();
        }).bounds(l + 10, y + 26, 90, 14).build());
        if (edit) addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> {
            editing = -1;
            rebuildWidgets();
        }).bounds(l + 104, y + 26, 60, 14).build());
        if (sel != null && !edit) {
            int id = sel.id;
            int bx = l + PAGE - 10;
            bx -= 16;
            addRenderableWidget(Button.builder(Component.literal("✎"), b -> {
                editing = id;
                rebuildWidgets();
            }).bounds(bx, y + 26, 15, 14).tooltip(Tooltip.create(Component.translatable("rpmedicine.medcard.edit"))).build());
            if (sel.proposed) {
                bx -= 16;
                addRenderableWidget(Button.builder(Component.literal("✗"), b -> {
                    send(new MedcardActionPacket(card.uuid, MedcardActionPacket.Op.DECLINE, id, "", List.of()));
                    selected = -1;
                }).bounds(bx, y + 26, 15, 14).tooltip(Tooltip.create(Component.translatable("rpmedicine.medcard.decline"))).build());
                bx -= 16;
                addRenderableWidget(Button.builder(Component.literal("✓"), b -> send(new MedcardActionPacket(card.uuid, MedcardActionPacket.Op.ACCEPT, id, "", List.of())))
                        .bounds(bx, y + 26, 15, 14).tooltip(Tooltip.create(Component.translatable("rpmedicine.medcard.accept"))).build());
            }
        }
    }

    private void saveTitle() {
        String[] fields = {"fullName", "callsign", "serviceDate", "birthDate", "allergies", "chronic", "medications", "implants", "disability", "comment"};
        String[] now = {card.fullName.isEmpty() ? card.name : card.fullName, card.callsign, card.serviceDate, card.birthDate, card.allergies,
                card.chronic, card.medications, card.implants, card.disability, card.comment};
        for (int i = 0; i < fields.length; i++) {
            EditBox b = boxes.get(fields[i]);
            if (b != null && !b.getValue().equals(now[i])) send(MedcardActionPacket.set(card.uuid, fields[i], b.getValue()));
        }
        if (!gender.equals(card.gender)) send(MedcardActionPacket.set(card.uuid, "gender", gender));
        if (!department.equals(card.department)) send(MedcardActionPacket.set(card.uuid, "department", department));
        if (marks != card.marks) send(MedcardActionPacket.set(card.uuid, "marks", String.valueOf(marks)));
        drafts.clear();
    }

    private void send(MedcardActionPacket p) {
        Network.sendToServer(p);
    }

    private Component statusLabel() {
        return Component.literal(closed ? "☐ " : "☑ ").append(Component.translatable("rpmedicine.medcard.status_active"))
                .append(Component.literal(closed ? "  ☑ " : "  ☐ ")).append(Component.translatable("rpmedicine.medcard.status_closed"));
    }

    private Component genderLabel() {
        return Component.translatable("rpmedicine.medcard.gender." + (gender.isEmpty() ? "none" : gender));
    }

    private Component departmentLabel() {
        return department.isEmpty() ? Component.literal("—") : Component.translatable("rpmedicine.medcard.dept." + department);
    }

    private Component markLabel(int bit) {
        String key = bit == Medcard.MARK_PSYCH ? "mark_psych" : bit == Medcard.MARK_INCAPACITY ? "mark_incapacity" : "mark_high_risk";
        return Component.literal((marks & bit) != 0 ? "☑ " : "☐ ").append(Component.translatable("rpmedicine.medcard." + key));
    }

    // ------------------------------------------------------------------ текст

    /** Текст записи: своя — как есть; от мода — перевод с аргументами ({@code #ключ} — тоже перевод). */
    static MutableComponent entryComponent(Medcard.Entry e) {
        if (!e.text.isEmpty()) return Component.literal(e.text);
        Object[] args = new Object[e.args.size()];
        for (int i = 0; i < args.length; i++) args[i] = maybeKey(e.args.get(i));
        return Component.translatable("rpmedicine.medcard.entry." + e.key, args);
    }

    private static MutableComponent maybeKey(String s) {
        return s.startsWith("#") ? Component.translatable(s.substring(1)) : Component.literal(s);
    }

    private static String plain(String s) {
        return s.isEmpty() ? "" : maybeKey(s).getString();
    }

    /** Группа крови по форме: «A (II) Rh+». */
    private static String bloodLong(String id) {
        if (id.isEmpty()) return "?";
        String abo = id.substring(0, id.length() - 1).toUpperCase(java.util.Locale.ROOT);
        String roman = switch (abo) {
            case "O" -> "I";
            case "A" -> "II";
            case "B" -> "III";
            case "AB" -> "IV";
            default -> "?";
        };
        return abo + " (" + roman + ") Rh" + id.charAt(id.length() - 1);
    }

    /** Строки анамнеза от старых к новым, разбитые на развороты. */
    private List<List<Row>> paginate() {
        List<Medcard.Entry> list = new ArrayList<>(card.entries);
        list.sort((a, b) -> Long.compare(a.time, b.time));
        int diagW = PAGE - 20 - COL_DATE - 4;
        int halfW = (PAGE - 24) / 2;
        List<List<Row>> out = new ArrayList<>();
        List<Row> page = new ArrayList<>();
        int used = 0;
        int cap = (TABLE_BOTTOM - TABLE_TOP) / LINE;
        for (Medcard.Entry e : list) {
            Instant at = Instant.ofEpochMilli(e.time);
            List<FormattedCharSequence> date = List.of(Component.literal(DAY.format(at)).getVisualOrderText(),
                    Component.literal(TIME.format(at)).withStyle(s -> s.withColor(FADED)).getVisualOrderText());
            MutableComponent d = entryComponent(e).withStyle(s -> s.withColor(e.proposed ? GOLD : INK));
            if (!e.author.isEmpty()) d.append(Component.literal(" — " + e.author).withStyle(s -> s.withColor(FADED).withItalic(true)));
            if (e.proposed) d.append(Component.translatable("rpmedicine.medcard.proposed").withStyle(s -> s.withColor(0xB07800)));
            Row r = new Row(e, date, font.split(d, diagW),
                    font.split(e.circumstances.isEmpty() ? Component.literal("—").withStyle(s -> s.withColor(FADED)) : maybeKey(e.circumstances), halfW),
                    font.split(e.consequences.isEmpty() ? Component.literal("—").withStyle(s -> s.withColor(FADED)) : Component.literal(e.consequences), halfW));
            int n = Math.min(r.lines(), cap) + 1;
            if (used + n > cap && !page.isEmpty()) {
                out.add(page);
                page = new ArrayList<>();
                used = 0;
            }
            page.add(r);
            used += n;
        }
        if (!page.isEmpty()) out.add(page);
        return out;
    }

    // ------------------------------------------------------------------ рисование

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int l = left();
        int t = top();
        g.fill(l - 4, t - 4, l + W + 4, t + H + 4, 0xFF5A3A28);
        g.fill(l, t, l + PAGE - 1, t + H, PAPER);
        g.fill(l + PAGE + 1, t, l + W, t + H, PAPER);
        g.fill(l + PAGE - 1, t, l + PAGE + 1, t + H, 0xFFB8AD96);
        if (spread == 0) renderTitle(g, l, t, mx, my);
        else renderHistory(g, l, t);
        int total = 1 + Math.max(1, pages.size());
        text(g, Component.literal((spread + 1) + " / " + total), l + PAGE, t + H - 13, FADED, true);
        super.render(g, mx, my, pt);
    }

    private void renderTitle(GuiGraphics g, int l, int t, int mx, int my) {
        text(g, Component.translatable("rpmedicine.medcard.form_title").withStyle(ChatFormatting.BOLD), l + PAGE / 2, t + 8, INK, true);
        text(g, Component.literal(card.fullName.isEmpty() ? card.name : card.fullName), l + PAGE / 2, t + 20, INK, true);
        g.fill(l + 10, t + 31, l + PAGE - 10, t + 32, 0xFFB8AD96);
        // Регистрационные данные.
        text(g, Component.translatable("rpmedicine.medcard.registration").withStyle(ChatFormatting.UNDERLINE), l + 10, t + 36, INK, false);
        field(g, "rpmedicine.medcard.number", card.cardNumber, l + 10, t + 48);
        field(g, "rpmedicine.medcard.opened", card.created > 0 ? DAY_SHORT.format(Instant.ofEpochMilli(card.created)) : "—", l + 10, t + 58);
        text(g, Component.translatable("rpmedicine.medcard.status"), l + 10, t + 68, FADED, false);
        drawPhoto(g, l + PAGE - 62, t + 36);
        // Сведения о пациенте.
        text(g, Component.translatable("rpmedicine.medcard.patient_info").withStyle(ChatFormatting.UNDERLINE), l + 10, t + 96, INK, false);
        String[] labels = {"full_name", "callsign", "service_date", "birth_date", "gender", "department"};
        for (int i = 0; i < labels.length; i++)
            text(g, Component.translatable("rpmedicine.medcard." + labels[i]), l + 10, t + 110 + i * 14, FADED, false);
        int vy = t + 194;
        field(g, "rpmedicine.medcard.blood", bloodLong(card.bloodType), l + 10, vy);
        field(g, "rpmedicine.medcard.height", String.valueOf(Math.round(card.height)), l + 10, vy + 11);
        field(g, "rpmedicine.medcard.weight", String.valueOf(Math.round(card.weight)), l + 110, vy + 11);
        if (mx >= l + 10 && mx < l + PAGE - 10 && my >= vy && my < vy + 22)
            g.renderTooltip(font, Component.translatable("rpmedicine.medcard.gm_only"), mx, my);

        // Медицинские сведения.
        int rx = l + PAGE + 10;
        text(g, Component.translatable("rpmedicine.medcard.medical_info").withStyle(ChatFormatting.BOLD), l + PAGE + PAGE / 2, t + 8, INK, true);
        String[] med = {"allergies", "chronic", "medications", "implants", "disability"};
        for (int i = 0; i < med.length; i++) text(g, Component.translatable("rpmedicine.medcard." + med[i]), rx, t + 22 + i * 24, FADED, false);
        text(g, Component.translatable("rpmedicine.medcard.marks").withStyle(ChatFormatting.UNDERLINE), rx, t + 153, INK, false);
        text(g, Component.translatable("rpmedicine.medcard.comment").withStyle(ChatFormatting.UNDERLINE), rx, t + 210, INK, false);
        text(g, Component.translatable("rpmedicine.medcard.confidential").withStyle(ChatFormatting.ITALIC), l + 10, t + H - 28, FADED, false);
    }

    private void renderHistory(GuiGraphics g, int l, int t) {
        int lx = l + 10;
        int rx = l + PAGE + 10;
        int halfW = (PAGE - 24) / 2;
        text(g, Component.translatable("rpmedicine.medcard.history").withStyle(ChatFormatting.BOLD), l + PAGE / 2, t + 8, INK, true);
        // Шапка таблицы.
        text(g, Component.translatable("rpmedicine.medcard.col_date").withStyle(ChatFormatting.UNDERLINE), lx, t + 18, INK, false);
        text(g, Component.translatable("rpmedicine.medcard.col_diag").withStyle(ChatFormatting.UNDERLINE), lx + COL_DATE + 4, t + 18, INK, false);
        text(g, Component.translatable("rpmedicine.medcard.col_circ").withStyle(ChatFormatting.UNDERLINE), rx, t + 18, INK, false);
        text(g, Component.translatable("rpmedicine.medcard.col_cons").withStyle(ChatFormatting.UNDERLINE), rx + halfW + 4, t + 18, INK, false);
        List<Row> rows = spread - 1 < pages.size() ? pages.get(spread - 1) : List.of();
        if (rows.isEmpty()) text(g, Component.translatable("rpmedicine.medcard.no_entries"), lx, t + TABLE_TOP, FADED, false);
        int y = t + TABLE_TOP;
        int cap = (TABLE_BOTTOM - TABLE_TOP) / LINE;
        for (Row r : rows) {
            int n = Math.min(r.lines(), cap);
            if (r.e().id == selected) {
                g.fill(lx - 2, y - 1, l + PAGE - 6, y + n * LINE, 0x40B08A40);
                g.fill(rx - 2, y - 1, l + W - 6, y + n * LINE, 0x40B08A40);
            }
            for (int i = 0; i < n; i++) {
                int yy = y + i * LINE;
                if (i < r.date().size()) g.drawString(font, r.date().get(i), lx, yy, INK, false);
                if (i < r.diag().size()) g.drawString(font, r.diag().get(i), lx + COL_DATE + 4, yy, INK, false);
                if (i < r.circ().size()) g.drawString(font, r.circ().get(i), rx, yy, INK, false);
                if (i < r.cons().size()) g.drawString(font, r.cons().get(i), rx + halfW + 4, yy, INK, false);
            }
            y += n * LINE;
            g.fill(lx, y + 3, l + PAGE - 8, y + 4, 0x40806040);
            g.fill(rx, y + 3, l + W - 8, y + 4, 0x40806040);
            y += LINE;
        }
        // Подписи полей ввода.
        int by = t + H - 56;
        Component what = editing >= 0 ? Component.translatable("rpmedicine.medcard.editing") : Component.translatable("rpmedicine.medcard.new_entry");
        text(g, what.copy().append(" ").append(Component.translatable("rpmedicine.medcard.col_diag")), lx, by + 2, editing >= 0 ? GOLD : FADED, false);
        text(g, Component.translatable("rpmedicine.medcard.col_circ").append(" / ").append(Component.translatable("rpmedicine.medcard.col_cons")),
                rx, by + 2, FADED, false);
    }

    private void text(GuiGraphics g, Component c, int x, int y, int color, boolean center) {
        g.drawString(font, c, center ? x - font.width(c) / 2 : x, y, color, false);
    }

    private void field(GuiGraphics g, String key, String value, int x, int y) {
        Component k = Component.translatable(key);
        g.drawString(font, k, x, y, FADED, false);
        g.drawString(font, value, x + font.width(k) + 4, y, INK, false);
    }

    /** Фото: персонаж, если он рядом (виден клиенту), в приглушённом «снимочном» цвете; иначе пустая рамка. */
    private void drawPhoto(GuiGraphics g, int x, int y) {
        int w = 52;
        int h = 62;
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFFFFFFFF);
        g.fill(x, y, x + w, y + h, 0xFFB9B6AE);
        Player p = minecraft.level != null ? minecraft.level.getPlayerByUUID(card.uuid) : null;
        if (p == null) {
            text(g, Component.translatable("rpmedicine.medcard.no_photo"), x + w / 2, y + h / 2 - 4, 0x5A5A5A, true);
            return;
        }
        g.enableScissor(x, y, x + w, y + h);
        RenderSystem.setShaderColor(0.62f, 0.6f, 0.56f, 1f);
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, x + w / 2, y + h + 28, 34, x + w / 2f, y + 8, p);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        g.disableScissor();
    }

    // ------------------------------------------------------------------ мышь

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (spread == 0 || spread - 1 >= pages.size()) return false;
        int l = left();
        int t = top();
        if (my < t + TABLE_TOP || my >= t + TABLE_BOTTOM || mx < l + 8 || mx >= l + W - 8) return false;
        int y = t + TABLE_TOP;
        int cap = (TABLE_BOTTOM - TABLE_TOP) / LINE;
        for (Row r : pages.get(spread - 1)) {
            int n = Math.min(r.lines(), cap);
            if (my >= y && my < y + n * LINE) {
                selected = selected == r.e().id ? -1 : r.e().id;
                editing = -1;
                rebuildWidgets();
                return true;
            }
            y += (n + 1) * LINE;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int total = 1 + Math.max(1, pages.size());
        int ns = Math.max(0, Math.min(total - 1, spread - (int) Math.signum(delta)));
        if (ns != spread) turn(ns - spread);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
