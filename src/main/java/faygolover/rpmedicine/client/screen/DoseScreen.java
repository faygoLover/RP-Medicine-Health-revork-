package faygolover.rpmedicine.client.screen;

import faygolover.rpmedicine.network.DosePacket;
import faygolover.rpmedicine.network.Network;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Выбор дозы шприцем: половина, стандартная, полторы, двойная (решения, п. 1.13). */
public class DoseScreen extends Screen {
    private static final float[] DOSES = {0.5f, 1f, 1.5f, 2f};
    private final DosePacket.Request req;
    @Nullable
    private final Screen back;
    private boolean sent;

    public DoseScreen(DosePacket.Request req, @Nullable Screen back) {
        super(Component.translatable("rpmedicine.dose.title"));
        this.req = req;
        this.back = back;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int y = height / 2 - 4;
        for (int i = 0; i < DOSES.length; i++) {
            float d = DOSES[i];
            addRenderableWidget(Button.builder(Component.translatable("rpmedicine.dose.option_" + i), b -> choose(d))
                    .bounds(cx - 160 + i * 80, y, 76, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(cx - 50, y + 28, 100, 20).build());
    }

    private void choose(float dose) {
        if (sent) return;
        sent = true;
        Network.sendToServer(new DosePacket.Choice(req.targetId(), req.slot(), req.part(), dose));
        minecraft.setScreen(back);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int cx = width / 2;
        g.drawCenteredString(font, title.copy().withStyle(ChatFormatting.GOLD), cx, height / 2 - 46, 0xFFFFFF);
        g.drawCenteredString(font, req.drug(), cx, height / 2 - 32, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("rpmedicine.dose.weight", req.weightKg()).withStyle(ChatFormatting.GRAY), cx, height / 2 - 20, 0xFFFFFF);
        super.render(g, mx, my, pt);
    }

    @Override
    public void onClose() {
        if (!sent) {
            sent = true;
            Network.sendToServer(new DosePacket.Choice(req.targetId(), req.slot(), req.part(), 0f));
        }
        minecraft.setScreen(back);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
