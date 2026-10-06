package faygolover.rpmedicine.client.screen;

import faygolover.rpmedicine.client.ClientState;
import faygolover.rpmedicine.client.MedicalHud;
import faygolover.rpmedicine.config.ClientConfig;
import faygolover.rpmedicine.config.ClientConfig.HudElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * Редактор HUD (п. 7.4 ТЗ), как в RP Stamina: перетаскивание мышью, колесо — масштаб, ПКМ — скрыть
 * или показать элемент, кнопка — якорь выбранного элемента.
 */
public class HudEditorScreen extends Screen {
    @Nullable
    private HudElement selected;
    @Nullable
    private HudElement dragging;
    private int grabX;
    private int grabY;
    private Button anchorButton;

    public HudEditorScreen() {
        super(Component.translatable("rpmedicine.hud.editor.title"));
    }

    @Override
    protected void init() {
        // Кнопки маленькие, в правом верхнем углу — не закрывают элементы HUD (замечание 25).
        int y = 4;
        int right = width - 4;
        anchorButton = addRenderableWidget(Button.builder(anchorLabel(), b -> {
            if (selected == null) return;
            ClientConfig.ElementConfig c = ClientConfig.hud(selected);
            c.anchor.set(c.anchor.get().next());
            c.offsetX.set(0);
            c.offsetY.set(0);
            b.setMessage(anchorLabel());
            save();
        }).bounds(right - 236, y, 120, 14).build());
        addRenderableWidget(Button.builder(Component.translatable("rpmedicine.hud.editor.reset"), b -> {
            for (HudElement e : HudElement.values()) ClientConfig.hud(e).reset(e);
            save();
        }).bounds(right - 112, y, 54, 14).build());
        addRenderableWidget(Button.builder(Component.translatable("rpmedicine.hud.editor.done"), b -> onClose())
                .bounds(right - 54, y, 54, 14).build());
    }

    private Component anchorLabel() {
        if (selected == null) return Component.translatable("rpmedicine.hud.editor.select");
        return Component.translatable("rpmedicine.hud.editor.anchor", ClientConfig.hud(selected).anchor.get().name());
    }

    private void save() {
        ClientConfig.SPEC.save();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("rpmedicine.hud.editor.hint"), width / 2, 24, 0xA0A0A0);
        for (HudElement e : HudElement.values()) {
            ClientConfig.ElementConfig c = ClientConfig.hud(e);
            int[] l = MedicalHud.layout(e, width, height);
            g.pose().pushPose();
            g.pose().translate(l[0], l[1], 0);
            float s = c.scale.get().floatValue();
            g.pose().scale(s, s, 1);
            MedicalHud.drawElement(g, font, e, ClientState.self, true);
            g.pose().popPose();
            int color = !c.visible.get() ? 0xFF803030 : e == selected ? 0xFFFFFFFF : 0xFF707070;
            g.renderOutline(l[0] - 2, l[1] - 2, l[2] + 4, l[3] + 4, color);
            g.drawString(font, Component.translatable("rpmedicine.hud.element." + e.id), l[0], l[1] - 11, color, true);
        }
        super.render(g, mx, my, pt);
    }

    @Nullable
    private HudElement at(double mx, double my) {
        for (HudElement e : HudElement.values()) {
            int[] l = MedicalHud.layout(e, width, height);
            if (mx >= l[0] - 3 && mx <= l[0] + l[2] + 3 && my >= l[1] - 3 && my <= l[1] + l[3] + 3) return e;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        HudElement e = at(mx, my);
        if (e == null) return false;
        selected = e;
        anchorButton.setMessage(anchorLabel());
        if (button == 1) {
            ClientConfig.ElementConfig c = ClientConfig.hud(e);
            c.visible.set(!c.visible.get());
            save();
            return true;
        }
        if (button == 0) {
            int[] l = MedicalHud.layout(e, width, height);
            dragging = e;
            grabX = (int) mx - l[0];
            grabY = (int) my - l[1];
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging != null) {
            setPosition(dragging, (int) mx - grabX, (int) my - grabY);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging != null) {
            dragging = null;
            save();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        HudElement e = at(mx, my);
        if (e == null) e = selected;
        if (e == null) return false;
        ClientConfig.ElementConfig c = ClientConfig.hud(e);
        c.scale.set(Mth.clamp(Math.round((c.scale.get() + (delta > 0 ? 0.1 : -0.1)) * 10) / 10.0, 0.25, 4.0));
        save();
        return true;
    }

    /** Абсолютная позиция левого верхнего угла → смещение от якоря. */
    private void setPosition(HudElement e, int x, int y) {
        ClientConfig.ElementConfig c = ClientConfig.hud(e);
        ClientConfig.Anchor a = c.anchor.get();
        int[] l = MedicalHud.layout(e, width, height);
        int baseX = Math.round(width * a.fx - l[2] * a.fx);
        int baseY = Math.round(height * a.fy - l[3] * a.fy);
        c.offsetX.set(Mth.clamp(x - baseX, -4000, 4000));
        c.offsetY.set(Mth.clamp(y - baseY, -4000, 4000));
    }

    @Override
    public void onClose() {
        save();
        super.onClose();
    }
}
