package faygolover.rpmedicine.network;

import faygolover.rpmedicine.server.ExamService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Клиент → сервер: открыть или закрыть панель осмотра. {@code targetId} −1 — себя; иначе сервер
 * сам проверяет, что цель рядом. Пока панель открыта, сервер присылает изменения.
 */
public record RequestExamPacket(int targetId, boolean open) {
    public static void encode(RequestExamPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.targetId + 1);
        buf.writeBoolean(p.open);
    }

    public static RequestExamPacket decode(FriendlyByteBuf buf) {
        return new RequestExamPacket(buf.readVarInt() - 1, buf.readBoolean());
    }

    public static void handle(RequestExamPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sp = ctx.get().getSender();
        if (sp != null) ctx.get().enqueueWork(() -> ExamService.request(sp, p.targetId, p.open));
        ctx.get().setPacketHandled(true);
    }
}
