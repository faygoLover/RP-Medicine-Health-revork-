package faygolover.rpmedicine.hospital;

import faygolover.rpmedicine.capability.MedicalData;
import faygolover.rpmedicine.server.Medical;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityEvent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Поза на койке: игрок лежит на спине (поза сна), но с обычным хитбоксом лежачего — иначе у позы сна
 * хитбокс 0,2 блока и медик не попадёт по пациенту. Работает на обеих сторонах.
 */
public final class BedPose {
    private BedPose() {}

    /** Размер лежащего на койке. */
    public static final EntityDimensions SIZE = EntityDimensions.scalable(0.6f, 0.6f);
    public static final float EYE_HEIGHT = 0.35f;

    /** Клиент: id игроков на койках (заполняется пакетом позы). */
    public static final Set<Integer> CLIENT_ON_BED = ConcurrentHashMap.newKeySet();
    /** Клиент: поворот тела на койке, градусы. */
    public static final java.util.Map<Integer, Float> CLIENT_BED_YAW = new ConcurrentHashMap<>();

    public static boolean onBed(Player p) {
        if (p.level().isClientSide) return CLIENT_ON_BED.contains(p.getId());
        MedicalData d = Medical.data(p);
        return d != null && d.bedPos != null;
    }

    public static void onSize(EntityEvent.Size e) {
        if (e.getPose() == Pose.SLEEPING && e.getEntity() instanceof Player p && !p.isSleeping() && onBed(p)) {
            e.setNewSize(SIZE);
            e.setNewEyeHeight(EYE_HEIGHT);
        }
    }
}
