package faygolover.rpmedicine.server;

import faygolover.rpmedicine.capability.MedicalData;
import faygolover.rpmedicine.core.MedicalSettings;
import faygolover.rpmedicine.core.Surgery;
import faygolover.rpmedicine.entity.BodyStubEntity;
import faygolover.rpmedicine.hospital.HospitalBlocks;
import faygolover.rpmedicine.hospital.HospitalFunction;
import faygolover.rpmedicine.item.SurgicalInstrumentItem;
import faygolover.rpmedicine.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Обстановка операции (ТЗ третьего этапа, п. 4.2–4.3): где лежит пациент, стерилен ли инструмент,
 * есть ли у хирурга маска и перчатки. Отсюда множители успеха и заражения для {@link Surgery}.
 */
public final class SurgeryService {
    private SurgeryService() {}

    public enum Place { TABLE, BED, FIELD, FLOOR }

    public static Surgery.Context context(ServerPlayer surgeon, LivingEntity patient, ItemStack tool) {
        MedicalSettings s = MedicalSettings.get();
        Place place = place(surgeon, patient);
        double success = switch (place) {
            case TABLE -> s.surgeryTableSuccess;
            case BED -> s.surgeryBedSuccess;
            case FIELD -> s.surgeryFieldSuccess;
            case FLOOR -> s.surgeryFloorSuccess;
        };
        double infection = switch (place) {
            case TABLE -> s.surgeryTableInfection;
            case BED -> s.surgeryBedInfection;
            case FIELD -> s.surgeryFieldInfection;
            case FLOOR -> s.surgeryFloorInfection;
        };
        boolean sterile = !(tool.getItem() instanceof SurgicalInstrumentItem) || SurgicalInstrumentItem.isSterile(tool);
        if (!sterile) infection *= s.surgeryNonSterileFactor;
        if (!hasMask(surgeon)) infection *= s.surgeryNoMaskFactor;
        ItemStack gloves = gloves(surgeon);
        if (gloves.isEmpty()) {
            infection *= s.surgeryNoGlovesFactor;
        } else if (faygolover.rpmedicine.item.Wear.dirtyFor(gloves, patient.getUUID())) {
            // Грязные перчатки (другой пациент, долгая операция) — хуже, чем без них (замечание 09.10, И38).
            infection *= s.surgeryNoGlovesFactor * s.dirtyGlovesFactor;
        }
        return new Surgery.Context(sterile, success, infection);
    }

    /** Место: стол (операционный или с фиксацией), койка, полевой набор у хирурга, где угодно. */
    public static Place place(ServerPlayer surgeon, LivingEntity patient) {
        BlockPos bed = bedOf(patient);
        if (bed != null) {
            var st = patient.level().getBlockState(bed);
            if (HospitalBlocks.is(st, HospitalFunction.OPERATING_TABLE) || HospitalBlocks.is(st, HospitalFunction.RESTRAINT_TABLE)) return Place.TABLE;
            return Place.BED;
        }
        return surgeon.getInventory().contains(new ItemStack(ModItems.FIELD_SURGERY_KIT.get())) ? Place.FIELD : Place.FLOOR;
    }

    @Nullable
    private static BlockPos bedOf(LivingEntity e) {
        if (e instanceof ServerPlayer sp) {
            MedicalData d = Medical.data(sp);
            return d != null ? d.bedPos : null;
        }
        return e instanceof BodyStubEntity stub ? stub.bedPos() : null;
    }

    /** Маска надета (слот головы) и не отсырела. */
    public static boolean hasMask(ServerPlayer p) {
        ItemStack head = p.getItemBySlot(EquipmentSlot.HEAD);
        return head.is(ModItems.SURGICAL_MASK.get()) && !faygolover.rpmedicine.item.Wear.dirty(head);
    }

    /** Перчатки на хирурге: слот Curios «руки», без Curios — в инвентаре. Пусто — перчаток нет. */
    public static ItemStack gloves(ServerPlayer p) {
        if (faygolover.rpmedicine.integration.Integrations.curios()) {
            ItemStack c = faygolover.rpmedicine.integration.CuriosCompat.find(p, ModItems.SURGICAL_GLOVES.get());
            if (!c.isEmpty()) return c;
        }
        for (ItemStack st : p.getInventory().items) if (st.is(ModItems.SURGICAL_GLOVES.get())) return st;
        ItemStack off = p.getOffhandItem();
        return off.is(ModItems.SURGICAL_GLOVES.get()) ? off : ItemStack.EMPTY;
    }

    /** Чистые перчатки на хирурге. */
    public static boolean hasGloves(ServerPlayer p) {
        ItemStack g = gloves(p);
        return !g.isEmpty() && !faygolover.rpmedicine.item.Wear.dirty(g);
    }

    /** Шаг операции выполнен: перчатки изнашиваются (контекст считается каждый тик и предмет не меняет). */
    public static void stepDone(ServerPlayer surgeon, LivingEntity patient) {
        ItemStack g = gloves(surgeon);
        if (g.isEmpty()) return;
        boolean was = faygolover.rpmedicine.item.Wear.dirty(g);
        if (faygolover.rpmedicine.item.Wear.useGloves(g, patient.getUUID(), MedicalSettings.get().gloveUses) || was)
            surgeon.displayClientMessage(net.minecraft.network.chat.Component.translatable("rpmedicine.msg.gloves_dirty")
                    .withStyle(net.minecraft.ChatFormatting.GOLD), true);
    }

    /** Раз в 30 секунд: маска на лице отсыревает. */
    public static void tickMask(ServerPlayer p) {
        ItemStack head = p.getItemBySlot(EquipmentSlot.HEAD);
        if (head.is(ModItems.SURGICAL_MASK.get())
                && faygolover.rpmedicine.item.Wear.wearMask(head, 30, faygolover.rpmedicine.core.MedicalSettings.get().maskWearMinutes * 60))
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("rpmedicine.msg.mask_dirty")
                    .withStyle(net.minecraft.ChatFormatting.GOLD), true);
    }
}
