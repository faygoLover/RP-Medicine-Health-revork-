package faygolover.rpmedicine.integration;

import faygolover.rpmedicine.RpMedicine;
import faygolover.rpstamina.api.StaminaAPI;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * RP Stamina (необязательная). Потолок и восстановление меняются атрибутами
 * ({@code EffectsApplier}); здесь — расход на переноску тела. Загружается только при наличии мода.
 */
public final class StaminaCompat {
    private StaminaCompat() {}

    private static final ResourceLocation CARRY = new ResourceLocation(RpMedicine.MODID, "carry_body");

    public static void consume(ServerPlayer sp, float amount) {
        StaminaAPI.consume(sp, amount, CARRY);
    }
}
