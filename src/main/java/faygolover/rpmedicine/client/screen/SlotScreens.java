package faygolover.rpmedicine.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/** Общая отрисовка фона меню без текстур: панель и ячейки. */
final class SlotScreens {
    private SlotScreens() {}

    static void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xF0C6C6C6);
        g.renderOutline(x, y, w, h, 0xFF555555);
    }

    static void drawSlots(GuiGraphics g, AbstractContainerMenu menu, int left, int top) {
        for (Slot s : menu.slots) {
            int x = left + s.x;
            int y = top + s.y;
            g.fill(x - 1, y - 1, x + 17, y + 17, 0xFF8B8B8B);
            g.fill(x, y, x + 16, y + 16, 0xFF555555);
        }
    }
}
