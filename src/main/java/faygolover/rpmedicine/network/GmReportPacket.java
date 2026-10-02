package faygolover.rpmedicine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Сервер → ГМ: полный осмотр цифрами или история для панели ГМа. */
public record GmReportPacket(UUID uuid, List<Component> lines) {
    public static void encode(GmReportPacket p, FriendlyByteBuf buf) {
        buf.writeUUID(p.uuid);
        buf.writeVarInt(p.lines.size());
        for (Component c : p.lines) buf.writeComponent(c);
    }

    public static GmReportPacket decode(FriendlyByteBuf buf) {
        UUID u = buf.readUUID();
        int n = Math.min(64, buf.readVarInt());
        List<Component> lines = new ArrayList<>(n);
        for (int i = 0; i < n; i++) lines.add(buf.readComponent());
        return new GmReportPacket(u, lines);
    }

    public static void handle(GmReportPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> faygolover.rpmedicine.client.ClientHandlers.onGmReport(p)));
        ctx.get().setPacketHandled(true);
    }
}
