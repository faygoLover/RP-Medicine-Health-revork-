package faygolover.rpmedicine.server;

import faygolover.rpmedicine.core.MedicalSettings;
import faygolover.rpmedicine.core.MedicalState;
import faygolover.rpmedicine.core.Substance;
import faygolover.rpmedicine.core.Substances;
import faygolover.rpmedicine.data.SubstanceRules;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * Дозы веществ от чужих модов (ТЗ третьего этапа, п. 9.1) по датапаку {@code substances}: допитый
 * предмет (Brewery, Vinery, Herbal Brews), ПКМ предметом (сигареты The Dirty Stuff), наложенный эффект
 * (никотин Tobacconist). Тяга при ломке — сообщениями.
 */
public final class SubstanceService {
    private SubstanceService() {}

    private static final SplittableRandom RANDOM = new SplittableRandom();
    /** Последняя доза вещества у игрока (игровой тик) — для перезарядки повторных событий. */
    private static final Map<UUID, long[]> LAST = new HashMap<>();
    /** Последнее сообщение о тяге. */
    private static final Map<UUID, Long> CRAVING = new HashMap<>();

    public static void onUseFinish(LivingEntityUseItemEvent.Finish e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        dose(sp, SubstanceRules.forItem(ForgeRegistries.ITEMS.getKey(e.getItem().getItem())));
    }

    public static void onRightClick(PlayerInteractEvent.RightClickItem e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        dose(sp, SubstanceRules.forClick(ForgeRegistries.ITEMS.getKey(e.getItemStack().getItem())));
    }

    public static void onEffectAdded(MobEffectEvent.Added e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        dose(sp, SubstanceRules.forEffect(ForgeRegistries.MOB_EFFECTS.getKey(e.getEffectInstance().getEffect())));
    }

    private static void dose(ServerPlayer sp, SubstanceRules.Entry e) {
        if (e == null) return;
        MedicalState m = Medical.state(sp);
        if (m == null) return;
        long now = sp.serverLevel().getGameTime();
        long[] last = LAST.computeIfAbsent(sp.getUUID(), k -> new long[Substance.VALUES.length]);
        int i = e.substance().ordinal();
        if (e.cooldownSeconds() > 0 && last[i] != 0 && now - last[i] < e.cooldownSeconds() * 20) return;
        last[i] = now;
        Substances.dose(m, e.substance(), e.amount(), Medical.traits(sp), RANDOM.split(), MedicalSettings.get());
        Medical.changed(sp);
    }

    /** Раз в несколько минут ломка напоминает о себе. */
    public static void tick(ServerPlayer sp, MedicalState m) {
        MedicalSettings s = MedicalSettings.get();
        long now = sp.serverLevel().getGameTime();
        Long last = CRAVING.get(sp.getUUID());
        if (last != null && now - last < 20 * 240) return;
        for (Substance x : Substance.VALUES) {
            if (!Substances.withdrawal(m, x, s)) continue;
            CRAVING.put(sp.getUUID(), now);
            sp.displayClientMessage(Component.translatable("rpmedicine.craving." + x.id).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
            return;
        }
    }

    public static void forget(ServerPlayer sp) {
        LAST.remove(sp.getUUID());
        CRAVING.remove(sp.getUUID());
    }
}
