package faygolover.rpmedicine.menu;

import faygolover.rpmedicine.registry.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Обыск лежачего (п. 5.2 ТЗ): весь его инвентарь, броня и слоты Curios. Индексы контейнера цели —
 * как у {@link Inventory}: 0–35 основной, 36–39 броня, 40 вторая рука.
 */
public class SearchMenu extends AbstractContainerMenu {
    public static final int TARGET_SIZE = 41;

    private final Container target;
    private final int curiosCount;
    @Nullable
    private final LivingEntity targetEntity;
    private final Predicate<Player> validator;

    /** Сервер. */
    public SearchMenu(int id, Inventory own, Container target, @Nullable IItemHandlerModifiable curios,
                      @Nullable LivingEntity targetEntity, Predicate<Player> validator) {
        super(ModMenus.SEARCH.get(), id);
        this.target = target;
        this.targetEntity = targetEntity;
        this.validator = validator;
        IItemHandlerModifiable c = curios != null ? curios : new ItemStackHandler(0);
        this.curiosCount = c.getSlots();
        layout(own, c);
    }

    /** Клиент. */
    public static SearchMenu fromNetwork(int id, Inventory own, FriendlyByteBuf buf) {
        int curios = buf.readVarInt();
        return new SearchMenu(id, own, new SimpleContainer(TARGET_SIZE), new ItemStackHandler(curios), null, p -> true);
    }

    public int curiosRows() {
        return (curiosCount + 8) / 9;
    }

    /** Высота области цели в пикселях (для экрана). */
    public int targetHeight() {
        return 18 + 5 * 18 + curiosRows() * 18 + 10;
    }

    private void layout(Inventory own, IItemHandlerModifiable curios) {
        int y = 18;
        // Броня (голова…ступни) и вторая рука.
        for (int i = 0; i < 4; i++) addSlot(new Slot(target, 39 - i, 8 + i * 18, y));
        addSlot(new Slot(target, 40, 8 + 5 * 18, y));
        y += 22;
        for (int r = 0; r < 3; r++)
            for (int col = 0; col < 9; col++) addSlot(new Slot(target, 9 + r * 9 + col, 8 + col * 18, y + r * 18));
        y += 3 * 18 + 4;
        for (int col = 0; col < 9; col++) addSlot(new Slot(target, col, 8 + col * 18, y));
        y += 22;
        for (int i = 0; i < curiosCount; i++) addSlot(new SlotItemHandler(curios, i, 8 + (i % 9) * 18, y + (i / 9) * 18));
        y += curiosRows() * 18 + 14;
        for (int r = 0; r < 3; r++)
            for (int col = 0; col < 9; col++) addSlot(new Slot(own, 9 + r * 9 + col, 8 + col * 18, y + r * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(own, col, 8 + col * 18, y + 58));
    }

    /** Y верхнего края инвентаря обыскивающего. */
    public int ownInventoryTop() {
        return 18 + 22 + 3 * 18 + 4 + 22 + curiosRows() * 18 + 14;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int targetSlots = TARGET_SIZE + curiosCount;
        if (index < targetSlots) {
            if (!moveItemStackTo(stack, targetSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            // В основной инвентарь цели (без брони и Curios).
            if (!moveItemStackTo(stack, 5, 5 + 36, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return validator.test(player);
    }

    @Nullable
    public LivingEntity targetEntity() {
        return targetEntity;
    }
}
