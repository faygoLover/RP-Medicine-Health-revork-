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
    private static RingingSound ringing;

    /** Звон в ушах: громкость по силе звона, плавное затухание, стоп после смерти. */
    private static final class RingingSound extends net.minecraft.client.resources.sounds.AbstractTickableSoundInstance {
        private float target;

        RingingSound() {
            super(ModSounds.EAR_RINGING.get(), net.minecraft.sounds.SoundSource.MASTER, net.minecraft.client.resources.sounds.SoundInstance.createUnseededRandom());
            this.looping = true;
            this.relative = true;
            this.attenuation = Attenuation.NONE;
            this.volume = 0.01f;
        }

        @Override
        public void tick() {
            Minecraft mc = Minecraft.getInstance();
            SelfView v = faygolover.rpmedicine.client.ClientState.self;
            boolean on = mc.player != null && !mc.player.isDeadOrDying() && v.down != 3 && ClientConfig.CONCUSSION_RINGING.get();
            float vol = (float) (double) ClientConfig.RINGING_VOLUME.get();
            target = on ? (0.15f + v.ringing / 20f * 0.6f) * vol : 0f;
            volume += (target - volume) * 0.08f;
            if (target <= 0 && volume < 0.01f) stop();
        }
    }

    public static void tick(Minecraft mc, SelfView v) {
        // После смерти до возрождения и в клинической смерти — тишина.
        if (v.down == 3 || mc.player == null || mc.player.isDeadOrDying()) return;
        float heartVol = (float) (double) ClientConfig.HEARTBEAT_VOLUME.get();
        if (ClientConfig.HEARTBEAT.get() && heartVol > 0 && v.heartbeat > 0) {
            if (--heartCooldown <= 0) {
                var ev = v.heartbeat >= 3 ? ModSounds.HEARTBEAT_FAST.get() : ModSounds.HEARTBEAT.get();
                mc.getSoundManager().play(SimpleSoundInstance.forUI(ev, 1.0f, 0.8f * heartVol));
                heartCooldown = switch (v.heartbeat) { case 3 -> 16; case 2 -> 9; default -> 12; };
            }
        }
        float breathVol = (float) (double) ClientConfig.BREATHING_VOLUME.get();
        if (ClientConfig.HEAVY_BREATHING.get() && breathVol > 0 && v.heavyBreathing && !v.isDown()) {
            if (--breathCooldown <= 0) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.HEAVY_BREATHING.get(), 1.0f, 0.6f * breathVol));
                breathCooldown = 60;
            }
        }
        float ringVol = (float) (double) ClientConfig.RINGING_VOLUME.get();
        // Звон после контузии — один длинный звук, громкость идёт за силой звона, затихает плавно.
        if (ClientConfig.CONCUSSION_RINGING.get() && ringVol > 0 && v.ringing > 0 && (ringing == null || ringing.isStopped())) {
            ringing = new RingingSound();
            mc.getSoundManager().play(ringing);
        }
        lastRinging = v.ringing;
        // Баротравма: резкий звон в момент оглушения взрывом.
        if (ClientConfig.CONCUSSION_RINGING.get() && ringVol > 0 && v.deaf > lastDeaf + 5)
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.EARDRUM_BURST.get(), 1.0f, 1.0f * ringVol));
        lastDeaf = v.deaf;
    }
}
