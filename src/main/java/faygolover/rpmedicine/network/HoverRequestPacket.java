package faygolover.rpmedicine.network;

import faygolover.rpmedicine.server.ExamService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Клиент → сервер: на кого смотрит игрок с медпредметом в руке (−1 — ни на кого). */
public record HoverRequestPacket(int targetId) {
    public static void encode(HoverRequestPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.targetId + 1);
    }

    public static HoverRequestPacket decode(FriendlyByteBuf buf) {
        return new HoverRequestPacket(buf.readVarInt() - 1);
    }

    public static void handle(HoverRequestPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sp = ctx.get().getSender();
        if (sp != null) ctx.get().enqueueWork(() -> ExamService.hover(sp, p.targetId));
        ctx.get().setPacketHandled(true);
    }
}
