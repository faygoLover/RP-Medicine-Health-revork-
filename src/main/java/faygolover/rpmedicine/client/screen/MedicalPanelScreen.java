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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Панель осмотра (п. 7.1 ТЗ): силуэт из девяти частей с цветом по состоянию, клик по части — что на
 * ней видно (детализация по уровню медицины), сбоку общие признаки, снизу медицинские предметы для
 * перетаскивания на часть тела. Цифр нет.
 */
public class MedicalPanelScreen extends Screen {
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
        return width / 2 - 170;
    }

    private int top() {
        return height / 2 - 105;
    }

    private int silX() {
        return left() + 10;
    }

    private int silY() {
        return top() + 24;
    }

    @Override
    protected void init() {
        removeButtons.clear();
        int x = left() + 100;
        int y = top() + 150;
        String[] keys = {"dressing", "tourniquet", "splint", "occlusive"};
        PanelActionPacket.Kind[] kinds = {PanelActionPacket.Kind.REMOVE_DRESSING, PanelActionPacket.Kind.REMOVE_TOURNIQUET,
                PanelActionPacket.Kind.REMOVE_SPLINT, PanelActionPacket.Kind.REMOVE_OCCLUSIVE};
        for (int i = 0; i < 4; i++) {
            PanelActionPacket.Kind kind = kinds[i];
            Button b = Button.builder(Component.translatable("rpmedicine.panel.remove_" + keys[i]), btn -> {
                if (selected != null) Network.sendToServer(new PanelActionPacket(targetId, kind, selected, -1));
            }).bounds(x + (i % 2) * 112, y + (i / 2) * 22, 110, 20).build();
            removeButtons.add(addRenderableWidget(b));
        }
        searchButton = addRenderableWidget(Button.builder(Component.translatable("rpmedicine.panel.search"), btn -> {
            Network.sendToServer(new PanelActionPacket(targetId, PanelActionPacket.Kind.SEARCH, BodyPart.CHEST, -1));
            onClose();
        }).bounds(left() + 10, top() + 190, 80, 20).build());
        if (ClientState.exam != null) onExam(ClientState.exam);
        updateButtons();
    }

    private void updateButtons() {
        int bits = exam != null && selected != null ? exam.removable()[selected.ordinal()] : 0;
        for (int i = 0; i < removeButtons.size(); i++) {
            Button b = removeButtons.get(i);
            b.visible = (bits & (1 << i)) != 0;
        }
        if (searchButton != null) searchButton.visible = exam != null && !exam.self() && exam.downState() != 0;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int l = left();
        int t = top();
        g.fill(l, t, l + 340, t + 215, 0xE0101418);
        g.renderOutline(l, t, 340, 215, 0xFF505860);
        Component name = exam != null ? exam.name() : Component.literal("…");
        g.drawString(font, Component.translatable(exam != null && exam.self() ? "rpmedicine.panel.self" : "rpmedicine.panel.other", name), l + 8, t + 8, 0xFFFFFF);

        byte[] colors = new byte[9];
        if (exam != null) for (Examination.PartView pv : exam.view().parts()) colors[pv.part().ordinal()] = (byte) pv.color();
        BodyPart hover = Silhouette.partAt(silX(), silY(), UNIT, mx, my);
        Silhouette.draw(g, silX(), silY(), UNIT, colors, selected, hover);

        // Часть тела: что видно.
        int x = l + 100;
        int y = t + 24;
        if (exam == null) {
            g.drawString(font, Component.translatable("rpmedicine.panel.loading"), x, y, 0xAAAAAA);
        } else if (selected == null) {
            g.drawString(font, Component.translatable("rpmedicine.panel.pick_part").withStyle(ChatFormatting.GRAY), x, y, 0xFFFFFF);
        } else {
            g.drawString(font, Component.translatable(selected.translationKey()).withStyle(ChatFormatting.UNDERLINE), x, y, 0xFFFFFF);
            y += 12;
            List<Examination.Line> lines = exam.view().parts().get(selected.ordinal()).lines();
            if (lines.isEmpty()) g.drawString(font, Component.translatable("rpmedicine.exam.nothing_visible").withStyle(ChatFormatting.GRAY), x, y, 0xFFFFFF);
            for (Examination.Line line : lines) {
                if (y > t + 140) break;
                g.drawString(font, ExamText.format(line).withStyle(ExamText.color(line)), x, y, 0xFFFFFF);
                y += 10;
            }
        }
        // Общие признаки.
        int gx = l + 225;
        int gy = t + 24;
        g.drawString(font, Component.translatable("rpmedicine.panel.general").withStyle(ChatFormatting.UNDERLINE), gx, gy, 0xFFFFFF);
        gy += 12;
        if (exam != null) {
            for (Examination.Line line : exam.view().general()) {
                if (gy > t + 140) break;
                for (var seq : font.split(ExamText.format(line).withStyle(ExamText.color(line)), 110)) {
                    g.drawString(font, seq, gx, gy, 0xFFFFFF);
                    gy += 10;
                }
            }
        }
        // Медицинские предметы для перетаскивания.
        drawItems(g, mx, my);
        // Прогресс действия поверх панели (HUD рисуется под экраном).
        if (ClientState.progressActive()) {
            g.pose().pushPose();
            g.pose().translate(l + 225, t + 160, 0);
            faygolover.rpmedicine.client.MedicalHud.drawElement(g, font, faygolover.rpmedicine.config.ClientConfig.HudElement.PROGRESS, ClientState.self, false);
            g.pose().popPose();
        }
        super.render(g, mx, my, pt);
        if (dragSlot >= 0) {
            ItemStack s = minecraft.player.getInventory().getItem(dragSlot);
            g.renderItem(s, mx - 8, my - 8);
        } else {
            int slot = itemAt(mx, my);
            if (slot >= 0) g.renderTooltip(font, minecraft.player.getInventory().getItem(slot), mx, my);
        }
    }

    private List<Integer> medicalSlots() {
        List<Integer> out = new ArrayList<>();
        Inventory inv = minecraft.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.is(MedicalContainerMenu.MEDICAL_ITEMS)) out.add(i);
        }
        return out;
    }

    private int itemsX() {
        return left() + 100;
    }

    private int itemsY() {
        return top() + 196;
    }

    private void drawItems(GuiGraphics g, int mx, int my) {
        List<Integer> slots = medicalSlots();
        int x = itemsX();
        int y = itemsY() - 12;
        g.drawString(font, Component.translatable("rpmedicine.panel.drag_hint").withStyle(ChatFormatting.DARK_GRAY), x, y, 0xFFFFFF);
        for (int i = 0; i < slots.size() && i < 12; i++) {
            int sx = x + i * 19;
            g.fill(sx - 1, itemsY() - 1, sx + 17, itemsY() + 17, 0x80303840);
            ItemStack s = minecraft.player.getInventory().getItem(slots.get(i));
            if (slots.get(i) != dragSlot) {
                g.renderItem(s, sx, itemsY());
                g.renderItemDecorations(font, s, sx, itemsY());
            }
        }
    }

    private int itemAt(double mx, double my) {
        List<Integer> slots = medicalSlots();
        for (int i = 0; i < slots.size() && i < 12; i++) {
            int sx = itemsX() + i * 19;
            if (mx >= sx && mx < sx + 16 && my >= itemsY() && my < itemsY() + 16) return slots.get(i);
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button == 0) {
            BodyPart p = Silhouette.partAt(silX(), silY(), UNIT, mx, my);
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
            BodyPart p = Silhouette.partAt(silX(), silY(), UNIT, mx, my);
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
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
