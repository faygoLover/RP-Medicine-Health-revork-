package faygolover.rpmedicine.server;

import faygolover.rpmedicine.core.Injuries;
import faygolover.rpmedicine.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

/** Что игрок чувствует сразу при ранении: хруст кости, удар в голову. Без цифр. */
public final class Feedback {
    private Feedback() {}

    private static final java.util.Map<java.util.UUID, Long> LAST_GROAN = new java.util.HashMap<>();

    /** Стон от боли при ранении — не чаще раза в 3 секунды; громкость — ползунок «Голос» в настройках звука. */
    private static void groan(ServerPlayer sp, Injuries.Report rep) {
        if (rep.totalSeverity < 8 || Medical.isDown(sp)) return;
        long now = sp.serverLevel().getGameTime();
        Long last = LAST_GROAN.get(sp.getUUID());
        if (last != null && now - last < 60) return;
        LAST_GROAN.put(sp.getUUID(), now);
        var ev = rep.totalSeverity >= 25 ? ModSounds.PAIN_GROAN.get() : ModSounds.PAIN_MOAN.get();
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), ev, SoundSource.VOICE, 0.9f,
                faygolover.rpmedicine.medcard.MedcardService.voicePitch(sp) * (0.95f + sp.getRandom().nextFloat() * 0.1f));
    }

    public static void onInjury(ServerPlayer sp, Injuries.Report rep) {
        groan(sp, rep);
        if (rep.has(Injuries.Outcome.FRACTURE) || rep.has(Injuries.Outcome.OPEN_FRACTURE) || rep.has(Injuries.Outcome.RIB_FRACTURE)) {
            sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), ModSounds.BONE_BREAK.get(), SoundSource.PLAYERS, 0.9f, 1.0f);
            sp.displayClientMessage(Component.translatable("rpmedicine.msg.something_cracked").withStyle(ChatFormatting.RED), true);
        } else if (rep.has(Injuries.Outcome.ARTERIAL)) {
            sp.displayClientMessage(Component.translatable("rpmedicine.msg.blood_spurts").withStyle(ChatFormatting.DARK_RED), true);
        } else if (rep.has(Injuries.Outcome.KNOCKOUT) || rep.has(Injuries.Outcome.CONCUSSION)) {
            sp.displayClientMessage(Component.translatable("rpmedicine.msg.head_ringing").withStyle(ChatFormatting.GRAY), true);
        } else if (rep.has(Injuries.Outcome.DRESSING_REOPENED)) {
            sp.displayClientMessage(Component.translatable("rpmedicine.msg.dressing_reopened").withStyle(ChatFormatting.RED), true);
        }
    }
}
