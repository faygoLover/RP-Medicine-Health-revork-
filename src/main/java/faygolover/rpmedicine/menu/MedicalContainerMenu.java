package faygolover.rpmedicine.menu;

import faygolover.rpmedicine.RpMedicine;
import faygolover.rpmedicine.registry.ModMenus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

/** Меню подсумка или аптечки. Содержимое хранится в NBT предмета. */
public class MedicalContainerMenu extends AbstractContainerMenu {
    /** Тег предметов, которые можно положить в подсумок (синхронизируется с клиентом). */
    public static final TagKey<Item> MEDICAL_ITEMS = TagKey.create(Registries.ITEM, new ResourceLocation(RpMedicine.MODID, "medical_items"));

    private final InteractionHand hand;
    private final ItemStack container;
    private final ItemStackHandler handler;
    private final int lockedSlot;

    /** Сервер. */
    public MedicalContainerMenu(int id, Inventory inv, InteractionHand hand, int size) {
        this(id, inv, hand, size, hand == InteractionHand.MAIN_HAND ? inv.selected : -1);
    }

    /** Клиент. */
    public static MedicalContainerMenu fromNetwork(int id, Inventory inv, FriendlyByteBuf buf) {
        InteractionHand hand = buf.readEnum(InteractionHand.class);
        int size = buf.readVarInt();
        int slot = buf.readVarInt();
        return new MedicalContainerMenu(id, inv, hand, size, slot);
    }

    private MedicalContainerMenu(int id, Inventory inv, InteractionHand hand, int size, int lockedSlot) {
        super(ModMenus.MEDICAL_CONTAINER.get(), id);
        this.hand = hand;
        this.container = inv.player.getItemInHand(hand);
        this.lockedSlot = lockedSlot;
        this.handler = new ItemStackHandler(size) {
            @Override
            protected void onContentsChanged(int slot) {
                container.getOrCreateTag().put("Inventory", serializeNBT());
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return stack.is(MEDICAL_ITEMS);
            }
        };
        CompoundTag tag = container.getTag();
        if (tag != null && tag.contains("Inventory")) {
            CompoundTag invTag = tag.getCompound("Inventory");
            invTag.putInt("Size", size);
            handler.deserializeNBT(invTag);
        }

        int top;
        Layout lay = Layout.of(size);
        if (lay != null) {
            // Раскладка под фон из Tactical Medicine: подсумок IFAK (8) и рюкзак парамедика (20).
            for (int i = 0; i < size; i++) addSlot(new SlotItemHandler(handler, i, lay.xs[i % lay.xs.length], lay.ys[i / lay.xs.length]));
            top = lay.invTop;
        } else {
            int cols = Math.min(9, size);
            int rows = (size + 8) / 9;
            int left = 8 + (9 - cols) * 9;
            for (int i = 0; i < size; i++) {
                addSlot(new SlotItemHandler(handler, i, left + (i % 9) * 18, 18 + (i / 9) * 18));
            }
            top = 18 + rows * 18 + 14;
        }
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, top + r * 18));
        for (int c = 0; c < 9; c++) {
            final int idx = c;
            addSlot(new Slot(inv, c, 8 + c * 18, top + 58) {
                @Override
                public boolean mayPickup(Player player) {
                    return idx != MedicalContainerMenu.this.lockedSlot && super.mayPickup(player);
                }
            });
        }
    }

    /** Раскладка ячеек под нарисованный фон: столбцы, строки, где начинается инвентарь, высота окна. */
    public record Layout(int[] xs, int[] ys, int invTop, int height, int imageHeight, int texHeight, String texture) {
        public static Layout of(int size) {
            if (size == 8) return new Layout(new int[]{32, 64, 96, 128}, new int[]{50, 79}, 140, 223, 132, 770, "medical_pouch");
            // Аптечка — без рисунка (замечание 06.10): обычная сетка.
            return null;
        }
    }

    public int size() {
        return handler.getSlots();
    }

    public int containerRows() {
        return (handler.getSlots() + 8) / 9;
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        // Нельзя переложить сам подсумок, пока он открыт.
        if (type == ClickType.SWAP && button == lockedSlot) return;
        super.clicked(slotId, button, type, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int size = handler.getSlots();
        if (index < size) {
            if (!moveItemStackTo(stack, size, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (!stack.is(MEDICAL_ITEMS) || !moveItemStackTo(stack, 0, size, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getItemInHand(hand) == container;
    }
}
