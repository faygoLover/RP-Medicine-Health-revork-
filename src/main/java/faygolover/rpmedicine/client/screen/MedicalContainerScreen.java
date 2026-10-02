package faygolover.rpmedicine.client.screen;

import faygolover.rpmedicine.menu.MedicalContainerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Подсумок и аптечка. Фон рисуется без текстур. */
public class MedicalContainerScreen extends AbstractContainerScreen<MedicalContainerMenu> {
    public MedicalContainerScreen(MedicalContainerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 18 + menu.containerRows() * 18 + 14 + 76 + 6;
        this.inventoryLabelY = 18 + menu.containerRows() * 18 + 3;
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        SlotScreens.drawPanel(g, leftPos, topPos, imageWidth, imageHeight);
        SlotScreens.drawSlots(g, menu, leftPos, topPos);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }
}
