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
    /** Окно термостата из Health & Disease (TubeScreen). */
    private static final ResourceLocation THERMOSTAT = new ResourceLocation(faygolover.rpmedicine.RpMedicine.MODID, "textures/gui/thermostat.png");
    private final int rows;
    private final boolean thermostat;
    private final boolean sterilizer;

    public MedicalStorageScreen(MedicalStorageMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        rows = menu.rows();
        thermostat = MedicalStorageMenu.isThermostat(menu.filter());
        sterilizer = menu.filter() == MedicalStorageMenu.STERILIZE;
        imageHeight = thermostat ? 150 : 114 + rows * 18 + (sterilizer ? MedicalStorageMenu.STERILIZER_EXTRA : 0);
        inventoryLabelY = thermostat ? 57 : imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = (width - imageWidth) / 2, y = (height - imageHeight) / 2;
        if (thermostat) {
            g.blit(THERMOSTAT, x, y, 0, 0, 176, 150, 176, 150);
            return;
        }
        g.blit(CHEST, x, y, 0, 0, imageWidth, rows * 18 + 17);
        int extra = 0;
        if (sterilizer) {
            // Полоса под строку состояния — серый фон с рамкой из верхней части сундука.
            extra = MedicalStorageMenu.STERILIZER_EXTRA;
            g.blit(CHEST, x, y + rows * 18 + 17, 0, 4, imageWidth, extra);
        }
        g.blit(CHEST, x, y + rows * 18 + 17 + extra, 0, 126, imageWidth, 96);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        super.renderLabels(g, mx, my);
        if (!sterilizer) return;
        var d = menu.data();
        boolean work = d.get(1) == 1;
        boolean empty = true;
        for (int i = 0; i < rows * 9; i++) if (menu.getSlot(i).hasItem()) empty = false;
        String key = work ? "rpmedicine.sterilizer.close_lid" : empty ? "rpmedicine.sterilizer.empty" : "rpmedicine.sterilizer.done";
        int y = 18 + rows * 18 + 3;
        int sec = d.get(2) / 20;
        String time = sec >= 60 ? (sec / 60) + " мин" : sec + " с";
        g.drawString(font, Component.translatable(key, time), 8, y, work ? 0xB06010 : 0x2E7D32, false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }
}
