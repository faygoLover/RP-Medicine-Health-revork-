package faygolover.rpmedicine.server;

import faygolover.rpmedicine.capability.MedicalNbt;
import faygolover.rpmedicine.core.BloodType;
import faygolover.rpmedicine.core.Diagnostics;
import faygolover.rpmedicine.core.MedicalSettings;
import faygolover.rpmedicine.core.MedicalState;
import faygolover.rpmedicine.core.Skill;
import faygolover.rpmedicine.hospital.HospitalBlocks;
import faygolover.rpmedicine.hospital.HospitalFunction;
import faygolover.rpmedicine.item.BloodBagItem;
import faygolover.rpmedicine.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.SplittableRandom;

/**
 * Пробирка крови и лабораторный стол (ТЗ второго этапа, п. 3): шприц для забора набирает пробирку,
 * на лабораторном столе (функция {@code lab} датапака) за минуту — полный анализ.
 */
public final class LabService {
    private LabService() {}

    private static final SplittableRandom RANDOM = new SplittableRandom();
    private static final int LAB_MIN_LEVEL = 5;
    private static final double LAB_SECONDS = 60;

    /** Шприц набрал пробирку: в ней снимок крови пациента на этот момент. */
    public static void giveSample(ServerPlayer medic, LivingEntity patient) {
        MedicalState m = Medical.state(patient);
        if (m == null) return;
        ItemStack tube = new ItemStack(ModItems.BLOOD_SAMPLE.get());
        CompoundTag t = tube.getOrCreateTag();
        t.putString("Patient", patient.getName().getString());
        t.putUUID("PatientId", patient instanceof faygolover.rpmedicine.entity.BodyStubEntity stub ? stub.ownerId() : patient.getUUID());
        t.putLong("Taken", System.currentTimeMillis());
        t.put("Medical", MedicalNbt.write(m));
        if (!medic.getInventory().add(tube)) medic.drop(tube, false);
    }

    @Nullable
    public static String patientName(ItemStack tube) {
        CompoundTag t = tube.getTag();
        return t != null && t.contains("Patient") ? t.getString("Patient") : null;
    }

    /** ПКМ по лабораторному столу с пробиркой в руке. */
    public static boolean onUseBlock(ServerPlayer sp, BlockPos pos) {
        if (!HospitalBlocks.is(sp.level().getBlockState(pos), HospitalFunction.LAB)) return false;
        ItemStack tube = sp.getMainHandItem();
        if (!tube.is(ModItems.BLOOD_SAMPLE.get()) || !tube.hasTag()) return false;
        if (Medical.isDown(sp)) return true;
        int level = Medical.medicineLevel(sp);
        double seconds = Skill.applySeconds(LAB_SECONDS, Math.max(level, 2), false, 1.0, MedicalSettings.get());
        ActionManager.start(new LabAction(sp, pos, tube.copy(), (int) Math.round(seconds * 20), level));
        return true;
    }

    static final class LabAction extends ActionManager.TimedAction {
        private final BlockPos lab;
        private final ItemStack original;
        private final int level;

        LabAction(ServerPlayer actor, BlockPos lab, ItemStack original, int ticks, int level) {
            super(actor, ticks);
            this.lab = lab;
            this.original = original;
            this.level = level;
        }

        @Override
        public String label() {
            return "rpmedicine.action.lab";
        }

        @Override
        public boolean slowsActor() {
            return false;
        }

        @Override
        public String checkContinue() {
            if (actor.distanceToSqr(Vec3.atCenterOf(lab)) > 5 * 5) return "rpmedicine.action.lab_left";
            if (!HospitalBlocks.is(actor.level().getBlockState(lab), HospitalFunction.LAB)) return "rpmedicine.action.lab_left";
            if (!ItemStack.isSameItemSameTags(actor.getMainHandItem(), original)) return "rpmedicine.action.item_changed";
            return null;
        }

        @Override
        public void complete() {
            ItemStack tube = actor.getMainHandItem();
            if (!actor.getAbilities().instabuild) {
                tube.shrink(1);
                // После анализа — использованная пробирка (стерилизатор вернёт чистую).
                ItemStack used = new ItemStack(ModItems.DIRTY_TEST_TUBE.get());
                if (!actor.getInventory().add(used)) actor.drop(used, false);
            }
            MedicalSettings s = MedicalSettings.get();
            double err = Math.min(s.maxErrorChance, Math.max(0, LAB_MIN_LEVEL - level) * s.underLevelErrorPerLevel);
            if (RANDOM.nextDouble() < err) {
                actor.sendSystemMessage(Component.translatable("rpmedicine.lab.failed").withStyle(ChatFormatting.RED));
                return;
            }
            MedicalState m = new MedicalState(s);
            MedicalNbt.read(m, original.getTag().getCompound("Medical"), s);
            Diagnostics.Lab lab = Diagnostics.lab(m, s);
            // Бланк в окне (замечание 06.10), а не строки в чате.
            faygolover.rpmedicine.network.Network.send(actor, form(original, lab, actor.getOffhandItem()));
            MedcardHooks.labResult(actor, original, lab);
        }
    }

    /** Бланк анализа для окна на клиенте. */
    public static faygolover.rpmedicine.network.LabResultPacket form(ItemStack tube, Diagnostics.Lab lab, ItemStack offhand) {
        String name = patientName(tube);
        byte compat = -1;
        String bagLabel = "";
        if (offhand.getItem() instanceof BloodBagItem) {
            BloodType bag = BloodBagItem.type(offhand);
            if (bag == null || lab.bloodType() == null) {
                compat = 0;
            } else {
                compat = (byte) (bag.canDonateTo(lab.bloodType()) ? 1 : 2);
                bagLabel = bag.label;
            }
        }
        return new faygolover.rpmedicine.network.LabResultPacket(name != null ? name : "", lab.bloodType() != null ? lab.bloodType().label : "",
                new double[]{lab.hemoglobin(), lab.leukocytes(), lab.alt(), lab.creatinine(), lab.troponin(), lab.albumin(),
                        lab.triglycerides(), lab.glucose(), lab.b12()}, lab.sepsis(), compat, bagLabel);
    }

    /** Строки результата анализа. */
    public static java.util.List<Component> report(ItemStack tube, Diagnostics.Lab lab, ItemStack offhand) {
        java.util.List<Component> out = new java.util.ArrayList<>();
        String name = patientName(tube);
        out.add(Component.translatable("rpmedicine.lab.title", name != null ? name : "?").withStyle(ChatFormatting.GOLD));
        out.add(Component.translatable("rpmedicine.lab.blood_type", lab.bloodType() != null ? lab.bloodType().label : "?"));
        out.add(Component.translatable("rpmedicine.lab.hemoglobin", (int) lab.hemoglobin()));
        out.add(Component.translatable("rpmedicine.lab.leukocytes", String.format(java.util.Locale.ROOT, "%.1f", lab.leukocytes())));
        out.add(Component.translatable(lab.sepsis() ? "rpmedicine.lab.sepsis_yes" : "rpmedicine.lab.sepsis_no")
                .withStyle(lab.sepsis() ? ChatFormatting.RED : ChatFormatting.WHITE));
        // Органы (третий этап): выше нормы — красным.
        out.add(Component.translatable("rpmedicine.lab.alt", (int) lab.alt()).withStyle(lab.alt() > 40 ? ChatFormatting.RED : ChatFormatting.WHITE));
        out.add(Component.translatable("rpmedicine.lab.creatinine", (int) lab.creatinine())
                .withStyle(lab.creatinine() > 110 ? ChatFormatting.RED : ChatFormatting.WHITE));
        out.add(Component.translatable("rpmedicine.lab.troponin", (int) lab.troponin())
                .withStyle(lab.troponin() > 14 ? ChatFormatting.RED : ChatFormatting.WHITE));
        // Питание — только анализом.
        out.add(Component.translatable("rpmedicine.lab.albumin", (int) lab.albumin())
                .withStyle(lab.albumin() < 35 ? ChatFormatting.RED : lab.albumin() > 50 ? ChatFormatting.GOLD : ChatFormatting.WHITE));
        out.add(Component.translatable("rpmedicine.lab.triglycerides", String.format(java.util.Locale.ROOT, "%.1f", lab.triglycerides()))
                .withStyle(lab.triglycerides() < 0.5 ? ChatFormatting.RED : lab.triglycerides() > 1.7 ? ChatFormatting.GOLD : ChatFormatting.WHITE));
        out.add(Component.translatable("rpmedicine.lab.glucose", String.format(java.util.Locale.ROOT, "%.1f", lab.glucose()))
                .withStyle(lab.glucose() < 3.9 ? ChatFormatting.RED : lab.glucose() > 6.1 ? ChatFormatting.GOLD : ChatFormatting.WHITE));
        out.add(Component.translatable("rpmedicine.lab.b12", (int) lab.b12())
                .withStyle(lab.b12() < 200 ? ChatFormatting.RED : ChatFormatting.WHITE));
        // Пакет крови во второй руке — проверка совместимости.
        if (offhand.getItem() instanceof BloodBagItem) {
            BloodType bag = BloodBagItem.type(offhand);
            if (bag == null || lab.bloodType() == null) {
                out.add(Component.translatable("rpmedicine.lab.compat_unknown"));
            } else {
                boolean ok = bag.canDonateTo(lab.bloodType());
                out.add(Component.translatable(ok ? "rpmedicine.lab.compat_yes" : "rpmedicine.lab.compat_no", bag.label)
                        .withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED));
            }
        }
        return out;
    }
}
