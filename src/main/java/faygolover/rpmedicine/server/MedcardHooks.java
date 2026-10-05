package faygolover.rpmedicine.server;

import faygolover.rpmedicine.core.BodyPart;
import faygolover.rpmedicine.core.BloodType;
import faygolover.rpmedicine.core.Diagnostics;
import faygolover.rpmedicine.core.Injuries;
import faygolover.rpmedicine.core.Wound;
import faygolover.rpmedicine.core.WoundType;
import faygolover.rpmedicine.medcard.MedcardService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * События, после которых мод предлагает запись в медкарту (ТЗ второго этапа, п. 10): серьёзная травма,
 * клиническая смерть, переливание, анализ, извлечение пули.
 */
public final class MedcardHooks {
    private MedcardHooks() {}

    private static String part(BodyPart p) {
        return "#" + p.translationKey();
    }

    /** Серьёзная травма: переломы, огнестрел и осколки, артерия, пневмоторакс, внутреннее кровотечение, вывих. */
    public static void injury(LivingEntity target, Injuries.Report rep, @Nullable BodyPart hit) {
        injury(target, rep, hit, null, null);
    }

    /** Обстоятельства: кто ранил (игрок — по имени, существо — по виду), иначе вид раны. */
    static String circumstances(@Nullable net.minecraft.world.entity.Entity killer, @Nullable WoundType type) {
        if (killer instanceof net.minecraft.world.entity.player.Player pl) return pl.getGameProfile().getName();
        if (killer != null) return "#" + killer.getType().getDescriptionId();
        return type != null ? "#rpmedicine.wound." + type.id : "";
    }

    public static void injury(LivingEntity target, Injuries.Report rep, @Nullable BodyPart hit, @Nullable net.minecraft.world.entity.Entity killer,
                              @Nullable WoundType type) {
        if (target.getServer() == null || rep.outcomes.isEmpty()) return;
        BodyPart p = hit != null ? hit : rep.parts.isEmpty() ? null : rep.parts.get(0);
        if (p == null) return;
        var server = target.getServer();
        String circ = circumstances(killer, type);
        if (rep.has(Injuries.Outcome.OPEN_FRACTURE)) MedcardService.propose(server, target, "open_fracture", List.of(part(p)), "", circ);
        else if (rep.has(Injuries.Outcome.FRACTURE)) MedcardService.propose(server, target, "fracture", List.of(part(p)), "", circ);
        if (rep.has(Injuries.Outcome.RIB_FRACTURE)) MedcardService.propose(server, target, "rib_fracture", List.of(), "", circ);
        if (rep.has(Injuries.Outcome.ARTERIAL)) MedcardService.propose(server, target, "arterial", List.of(part(p)), "", circ);
        if (rep.has(Injuries.Outcome.PNEUMOTHORAX)) MedcardService.propose(server, target, "pneumothorax", List.of(), "", circ);
        if (rep.has(Injuries.Outcome.INTERNAL)) MedcardService.propose(server, target, "internal", List.of(part(p)), "", circ);
        for (BodyPart a : rep.amputated) MedcardService.propose(server, target, "traumatic_amputation", List.of(part(a)), "", circ);
        if (rep.has(Injuries.Outcome.DISLOCATION)) MedcardService.propose(server, target, "dislocation", List.of(part(p)), "", circ);
        // Третий этап: травма органа.
        for (faygolover.rpmedicine.core.Organ o : rep.organs) MedcardService.propose(server, target, "organ", List.of("#" + o.translationKey()), "", circ);
        for (Wound w : rep.wounds) {
            if (w.type == WoundType.GUNSHOT) MedcardService.propose(server, target, "gunshot", List.of(part(p)), "", circ);
            else if (w.type == WoundType.SHRAPNEL) MedcardService.propose(server, target, "shrapnel", List.of(part(p)), "", circ);
        }
    }

    public static void clinicalDeath(LivingEntity target) {
        if (target.getServer() != null) MedcardService.propose(target.getServer(), target, "clinical_death", List.of(), "");
    }

    public static void transfusion(ServerPlayer medic, LivingEntity target, @Nullable BloodType bag) {
        MedcardService.propose(medic.server, target, "transfusion", List.of(bag != null ? bag.label : "?"), medic.getGameProfile().getName());
    }

    public static void extraction(ServerPlayer medic, LivingEntity target, BodyPart p, boolean bullet) {
        MedcardService.propose(medic.server, target, bullet ? "bullet_removed" : "fragment_removed", List.of(part(p)), medic.getGameProfile().getName());
    }

    /** Укол или капельница (и любой антибиотик): препарат и доза. Таблетки и мази — не пишем. */
    public static void drug(ServerPlayer medic, LivingEntity target, ItemStack item, faygolover.rpmedicine.core.Drug d, double dose) {
        boolean antibiotic = d.id().contains("cillin") || d.id().contains("ceftriax") || d.id().contains("antibiotic");
        if (d.form() != faygolover.rpmedicine.core.Drug.Form.INJECTION && d.form() != faygolover.rpmedicine.core.Drug.Form.DRIP && !antibiotic) return;
        MedcardService.propose(medic.server, target, "drug", List.of("#" + item.getDescriptionId(),
                String.format(java.util.Locale.ROOT, "%.1f", dose).replace(".0", "")), medic.getGameProfile().getName());
    }

    public static void intubation(ServerPlayer medic, LivingEntity target) {
        MedcardService.propose(medic.server, target, "intubation", List.of(), medic.getGameProfile().getName());
    }

    /** Итоги операции, которые идут в медкарту. */
    public static final java.util.Set<String> SURGERY_KEYS = java.util.Set.of("incised", "surgery_closed", "surgery_closed_weak",
            "internal_stopped", "organ_repaired", "artery_repaired", "bone_fixated", "chest_drained", "foreign_all_removed",
            "amputated", "prosthesis_installed", "organ_removed", "organ_transplanted", "organ_transplanted_dead", "limb_reattached");

    public static void surgery(ServerPlayer medic, LivingEntity target, BodyPart p, String key) {
        MedcardService.propose(medic.server, target, "surgery_" + key, List.of(part(p)), medic.getGameProfile().getName());
    }

        /** Результат лаборатории — предложение записи в медкарту пациента. */
    public static void labResult(ServerPlayer medic, ItemStack tube, Diagnostics.Lab lab) {
        var t = tube.getTag();
        if (t == null || !t.hasUUID("PatientId")) return;
        var uuid = t.getUUID("PatientId");
        var c = faygolover.rpmedicine.medcard.MedcardStore.get(medic.server, uuid);
        long now = System.currentTimeMillis();
        c.add("lab", List.of(lab.bloodType() != null ? lab.bloodType().label : "?", String.valueOf((int) lab.hemoglobin()),
                String.format(java.util.Locale.ROOT, "%.1f", lab.leukocytes()),
                lab.sepsis() ? "#rpmedicine.medcard.yes" : "#rpmedicine.medcard.no"), "", medic.getGameProfile().getName(), true);
        faygolover.rpmedicine.medcard.MedcardStore.save(medic.server, c);
    }
}
