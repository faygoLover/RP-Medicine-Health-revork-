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

    public static void onInjury(ServerPlayer sp, Injuries.Report rep) {
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
