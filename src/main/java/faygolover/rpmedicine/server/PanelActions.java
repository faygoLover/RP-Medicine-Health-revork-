package faygolover.rpmedicine.server;

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
                if (p.slot() < 0 || p.slot() >= sp.getInventory().getContainerSize()) return;
                TreatmentService.startWithItem(sp, target, p.slot(), p.part());
            }
            case REMOVE_DRESSING -> remove(sp, target, m, p, Treatments.Removal.DRESSING);
            case REMOVE_TOURNIQUET -> remove(sp, target, m, p, Treatments.Removal.TOURNIQUET);
            case REMOVE_SPLINT -> remove(sp, target, m, p, Treatments.Removal.SPLINT);
            case REMOVE_OCCLUSIVE -> remove(sp, target, m, p, Treatments.Removal.OCCLUSIVE);
            case SEARCH -> SearchService.start(sp, target);
            case STOP_DRIP -> stopDrip(sp, target, m);
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

    /** Остановить капельницу: несовместимую кровь надо снять (второй этап, п. 4.2). */
    private static void stopDrip(ServerPlayer sp, LivingEntity target, MedicalState m) {
        String refuse = TreatmentService.actorRefusal(sp, target);
        if (refuse != null) {
            sp.displayClientMessage(Component.translatable(refuse).withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        if (m.bloodDripRemaining <= 0 && m.salineDripRemaining <= 0) return;
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
