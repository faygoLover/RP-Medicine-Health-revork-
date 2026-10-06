package faygolover.rpmedicine.client.screen;

import faygolover.rpmedicine.client.ClientState;
import faygolover.rpmedicine.client.ExamText;
import faygolover.rpmedicine.client.Silhouette;
import faygolover.rpmedicine.core.BodyPart;
import faygolover.rpmedicine.core.Examination;
import faygolover.rpmedicine.menu.MedicalContainerMenu;
import faygolover.rpmedicine.network.ExamResultPacket;
import faygolover.rpmedicine.network.Network;
import faygolover.rpmedicine.network.PanelActionPacket;
import faygolover.rpmedicine.network.RequestExamPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Панель осмотра (п. 7.1 ТЗ): силуэт из девяти частей и полоски их состояния (цифры — с уровня
 * медицины 4 или у ГМа), общая полоса, таймер нокдауна. Клик по части или её полоске — что на ней
 * видно; сбоку общие признаки; снизу медицинские предметы для перетаскивания. Сообщения сервера
 * («нужна пустая рука» и т.п.) показываются прямо в панели.
 */
public class MedicalPanelScreen extends Screen {
    private static final int W = 410;
    private static final int H = 236;
    private static final int UNIT = 5;
    private final int targetId;
    @Nullable
    private ExamResultPacket exam;
    @Nullable
    private BodyPart selected;
    /** Перетаскиваемый слот инвентаря (−1 — ничего). */
    private int dragSlot = -1;
    private final List<Button> removeButtons = new ArrayList<>();
    private Button searchButton;
    private Button dripButton;
    private Button tubeButton;
    private Button airwayButton;
    private Button restrainButton;
    /** Панель уже открывалась и отписалась от осмотра (вернулись после мини-игры) — подписаться снова. */
    private boolean unsubscribed;

    public MedicalPanelScreen(int targetId) {
        super(Component.translatable("rpmedicine.panel.title"));
        this.targetId = targetId;
        this.exam = null;
    }

    public void onExam(ExamResultPacket p) {
        if ((targetId < 0 && p.self()) || p.targetId() == targetId) {
            this.exam = p;
            updateButtons();
        }
    }

    private int left() {
        return width / 2 - W / 2;
    }

    private int top() {
        return height / 2 - H / 2;
    }

    private int silX() {
        return left() + 10;
    }

    private int silY() {
        return top() + 26;
    }

    /** Колонка полосок частей тела. */
    private int barsX() {
        return left() + 96;
    }

    private int barsY() {
        return top() + 26;
    }

    private static final int BAR_ROW = 15;

    /** Порядок частей в колонке полосок: сверху вниз. */
    private static final BodyPart[] ORDER = {BodyPart.HEAD, BodyPart.CHEST, BodyPart.ABDOMEN, BodyPart.RIGHT_ARM, BodyPart.LEFT_ARM,
            BodyPart.RIGHT_LEG, BodyPart.LEFT_LEG, BodyPart.RIGHT_FOOT, BodyPart.LEFT_FOOT};

    private int detailX() {
        return left() + 186;
    }

    private int generalX() {
        return left() + 302;
    }

    @Override
    protected void init() {
        if (unsubscribed) {
            Network.sendToServer(new RequestExamPacket(targetId, true));
            unsubscribed = false;
        }
        removeButtons.clear();
        int x = detailX();
        int y = top() + 140;
        String[] keys = {"remove_dressing", "remove_tourniquet", "remove_splint", "remove_occlusive", "reduce", "remove_prosthesis"};
        PanelActionPacket.Kind[] kinds = {PanelActionPacket.Kind.REMOVE_DRESSING, PanelActionPacket.Kind.REMOVE_TOURNIQUET,
                PanelActionPacket.Kind.REMOVE_SPLINT, PanelActionPacket.Kind.REMOVE_OCCLUSIVE, PanelActionPacket.Kind.REDUCE,
                PanelActionPacket.Kind.REMOVE_PROSTHESIS};
        for (int i = 0; i < kinds.length; i++) {
            PanelActionPacket.Kind kind = kinds[i];
            Button b = Button.builder(Component.translatable("rpmedicine.panel." + keys[i]), btn -> {
                if (selected != null) Network.sendToServer(new PanelActionPacket(targetId, kind, selected, -1));
            }).bounds(x + (i % 2) * 108, y + (i / 2) * 18, 106, 16).build();
            removeButtons.add(addRenderableWidget(b));
        }
        searchButton = addRenderableWidget(Button.builder(Component.translatable("rpmedicine.panel.search"), btn -> {
            Network.sendToServer(new PanelActionPacket(targetId, PanelActionPacket.Kind.SEARCH, BodyPart.CHEST, -1));
            onClose();
        }).bounds(left() + 8, top() + H - 24, 80, 16).build());
        dripButton = addRenderableWidget(Button.builder(Component.translatable("rpmedicine.panel.stop_drip"), btn ->
                Network.sendToServer(new PanelActionPacket(targetId, PanelActionPacket.Kind.STOP_DRIP, BodyPart.CHEST, -1)))
                .bounds(generalX(), top() + 104, 100, 16).build());
        tubeButton = addRenderableWidget(Button.builder(Component.translatable("rpmedicine.panel.remove_tube"), btn ->
                Network.sendToServer(new PanelActionPacket(targetId, PanelActionPacket.Kind.REMOVE_TUBE, BodyPart.HEAD, -1)))
                .bounds(generalX(), top() + 122, 100, 16).build());
        airwayButton = addRenderableWidget(Button.builder(Component.translatable("rpmedicine.panel.remove_airway"), btn ->
                Network.sendToServer(new PanelActionPacket(targetId, PanelActionPacket.Kind.REMOVE_AIRWAY, BodyPart.HEAD, -1)))
                .bounds(generalX(), top() + 140, 100, 16).build());
        restrainButton = addRenderableWidget(Button.builder(Component.translatable("rpmedicine.panel.restrain"), btn ->
                Network.sendToServer(new PanelActionPacket(targetId, PanelActionPacket.Kind.RESTRAIN, BodyPart.CHEST, -1)))
                .bounds(generalX(), top() + 158, 100, 16).build());
        if (ClientState.exam != null) onExam(ClientState.exam);
        updateButtons();
    }

    private void updateButtons() {
        int bits = exam != null && selected != null ? exam.removable()[selected.ordinal()] : 0;
        for (int i = 0; i < removeButtons.size(); i++) removeButtons.get(i).visible = (bits & (1 << i)) != 0;
        if (searchButton != null) searchButton.visible = exam != null && !exam.self() && exam.downState() != 0;
        if (dripButton != null) {
            boolean drip = exam != null && exam.view().general().stream()
                    .anyMatch(l -> l.key().equals("drip_blood") || l.key().equals("drip_saline"));
            boolean cath = exam != null && exam.view().general().stream().anyMatch(l -> l.key().startsWith("catheter_"));
            // Без капельницы та же кнопка вынимает катетер.
            dripButton.visible = drip || cath;
            dripButton.setMessage(Component.translatable(drip ? "rpmedicine.panel.stop_drip" : "rpmedicine.panel.remove_catheter"));
        }
        if (tubeButton != null) tubeButton.visible = exam != null && (exam.general() & 1) != 0;
        if (airwayButton != null) airwayButton.visible = exam != null && (exam.general() & 2) != 0;
        if (restrainButton != null) {
            restrainButton.visible = exam != null && (exam.general() & 12) != 0;
            restrainButton.setMessage(Component.translatable(exam != null && (exam.general() & 8) != 0 ? "rpmedicine.panel.release" : "rpmedicine.panel.restrain"));
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int l = left();
        int t = top();
        g.fill(l, t, l + W, t + H, 0xE0101418);
        g.renderOutline(l, t, W, H, 0xFF505860);
        Component name = exam != null ? exam.name() : Component.literal("…");
        g.drawString(font, Component.translatable(exam != null && exam.self() ? "rpmedicine.panel.self" : "rpmedicine.panel.other", name), l + 8, t + 8, 0xFFFFFF);
        // Таймер нокдауна — крупно справа сверху.
        if (exam != null && exam.knockdownSeconds() >= 0) {
            Component kd = Component.translatable("rpmedicine.hud.knockdown", ExamText.time(exam.knockdownSeconds())).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
            g.drawString(font, kd, l + W - 8 - font.width(kd), t + 8, 0xFFFFFF);
        }

        byte[] colors = new byte[9];
        if (exam != null) for (Examination.PartView pv : exam.view().parts()) colors[pv.part().ordinal()] = (byte) pv.color();
        BodyPart hover = partAt(mx, my);
        Silhouette.draw(g, silX(), silY(), UNIT, colors, selected, hover);
        drawBars(g, colors, hover);

        // Часть тела: что видно.
        int x = detailX();
        int y = t + 26;
        int dw = generalX() - detailX() - 6;
        if (exam == null) {
            g.drawString(font, Component.translatable("rpmedicine.panel.loading"), x, y, 0xAAAAAA);
        } else if (selected == null) {
            for (FormattedCharSequence seq : font.split(Component.translatable("rpmedicine.panel.pick_part").withStyle(ChatFormatting.GRAY), dw)) {
                g.drawString(font, seq, x, y, 0xFFFFFF);
                y += 10;
            }
        } else {
            g.drawString(font, Component.translatable(selected.translationKey()).withStyle(ChatFormatting.UNDERLINE), x, y, 0xFFFFFF);
            y += 12;
            List<Examination.Line> lines = exam.view().parts().get(selected.ordinal()).lines();
            if (lines.isEmpty()) g.drawString(font, Component.translatable("rpmedicine.exam.nothing_visible").withStyle(ChatFormatting.GRAY), x, y, 0xFFFFFF);
            for (Examination.Line line : lines) {
                for (FormattedCharSequence seq : font.split(ExamText.format(line).withStyle(ExamText.color(line)), dw)) {
                    if (y > t + 130) break;
                    g.drawString(font, seq, x, y, 0xFFFFFF);
                    y += 10;
                }
            }
        }
        // Общие признаки.
        int gx = generalX();
        int gy = t + 26;
        g.drawString(font, Component.translatable("rpmedicine.panel.general").withStyle(ChatFormatting.UNDERLINE), gx, gy, 0xFFFFFF);
        gy += 12;
        if (exam != null) {
            for (Examination.Line line : exam.view().general()) {
                for (FormattedCharSequence seq : font.split(ExamText.format(line).withStyle(ExamText.color(line)), W - (gx - l) - 6)) {
                    if (gy > t + 98) break;
                    g.drawString(font, seq, gx, gy, 0xFFFFFF);
                    gy += 10;
                }
            }
        }
        // Кнопки общего раздела — сразу под текстом, друг под другом (не наезжают на строки — замечание 17).
        int by = Math.max(gy + 4, t + 60);
        int bw = Math.min(140, W - (gx - l) - 8);
        for (Button b : new Button[]{dripButton, tubeButton, airwayButton, restrainButton}) {
            if (b == null || !b.visible) continue;
            b.setX(gx);
            b.setY(by);
            b.setWidth(bw);
            by += 18;
        }
        // Медицинские предметы для перетаскивания.
        drawItems(g, mx, my);
        // Сообщение сервера — внутри панели (над панелью быстрого доступа его закрывает окно).
        Component msg = ClientState.recentOverlay();
        if (msg != null) {
            int mw = font.width(msg);
            g.fill(l + W / 2 - mw / 2 - 4, t + H - 46, l + W / 2 + mw / 2 + 4, t + H - 34, 0xC0000000);
            g.drawCenteredString(font, msg, l + W / 2, t + H - 44, 0xFFFF55);
        }
        // Прогресс действия поверх панели (HUD рисуется под экраном).
        if (ClientState.progressActive() || ClientState.incomingActive()) {
            g.pose().pushPose();
            g.pose().translate(generalX(), Math.max(t + 162, by + 2), 0);
            faygolover.rpmedicine.client.MedicalHud.drawElement(g, font, faygolover.rpmedicine.config.ClientConfig.HudElement.PROGRESS, ClientState.self, false);
            g.pose().popPose();
        }
        super.render(g, mx, my, pt);
        if (dragSlot >= 0) {
            ItemStack s = dragStack();
            g.renderItem(s, mx - 8, my - 8);
        } else {
            int slot = itemAt(mx, my);
            if (slot >= 0) g.renderTooltip(font, minecraft.player.getInventory().getItem(slot), mx, my);
        }
    }

    /** Колонка полосок: название части, полоса цветом её состояния, цифры — если положено. */
    private void drawBars(GuiGraphics g, byte[] colors, @Nullable BodyPart hover) {
        int x = barsX();
        int y = barsY();
        boolean numbers = exam != null && exam.numbers();
        for (int i = 0; i < ORDER.length; i++) {
            BodyPart p = ORDER[i];
            int ry = y + i * BAR_ROW;
            int value = exam != null ? exam.bars()[p.ordinal()] : 100;
            int c = colors[p.ordinal()];
            boolean sel = p == selected;
            if (sel || p == hover) g.fill(x - 2, ry - 2, x + 86, ry + BAR_ROW - 2, sel ? 0x40FFFFFF : 0x20FFFFFF);
            g.pose().pushPose();
            g.pose().scale(0.75f, 0.75f, 1f);
            g.drawString(font, Component.translatable(p.translationKey()), (int) (x / 0.75f), (int) (ry / 0.75f), c == 5 ? 0x888888 : 0xDDDDDD, false);
            g.pose().popPose();
            int bx = x;
            int by = ry + 7;
            int bw = numbers ? 58 : 82;
            g.fill(bx, by, bx + bw, by + 4, 0xFF202428);
            g.fill(bx, by, bx + Math.max(0, Math.min(bw, bw * value / 100)), by + 4, Silhouette.color(c, false));
            if (numbers) {
                String n = String.valueOf(value);
                g.pose().pushPose();
                g.pose().scale(0.75f, 0.75f, 1f);
                g.drawString(font, n, (int) ((bx + bw + 3) / 0.75f), (int) ((by - 2) / 0.75f), c == 5 ? 0x888888 : 0xFFFFFF, false);
                g.pose().popPose();
            }
        }
        // Общий запас под силуэтом.
        int ox = silX();
        int oy = silY() + Silhouette.GRID_H * UNIT + 6;
        int overall = exam != null ? exam.overall() : 100;
        int oc = overall >= 80 ? 0 : overall >= 60 ? 1 : overall >= 40 ? 2 : overall >= 20 ? 3 : 4;
        g.drawString(font, Component.translatable("rpmedicine.panel.overall").withStyle(ChatFormatting.GRAY), ox, oy, 0xFFFFFF);
        g.fill(ox, oy + 10, ox + 80, oy + 15, 0xFF202428);
        g.fill(ox, oy + 10, ox + 80 * overall / 100, oy + 15, Silhouette.color(oc, false));
        if (numbers) g.drawString(font, overall + "%", ox + 82, oy + 9, 0xFFFFFF, false);
    }

    @Nullable
    private BodyPart partAt(double mx, double my) {
        BodyPart p = Silhouette.partAt(silX(), silY(), UNIT, mx, my);
        if (p != null) return p;
        int x = barsX();
        int y = barsY();
        if (mx >= x - 2 && mx < x + 86 && my >= y - 2 && my < y + ORDER.length * BAR_ROW - 2) {
            int i = (int) ((my - y + 2) / BAR_ROW);
            if (i >= 0 && i < ORDER.length) return ORDER[i];
        }
        return null;
    }

    /**
     * Медпредмет в панели: одна ячейка на одинаковые предметы с общим количеством (замечание 46),
     * включая содержимое аптечек и подсумков (замечание 47). {@code ref} — слот инвентаря или
     * {@link #CONTAINER_REF} + слот_контейнера × 100 + ячейка внутри.
     */
    private record PanelItem(ItemStack stack, int count, int ref) {}

    public static final int CONTAINER_REF = 10000;

    private List<PanelItem> medicalItems() {
        List<PanelItem> out = new ArrayList<>();
        Inventory inv = minecraft.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            if (s.getItem() instanceof faygolover.rpmedicine.item.MedicalContainerItem) {
                var tag = s.getTag();
                if (tag != null && tag.contains("Inventory")) {
                    var items = tag.getCompound("Inventory").getList("Items", net.minecraft.nbt.Tag.TAG_COMPOUND);
                    for (int k = 0; k < items.size(); k++) {
                        var it = items.getCompound(k);
                        ItemStack inner = ItemStack.of(it);
                        if (!inner.isEmpty()) add(out, inner, CONTAINER_REF + i * 100 + it.getInt("Slot"));
                    }
                }
                continue;
            }
            if (s.is(MedicalContainerMenu.MEDICAL_ITEMS)) add(out, s, i);
        }
        return out;
    }

    private static void add(List<PanelItem> out, ItemStack s, int ref) {
        for (int k = 0; k < out.size(); k++) {
            PanelItem e = out.get(k);
            if (ItemStack.isSameItemSameTags(e.stack(), s)) {
                // Предпочитаем слот инвентаря: из него быстрее.
                int r = e.ref() < CONTAINER_REF ? e.ref() : ref;
                out.set(k, new PanelItem(e.stack(), e.count() + s.getCount(), r));
                return;
            }
        }
        out.add(new PanelItem(s.copy(), s.getCount(), ref));
    }

    private int itemsX() {
        return left() + 96;
    }

    private int itemsY() {
        return top() + H - 22;
    }

    private static final int MAX_ITEMS = 15;
    /** Прокрутка ряда предметов колёсиком (замечание 45). */
    private int itemScroll;

    private void drawItems(GuiGraphics g, int mx, int my) {
        List<PanelItem> items = medicalItems();
        itemScroll = Math.max(0, Math.min(itemScroll, items.size() - MAX_ITEMS));
        int x = itemsX();
        int y = itemsY() - 10;
        g.drawString(font, Component.translatable("rpmedicine.panel.drag_hint").withStyle(ChatFormatting.DARK_GRAY), x, y, 0xFFFFFF);
        for (int i = 0; i < MAX_ITEMS && i + itemScroll < items.size(); i++) {
            PanelItem it = items.get(i + itemScroll);
            int sx = x + i * 19;
            g.fill(sx - 1, itemsY() - 1, sx + 17, itemsY() + 17, it.ref() >= CONTAINER_REF ? 0x80403828 : 0x80303840);
            if (it.ref() != dragSlot) {
                g.renderItem(it.stack(), sx, itemsY());
                g.renderItemDecorations(font, it.stack(), sx, itemsY(), it.count() > 1 ? String.valueOf(it.count()) : null);
            }
        }
        // Стрелки прокрутки.
        if (itemScroll > 0) g.drawString(font, "◀", x - 9, itemsY() + 4, 0xC0C0C0);
        if (itemScroll + MAX_ITEMS < items.size()) g.drawString(font, "▶", x + MAX_ITEMS * 19, itemsY() + 4, 0xC0C0C0);
    }

    private int itemAt(double mx, double my) {
        List<PanelItem> items = medicalItems();
        for (int i = 0; i < MAX_ITEMS && i + itemScroll < items.size(); i++) {
            int sx = itemsX() + i * 19;
            if (mx >= sx && mx < sx + 16 && my >= itemsY() && my < itemsY() + 16) return items.get(i + itemScroll).ref();
        }
        return -1;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (my >= itemsY() - 12 && my < itemsY() + 18) {
            itemScroll = Math.max(0, itemScroll - (int) Math.signum(delta));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    /** Предмет под курсором при перетаскивании (для иконки у мыши). */
    private ItemStack dragStack() {
        for (PanelItem it : medicalItems()) if (it.ref() == dragSlot) return it.stack();
        return ItemStack.EMPTY;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button == 0) {
            BodyPart p = partAt(mx, my);
            if (p != null) {
                selected = p;
                updateButtons();
                return true;
            }
            int slot = itemAt(mx, my);
            if (slot >= 0) {
                dragSlot = slot;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragSlot >= 0 && button == 0) {
            BodyPart p = partAt(mx, my);
            if (p != null) {
                Network.sendToServer(new PanelActionPacket(targetId, PanelActionPacket.Kind.APPLY_ITEM, p, dragSlot));
                selected = p;
                updateButtons();
            }
            dragSlot = -1;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public void removed() {
        Network.sendToServer(new RequestExamPacket(targetId, false));
        unsubscribed = true;
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
