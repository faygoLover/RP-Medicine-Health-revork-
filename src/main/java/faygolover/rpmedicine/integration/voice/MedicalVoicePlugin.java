package faygolover.rpmedicine.integration.voice;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.LocationalSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.SoundPacketEvent;
import de.maxhenkel.voicechat.api.events.StaticSoundPacketEvent;
import faygolover.rpmedicine.RpMedicine;

/**
 * Плагин Simple Voice Chat (п. 8 ТЗ). Загружается самим Simple Voice Chat, только если он установлен.
 * В нокдауне и обмороке отключён микрофон, в клинической смерти — ещё и слух; при одышке речь с обрывами.
 */
@ForgeVoicechatPlugin
public class MedicalVoicePlugin implements VoicechatPlugin {
    @Override
    public String getPluginId() {
        return RpMedicine.MODID;
    }

    @Override
    public void registerEvents(EventRegistration reg) {
        reg.registerEvent(MicrophonePacketEvent.class, this::onMicrophone);
        reg.registerEvent(EntitySoundPacketEvent.class, this::onSound);
        reg.registerEvent(LocationalSoundPacketEvent.class, this::onSound);
        reg.registerEvent(StaticSoundPacketEvent.class, this::onSound);
    }

    private void onMicrophone(MicrophonePacketEvent e) {
        VoicechatConnection sender = e.getSenderConnection();
        if (sender == null) return;
        java.util.UUID id = sender.getPlayer().getUuid();
        // Лежачий молчит; при одышке — обрывы (второй этап).
        if (VoiceState.micMuted(id) || VoiceState.dropBreathless(id)) e.cancel();
    }

    private void onSound(SoundPacketEvent<?> e) {
        VoicechatConnection receiver = e.getReceiverConnection();
        if (receiver != null && VoiceState.deaf(receiver.getPlayer().getUuid())) e.cancel();
    }
}
