package faygolover.rpmedicine.client;

import faygolover.rpmedicine.config.ClientConfig;
import faygolover.rpmedicine.network.SelfView;
import faygolover.rpmedicine.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;

/** Звуки состояния у самого игрока: стук сердца, тяжёлое дыхание, звон после контузии (п. 7.5 ТЗ). */
public final class ClientSounds {
    private ClientSounds() {}

    private static int heartCooldown;
    private static int breathCooldown;
    private static int ringCooldown;
    private static int lastDeaf;
    private static int lastRinging;

    public static void tick(Minecraft mc, SelfView v) {
        if (v.down == 3) return;
        if (ClientConfig.HEARTBEAT.get() && v.heartbeat > 0) {
            if (--heartCooldown <= 0) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.HEARTBEAT.get(), 1.0f, 0.5f));
                heartCooldown = switch (v.heartbeat) { case 3 -> 7; case 2 -> 9; default -> 12; };
            }
        }
        if (ClientConfig.HEAVY_BREATHING.get() && v.heavyBreathing && !v.isDown()) {
            if (--breathCooldown <= 0) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.HEAVY_BREATHING.get(), 1.0f, 0.35f));
                breathCooldown = 60;
            }
        }
        if (ClientConfig.CONCUSSION_RINGING.get() && v.ringing > 0) {
            if (v.ringing > lastRinging + 3 || --ringCooldown <= 0) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.EAR_RINGING.get(), 1.0f, 0.15f + v.ringing / 20f * 0.4f));
                ringCooldown = 140;
            }
        }
        lastRinging = v.ringing;
        // Баротравма: резкий звон в момент оглушения взрывом.
        if (ClientConfig.CONCUSSION_RINGING.get() && v.deaf > lastDeaf + 5)
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.EAR_RINGING.get(), 1.0f, 0.7f));
        lastDeaf = v.deaf;
    }
}
