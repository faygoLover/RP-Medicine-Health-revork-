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

    /**
     * Находки, ещё не выявленные врачом (замечание 65): травмы копятся у пациента и попадают в медкарту
     * предложениями, только когда врач с медкартой в инвентаре осмотрит его ({@link #examined}).
     */
    private record Finding(String key, List<String> args, String author, String circumstances, long time) {}

    private static final java.util.Map<java.util.UUID, java.util.List<Finding>> PENDING = new java.util.HashMap<>();
    private static final long FINDING_TTL_MS = 24L * 3600 * 1000;

    private static void find(LivingEntity target, String key, List<String> args, String author, String circ) {
        java.util.UUID owner = MedcardService.ownerOf(target);
        if (owner == null) return;
        var list = PENDING.computeIfAbsent(owner, k -> new java.util.ArrayList<>());
        long now = System.currentTimeMillis();
        list.removeIf(f -> now - f.time() > FINDING_TTL_MS || f.key().equals(key) && f.args().equals(args));
        list.add(new Finding(key, args, author, circ == null ? "" : circ, now));
        if (list.size() > 30) list.remove(0);
    }

    /** У медика есть медкарта (любая) — он может вносить записи. */
    public static boolean hasCard(ServerPlayer medic) {
        if (medic.getAbilities().instabuild) return true;
        return medic.getInventory().contains(new ItemStack(faygolover.rpmedicine.registry.ModItems.MEDCARD.get()))
                || medic.getInventory().items.stream().anyMatch(st -> st.getItem() instanceof faygolover.rpmedicine.item.MedcardItem);
    }

    /** Врач осмотрел пациента: выявленные находки — в медкарту предложениями от его имени. */
    public static void examined(ServerPlayer medic, LivingEntity patient) {
        if (medic == patient || medic.getServer() == null || !hasCard(medic)) return;
        java.util.UUID owner = MedcardService.ownerOf(patient);
        var list = owner == null ? null : PENDING.remove(owner);
        if (list == null) return;
        String who = medic.getGameProfile().getName();
        for (Finding f : list) MedcardService.propose(medic.getServer(), patient, f.key(), f.args(), f.author().isEmpty() ? who : f.author(), f.circumstances());
    }

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
        String circ = circumstances(killer, type);
        if (rep.has(Injuries.Outcome.OPEN_FRACTURE)) find(target, "open_fracture", List.of(part(p)), "", circ);
        else if (rep.has(Injuries.Outcome.FRACTURE)) find(target, "fracture", List.of(part(p)), "", circ);
        if (rep.has(Injuries.Outcome.RIB_FRACTURE)) find(target, "rib_fracture", List.of(), "", circ);
        if (rep.has(Injuries.Outcome.ARTERIAL)) find(target, "arterial", List.of(part(p)), "", circ);
        if (rep.has(Injuries.Outcome.PNEUMOTHORAX)) find(target, "pneumothorax", List.of(), "", circ);
        if (rep.has(Injuries.Outcome.INTERNAL)) find(target, "internal", List.of(part(p)), "", circ);
        for (BodyPart a : rep.amputated) find(target, "traumatic_amputation", List.of(part(a)), "", circ);
        if (rep.has(Injuries.Outcome.DISLOCATION)) find(target, "dislocation", List.of(part(p)), "", circ);
        // Третий этап: травма органа.
        for (faygolover.rpmedicine.core.Organ o : rep.organs) find(target, "organ", List.of("#" + o.translationKey()), "", circ);
        for (Wound w : rep.wounds) {
            if (w.type == WoundType.GUNSHOT) find(target, "gunshot", List.of(part(p)), "", circ);
            else if (w.type == WoundType.SHRAPNEL) find(target, "shrapnel", List.of(part(p)), "", circ);
        }
    }

    public static void clinicalDeath(LivingEntity target) {
        if (target.getServer() != null) find(target, "clinical_death", List.of(), "", "");
    }

    public static void transfusion(ServerPlayer medic, LivingEntity target, @Nullable BloodType bag) {
        if (!hasCard(medic)) return;
        MedcardService.propose(medic.server, target, "transfusion", List.of(bag != null ? bag.label : "?"), medic.getGameProfile().getName());
    }

    public static void extraction(ServerPlayer medic, LivingEntity target, BodyPart p, boolean bullet) {
        if (!hasCard(medic)) return;
        MedcardService.propose(medic.server, target, bullet ? "bullet_removed" : "fragment_removed", List.of(part(p)), medic.getGameProfile().getName());
    }

    /** Укол или капельница (и любой антибиотик): препарат и доза. Таблетки и мази — не пишем. */
    public static void drug(ServerPlayer medic, LivingEntity target, ItemStack item, faygolover.rpmedicine.core.Drug d, double dose) {
        if (!hasCard(medic)) return;
        boolean antibiotic = d.id().contains("cillin") || d.id().contains("ceftriax") || d.id().contains("antibiotic");
        if (d.form() != faygolover.rpmedicine.core.Drug.Form.INJECTION && d.form() != faygolover.rpmedicine.core.Drug.Form.DRIP && !antibiotic) return;
        MedcardService.propose(medic.server, target, "drug", List.of("#" + item.getDescriptionId(),
                String.format(java.util.Locale.ROOT, "%.1f", dose).replace(".0", "")), medic.getGameProfile().getName());
    }

    public static void intubation(ServerPlayer medic, LivingEntity target) {
        if (!hasCard(medic)) return;
        MedcardService.propose(medic.server, target, "intubation", List.of(), medic.getGameProfile().getName());
    }

    /** Итоги операции, которые идут в медкарту. */
    public static final java.util.Set<String> SURGERY_KEYS = java.util.Set.of("incised", "surgery_closed", "surgery_closed_weak",
            "internal_stopped", "organ_repaired", "artery_repaired", "bone_fixated", "chest_drained", "foreign_all_removed",
            "amputated", "prosthesis_installed", "organ_removed", "organ_transplanted", "organ_transplanted_dead", "limb_reattached");

    public static void surgery(ServerPlayer medic, LivingEntity target, BodyPart p, String key) {
        if (!hasCard(medic)) return;
        MedcardService.propose(medic.server, target, "surgery_" + key, List.of(part(p)), medic.getGameProfile().getName());
    }

        /** Результат лаборатории — предложение записи в медкарту пациента. */
    public static void labResult(ServerPlayer medic, ItemStack tube, Diagnostics.Lab lab) {
        if (!hasCard(medic)) return;
        var t = tube.getTag();
        if (t == null || !t.hasUUID("PatientId")) return;
        var uuid = t.getUUID("PatientId");
        var c = faygolover.rpmedicine.medcard.MedcardStore.get(medic.server, uuid);
        long now = System.currentTimeMillis();
        c.add("lab", List.of(lab.bloodType() != null ? lab.bloodType().label : "?", String.valueOf((int) lab.hemoglobin()),
                String.format(java.util.Locale.ROOT, "%.1f", lab.leukocytes()),
                lab.sepsis() ? "#rpmedicine.medcard.yes" : "#rpmedicine.medcard.no"), "", medic.getGameProfile().getName(), true);
        c.add("lab_nutrition", List.of(String.valueOf((int) lab.albumin()), String.format(java.util.Locale.ROOT, "%.1f", lab.triglycerides()),
                String.format(java.util.Locale.ROOT, "%.1f", lab.glucose()), String.valueOf((int) lab.b12())), "", medic.getGameProfile().getName(), true);
        faygolover.rpmedicine.medcard.MedcardStore.save(medic.server, c);
    }
}
