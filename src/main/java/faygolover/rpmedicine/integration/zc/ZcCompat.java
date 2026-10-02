package faygolover.rpmedicine.integration.zc;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.zerocontact.api.HelmetInfoProvider;
import net.zerocontact.api.ICombatArmorItem;

/**
 * Zero Contact (код GPL, не копируем — только публичные интерфейсы). Загружается, только если мод
 * установлен. Пока в Zero Contact нет события с исходом попадания (запрос — docs/zc_request.md),
 * исход определяет {@code GunHits} по урону до и после брони.
 */
public final class ZcCompat {
    private ZcCompat() {}

    /** Бронежилет или плитник Zero Contact на груди. */
    public static boolean hasBodyArmor(LivingEntity e) {
        return e.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof ICombatArmorItem;
    }

    public static boolean hasHelmet(LivingEntity e) {
        var item = e.getItemBySlot(EquipmentSlot.HEAD).getItem();
        return item instanceof HelmetInfoProvider && item instanceof ICombatArmorItem;
    }

    /**
     * Угол, при котором Zero Contact считает рикошет: направление на стрелка относительно взгляда
     * цели отличается от перпендикуляра на 10–30°. Геометрия повторена по описанию в каталоге (п. 21.3).
     */
    public static boolean ricochetAngle(LivingEntity target, Entity shooter) {
        double sx = target.getX() - shooter.getX();
        double sz = target.getZ() - shooter.getZ();
        double lx = target.getLookAngle().x;
        double lz = target.getLookAngle().z;
        double angle = Math.toDegrees(Math.atan2(sz, sx)) - Math.toDegrees(Math.atan2(lz, lx));
        double off = Math.abs(Math.abs(angle) - 90);
        return off <= 30 && off >= 10;
    }
}
