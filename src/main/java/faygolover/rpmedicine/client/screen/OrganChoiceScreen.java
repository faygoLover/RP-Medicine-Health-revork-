package faygolover.rpmedicine.client.screen;

import faygolover.rpmedicine.network.Network;
import faygolover.rpmedicine.network.OrganChoicePacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Какой орган изъять (ТЗ третьего этапа, п. 7.1). */
public class OrganChoiceScreen extends Screen {
    private final OrganChoicePacket.Request req;
    @Nullable
    private final Screen back;
    private boolean sent;

    public OrganChoiceScreen(OrganChoicePacket.Request req, @Nullable Screen back) {
        super(Component.translatable("rpmedicine.organ_choice.title"));
        this.req = req;
        this.back = back;
    }

    @Override
    protected void init() {
        int n = req.organs().size();
        int bw = 90;
        int x0 = width / 2 - (n * (bw + 4)) / 2;
        for (int i = 0; i < n; i++) {
            String id = req.organs().get(i);
            addRenderableWidget(Button.builder(Component.translatable("rpmedicine.organ." + id), b -> choose(id))
                    .bounds(x0 + i * (bw + 4), height / 2 - 4, bw, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(width / 2 - 50, height / 2 + 24, 100, 20).build());
    }

    private void choose(String id) {
        if (sent) return;
        sent = true;
        Network.sendToServer(new OrganChoicePacket.Choice(req.targetId(), req.slot(), req.part(), id));
        minecraft.setScreen(back);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, title.copy().withStyle(ChatFormatting.GOLD), width / 2, height / 2 - 30, 0xFFFFFF);
        super.render(g, mx, my, pt);
    }

    @Override
    public void onClose() {
        sent = true;
        minecraft.setScreen(back);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
