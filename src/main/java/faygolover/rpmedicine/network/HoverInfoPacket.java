package faygolover.rpmedicine.network;

import faygolover.rpmedicine.core.Examination;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/** Сервер → клиент: краткая сводка по игроку под прицелом (п. 7.3 ТЗ). Пустой список — сводки нет. */
public record HoverInfoPacket(int targetId, List<Examination.Line> lines) {
    public static void encode(HoverInfoPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.targetId);
        ExamResultPacket.writeLines(buf, p.lines);
    }

    public static HoverInfoPacket decode(FriendlyByteBuf buf) {
        return new HoverInfoPacket(buf.readVarInt(), ExamResultPacket.readLines(buf));
    }

    public static void handle(HoverInfoPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> faygolover.rpmedicine.client.ClientHandlers.onHover(p)));
        ctx.get().setPacketHandled(true);
    }
}
