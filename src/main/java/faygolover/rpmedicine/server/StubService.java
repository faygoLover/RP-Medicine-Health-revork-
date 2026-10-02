package faygolover.rpmedicine.server;

import faygolover.rpmedicine.RpMedicine;
import faygolover.rpmedicine.capability.MedicalNbt;
import faygolover.rpmedicine.config.ServerConfig;
import faygolover.rpmedicine.core.MedicalSettings;
import faygolover.rpmedicine.core.MedicalState;
import faygolover.rpmedicine.core.Physiology;
import faygolover.rpmedicine.entity.BodyStubEntity;
import faygolover.rpmedicine.hospital.HospitalBlocks;
import faygolover.rpmedicine.hospital.HospitalService;
import faygolover.rpmedicine.integration.CuriosCompat;
import faygolover.rpmedicine.integration.Integrations;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Заглушка — тело офлайн-игрока (п. 5.5 ТЗ).
 * <pre>
 * Вышел            | «без смерти» включён          | выключен
 * в нокдауне       | клиническая смерть, заглушка  | смерть
 * в клин. смерти   | заглушка                      | —
 * в обмороке       | заглушка, обморок как обычно  | то же
 * </pre>
 * Игрок при входе оказывается на месте заглушки в том состоянии, в каком она сейчас.
 * Второй этап: выход на больничной койке в любом состоянии (кроме нокдауна без режима «без смерти»)
 * тоже оставляет заглушку — на койке; при входе игрок снова лежит на ней.
 */
public final class StubService {
    private StubService() {}

    public static void onLogout(ServerPlayer sp) {
        CarryService.onLogout(sp);
        ActionManager.cancel(sp, null);
        MedicalState m = Medical.state(sp);
        if (m == null || sp.isDeadOrDying()) return;
        MedicalSettings s = MedicalSettings.get();
        BlockPos bed = HospitalService.bedOf(sp);
        switch (m.down) {
            case NONE -> {
                if (bed != null) leaveStub(sp, m);
            }
            case KNOCKDOWN -> {
                if (s.noDeathMode) {
                    Physiology.enterClinical(m);
                    leaveStub(sp, m);
                } else {
                    DownedService.kill(sp, DownedService.BRAIN_DEATH, null);
                }
            }
            case CLINICAL, FAINT -> leaveStub(sp, m);
        }
    }

    private static void leaveStub(ServerPlayer sp, MedicalState m) {
        if (!ServerConfig.STUBS_ENABLED.get()) return;
        ServerLevel level = sp.serverLevel();
        BodyStubEntity stub = BodyStubEntity.create(level);
        stub.initFrom(sp, Medical.traits(sp));
        stub.state().copyFrom(m);
        Inventory inv = sp.getInventory();
        for (int i = 0; i < BodyStubEntity.INV_SIZE && i < inv.getContainerSize(); i++) {
            stub.inventory().setItem(i, inv.getItem(i).copy());
        }
        inv.clearContent();
        if (Integrations.curios()) CuriosCompat.moveToStub(sp, stub);
        BlockPos bed = HospitalService.bedOf(sp);
        if (bed != null) stub.setBedPos(bed);
        level.addFreshEntity(stub);
        StubRegistry.Record r = new StubRegistry.Record();
        r.entityId = stub.getUUID();
        fill(r, stub);
        StubRegistry.get(sp.server).put(sp.getUUID(), r);
        RpMedicine.LOGGER.info("RP Medicine: {} вышел лежачим ({}), оставлена заглушка в {} {}", sp.getGameProfile().getName(),
                m.down, level.dimension().location(), stub.blockPosition());
    }

    private static void fill(StubRegistry.Record r, BodyStubEntity stub) {
        r.dimension = stub.level().dimension();
        r.pos = stub.position();
        r.yaw = stub.getYRot();
        CompoundTag snap = new CompoundTag();
        snap.put("Medical", MedicalNbt.write(stub.state()));
        snap.put("Inv", stub.saveInventory());
        snap.put("Curios", stub.saveCurios());
        if (stub.bedPos() != null) snap.putLong("Bed", stub.bedPos().asLong());
        r.snapshot = snap;
    }

    /** Заглушка изменилась (лечение, ранение, обыск): обновить снимок. */
    public static void snapshot(BodyStubEntity stub) {
        if (stub.level().isClientSide || stub.getServer() == null) return;
        StubRegistry reg = StubRegistry.get(stub.getServer());
        StubRegistry.Record r = reg.get(stub.ownerId());
        if (r == null || !r.entityId.equals(stub.getUUID())) {
            // Игрок уже вошёл (тело восстановлено из снимка) — лишняя сущность исчезает.
            stub.discard();
            return;
        }
        fill(r, stub);
        reg.setDirty();
    }

    /** Мозг заглушки погиб (режим «без смерти» выключен). */
    public static void onStubBrainDeath(BodyStubEntity stub) {
        killStub(stub, DownedService.BRAIN_DEATH, null);
    }

    public static void killStub(BodyStubEntity stub, ResourceKey<DamageType> type, @Nullable Entity killer) {
        if (stub.isRemoved() || stub.isDeadOrDying()) return;
        DamageSource src = DownedService.source((ServerLevel) stub.level(), type, killer);
        stub.setHealth(0);
        stub.die(src);
    }

    /** Заглушку убили: игрок войдёт мёртвым, предметы — трупом Corpse на месте тела. */
    public static void onStubKilled(BodyStubEntity stub, DamageSource src) {
        if (stub.getServer() == null) return;
        StubRegistry reg = StubRegistry.get(stub.getServer());
        StubRegistry.Record r = reg.get(stub.ownerId());
        if (r == null || !r.entityId.equals(stub.getUUID())) return;
        fill(r, stub);
        r.dead = true;
        reg.setDirty();
        RpMedicine.LOGGER.info("RP Medicine: заглушка игрока {} убита ({})", stub.ownerName(), src.getMsgId());
    }

    public static void onLogin(ServerPlayer sp) {
        StubRegistry reg = StubRegistry.get(sp.server);
        StubRegistry.Record r = reg.remove(sp.getUUID());
        if (r == null) return;
        ServerLevel level = sp.server.getLevel(r.dimension);
        if (level == null) level = sp.server.overworld();
        Entity e = level.getEntity(r.entityId);
        BodyStubEntity stub = e instanceof BodyStubEntity b && b.isAlive() ? b : null;
        MedicalState m = Medical.state(sp);
        if (m == null) return;
        Inventory inv = sp.getInventory();
        List<ItemStack> leftovers = new java.util.ArrayList<>();
        // Всё, что успело оказаться в инвентаре игрока, сохраняем и возвращаем после.
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (!inv.getItem(i).isEmpty()) leftovers.add(inv.getItem(i).copy());
        }
        inv.clearContent();
        List<BodyStubEntity.CurioEntry> curios;
        BlockPos bed;
        if (stub != null) {
            bed = stub.bedPos();
            m.copyFrom(stub.state());
            for (int i = 0; i < BodyStubEntity.INV_SIZE; i++) inv.setItem(i, stub.inventory().getItem(i).copy());
            curios = List.copyOf(stub.curios);
            r.pos = stub.position();
            r.yaw = stub.getYRot();
            stub.discard();
        } else {
            CompoundTag snap = r.snapshot;
            bed = snap.contains("Bed") ? BlockPos.of(snap.getLong("Bed")) : null;
            MedicalNbt.read(m, snap.getCompound("Medical"), MedicalSettings.get());
            BodyStubEntity tmp = BodyStubEntity.create(level);
            tmp.loadInventory(snap.getList("Inv", Tag.TAG_COMPOUND));
            tmp.loadCurios(snap.getList("Curios", Tag.TAG_COMPOUND));
            for (int i = 0; i < BodyStubEntity.INV_SIZE; i++) inv.setItem(i, tmp.inventory().getItem(i).copy());
            curios = List.copyOf(tmp.curios);
        }
        if (Integrations.curios()) leftovers.addAll(CuriosCompat.restoreFromStub(sp, curios));
        else for (BodyStubEntity.CurioEntry c : curios) leftovers.add(c.stack().copy());
        for (ItemStack s : leftovers) if (!inv.add(s)) sp.drop(s, false);

        sp.teleportTo(level, r.pos.x, r.pos.y, r.pos.z, r.yaw, sp.getXRot());
        var d = Medical.data(sp);
        if (d != null) d.bedPos = null;
        Medical.changed(sp);
        SelfSync.forceSync(sp);
        if (r.dead) {
            DownedService.kill(sp, DownedService.BRAIN_DEATH, null);
            return;
        }
        // Тело лежало на койке — игрок снова на ней.
        if (bed != null && level.isLoaded(bed) && HospitalBlocks.isBed(level.getBlockState(bed)) && !HospitalService.occupied(level, bed, sp)) {
            HospitalService.placeOnBed(sp, bed);
        } else if (m.isDown()) {
            DownedService.broadcastDowned(sp, true);
        }
    }
}
