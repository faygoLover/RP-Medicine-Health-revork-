package faygolover.rpmedicine.server;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import faygolover.rpmedicine.core.MedicalState;
import faygolover.rpmedicine.core.Treatments;
import faygolover.rpmedicine.network.PanelActionPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Действия с панели осмотра (п. 6.1, 6.2 ТЗ): перетаскивание предмета на часть тела, снятие
 * повязки, жгута, шины, наклейки пустой рукой, обыск. Всё проверяется заново на сервере.
 */
public final class PanelActions {
    private PanelActions() {}

    public static void handle(ServerPlayer sp, PanelActionPacket p) {
        if (p.kind() == PanelActionPacket.Kind.CANCEL) {
            ActionManager.cancel(sp, null);
            return;
        }
        LivingEntity target = target(sp, p.targetId());
        if (target == null) return;
        MedicalState m = Medical.state(target);
        if (m == null) return;
        switch (p.kind()) {
            case APPLY_ITEM -> {
                int slot = p.slot();
                // Из аптечки или подсумка: достать одну штуку в инвентарь и применить (замечание 47).
                if (slot >= 10000) slot = takeFromContainer(sp, slot - 10000);
                if (slot < 0 || slot >= sp.getInventory().getContainerSize()) return;
                TreatmentService.startWithItem(sp, target, slot, p.part());
            }
            case REMOVE_DRESSING -> remove(sp, target, m, p, Treatments.Removal.DRESSING);
            case REMOVE_TOURNIQUET -> remove(sp, target, m, p, Treatments.Removal.TOURNIQUET);
            case REMOVE_SPLINT -> remove(sp, target, m, p, Treatments.Removal.SPLINT);
            case REMOVE_OCCLUSIVE -> remove(sp, target, m, p, Treatments.Removal.OCCLUSIVE);
            case SEARCH -> SearchService.start(sp, target);
            case STOP_DRIP -> stopDrip(sp, target, m);
            case REMOVE_TUBE, REMOVE_AIRWAY -> removeAirway(sp, target, m, p.kind() == PanelActionPacket.Kind.REMOVE_TUBE);
            case RESTRAIN -> restrain(sp, target, m);
            case REMOVE_PROSTHESIS -> removeProsthesis(sp, target, m, p.part());
            case REDUCE -> {
                if (!sp.getMainHandItem().isEmpty()) {
                    sp.displayClientMessage(Component.translatable("rpmedicine.refuse.empty_hand").withStyle(ChatFormatting.YELLOW), true);
                    return;
                }
                TreatmentService.startHandAction(sp, target, p.part(), faygolover.rpmedicine.core.TreatmentAction.REDUCE, 4, 2);
            }
            default -> { }
        }
    }

    /** Достать одну штуку из ячейки контейнера в инвентарь; слот, куда легла, или −1. */
    private static int takeFromContainer(ServerPlayer sp, int code) {
        int invSlot = code / 100;
        int inner = code % 100;
        var inv = sp.getInventory();
        if (invSlot < 0 || invSlot >= inv.getContainerSize()) return -1;
        ItemStack bag = inv.getItem(invSlot);
        if (!(bag.getItem() instanceof faygolover.rpmedicine.item.MedicalContainerItem) || !bag.hasTag()
                || !bag.getTag().contains("Inventory")) return -1;
        var invTag = bag.getTag().getCompound("Inventory");
        var list = invTag.getList("Items", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int k = 0; k < list.size(); k++) {
            var it = list.getCompound(k);
            if (it.getInt("Slot") != inner) continue;
            ItemStack st = ItemStack.of(it);
            if (st.isEmpty()) return -1;
            ItemStack one = st.split(1);
            int free = inv.getFreeSlot();
            if (free < 0) {
                sp.displayClientMessage(Component.translatable("rpmedicine.refuse.inventory_full").withStyle(ChatFormatting.YELLOW), true);
                return -1;
            }
            inv.setItem(free, one);
            if (st.isEmpty()) list.remove(k);
            else {
                CompoundTag nt = st.save(new CompoundTag());
                nt.putInt("Slot", inner);
                list.set(k, nt);
            }
            invTag.put("Items", list);
            bag.getTag().put("Inventory", invTag);
            return free;
        }
        return -1;
    }

    /** Остановить капельницу: несовместимую кровь надо снять (второй этап, п. 4.2). */
    private static void stopDrip(ServerPlayer sp, LivingEntity target, MedicalState m) {
        String refuse = TreatmentService.actorRefusal(sp, target);
        if (refuse != null) {
            sp.displayClientMessage(Component.translatable(refuse).withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        // Без капельницы — вынуть катетер.
        if (m.bloodDripRemaining <= 0 && m.salineDripRemaining <= 0) {
            if (m.catheterPart < 0) return;
            if (target instanceof ServerPlayer tp) faygolover.rpmedicine.hospital.IvStandService.detachPatient(tp);
            m.catheterPart = -1;
            Medical.changed(target);
            sp.displayClientMessage(Component.translatable("rpmedicine.msg.catheter_removed"), true);
            ExamService.refreshFor(sp);
            return;
        }
        // Со стойки: недокапавшее остаётся в пакете.
        if (target instanceof ServerPlayer tp) faygolover.rpmedicine.hospital.IvStandService.detachPatient(tp);
        m.bloodDripRemaining = 0;
        m.bloodDripRate = 0;
        m.bloodDripType = null;
        m.bloodDripSpoiled = false;
        m.salineDripRemaining = 0;
        m.salineDripRate = 0;
        Medical.changed(target);
        sp.displayClientMessage(Component.translatable("rpmedicine.msg.drip_stopped"), true);
        ExamService.refreshFor(sp);
    }

    /** Извлечь трубку или воздуховод: пустой рукой. */
    private static void removeAirway(ServerPlayer sp, LivingEntity target, MedicalState m, boolean tube) {
        String refuse = TreatmentService.actorRefusal(sp, target);
        if (refuse != null) {
            sp.displayClientMessage(Component.translatable(refuse).withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        if (!sp.getMainHandItem().isEmpty()) {
            sp.displayClientMessage(Component.translatable("rpmedicine.refuse.empty_hand").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        if (tube ? !m.intubated : !m.airway) return;
        if (tube) m.intubated = false;
        else m.airway = false;
        Medical.changed(target);
        sp.displayClientMessage(Component.translatable(tube ? "rpmedicine.msg.extubated" : "rpmedicine.msg.airway_removed"), true);
        ExamService.refreshFor(sp);
    }

    /** Зафиксировать или освободить: только чужого и только на столе с фиксацией; освободить можно всегда. */
    private static void restrain(ServerPlayer sp, LivingEntity target, MedicalState m) {
        if (target == sp) return;
        if (!m.restrained && !faygolover.rpmedicine.hospital.HospitalService.onRestraintTable(target)) {
            sp.displayClientMessage(Component.translatable("rpmedicine.refuse.not_on_restraint_table").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        m.restrained = !m.restrained;
        Medical.changed(target);
        sp.displayClientMessage(Component.translatable(m.restrained ? "rpmedicine.msg.restrained" : "rpmedicine.msg.released"), true);
        if (target instanceof ServerPlayer tp)
            tp.displayClientMessage(Component.translatable(m.restrained ? "rpmedicine.msg.you_restrained" : "rpmedicine.msg.you_released")
                    .withStyle(ChatFormatting.GOLD), true);
        ExamService.refreshFor(sp);
    }

    private static void removeProsthesis(ServerPlayer sp, LivingEntity target, MedicalState m, faygolover.rpmedicine.core.BodyPart part) {
        var ps = m.part(part);
        if (ps.prosthesis == faygolover.rpmedicine.core.BodyPartState.Prosthesis.NONE) return;
        var item = switch (ps.prosthesis) {
            case FOOT -> faygolover.rpmedicine.registry.ModItems.PROSTHETIC_FOOT.get();
            case PEG_LEG -> faygolover.rpmedicine.registry.ModItems.PEG_LEG.get();
            default -> faygolover.rpmedicine.registry.ModItems.PROSTHETIC_HOOK.get();
        };
        ps.prosthesis = faygolover.rpmedicine.core.BodyPartState.Prosthesis.NONE;
        if (!sp.getInventory().add(new net.minecraft.world.item.ItemStack(item))) sp.drop(new net.minecraft.world.item.ItemStack(item), false);
        Medical.changed(target);
        sp.displayClientMessage(Component.translatable("rpmedicine.msg.prosthesis_removed"), true);
        ExamService.refreshFor(sp);
    }

    private static LivingEntity target(ServerPlayer sp, int id) {
        if (id < 0) return sp;
        Entity e = sp.level().getEntity(id);
        if (e instanceof LivingEntity le && Medical.isPatient(le) && sp.distanceTo(le) <= ExamService.PANEL_DISTANCE + 0.5) return le;
        return null;
    }

    private static void remove(ServerPlayer sp, LivingEntity target, MedicalState m, PanelActionPacket p, Treatments.Removal what) {
        String refuse = TreatmentService.actorRefusal(sp, target);
        if (refuse != null) {
            sp.displayClientMessage(Component.translatable(refuse).withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        if (!sp.getMainHandItem().isEmpty()) {
            sp.displayClientMessage(Component.translatable("rpmedicine.refuse.empty_hand").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        boolean cat = what == Treatments.Removal.TOURNIQUET
                && m.part(p.part()).tourniquet == faygolover.rpmedicine.core.BodyPartState.Tourniquet.CAT;
        if (Treatments.remove(m, p.part(), what)) {
            // Турникет многоразовый — возвращается в руки; жгут Эсмарха одноразовый (п. 6.2 ТЗ).
            if (cat) {
                net.minecraft.world.item.ItemStack back = new net.minecraft.world.item.ItemStack(faygolover.rpmedicine.registry.ModItems.TOURNIQUET.get());
                if (!sp.getInventory().add(back)) sp.drop(back, false);
            }
            Medical.changed(target);
            sp.displayClientMessage(Component.translatable("rpmedicine.msg.removed_" + what.name().toLowerCase(java.util.Locale.ROOT),
                    Component.translatable(p.part().translationKey())), true);
            ExamService.refreshFor(sp);
        }
    }
}
