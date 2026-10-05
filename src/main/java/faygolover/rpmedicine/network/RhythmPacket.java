package faygolover.rpmedicine.network;

import faygolover.rpmedicine.server.TreatmentService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Клиент → сервер: такт удерживаемого действия (решения, п. 1.13): нажатие пробела при СЛР или вдох
 * мешком Амбу и его точность 0–1. Сервер принимает такт, только пока медик держит действие.
 */
public record RhythmPacket(boolean ambu, float quality) {
    public static void encode(RhythmPacket p, FriendlyByteBuf buf) {
        buf.writeBoolean(p.ambu);
        buf.writeFloat(p.quality);
    }

    public static RhythmPacket decode(FriendlyByteBuf buf) {
        return new RhythmPacket(buf.readBoolean(), buf.readFloat());
    }

    public static void handle(RhythmPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sp = ctx.get().getSender();
        if (sp != null) ctx.get().enqueueWork(() -> TreatmentService.onRhythm(sp, p.ambu, p.quality));
        ctx.get().setPacketHandled(true);
    }
}
