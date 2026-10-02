package faygolover.rpmedicine.network;

import faygolover.rpmedicine.server.DownedService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Клиент → сервер: кнопки лежачего и добивание. */
public record DownedActionPacket(Kind kind, int targetId) {
    public enum Kind {
        /** «Сдаться» — настоящая смерть. */
        SURRENDER,
        /** «Позвать администратора» (клиническая смерть). */
        CALL_ADMIN,
        /** Начать добивание лежачего {@code targetId} (клавиша зажата). */
        FINISH_START,
        /** Клавиша добивания отпущена. */
        FINISH_STOP
    }

    public static void encode(DownedActionPacket p, FriendlyByteBuf buf) {
        buf.writeEnum(p.kind);
        buf.writeVarInt(p.targetId + 1);
    }

    public static DownedActionPacket decode(FriendlyByteBuf buf) {
        return new DownedActionPacket(buf.readEnum(Kind.class), buf.readVarInt() - 1);
    }

    public static void handle(DownedActionPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sp = ctx.get().getSender();
        if (sp != null) ctx.get().enqueueWork(() -> DownedService.handleAction(sp, p));
        ctx.get().setPacketHandled(true);
    }
}
