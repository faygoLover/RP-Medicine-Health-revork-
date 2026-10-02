package faygolover.rpmedicine.network;

import faygolover.rpmedicine.hospital.HospitalService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Клиент → сервер: игрок смотрит на монитор показателей (запрос повторяется, пока смотрит). */
public record MonitorRequestPacket(BlockPos pos) {
    public static void encode(MonitorRequestPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.pos);
    }

    public static MonitorRequestPacket decode(FriendlyByteBuf buf) {
        return new MonitorRequestPacket(buf.readBlockPos());
    }

    public static void handle(MonitorRequestPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sp = ctx.get().getSender();
        if (sp != null) ctx.get().enqueueWork(() -> HospitalService.onMonitorRequest(sp, p.pos));
        ctx.get().setPacketHandled(true);
    }
}
