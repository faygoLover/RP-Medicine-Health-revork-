package faygolover.rpmedicine.menu;

import faygolover.rpmedicine.registry.ModItems;
import faygolover.rpmedicine.registry.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Хранилище госпиталя (решения, п. 1.16): аптечный шкаф и ящик — только медицина, термостат — только пробирки
 * с кровью. Сетка как у сундука.
 */
public class MedicalStorageMenu extends AbstractContainerMenu {
    /** Что можно положить. */
    public static final byte MEDICAL = 0, BLOOD_SAMPLES = 1;

    private final Container container;
    private final int rows;
    private final byte filter;

    public MedicalStorageMenu(int id, Inventory inv, Container container, int rows, byte filter) {
        super(ModMenus.MEDICAL_STORAGE.get(), id);
        this.container = container;
        this.rows = rows;
        this.filter = filter;
        container.startOpen(inv.player);
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < 9; c++)
                addSlot(new Slot(container, c + r * 9, 8 + c * 18, 18 + r * 18) {
                    @Override
                    public boolean mayPlace(ItemStack st) {
                        return allowed(st, MedicalStorageMenu.this.filter);
                    }
                });
        int top = 18 + rows * 18 + 13;
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, top + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, top + 58));
    }

    public static MedicalStorageMenu fromNetwork(int id, Inventory inv, FriendlyByteBuf buf) {
        int rows = buf.readVarInt();
        byte filter = buf.readByte();
        return new MedicalStorageMenu(id, inv, new SimpleContainer(rows * 9), rows, filter);
    }

    public static boolean allowed(ItemStack st, byte filter) {
        if (st.isEmpty()) return true;
        if (filter == BLOOD_SAMPLES) return st.is(ModItems.BLOOD_SAMPLE.get());
        return st.is(MedicalContainerMenu.MEDICAL_ITEMS);
    }

    public int rows() {
        return rows;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack st = slot.getItem();
        ItemStack copy = st.copy();
        int size = rows * 9;
        if (index < size) {
            if (!moveItemStackTo(st, size, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (!allowed(st, filter) || !moveItemStackTo(st, 0, size, false)) return ItemStack.EMPTY;
        }
        if (st.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }
}
