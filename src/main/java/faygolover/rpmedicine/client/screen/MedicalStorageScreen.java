package faygolover.rpmedicine.client.screen;

import faygolover.rpmedicine.menu.MedicalStorageMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Хранилище госпиталя: окно как у сундука (ванильная текстура). */
public class MedicalStorageScreen extends AbstractContainerScreen<MedicalStorageMenu> {
    private static final ResourceLocation CHEST = new ResourceLocation("textures/gui/container/generic_54.png");
    private final int rows;

    public MedicalStorageScreen(MedicalStorageMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        rows = menu.rows();
        imageHeight = 114 + rows * 18;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = (width - imageWidth) / 2, y = (height - imageHeight) / 2;
        g.blit(CHEST, x, y, 0, 0, imageWidth, rows * 18 + 17);
        g.blit(CHEST, x, y + rows * 18 + 17, 0, 126, imageWidth, 96);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }
}
