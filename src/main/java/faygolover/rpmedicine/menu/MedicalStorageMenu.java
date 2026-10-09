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
    public static final byte MEDICAL = 0, BLOOD_SAMPLES = 1, STERILIZE = 2;
    /** Стерилизатор: под ячейками строка состояния — окно выше на столько. */
    public static final int STERILIZER_EXTRA = 12;

    private final Container container;
    private final int rows;
    private final byte filter;
    /** Стерилизатор: цикл, есть ли работа, длина цикла (см. HospitalStorageBlockEntity.sterilizerData). */
    private final net.minecraft.world.inventory.ContainerData data;

    public MedicalStorageMenu(int id, Inventory inv, Container container, int rows, byte filter) {
        this(id, inv, container, rows, filter, new net.minecraft.world.inventory.SimpleContainerData(3));
    }

    public MedicalStorageMenu(int id, Inventory inv, Container container, int rows, byte filter, net.minecraft.world.inventory.ContainerData data) {
        super(ModMenus.MEDICAL_STORAGE.get(), id);
        this.container = container;
        this.rows = rows;
        this.filter = filter;
        this.data = data;
        if (filter == STERILIZE) addDataSlots(data);
        container.startOpen(inv.player);
        int top;
        if (isThermostat(filter)) {
            // Термостат — окно H&D: пробирки 4×2 по центру (замечание живого теста 5).
            for (int i = 0; i < THERMOSTAT_SLOTS; i++) addSlot(storageSlot(container, i, 52 + (i % 4) * 18, 14 + (i / 4) * 18));
            top = 68;
        } else {
            for (int r = 0; r < rows; r++)
                for (int c = 0; c < 9; c++) addSlot(storageSlot(container, c + r * 9, 8 + c * 18, 18 + r * 18));
            top = 18 + rows * 18 + 13 + (filter == STERILIZE ? STERILIZER_EXTRA : 0);
        }
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, top + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, top + 58));
    }

    /** Ячеек у термостата (модели H&D — до 8 пробирок). */
    public static final int THERMOSTAT_SLOTS = 8;

    public static boolean isThermostat(byte filter) {
        return filter == BLOOD_SAMPLES;
    }

    private Slot storageSlot(Container c, int index, int x, int y) {
        return new Slot(c, index, x, y) {
            @Override
            public boolean mayPlace(ItemStack st) {
                return allowed(st, MedicalStorageMenu.this.filter);
            }
        };
    }

    public static MedicalStorageMenu fromNetwork(int id, Inventory inv, FriendlyByteBuf buf) {
        int rows = buf.readVarInt();
        byte filter = buf.readByte();
        return new MedicalStorageMenu(id, inv, new SimpleContainer(isThermostat(filter) ? THERMOSTAT_SLOTS : rows * 9), rows, filter);
    }

    public byte filter() {
        return filter;
    }

    public static boolean allowed(ItemStack st, byte filter) {
        if (st.isEmpty()) return true;
        if (filter == BLOOD_SAMPLES) return st.is(ModItems.BLOOD_SAMPLE.get());
        if (filter == STERILIZE) return faygolover.rpmedicine.hospital.HospitalStorageBlockEntity.sterilizable(st);
        return st.is(MedicalContainerMenu.MEDICAL_ITEMS);
    }

    public net.minecraft.world.inventory.ContainerData data() {
        return data;
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
        int size = isThermostat(filter) ? THERMOSTAT_SLOTS : rows * 9;
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
