package faygolover.rpmedicine.server;

import faygolover.rpmedicine.capability.MedicalData;
import faygolover.rpmedicine.config.ServerConfig;
import faygolover.rpmedicine.integration.Integrations;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityMountEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Переноска лежачего на плече (п. 5.2 ТЗ). Тело — пассажир несущего. Несущий идёт медленнее, не
 * бегает и не стреляет; сбросить — присесть.
 */
public final class CarryService {
    private CarryService() {}

    /** Несущий → кого несёт. */
    private static final Map<UUID, Entity> CARRIED = new HashMap<>();
    /** Разрешённое нами слезание (сброс). */
    private static boolean dropping;

    public static boolean isCarrying(ServerPlayer sp) {
        return CARRIED.containsKey(sp.getUUID());
    }

    /** Кого несёт игрок (или null). */
    @org.jetbrains.annotations.Nullable
    public static Entity carried(ServerPlayer sp) {
        return CARRIED.get(sp.getUUID());
    }

    public static boolean isCarried(Entity e) {
        return e.getVehicle() instanceof ServerPlayer carrier && CARRIED.get(carrier.getUUID()) == e;
    }

    public static boolean pickUp(ServerPlayer carrier, LivingEntity target) {
        if (carrier == target || isCarrying(carrier) || target.isPassenger() || !Medical.isDown(target)) return false;
        if (Medical.isDown(carrier)) return false;
        MedicalData d = Medical.data(carrier);
        if (d != null && d.lastMods.armsDisabled) {
            carrier.displayClientMessage(Component.translatable("rpmedicine.refuse.arms_broken").withStyle(ChatFormatting.YELLOW), true);
            return false;
        }
        if (carrier.distanceTo(target) > ServerConfig.INTERACT_DISTANCE.get() + 0.5) return false;
        if (!target.startRiding(carrier, true)) return false;
        CARRIED.put(carrier.getUUID(), target);
        if (target instanceof ServerPlayer tp) ActionManager.cancel(tp, null);
        ActionManager.cancel(carrier, null);
        setCarrying(carrier, true);
        carrier.displayClientMessage(Component.translatable("rpmedicine.msg.carrying"), true);
        return true;
    }

    /** Сбросить тело, которое несёт игрок. */
    public static void dropCarried(ServerPlayer carrier) {
        Entity e = CARRIED.remove(carrier.getUUID());
        setCarrying(carrier, false);
        if (e == null) return;
        if (e.getVehicle() == carrier) {
            dropping = true;
            try {
                e.stopRiding();
            } finally {
                dropping = false;
            }
            Vec3 look = carrier.getLookAngle().multiply(1, 0, 1).normalize().scale(0.8);
            e.teleportTo(carrier.getX() + look.x, carrier.getY(), carrier.getZ() + look.z);
        }
        Medical.changed(e);
    }

    /** Сбросить игрока, если его несут (пришёл в себя, умер, вышел). */
    public static void dropIfCarried(Entity e) {
        if (e.getVehicle() instanceof ServerPlayer carrier && CARRIED.get(carrier.getUUID()) == e) dropCarried(carrier);
    }

    /** Каждый тик несущего. */
    public static void tickCarrier(ServerPlayer carrier) {
        Entity e = CARRIED.get(carrier.getUUID());
        if (e == null) return;
        if (e.isRemoved() || e.getVehicle() != carrier || carrier.isShiftKeyDown() || Medical.isDown(carrier)) {
            dropCarried(carrier);
            return;
        }
        if (carrier.tickCount % 20 == 0) Integrations.consumeStamina(carrier, ServerConfig.CARRY_STAMINA_PER_SECOND.get().floatValue());
    }

    /** Лежачий не может слезть сам. */
    public static void onMount(EntityMountEvent event) {
        if (dropping || !event.isDismounting() || event.getLevel().isClientSide) return;
        Entity rider = event.getEntityMounting();
        if (Medical.isDown(rider) && event.getEntityBeingMounted() instanceof ServerPlayer carrier && CARRIED.get(carrier.getUUID()) == rider) {
            if (!rider.isRemoved() && !carrier.isRemoved() && !carrier.isDeadOrDying()) event.setCanceled(true);
        }
    }

    public static void onLogout(ServerPlayer sp) {
        dropCarried(sp);
        dropIfCarried(sp);
    }

    public static void clear() {
        CARRIED.clear();
    }

    private static void setCarrying(ServerPlayer sp, boolean on) {
        MedicalData d = Medical.data(sp);
        if (d != null && d.carrying != on) {
            d.carrying = on;
            d.markDirty();
        }
    }
}
