package faygolover.rpmedicine.client.screen;

import faygolover.rpmedicine.RpMedicine;
import faygolover.rpmedicine.menu.MedicalContainerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Подсумок и аптечка. Фон — рисунок из Tactical Medicine (подсумок IFAK, рюкзак парамедика, замечание 48),
 * ниже — обычный инвентарь; для других размеров — простая панель.
 */
public class MedicalContainerScreen extends AbstractContainerScreen<MedicalContainerMenu> {
    private final MedicalContainerMenu.Layout layout;

    public MedicalContainerScreen(MedicalContainerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.layout = MedicalContainerMenu.Layout.of(menu.size());
        this.imageWidth = 176;
        if (layout != null) {
            this.imageHeight = layout.height();
            this.inventoryLabelY = layout.invTop() - 11;
            this.titleLabelY = 6;
        } else {
            this.imageHeight = 18 + menu.containerRows() * 18 + 14 + 76 + 6;
            this.inventoryLabelY = 18 + menu.containerRows() * 18 + 3;
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        if (layout == null) {
            SlotScreens.drawPanel(g, leftPos, topPos, imageWidth, imageHeight);
            SlotScreens.drawSlots(g, menu, leftPos, topPos);
            return;
        }
        ResourceLocation tex = new ResourceLocation(RpMedicine.MODID, "textures/gui/" + layout.texture() + ".png");
        g.blit(tex, leftPos, topPos, imageWidth, layout.imageHeight(), 0, 0, 1024, layout.texHeight(), 1024, 1024);
        // Инвентарь игрока — простая панель под рисунком.
        int iy = topPos + layout.imageHeight();
        SlotScreens.drawPanel(g, leftPos, iy, imageWidth, imageHeight - layout.imageHeight());
        for (Slot s : menu.slots) {
            if (s.index < menu.size()) continue;
            int x = leftPos + s.x;
            int y = topPos + s.y;
            g.fill(x - 1, y - 1, x + 17, y + 17, 0xFF8B8B8B);
            g.fill(x, y, x + 16, y + 16, 0xFF555555);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        if (layout == null) {
            super.renderLabels(g, mx, my);
            return;
        }
        // Название на рисунке — светлым с тенью, «Инвентарь» — как обычно.
        g.drawString(font, title, titleLabelX, titleLabelY, 0xF0E6C8, true);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }
}
