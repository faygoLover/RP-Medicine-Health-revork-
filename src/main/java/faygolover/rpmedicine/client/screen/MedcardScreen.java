package faygolover.rpmedicine.client.screen;

import faygolover.rpmedicine.medcard.Medcard;
import faygolover.rpmedicine.network.MedcardActionPacket;
import faygolover.rpmedicine.network.MedcardDataPacket;
import faygolover.rpmedicine.network.Network;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Экран медкарты (ТЗ второго этапа, п. 10): рост, вес, группа (меняет ГМ), аллергии и хронические
 * состояния, записи; предложения мода — принять, отклонить, изменить; своя запись.
 */
public class MedcardScreen extends Screen {
    private static final int W = 380;
    private static final int H = 240;
    private static final int ROWS = 8;
    private static final int ROW_H = 13;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    private MedcardDataPacket card;
    private int scroll;
    /** Какую запись правим (−1 — новая). */
    private int editing = -1;
    private EditBox allergies;
    private EditBox chronic;
    private EditBox entryText;

    public MedcardScreen(MedcardDataPacket card) {
        super(Component.translatable("rpmedicine.medcard.title"));
        this.card = card;
    }

    public UUID uuid() {
        return card.uuid();
    }

    public void update(MedcardDataPacket p) {
        this.card = p;
        rebuildWidgets();
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    /** Записи от новых к старым. */
    private List<Medcard.Entry> ordered() {
        List<Medcard.Entry> list = new ArrayList<>(card.entries());
        java.util.Collections.reverse(list);
        return list;
    }

    @Override
    protected void init() {
        int l = left();
        int t = top();
        String keepA = allergies != null ? allergies.getValue() : card.allergies();
        String keepC = chronic != null ? chronic.getValue() : card.chronic();
        String keepE = entryText != null ? entryText.getValue() : "";
        allergies = addRenderableWidget(new EditBox(font, l + 100, t + 40, W - 160, 14, Component.translatable("rpmedicine.medcard.allergies")));
        allergies.setMaxLength(300);
        allergies.setValue(keepA);
        addRenderableWidget(Button.builder(Component.translatable("rpmedicine.medcard.save"), b -> send(MedcardActionPacket.Op.ALLERGIES, -1, allergies.getValue()))
                .bounds(l + W - 56, t + 39, 50, 16).build());
        chronic = addRenderableWidget(new EditBox(font, l + 100, t + 58, W - 160, 14, Component.translatable("rpmedicine.medcard.chronic")));
        chronic.setMaxLength(300);
        chronic.setValue(keepC);
        addRenderableWidget(Button.builder(Component.translatable("rpmedicine.medcard.save"), b -> send(MedcardActionPacket.Op.CHRONIC, -1, chronic.getValue()))
                .bounds(l + W - 56, t + 57, 50, 16).build());

        // Кнопки у видимых записей.
        List<Medcard.Entry> list = ordered();
        for (int i = 0; i < ROWS && scroll + i < list.size(); i++) {
            Medcard.Entry e = list.get(scroll + i);
            int y = t + 92 + i * ROW_H;
            int x = l + W - 8;
            x -= 14;
            int id = e.id;
            addRenderableWidget(Button.builder(Component.literal("✎"), b -> {
                editing = id;
                entryText.setValue(e.text.isEmpty() ? entryComponent(e).getString() : e.text);
            }).bounds(x, y - 1, 13, 12).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("rpmedicine.medcard.edit"))).build());
            if (e.proposed) {
                x -= 14;
                addRenderableWidget(Button.builder(Component.literal("✗"), b -> send(MedcardActionPacket.Op.DECLINE, id, ""))
                        .bounds(x, y - 1, 13, 12).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("rpmedicine.medcard.decline"))).build());
                x -= 14;
                addRenderableWidget(Button.builder(Component.literal("✓"), b -> send(MedcardActionPacket.Op.ACCEPT, id, ""))
                        .bounds(x, y - 1, 13, 12).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("rpmedicine.medcard.accept"))).build());
            }
        }

        entryText = addRenderableWidget(new EditBox(font, l + 8, t + H - 24, W - 120, 16, Component.translatable("rpmedicine.medcard.new_entry")));
        entryText.setMaxLength(300);
        entryText.setValue(keepE);
        addRenderableWidget(Button.builder(Component.translatable(editing >= 0 ? "rpmedicine.medcard.save" : "rpmedicine.medcard.add"), b -> {
            if (editing >= 0) send(MedcardActionPacket.Op.EDIT, editing, entryText.getValue());
            else send(MedcardActionPacket.Op.ADD, -1, entryText.getValue());
            editing = -1;
            entryText.setValue("");
        }).bounds(l + W - 108, t + H - 25, 100, 18).build());
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
        g.fill(l, t, l + W, t + H, 0xF0EDE6D6);
        g.renderOutline(l, t, W, H, 0xFF8A7F6A);
        g.drawCenteredString(font, Component.translatable("rpmedicine.medcard.title_of", card.name()), width / 2, t + 6, 0x203040);
        String blood = card.bloodType().isEmpty() ? "?" : faygolover.rpmedicine.core.BloodType.byId(card.bloodType()).map(b -> b.label).orElse("?");
        g.drawString(font, Component.translatable("rpmedicine.medcard.vitals", Math.round(card.height()), Math.round(card.weight()), blood),
                l + 8, t + 22, 0x303030, false);
        g.drawString(font, Component.translatable("rpmedicine.medcard.allergies"), l + 8, t + 43, 0x303030, false);
        g.drawString(font, Component.translatable("rpmedicine.medcard.chronic"), l + 8, t + 61, 0x303030, false);
        g.drawString(font, Component.translatable("rpmedicine.medcard.entries").withStyle(ChatFormatting.UNDERLINE), l + 8, t + 79, 0x303030, false);

        List<Medcard.Entry> list = ordered();
        if (list.isEmpty()) g.drawString(font, Component.translatable("rpmedicine.medcard.no_entries"), l + 8, t + 92, 0x707070, false);
        for (int i = 0; i < ROWS && scroll + i < list.size(); i++) {
            Medcard.Entry e = list.get(scroll + i);
            int y = t + 92 + i * ROW_H;
            int color = e.proposed ? 0x8A6A00 : 0x202020;
            String date = DATE.format(Instant.ofEpochMilli(e.time));
            MutableComponent line = Component.literal(date + "  ").withStyle(ChatFormatting.DARK_GRAY).append(entryComponent(e).withStyle(s -> s.withColor(color)));
            if (!e.author.isEmpty()) line.append(Component.literal(" — " + e.author).withStyle(ChatFormatting.GRAY));
            if (e.proposed) line.append(Component.translatable("rpmedicine.medcard.proposed").withStyle(ChatFormatting.GOLD));
            // Длинная строка обрезается, целиком — во всплывающей подсказке.
            var split = font.split(line, W - 60);
            if (!split.isEmpty()) g.drawString(font, split.get(0), l + 8, y, 0xFFFFFF, false);
            if (split.size() > 1 && mx >= l + 8 && mx < l + W - 52 && my >= y && my < y + ROW_H) g.renderTooltip(font, font.split(line, 300), mx, my);
        }
        if (list.size() > ROWS)
            g.drawString(font, (scroll + 1) + "–" + Math.min(list.size(), scroll + ROWS) + " / " + list.size(), l + 8, t + 92 + ROWS * ROW_H, 0x707070, false);
        if (editing >= 0) g.drawString(font, Component.translatable("rpmedicine.medcard.editing"), l + 8, t + H - 34, 0x8A6A00, false);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int max = Math.max(0, card.entries().size() - ROWS);
        int ns = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
        if (ns != scroll) {
            scroll = ns;
            rebuildWidgets();
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
