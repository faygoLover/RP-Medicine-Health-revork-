package faygolover.rpmedicine.client.screen;

import faygolover.rpmedicine.menu.SearchMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Обыск: инвентарь, броня и Curios лежачего сверху, свой инвентарь снизу. */
public class SearchScreen extends AbstractContainerScreen<SearchMenu> {
    public SearchScreen(SearchMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = menu.ownInventoryTop() + 76 + 6;
        this.inventoryLabelY = menu.ownInventoryTop() - 11;
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        SlotScreens.drawPanel(g, leftPos, topPos, imageWidth, imageHeight);
        SlotScreens.drawSlots(g, menu, leftPos, topPos);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        super.renderLabels(g, mx, my);
        if (menu.curiosRows() > 0)
            g.drawString(font, Component.translatable("rpmedicine.search.curios"), 8, 18 + 22 + 58 + 22 - 10, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }
}
