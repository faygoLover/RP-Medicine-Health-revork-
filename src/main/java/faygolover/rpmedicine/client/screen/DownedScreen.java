package faygolover.rpmedicine.client.screen;

import faygolover.rpmedicine.client.ClientState;
import faygolover.rpmedicine.network.DownedActionPacket;
import faygolover.rpmedicine.network.Network;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Меню лежачего (нокдаун, обморок): кнопка «Сдаться» — настоящая смерть (п. 5.3 ТЗ). */
public class DownedScreen extends Screen {
    public DownedScreen() {
        super(Component.translatable("rpmedicine.downed.title"));
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int y = height / 2;
        addRenderableWidget(Button.builder(Component.translatable("rpmedicine.downed.surrender"), b ->
                minecraft.setScreen(new ConfirmScreen(ok -> {
                    if (ok) Network.sendToServer(new DownedActionPacket(DownedActionPacket.Kind.SURRENDER, -1));
                    minecraft.setScreen(null);
                }, Component.translatable("rpmedicine.downed.surrender_confirm"), Component.translatable("rpmedicine.downed.surrender_detail"))))
                .bounds(cx - 100, y, 200, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose()).bounds(cx - 100, y + 24, 200, 20).build());
    }

    @Override
    public void tick() {
        if (!ClientState.self.isDown()) onClose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, height / 2 - 30, 0xFFFFFF);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
