package faygolover.rpmedicine.server;

import faygolover.rpmedicine.core.BodyPart;
import faygolover.rpmedicine.entity.BodyStubEntity;
import faygolover.rpmedicine.item.SeveredLimbItem;
import faygolover.rpmedicine.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;

/** Отнятая конечность падает предметом рядом с телом (ТЗ третьего этапа, п. 6.1). */
public final class LimbDrops {
    private LimbDrops() {}

    public static void drop(LivingEntity target, BodyPart part) {
        if (!part.isLimb()) return;
        var owner = target instanceof BodyStubEntity stub ? stub.ownerId() : target.getUUID();
        String name = target instanceof BodyStubEntity stub ? stub.ownerName()
                : target instanceof ServerPlayer sp ? sp.getGameProfile().getName() : target.getName().getString();
        var stack = SeveredLimbItem.create(ModItems.SEVERED_LIMB.get(), part, owner, name, target.level().getGameTime());
        ItemEntity e = new ItemEntity(target.level(), target.getX(), target.getY() + 0.3, target.getZ(), stack);
        e.setDefaultPickUpDelay();
        target.level().addFreshEntity(e);
    }
}
