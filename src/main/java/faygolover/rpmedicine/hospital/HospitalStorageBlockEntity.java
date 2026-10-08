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
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        items = NonNullList.withSize(kind().slots, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(t, items);
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
        return new MedicalStorageMenu(id, inv, this, kind().slots / 9, kind().filter);
    }
}
