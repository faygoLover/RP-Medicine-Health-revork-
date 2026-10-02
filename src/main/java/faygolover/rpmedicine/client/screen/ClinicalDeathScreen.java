package faygolover.rpmedicine.client.screen;

import faygolover.rpmedicine.network.DownedActionPacket;
import faygolover.rpmedicine.network.Network;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;

/**
 * Клиническая смерть (п. 5.4 ТЗ): экран чёрный, чат не виден. Кнопки: «Сдаться», «Выйти в меню»,
 * «Позвать администратора» (раз в 3 минуты, проверяет сервер).
 */
public class ClinicalDeathScreen extends Screen {
    public ClinicalDeathScreen() {
        super(Component.translatable("rpmedicine.clinical.title"));
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int y = height / 2 + 10;
        addRenderableWidget(Button.builder(Component.translatable("rpmedicine.downed.surrender"), b ->
                minecraft.setScreen(new ConfirmScreen(ok -> {
                    if (ok) Network.sendToServer(new DownedActionPacket(DownedActionPacket.Kind.SURRENDER, -1));
                    minecraft.setScreen(ok ? null : this);
                }, Component.translatable("rpmedicine.downed.surrender_confirm"), Component.translatable("rpmedicine.downed.surrender_detail"))))
                .bounds(cx - 100, y, 200, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("rpmedicine.clinical.call_admin"), b ->
                Network.sendToServer(new DownedActionPacket(DownedActionPacket.Kind.CALL_ADMIN, -1)))
                .bounds(cx - 100, y + 24, 200, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("menu.disconnect"), b -> disconnect())
                .bounds(cx - 100, y + 48, 200, 20).build());
    }

    private void disconnect() {
        boolean local = minecraft.isLocalServer();
        if (minecraft.level != null) minecraft.level.disconnect();
        minecraft.clearLevel();
        TitleScreen title = new TitleScreen();
        minecraft.setScreen(local ? title : new JoinMultiplayerScreen(title));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xFF000000);
        g.drawCenteredString(font, title.copy().withStyle(ChatFormatting.DARK_RED), width / 2, height / 2 - 40, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("rpmedicine.clinical.hint").withStyle(ChatFormatting.DARK_GRAY), width / 2, height / 2 - 24, 0xFFFFFF);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
