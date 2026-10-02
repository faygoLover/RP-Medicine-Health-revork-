package faygolover.rpmedicine.server;

import faygolover.rpmedicine.core.Diagnostics;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Связь событий лечения с медкартой (шаг 8 второго этапа): здесь мод предлагает записи. */
public final class MedcardHooks {
    private MedcardHooks() {}

    /** Результат лаборатории — предложение записи в медкарту пациента. */
    public static void labResult(ServerPlayer medic, ItemStack tube, Diagnostics.Lab lab) {
        // Медкарта появится на шаге 8.
    }
}
