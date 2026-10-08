package faygolover.rpmedicine.client;

import faygolover.rpmedicine.RpMedicine;
import faygolover.rpmedicine.core.Drug;
import faygolover.rpmedicine.network.ItemInfoPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Подсказки медицинских предметов (решения, п. 1.13): коротко — что это и какой нужен уровень медицины
 * (зелёным, если хватает, красным — если нет); по Shift — как действует, и чем выше свой уровень, тем
 * подробнее: с 2 — словами, с 4 — цифрами (время, сила и длительность эффектов, пределы доз, передозировка).
 * Работает и для чужих предметов-аналогов из датапака.
 */
@Mod.EventBusSubscriber(modid = RpMedicine.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ItemTooltips {
    private ItemTooltips() {}

    private static volatile Map<ResourceLocation, ItemInfoPacket.Info> INFO = Map.of();

    public static void set(ItemInfoPacket p) {
        Map<ResourceLocation, ItemInfoPacket.Info> m = new HashMap<>();
        for (ItemInfoPacket.Info i : p.items()) m.put(i.item(), i);
        INFO = Map.copyOf(m);
    }

    /** Свой уровень медицины; в творческом режиме — всё видно. */
    private static int level() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.isCreative()) return 10;
        return ClientState.self.medLevel;
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent e) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(e.getItemStack().getItem());
        if (id == null) return;
        ItemInfoPacket.Info info = INFO.get(id);
        if (info == null) return;
        List<Component> t = e.getToolTip();
        int lvl = level();
        // Чужой предмет — аналог нашего.
        ResourceLocation ourId = info.analogOf() != null ? info.analogOf() : id;
        if (info.analogOf() != null) {
            Item ours = ForgeRegistries.ITEMS.getValue(info.analogOf());
            if (ours != null) t.add(Component.translatable("rpmedicine.tooltip.analog", Component.translatable(ours.getDescriptionId()))
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
        if (info.minLevel() > 0) {
            boolean ok = lvl >= info.minLevel();
            t.add(Component.translatable("rpmedicine.tooltip.level", info.minLevel()).withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED));
        } else {
            t.add(Component.translatable("rpmedicine.tooltip.anyone").withStyle(ChatFormatting.GREEN));
        }
        if (!Screen.hasShiftDown()) {
            t.add(Component.translatable("rpmedicine.tooltip.shift").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            return;
        }
        if (lvl < 2) {
            t.add(Component.translatable("rpmedicine.tooltip.no_skill").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            return;
        }
        // Словами: что делает.
        String descKey = "item." + ourId.getNamespace() + "." + ourId.getPath() + ".desc";
        t.add(Component.translatable(descKey).withStyle(ChatFormatting.GRAY));
        if (lvl < 4) return;
        // Цифрами.
        t.add(Component.translatable("rpmedicine.tooltip.time", num(info.seconds())).withStyle(ChatFormatting.DARK_GRAY));
        Drug d = info.drug();
        if (d == null) return;
        t.add(Component.translatable("rpmedicine.tooltip.form_" + d.form().name().toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.DARK_GRAY));
        for (Drug.Dose dose : d.effects()) t.add(dose(dose).withStyle(ChatFormatting.AQUA));
        if (d.doseLimit() > 0)
            t.add(Component.translatable("rpmedicine.tooltip.dose_limit", d.doseLimit(), num(d.doseWindowSeconds() / 3600.0)).withStyle(ChatFormatting.YELLOW));
        if (d.opioid()) t.add(Component.translatable("rpmedicine.tooltip.opioid").withStyle(ChatFormatting.LIGHT_PURPLE));
        if (lvl >= 5 && (!d.overdose().isEmpty() || d.overdoseArrestChance() > 0)) {
            t.add(Component.translatable("rpmedicine.tooltip.overdose").withStyle(ChatFormatting.RED));
            for (Drug.Dose dose : d.overdose()) t.add(dose(dose).withStyle(ChatFormatting.RED));
            if (d.overdoseArrestChance() > 0)
                t.add(Component.translatable("rpmedicine.tooltip.arrest", Math.round(d.overdoseArrestChance() * 100)).withStyle(ChatFormatting.RED));
        }
    }

    private static MutableComponent dose(Drug.Dose dose) {
        String strength = dose.strength() < 1 && dose.strength() > -1 ? Math.round(dose.strength() * 100) + "%" : num(dose.strength());
        Component when = dose.delay() <= 0 ? Component.translatable("rpmedicine.tooltip.instant")
                : Component.translatable("rpmedicine.tooltip.after", time(dose.delay()));
        return Component.translatable("rpmedicine.tooltip.effect",
                Component.translatable("rpmedicine.effect." + dose.effect().id), strength,
                Component.translatable("rpmedicine.tooltip.for", time(dose.seconds())).append(", ").append(when));
    }

    private static String num(double v) {
        return Math.abs(v - Math.rint(v)) < 1e-6 ? String.valueOf((long) Math.rint(v)) : String.format(Locale.ROOT, "%.1f", v);
    }

    /** Секунды словами: 90 → «1,5 мин», 3600 → «1 ч». */
    private static String time(double sec) {
        if (sec >= 3600) return num(sec / 3600.0) + " ч";
        if (sec >= 60) return num(sec / 60.0) + " мин";
        return num(sec) + " с";
    }
}
