package faygolover.rpmedicine.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Наборы для проверки {@code /rpmedicine kit <набор> [игроки]}: выдаёт готовый комплект предметов.
 * Строка набора — «id:количество», без пространства имён — предмет RP Medicine.
 */
public final class KitCommand {
    private KitCommand() {}

    /** Набор: описание и предметы. */
    private record Kit(String title, List<String> items) {}

    private static final Map<String, Kit> KITS = new LinkedHashMap<>();

    static {
        KITS.put("field", new Kit("полевой медик", List.of(
                "first_aid_kit:2", "medical_pouch", "bandage:16", "pressure_dressing:8", "hemostatic_gauze:8", "tourniquet:4",
                "esmarch:2", "occlusive_dressing:4", "antiseptic:4", "antibiotic_ointment:2", "splint:4", "scissors",
                "decompression_needle:2", "airway:2", "ammonia:2", "painkillers:8", "morphine:4", "adrenaline:4", "txa:4", "syringe:8")));
        KITS.put("resus", new Kit("реанимация и капельницы", List.of(
                "ambu_bag", "laryngoscope", "endotracheal_tube:4", "defibrillator", "adrenaline:4", "atropine:4", "naloxone:2",
                "saline:4", "norepinephrine:2", "empty_blood_bag:4", "blood_bag:4", "blood_draw_syringe:2", "syringe:8")));
        KITS.put("diag", new Kit("диагностика и лаборатория", List.of(
                "stethoscope", "thermometer", "pulse_oximeter", "tonometer", "glucometer", "hemoanalyzer", "portable_scanner",
                "test_tube:8", "lancet:8", "blood_draw_syringe:2", "medcard:2")));
        KITS.put("surgeon", new Kit("хирург", List.of(
                "field_surgery_kit", "stabilization_kit", "surgical_mask", "surgical_gloves:4", "scalpel", "hemostat", "retractor",
                "surgical_tweezers", "vascular_suture:4", "suture_kit:4", "surgical_drill", "osteosynthesis_kit:2", "chest_drain:2",
                "bone_saw", "lidocaine:4", "ketamine:4", "propofol:4", "ketorolac:4", "ceftriaxone:4", "antiseptic:4", "syringe:16",
                "laryngoscope", "endotracheal_tube:2")));
        KITS.put("transplant", new Kit("органы и протезы", List.of(
                "organ_container:2", "cyclosporine:8", "prosthetic_foot", "peg_leg", "prosthetic_hook", "bone_saw", "syringe:8")));
        KITS.put("drugs", new Kit("все лекарства", List.of(
                "painkillers:4", "paracetamol:4", "ibuprofen:4", "tramadol:4", "amoxicillin:4", "cyclosporine:4", "glucose_tablets:4",
                "morphine:4", "adrenaline:4", "txa:4", "ketorolac:4", "naloxone:4", "ceftriaxone:4", "diazepam:4", "atropine:4",
                "lidocaine:4", "ketamine:4", "propofol:4", "insulin:4", "norepinephrine:4", "syringe:32")));
        // Вещества: в тестовом датапаке rpm_test мёд = алкоголь, свекольный суп = кофе, бумага = сигарета.
        KITS.put("substances", new Kit("вещества (тест: мёд — алкоголь, борщ — кофе, бумага — сигарета)", List.of(
                "minecraft:honey_bottle:8", "minecraft:beetroot_soup:4", "minecraft:paper:16",
                "tramadol:8", "morphine:4", "diazepam:4", "naloxone:4", "syringe:8")));
        KITS.put("food", new Kit("еда (питание, «приелось»)", List.of(
                "minecraft:bread:16", "minecraft:cooked_beef:8", "minecraft:cooked_chicken:8", "minecraft:cooked_salmon:8",
                "minecraft:baked_potato:8", "minecraft:apple:8", "minecraft:carrot:8", "minecraft:golden_carrot:4",
                "minecraft:mushroom_stew:2", "minecraft:pumpkin_pie:4", "minecraft:cookie:16", "minecraft:milk_bucket",
                "minecraft:sweet_berries:16", "minecraft:melon_slice:16", "minecraft:egg:8", "glucose_tablets:4")));
        KITS.put("gm", new Kit("ГМ", List.of("gm_scanner", "medcard:4")));
    }

    static LiteralArgumentBuilder<CommandSourceStack> node() {
        LiteralArgumentBuilder<CommandSourceStack> kit = Commands.literal("kit").requires(s -> s.hasPermission(2));
        for (String name : KITS.keySet()) {
            kit.then(Commands.literal(name)
                    .executes(c -> give(c, name, List.of(c.getSource().getPlayerOrException())))
                    .then(Commands.argument("targets", EntityArgument.players())
                            .executes(c -> give(c, name, EntityArgument.getPlayers(c, "targets")))));
        }
        kit.then(Commands.literal("all")
                .executes(c -> giveAll(c, List.of(c.getSource().getPlayerOrException())))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(c -> giveAll(c, EntityArgument.getPlayers(c, "targets")))));
        kit.executes(c -> {
            StringBuilder sb = new StringBuilder("Наборы: ");
            KITS.forEach((k, v) -> sb.append(k).append(" — ").append(v.title()).append("; "));
            sb.append("all — все сразу.");
            c.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
            return 1;
        });
        return kit;
    }

    private static int giveAll(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> targets) throws CommandSyntaxException {
        int n = 0;
        for (String name : KITS.keySet()) n += give(c, name, targets);
        return n;
    }

    private static int give(CommandContext<CommandSourceStack> c, String name, Collection<? extends ServerPlayer> targets) {
        Kit kit = KITS.get(name);
        int given = 0;
        StringBuilder missing = new StringBuilder();
        for (ServerPlayer p : targets) {
            for (String line : kit.items()) {
                int cut = line.lastIndexOf(':');
                String id = line;
                int count = 1;
                if (cut > 0 && line.substring(cut + 1).chars().allMatch(Character::isDigit)) {
                    id = line.substring(0, cut);
                    count = Integer.parseInt(line.substring(cut + 1));
                }
                ResourceLocation rl = id.contains(":") ? ResourceLocation.tryParse(id) : new ResourceLocation("rpmedicine", id);
                Item item = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
                if (item == null || rl == null || !ForgeRegistries.ITEMS.containsKey(rl)) {
                    if (missing.indexOf(id) < 0) missing.append(id).append(' ');
                    continue;
                }
                while (count > 0) {
                    int n = Math.min(count, item.getMaxStackSize());
                    ItemStack st = new ItemStack(item, n);
                    if (!p.getInventory().add(st)) p.drop(st, false);
                    count -= n;
                }
                given++;
            }
        }
        String miss = missing.toString().trim();
        int total = given;
        c.getSource().sendSuccess(() -> Component.literal("Набор «" + kit.title() + "» выдан (" + total + " позиций)"
                + (miss.isEmpty() ? "" : "; нет таких предметов: " + miss)), true);
        return total;
    }
}
