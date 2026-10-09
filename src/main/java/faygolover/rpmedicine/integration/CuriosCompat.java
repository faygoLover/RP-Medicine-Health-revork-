package faygolover.rpmedicine.integration;

import faygolover.rpmedicine.entity.BodyStubEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.List;
import java.util.Map;

/** Curios: слоты для обыска и перенос в заглушку. Загружается только при наличии мода. */
public final class CuriosCompat {
    private CuriosCompat() {}

    /** Все надетые слоты Curios одним обработчиком (для меню обыска); null — Curios у сущности нет. */
    @Nullable
    public static IItemHandlerModifiable equipped(LivingEntity e) {
        return CuriosApi.getCuriosInventory(e).resolve().map(ICuriosItemHandler::getEquippedCurios).orElse(null);
    }

    /** Надетый в Curios предмет (первый найденный) или пусто. */
    public static ItemStack find(LivingEntity e, net.minecraft.world.item.Item item) {
        return CuriosApi.getCuriosInventory(e).resolve().flatMap(h -> h.findFirstCurio(item))
                .map(top.theillusivec4.curios.api.SlotResult::stack).orElse(ItemStack.EMPTY);
    }

    /** Переносит предметы Curios игрока в заглушку и очищает слоты игрока. */
    public static void moveToStub(LivingEntity player, BodyStubEntity stub) {
        ICuriosItemHandler h = CuriosApi.getCuriosInventory(player).resolve().orElse(null);
        if (h == null) return;
        for (Map.Entry<String, ICurioStacksHandler> e : h.getCurios().entrySet()) {
            IDynamicStackHandler stacks = e.getValue().getStacks();
            for (int i = 0; i < stacks.getSlots(); i++) {
                ItemStack s = stacks.getStackInSlot(i);
                if (s.isEmpty()) continue;
                stub.curios.add(new BodyStubEntity.CurioEntry(e.getKey(), i, s.copy()));
                stacks.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }

    /**
     * Возвращает предметы из заглушки игроку. То, что не поместилось в свой слот, возвращается
     * списком — вызывающий кладёт это в инвентарь или бросает рядом.
     */
    public static List<ItemStack> restoreFromStub(LivingEntity player, List<BodyStubEntity.CurioEntry> entries) {
        java.util.ArrayList<ItemStack> leftover = new java.util.ArrayList<>();
        ICuriosItemHandler h = CuriosApi.getCuriosInventory(player).resolve().orElse(null);
        for (BodyStubEntity.CurioEntry c : entries) {
            if (c.stack().isEmpty()) continue;
            boolean placed = false;
            if (h != null) {
                ICurioStacksHandler sh = h.getCurios().get(c.identifier());
                if (sh != null && c.index() < sh.getSlots() && sh.getStacks().getStackInSlot(c.index()).isEmpty()) {
                    sh.getStacks().setStackInSlot(c.index(), c.stack().copy());
                    placed = true;
                }
            }
            if (!placed) leftover.add(c.stack().copy());
        }
        return leftover;
    }
}
