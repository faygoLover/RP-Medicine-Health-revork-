package faygolover.rpmedicine.network;

import faygolover.rpmedicine.server.MinigameService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Клиент → сервер: результат мини-игры: качество 0–1 или отрицательное — отказ (прогресс-бар). */
public record MinigameResultPacket(int session, float quality) {
    public static void encode(MinigameResultPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.session);
        buf.writeFloat(p.quality);
    }

    public static MinigameResultPacket decode(FriendlyByteBuf buf) {
        return new MinigameResultPacket(buf.readVarInt(), buf.readFloat());
    }

    public static void handle(MinigameResultPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sp = ctx.get().getSender();
        if (sp != null) ctx.get().enqueueWork(() -> MinigameService.onResult(sp, p.session, p.quality));
        ctx.get().setPacketHandled(true);
    }
}
