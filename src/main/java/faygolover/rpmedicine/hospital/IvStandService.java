package faygolover.rpmedicine.hospital;

import faygolover.rpmedicine.core.BodyPart;
import faygolover.rpmedicine.core.MedicalState;
import faygolover.rpmedicine.server.Medical;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Стойка капельницы: повесить и снять пакет, взять шланг, подключить к катетеру пациента (замечание 35). */
public final class IvStandService {
    private IvStandService() {}

    private record Hose(ResourceKey<Level> dim, BlockPos pos) {}

    /** Медик держит шланг этой стойки и ещё не подключил его. */
    private static final Map<UUID, Hose> HOSES = new HashMap<>();

    /** ПКМ по стойке. true — действие выполнено. */
    public static boolean use(ServerPlayer sp, BlockPos pos) {
        if (!(sp.level().getBlockEntity(pos) instanceof IvStandBlockEntity be)) return false;
        ItemStack hand = sp.getMainHandItem();
        if (IvStandBlockEntity.isBag(hand)) {
            if (!be.hang(hand)) {
                msg(sp, "rpmedicine.iv.no_hook", ChatFormatting.YELLOW);
                return true;
            }
            if (!sp.getAbilities().instabuild) hand.shrink(1);
            msg(sp, "rpmedicine.iv.hung", ChatFormatting.WHITE);
            return true;
        }
        // Набранный шприц с препаратом для капельницы — ввести в пакет физраствора (решения, п. 1.16).
        if (hand.getItem() instanceof faygolover.rpmedicine.item.FilledSyringeItem) {
            var d = faygolover.rpmedicine.item.FilledSyringeItem.drugOf(hand);
            if (d == null || d.form() != faygolover.rpmedicine.core.Drug.Form.DRIP) {
                msg(sp, "rpmedicine.iv.drug_not_for_drip", ChatFormatting.YELLOW);
                return true;
            }
            String why = be.inject(d.id(), faygolover.rpmedicine.item.FilledSyringeItem.ml(hand));
            if (why != null) {
                msg(sp, why, ChatFormatting.YELLOW);
                return true;
            }
            if (!sp.getAbilities().instabuild) {
                hand.shrink(1);
                ItemStack dirty = new ItemStack(faygolover.rpmedicine.registry.ModItems.DIRTY_SYRINGE.get());
                if (!sp.getInventory().add(dirty)) sp.drop(dirty, false);
            }
            msg(sp, "rpmedicine.iv.drug_added", ChatFormatting.WHITE);
            return true;
        }
        if (!hand.isEmpty()) return false;
        if (sp.isShiftKeyDown()) {
            ItemStack bag = be.takeLast();
            if (bag == null) {
                msg(sp, be.linked() ? "rpmedicine.iv.bag_in_use" : "rpmedicine.iv.no_bags", ChatFormatting.YELLOW);
            } else {
                boolean used = bag.is(faygolover.rpmedicine.registry.ModItems.USED_IV_BAG.get());
                if (!sp.getInventory().add(bag)) sp.drop(bag, false);
                msg(sp, used ? "rpmedicine.iv.bag_used" : "rpmedicine.iv.bag_taken", ChatFormatting.WHITE);
            }
            return true;
        }
        if (be.linked()) {
            be.detach();
            msg(sp, "rpmedicine.iv.detached", ChatFormatting.WHITE);
            return true;
        }
        HOSES.put(sp.getUUID(), new Hose(sp.level().dimension(), pos.immutable()));
        msg(sp, "rpmedicine.iv.hose_taken", ChatFormatting.WHITE);
        return true;
    }

    /** Держит ли медик шланг. */
    public static boolean holdingHose(ServerPlayer sp) {
        return HOSES.containsKey(sp.getUUID());
    }

    /** ПКМ пустой рукой по пациенту со шлангом в руке: подключить к катетеру. true — событие обработано. */
    public static boolean onPatient(ServerPlayer sp, LivingEntity target) {
        Hose h = HOSES.remove(sp.getUUID());
        if (h == null) return false;
        if (!Medical.isPatient(target)) {
            msg(sp, "rpmedicine.iv.players_only", ChatFormatting.YELLOW);
            return true;
        }
        LivingEntity patient = target;
        ServerLevel level = sp.server.getLevel(h.dim());
        if (level == null || level != sp.level() || !(level.getBlockEntity(h.pos()) instanceof IvStandBlockEntity be)) {
            msg(sp, "rpmedicine.iv.stand_gone", ChatFormatting.YELLOW);
            return true;
        }
        MedicalState m = Medical.state(patient);
        if (m == null) return true;
        if (m.catheterPart < 0) {
            msg(sp, "rpmedicine.iv.need_catheter", ChatFormatting.YELLOW);
            return true;
        }
        double len = faygolover.rpmedicine.core.MedicalSettings.get().ivHoseLength;
        if (patient.position().distanceTo(Vec3.atBottomCenterOf(h.pos())) > len - 0.5) {
            msg(sp, "rpmedicine.iv.too_far", ChatFormatting.YELLOW);
            return true;
        }
        // Один пациент — одна стойка.
        detachPatient(patient);
        if (be.linked()) be.detach();
        be.link(patient, m.catheterPart);
        msg(sp, "rpmedicine.iv.linked", ChatFormatting.GREEN);
        return true;
    }

    /** Отсоединить пациента от стойки, к которой он подключён (снять капельницу в панели и т. п.). */
    public static void detachPatient(LivingEntity patient) {
        if (!(patient.level() instanceof ServerLevel level)) return;
        BlockPos c = patient.blockPosition();
        int r = (int) Math.ceil(faygolover.rpmedicine.core.MedicalSettings.get().ivHoseLength) + 1;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -2, -r), c.offset(r, 2, r))) {
            if (level.getBlockEntity(p) instanceof IvStandBlockEntity be && patient.getUUID().equals(be.patient())) be.detach();
        }
    }

    public static void forget(ServerPlayer sp) {
        HOSES.remove(sp.getUUID());
    }

    /** Часть тела катетера — для подсказок. */
    public static BodyPart arm(int ordinal) {
        return ordinal >= 0 && ordinal < BodyPart.values().length ? BodyPart.values()[ordinal] : BodyPart.RIGHT_ARM;
    }

    private static void msg(ServerPlayer sp, String key, ChatFormatting color) {
        sp.displayClientMessage(Component.translatable(key).withStyle(color), true);
    }
}
