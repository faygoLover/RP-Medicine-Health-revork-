package faygolover.rpstamina.client;

import faygolover.rpstamina.ClientConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Редактор положения полоски: перетаскивание мышью, колесо — ширина, Shift + колесо — высота. */
public class HudEditorScreen extends Screen {
    private boolean dragging;
    private int grabX;
    private int grabY;
    private Button anchorButton;

    public HudEditorScreen() {
        super(Component.translatable("rpstamina.hud.editor.title"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = this.height / 2 - 30;

        anchorButton = addRenderableWidget(Button.builder(anchorLabel(), b -> {
            ClientConfig.Anchor next = ClientConfig.ANCHOR.get().next();
            ClientConfig.ANCHOR.set(next);
            ClientConfig.OFFSET_X.set(0);
            ClientConfig.OFFSET_Y.set(0);
            b.setMessage(anchorLabel());
            save();
        }).bounds(cx - 100, y, 200, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("rpstamina.hud.editor.reset"), b -> {
            ClientConfig.ANCHOR.set(ClientConfig.Anchor.BOTTOM_CENTER);
            ClientConfig.OFFSET_X.set(ClientConfig.DEFAULT_OFFSET_X);
            ClientConfig.OFFSET_Y.set(ClientConfig.DEFAULT_OFFSET_Y);
            ClientConfig.WIDTH.set(ClientConfig.DEFAULT_WIDTH);
            ClientConfig.HEIGHT.set(ClientConfig.DEFAULT_HEIGHT);
            if (anchorButton != null) anchorButton.setMessage(anchorLabel());
            save();
        }).bounds(cx - 100, y + 24, 98, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("rpstamina.hud.editor.done"), b -> onClose())
                .bounds(cx + 2, y + 24, 98, 20).build());
    }

    private Component anchorLabel() {
        return Component.translatable("rpstamina.hud.editor.anchor", ClientConfig.ANCHOR.get().name());
    }

    private void save() {
        ClientConfig.OFFSET_X.save();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);
        g.drawCenteredString(this.font, Component.translatable("rpstamina.hud.editor.hint"), this.width / 2, 30, 0xA0A0A0);

        int[] l = StaminaHud.layout(this.width, this.height);
        StaminaHud.drawBar(g, l[0], l[1], l[2], l[3], 0.7f, false, 1f);
        boolean hover = inBar(mouseX, mouseY, l);
        if (hover || dragging) g.renderOutline(l[0] - 3, l[1] - 3, l[2] + 6, l[3] + 6, 0xFFFFFFFF);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private static boolean inBar(double mx, double my, int[] l) {
        return mx >= l[0] - 4 && mx <= l[0] + l[2] + 4 && my >= l[1] - 4 && my <= l[1] + l[3] + 4;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button == 0) {
            int[] l = StaminaHud.layout(this.width, this.height);
            if (inBar(mx, my, l)) {
                dragging = true;
                grabX = (int) mx - l[0];
                grabY = (int) my - l[1];
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            setPosition((int) mx - grabX, (int) my - grabY);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging) {
            dragging = false;
            save();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int step = delta > 0 ? 1 : -1;
        if (hasShiftDown()) {
            ClientConfig.HEIGHT.set(Mth.clamp(ClientConfig.HEIGHT.get() + step, 1, 30));
        } else {
            ClientConfig.WIDTH.set(Mth.clamp(ClientConfig.WIDTH.get() + step * 6, 10, 800));
        }
        save();
        return true;
    }

    /** Переводит абсолютную позицию верхнего левого угла в смещение от якоря. */
    private void setPosition(int x, int y) {
        ClientConfig.Anchor a = ClientConfig.ANCHOR.get();
        int w = ClientConfig.WIDTH.get();
        int h = ClientConfig.HEIGHT.get();
        int baseX = Math.round(this.width * a.fx - w * a.fx);
        int baseY = Math.round(this.height * a.fy - h * a.fy);
        ClientConfig.OFFSET_X.set(Mth.clamp(x - baseX, -4000, 4000));
        ClientConfig.OFFSET_Y.set(Mth.clamp(y - baseY, -4000, 4000));
    }

    @Override
    public void onClose() {
        save();
        super.onClose();
    }
}
