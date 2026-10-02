package faygolover.rpmedicine.hospital;

import faygolover.rpmedicine.capability.MedicalData;
import faygolover.rpmedicine.config.ServerConfig;
import faygolover.rpmedicine.core.MedicalSettings;
import faygolover.rpmedicine.core.MedicalState;
import faygolover.rpmedicine.core.StepInput;
import faygolover.rpmedicine.entity.BodyStubEntity;
import faygolover.rpmedicine.network.MonitorPacket;
import faygolover.rpmedicine.network.Network;
import faygolover.rpmedicine.registry.ModSounds;
import faygolover.rpmedicine.server.ActionManager;
import faygolover.rpmedicine.server.CarryService;
import faygolover.rpmedicine.server.DownedService;
import faygolover.rpmedicine.server.Medical;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Госпиталь (ТЗ второго этапа, п. 2): больничная койка, монитор показателей, стойка капельницы.
 * Блоки — чужие, их функции задаёт датапак ({@link HospitalBlocks}).
 */
public final class HospitalService {
    private HospitalService() {}

    /** Блоки вокруг пациента ищутся не чаще раза в столько тиков (п. 14 ТЗ второго этапа). */
    private static final int SCAN_TICKS = 40;
    private static final int ALARM_TICKS = 40;

    // ------------------------------------------------------------------ койка

    /** Точка, где лежит пациент: центр верхней грани блока. */
    public static Vec3 bedSpot(Level level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        VoxelShape shape = st.getCollisionShape(level, pos);
        double top = shape.isEmpty() ? 0.5625 : Math.min(1.5, shape.max(Direction.Axis.Y));
        return new Vec3(pos.getX() + 0.5, pos.getY() + top, pos.getZ() + 0.5);
    }

    /** Койка, на которой лежит человек, или null. */
    @Nullable
    public static BlockPos bedOf(@Nullable Entity e) {
        if (e instanceof Player p) {
            MedicalData d = Medical.data(p);
            return d != null ? d.bedPos : null;
        }
        if (e instanceof BodyStubEntity stub) return stub.bedPos();
        return null;
    }

    public static boolean isOnBed(@Nullable Entity e) {
        return bedOf(e) != null;
    }

    /** Занята ли койка кем-то, кроме {@code except}. */
    public static boolean occupied(Level level, BlockPos pos, @Nullable Entity except) {
        return !level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(2),
                x -> x != except && x.isAlive() && pos.equals(bedOf(x))).isEmpty();
    }

    /**
     * ПКМ по блоку: лечь на койку пустой рукой или положить на неё того, кого несёшь.
     *
     * @return true — событие поглощено
     */
    public static boolean onUseBlock(ServerPlayer sp, BlockPos pos) {
        BlockState st = sp.level().getBlockState(pos);
        if (!HospitalBlocks.isBed(st)) return false;
        if (sp.distanceToSqr(Vec3.atCenterOf(pos)) > sq(ServerConfig.INTERACT_DISTANCE.get() + 1.0)) return false;
        Entity carried = CarryService.carried(sp);
        if (carried instanceof LivingEntity patient) {
            if (occupied(sp.level(), pos, patient)) {
                sp.displayClientMessage(Component.translatable("rpmedicine.bed.occupied").withStyle(ChatFormatting.YELLOW), true);
                return true;
            }
            CarryService.dropCarried(sp);
            placeOnBed(patient, pos);
            sp.displayClientMessage(Component.translatable("rpmedicine.bed.placed", patient.getDisplayName()), true);
            return true;
        }
        if (sp.isShiftKeyDown() || Medical.isDown(sp) || !sp.getMainHandItem().isEmpty()) return false;
        MedicalData d = Medical.data(sp);
        if (d == null) return false;
        if (pos.equals(d.bedPos)) return true;
        if (occupied(sp.level(), pos, sp)) {
            sp.displayClientMessage(Component.translatable("rpmedicine.bed.occupied").withStyle(ChatFormatting.YELLOW), true);
            return true;
        }
        placeOnBed(sp, pos);
        sp.displayClientMessage(Component.translatable("rpmedicine.bed.lie_down"), true);
        return true;
    }

    /** Положить человека на койку (койка уже проверена). */
    public static void placeOnBed(LivingEntity e, BlockPos pos) {
        Vec3 spot = bedSpot(e.level(), pos);
        if (e instanceof ServerPlayer sp) {
            MedicalData d = Medical.data(sp);
            if (d == null) return;
            if (sp.isPassenger()) sp.stopRiding();
            ActionManager.cancel(sp, null);
            sp.stopUsingItem();
            sp.setSprinting(false);
            d.bedPos = pos.immutable();
            d.bedQuarter = (byte) (Math.floorMod(Math.round(sp.getYRot() / 90f), 4));
            d.hospitalScanTick = Long.MIN_VALUE / 2;
            sp.teleportTo(spot.x, spot.y, spot.z);
            sp.setDeltaMovement(Vec3.ZERO);
            Medical.changed(sp);
            DownedService.broadcastPose(sp);
        } else if (e instanceof BodyStubEntity stub) {
            stub.setBedPos(pos.immutable());
            stub.teleportTo(spot.x, spot.y, spot.z);
            stub.setDeltaMovement(Vec3.ZERO);
        }
    }

    /** Встать с койки (или снять с неё). {@code standUp} — переставить рядом с койкой. */
    public static void leaveBed(ServerPlayer sp, boolean standUp) {
        MedicalData d = Medical.data(sp);
        if (d == null || d.bedPos == null) return;
        BlockPos bed = d.bedPos;
        d.bedPos = null;
        d.monitorPos = null;
        if (standUp) {
            Vec3 out = standUpSpot(sp.serverLevel(), bed, sp);
            sp.teleportTo(out.x, out.y, out.z);
        }
        Medical.changed(sp);
        DownedService.broadcastPose(sp);
    }

    /** Свободное место рядом с койкой, где помещается стоящий игрок. */
    private static Vec3 standUpSpot(ServerLevel level, BlockPos bed, Entity e) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos p = bed.relative(dir);
            for (int dy = 0; dy <= 1; dy++) {
                BlockPos feet = p.above(dy);
                Vec3 v = new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
                AABB box = e.getType().getDimensions().makeBoundingBox(v);
                if (level.noCollision(box) && !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty())
                    return v;
            }
        }
        return bedSpot(level, bed).add(0, 0.1, 0);
    }

    // ------------------------------------------------------------------ тики

    /** Каждый тик игрока на сервере: удержание на койке, кэш блоков рядом, тревога монитора. */
    public static void tickPlayer(ServerPlayer sp, MedicalData d) {
        MedicalState m = d.state;
        BlockPos bed = d.bedPos;
        long now = sp.serverLevel().getGameTime();
        if (bed != null) {
            if (sp.isPassenger()) {
                // Подняли и несут — уже не на койке.
                leaveBed(sp, false);
                return;
            }
            if ((now + sp.getId()) % 10 == 0 && !HospitalBlocks.isBed(sp.level().getBlockState(bed))) {
                leaveBed(sp, false);
                return;
            }
            if (!m.isDown() && sp.isShiftKeyDown()) {
                leaveBed(sp, true);
                sp.displayClientMessage(Component.translatable("rpmedicine.bed.stood_up"), true);
                return;
            }
            Vec3 spot = bedSpot(sp.level(), bed);
            if (sp.position().distanceToSqr(spot) > 0.04) {
                sp.teleportTo(spot.x, spot.y, spot.z);
                sp.setDeltaMovement(Vec3.ZERO);
            }
        }
        boolean drip = m.salineDripRemaining > 0;
        if ((bed != null || drip) && now - d.hospitalScanTick >= SCAN_TICKS) {
            d.hospitalScanTick = now;
            d.monitorPos = bed != null ? HospitalBlocks.findNearest(sp.level(), bed, HospitalFunction.MONITOR, HospitalBlocks.radius(HospitalFunction.MONITOR)) : null;
            d.nearIvStand = drip && HospitalBlocks.findNearest(sp.level(), sp.blockPosition(), HospitalFunction.IV_STAND,
                    HospitalBlocks.radius(HospitalFunction.IV_STAND)) != null;
        } else if (bed == null && !drip) {
            d.monitorPos = null;
            d.nearIvStand = false;
        }
        if (d.monitorPos != null && now - d.lastAlarmTick >= ALARM_TICKS && alarm(m)) {
            d.lastAlarmTick = now;
            playAlarm(sp.serverLevel(), d.monitorPos);
        }
    }

    /** Заглушка на койке: койку убрали или тело унесли — уже не на койке; тревога монитора. */
    public static void tickStub(BodyStubEntity stub) {
        BlockPos bed = stub.bedPos();
        if (bed == null || (stub.tickCount + stub.getId()) % ALARM_TICKS != 0) return;
        if (stub.isPassenger() || !HospitalBlocks.isBed(stub.level().getBlockState(bed))) {
            stub.setBedPos(null);
            return;
        }
        if (alarm(stub.state())) {
            BlockPos mon = HospitalBlocks.findNearest(stub.level(), bed, HospitalFunction.MONITOR, HospitalBlocks.radius(HospitalFunction.MONITOR));
            if (mon != null) playAlarm((ServerLevel) stub.level(), mon);
        }
    }

    private static boolean alarm(MedicalState m) {
        return m.heart != MedicalState.Heart.NORMAL || m.spo2 < MedicalSettings.get().monitorAlarmSpo2;
    }

    private static void playAlarm(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, ModSounds.MONITOR_ALARM.get(), SoundSource.BLOCKS, 1.0f, 1.6f);
    }

    /** Условия на шаг физиологии: койка ускоряет заживление, стойка позволяет капельнице идти на ходу. */
    public static void applyConditions(MedicalData d, StepInput in, MedicalSettings s) {
        if (d.bedPos != null) {
            in.healFactor *= s.bedHealFactor;
            in.bloodRegenFactor *= s.bedBloodRegenFactor;
            in.brainRecoveryFactor *= s.bedBrainRecoveryFactor;
            in.still = true;
        }
        if (d.nearIvStand) in.still = true;
    }

    // ------------------------------------------------------------------ монитор

    /** Пациент на койке, к которой привязан монитор (ближайший), или null. */
    @Nullable
    public static LivingEntity monitorPatient(Level level, BlockPos monitor) {
        int r = HospitalBlocks.radius(HospitalFunction.MONITOR);
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(monitor).inflate(r + 1.5), HospitalService::isOnBed)) {
            BlockPos bed = bedOf(e);
            if (bed == null) continue;
            if (Math.abs(bed.getX() - monitor.getX()) > r || Math.abs(bed.getY() - monitor.getY()) > r || Math.abs(bed.getZ() - monitor.getZ()) > r) continue;
            double dist = bed.distSqr(monitor);
            if (dist < bestDist) {
                bestDist = dist;
                best = e;
            }
        }
        return best;
    }

    /** Игрок смотрит на монитор: отправить цифры пациента (п. 2.3). */
    public static void onMonitorRequest(ServerPlayer viewer, BlockPos pos) {
        if (viewer.distanceToSqr(Vec3.atCenterOf(pos)) > sq(ServerConfig.MONITOR_VIEW_DISTANCE.get() + 1)) return;
        if (!viewer.level().isLoaded(pos) || !HospitalBlocks.is(viewer.level().getBlockState(pos), HospitalFunction.MONITOR)) return;
        if (Medical.isDown(viewer)) return;
        if (Medical.medicineLevel(viewer) < ServerConfig.MONITOR_MIN_LEVEL.get()) {
            Network.send(viewer, MonitorPacket.locked(pos));
            return;
        }
        LivingEntity patient = monitorPatient(viewer.level(), pos);
        MedicalState m = Medical.state(patient);
        Network.send(viewer, m == null ? MonitorPacket.empty(pos) : MonitorPacket.of(pos, m));
    }

    private static double sq(double v) {
        return v * v;
    }
}
