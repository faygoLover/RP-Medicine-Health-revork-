package faygolover.rpmedicine.hospital;

import faygolover.rpmedicine.menu.MedicalStorageMenu;
import faygolover.rpmedicine.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Блок госпиталя с 3D-моделью Health & Disease (JEDIGD, MIT): шкаф, ящик, стерилизатор, стол-лаборатория,
 * термостат. У хранилищ — инвентарь с фильтром; анимация открытия — когда кто-то смотрит внутрь.
 */
public class HospitalStorageBlockEntity extends BlockEntity implements Container, net.minecraft.world.MenuProvider {
    private NonNullList<ItemStack> items;
    private int viewers;
    /** Стерилизатор: тиков текущего цикла (0 — не идёт) и сколько ещё «горячий» после цикла (пар при открытии). */
    private int cycle, hot;
    public static final int STERILIZE_TICKS = 200;
    /** Для окна: [0] — тиков цикла, [1] — есть что стерилизовать (1), [2] — длина цикла. */
    public final net.minecraft.world.inventory.ContainerData sterilizerData = new net.minecraft.world.inventory.ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> cycle;
                case 1 -> needsWork() ? 1 : 0;
                default -> STERILIZE_TICKS;
            };
        }

        @Override
        public void set(int i, int v) {}

        @Override
        public int getCount() {
            return 3;
        }
    };
    /** Клиент: когда открыли/закрыли (мс) — для анимации. */
    public long clientOpenedAt = -1, clientClosedAt = -1;

    public HospitalStorageBlockEntity(BlockPos pos, BlockState st) {
        super(ModBlocks.HOSPITAL_STORAGE_BE.get(), pos, st);
        items = NonNullList.withSize(kind().slots, ItemStack.EMPTY);
    }

    public HospitalGeoBlock.Kind kind() {
        return getBlockState().getBlock() instanceof HospitalGeoBlock b ? b.kind : HospitalGeoBlock.Kind.CABINET;
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack st : items) if (!st.isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getItem(int i) {
        return items.get(i);
    }

    @Override
    public ItemStack removeItem(int i, int n) {
        ItemStack r = ContainerHelper.removeItem(items, i, n);
        if (!r.isEmpty()) changed();
        return r;
    }

    @Override
    public ItemStack removeItemNoUpdate(int i) {
        return ContainerHelper.takeItem(items, i);
    }

    @Override
    public void setItem(int i, ItemStack st) {
        items.set(i, st);
        changed();
    }

    @Override
    public boolean canPlaceItem(int i, ItemStack st) {
        return MedicalStorageMenu.allowed(st, kind().filter);
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide && getBlockState().getBlock() instanceof HospitalGeoBlock b) b.contentsChanged(level, worldPosition, this);
    }

    @Override
    public boolean stillValid(Player p) {
        return Container.stillValidBlockEntity(this, p);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    public NonNullList<ItemStack> items() {
        return items;
    }

    @Override
    public void startOpen(Player p) {
        if (p.isSpectator() || level == null) return;
        if (viewers++ == 0) {
            if (kind() == HospitalGeoBlock.Kind.STERILIZER && !level.isClientSide) {
                // Открыли крышку — выходит пар; идущий цикл прерывается.
                HospitalGeoBlock.steam(level, worldPosition, hot > 0 || cycle > 0);
                cycle = 0;
            }
            level.blockEvent(worldPosition, getBlockState().getBlock(), 1, 1);
            if (getBlockState().getBlock() instanceof HospitalGeoBlock b) HospitalGeoBlock.lidSound(level, worldPosition, b.kind, true);
        }
    }

    @Override
    public void stopOpen(Player p) {
        if (p.isSpectator() || level == null) return;
        if (--viewers <= 0) {
            viewers = 0;
            level.blockEvent(worldPosition, getBlockState().getBlock(), 1, 0);
            if (getBlockState().getBlock() instanceof HospitalGeoBlock b) HospitalGeoBlock.lidSound(level, worldPosition, b.kind, false);
        }
    }

    /** Стерилизатор: есть грязный шприц, пробирка или нестерильный инструмент. */
    public boolean needsWork() {
        for (ItemStack st : items) if (needsSterilizing(st)) return true;
        return false;
    }

    public static boolean needsSterilizing(ItemStack st) {
        if (st.is(faygolover.rpmedicine.registry.ModItems.DIRTY_SYRINGE.get()) || st.is(faygolover.rpmedicine.registry.ModItems.DIRTY_TEST_TUBE.get()))
            return true;
        return st.getItem() instanceof faygolover.rpmedicine.item.SurgicalInstrumentItem && !faygolover.rpmedicine.item.SurgicalInstrumentItem.isSterile(st);
    }

    /** Что можно положить в стерилизатор: шприцы и пробирки (грязные и чистые), хирургические инструменты. */
    public static boolean sterilizable(ItemStack st) {
        return needsSterilizing(st) || st.is(faygolover.rpmedicine.registry.ModItems.SYRINGE.get())
                || st.is(faygolover.rpmedicine.registry.ModItems.TEST_TUBE.get())
                || st.getItem() instanceof faygolover.rpmedicine.item.SurgicalInstrumentItem;
    }

    /** Сервер, каждый тик: крышка закрыта и есть работа — идёт цикл; в конце всё чистое и стерильное. */
    public void sterilizerTick() {
        if (hot > 0) hot--;
        if (viewers > 0 || !needsWork()) {
            if (viewers == 0) cycle = 0;
            return;
        }
        cycle++;
        if (cycle % 40 == 0 && level instanceof net.minecraft.server.level.ServerLevel sl)
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0,
                    worldPosition.getZ() + 0.5, 1, 0.15, 0.05, 0.15, 0.005);
        if (cycle < STERILIZE_TICKS) return;
        for (int i = 0; i < items.size(); i++) {
            ItemStack st = items.get(i);
            if (st.is(faygolover.rpmedicine.registry.ModItems.DIRTY_SYRINGE.get()))
                items.set(i, new ItemStack(faygolover.rpmedicine.registry.ModItems.SYRINGE.get(), st.getCount()));
            else if (st.is(faygolover.rpmedicine.registry.ModItems.DIRTY_TEST_TUBE.get()))
                items.set(i, new ItemStack(faygolover.rpmedicine.registry.ModItems.TEST_TUBE.get(), st.getCount()));
            else if (st.getItem() instanceof faygolover.rpmedicine.item.SurgicalInstrumentItem)
                faygolover.rpmedicine.item.SurgicalInstrumentItem.setSterile(st, true);
        }
        cycle = 0;
        hot = 20 * 60;
        changed();
        HospitalGeoBlock.steam(level, worldPosition, true);
    }

    /** Положить стопку в стерилизатор (ПКМ предметом по нему); вернуть, что не влезло. */
    public ItemStack insert(ItemStack st) {
        st = st.copy();
        for (int i = 0; i < items.size() && !st.isEmpty(); i++) {
            ItemStack cur = items.get(i);
            if (cur.isEmpty()) {
                items.set(i, st);
                st = ItemStack.EMPTY;
            } else if (ItemStack.isSameItemSameTags(cur, st) && cur.getCount() < cur.getMaxStackSize()) {
                int n = Math.min(st.getCount(), cur.getMaxStackSize() - cur.getCount());
                cur.grow(n);
                st.shrink(n);
            }
        }
        changed();
        return st;
    }

    /** Анимация открытия на клиенте. */
    @Override
    public boolean triggerEvent(int id, int data) {
        if (id == 1) {
            if (data == 1) clientOpenedAt = System.currentTimeMillis();
            else clientClosedAt = System.currentTimeMillis();
            return true;
        }
        return super.triggerEvent(id, data);
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        ContainerHelper.saveAllItems(t, items);
        if (cycle > 0) t.putInt("Cycle", cycle);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        items = NonNullList.withSize(kind().slots, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(t, items);
        cycle = t.getInt("Cycle");
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
        return new MedicalStorageMenu(id, inv, this, kind().slots / 9, kind().filter, sterilizerData);
    }
}
