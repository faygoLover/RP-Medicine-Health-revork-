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
 * Переноска лежачего — волоком по земле (решения, п. 1.16; было «на плече»). Тело лежит и тянется за
 * медиком, медик идёт медленнее, не бегает и не стреляет. Взять — Shift+ПКМ по лежачему, положить —
 * Shift+ПКМ по блоку или по койке.
 */
public final class CarryService {
    private CarryService() {}

    private static final java.util.SplittableRandom JERK = new java.util.SplittableRandom();
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
        return CARRIED.containsValue(e);
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
        if (isCarried(target)) return false;
        CARRIED.put(carrier.getUUID(), target);
        // Рывок при подъёме на плечо — малый шанс вывиха плеча (второй этап, п. 7).
        var s = faygolover.rpmedicine.core.MedicalSettings.get();
        var m = Medical.state(target);
        if (m != null && s.dislocationsEnabled && JERK.nextDouble() < s.carryDislocationChance) {
            var arm = m.part(JERK.nextBoolean() ? faygolover.rpmedicine.core.BodyPart.LEFT_ARM : faygolover.rpmedicine.core.BodyPart.RIGHT_ARM);
            if (!arm.hasFracture()) arm.dislocated = true;
        }
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
        // Волоком: тело и так лежит на земле позади — отпустить там, где есть.
        e.setDeltaMovement(Vec3.ZERO);
        Medical.changed(e);
    }

    /** Положить тело на указанное место (Shift+ПКМ по блоку): блок рядом, место свободно. */
    public static boolean dropAt(ServerPlayer carrier, net.minecraft.core.BlockPos pos) {
        Entity e = CARRIED.get(carrier.getUUID());
        if (e == null) return false;
        Vec3 spot = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        var level = carrier.level();
        // На блок с полной коллизией не кладём; если место занято — уровень выше.
        if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
            if (!level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) return false;
            spot = spot.add(0, 1, 0);
        }
        if (carrier.position().distanceTo(spot) > ServerConfig.INTERACT_DISTANCE.get() + 1.0) return false;
        dropCarried(carrier);
        e.teleportTo(spot.x, spot.y, spot.z);
        carrier.displayClientMessage(Component.translatable("rpmedicine.msg.put_down"), true);
        return true;
    }

    /** Сбросить игрока, если его несут (пришёл в себя, умер, вышел). */
    public static void dropIfCarried(Entity e) {
        for (var en : new java.util.ArrayList<>(CARRIED.entrySet())) {
            if (en.getValue() != e) continue;
            ServerPlayer carrier = e.getServer() != null ? e.getServer().getPlayerList().getPlayer(en.getKey()) : null;
            if (carrier != null) dropCarried(carrier);
            else CARRIED.remove(en.getKey());
        }
    }

    /** Каждый тик несущего. */
    public static void tickCarrier(ServerPlayer carrier) {
        Entity e = CARRIED.get(carrier.getUUID());
        if (e == null) return;
        if (e.isRemoved() || e.level() != carrier.level() || Medical.isDown(carrier) || !Medical.isDown(e)
                || e.distanceTo(carrier) > 4.5) {
            dropCarried(carrier);
            return;
        }
        // Тело тянется позади медика, за руки: ноги волочатся (решения, п. 1.16).
        Vec3 back = carrier.getLookAngle().multiply(1, 0, 1);
        if (back.lengthSqr() < 1e-4) back = Vec3.directionFromRotation(0, carrier.getYRot());
        back = back.normalize().scale(-1.2);
        Vec3 want = carrier.position().add(back);
        Vec3 cur = e.position();
        // Плавно, без рывков; по высоте — как медик (ступеньки, склоны).
        Vec3 next = cur.lerp(new Vec3(want.x, carrier.getY(), want.z), 0.35);
        if (cur.distanceToSqr(next) > 1e-4) {
            if (e instanceof ServerPlayer tp) tp.connection.teleport(next.x, next.y, next.z, carrier.getYRot() + 180, tp.getXRot());
            else e.moveTo(next.x, next.y, next.z, carrier.getYRot() + 180, e.getXRot());
            e.fallDistance = 0;
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
