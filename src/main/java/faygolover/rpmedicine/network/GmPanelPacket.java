package faygolover.rpmedicine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Сервер → ГМ: список игроков онлайн и тел для панели ГМа (только операторам). */
public record GmPanelPacket(List<Row> rows, List<String> drugs) {
    /** @param color 0 — здоров, 1 — ранен, 2 — серьёзно, 3 — лежит, 4 — клиническая смерть */
    public record Row(UUID uuid, String name, boolean stub, int color, String dimension, int x, int y, int z) {}

    public static void encode(GmPanelPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.rows.size());
        for (Row r : p.rows) {
            buf.writeUUID(r.uuid);
            buf.writeUtf(r.name, 64);
            buf.writeBoolean(r.stub);
            buf.writeByte(r.color);
            buf.writeUtf(r.dimension, 128);
            buf.writeInt(r.x);
            buf.writeInt(r.y);
            buf.writeInt(r.z);
        }
        buf.writeVarInt(p.drugs.size());
        for (String d : p.drugs) buf.writeUtf(d, 64);
    }

    public static GmPanelPacket decode(FriendlyByteBuf buf) {
        int n = Math.min(1000, buf.readVarInt());
        List<Row> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++)
            rows.add(new Row(buf.readUUID(), buf.readUtf(64), buf.readBoolean(), buf.readByte(), buf.readUtf(128), buf.readInt(), buf.readInt(), buf.readInt()));
        int d = Math.min(500, buf.readVarInt());
        List<String> drugs = new ArrayList<>(d);
        for (int i = 0; i < d; i++) drugs.add(buf.readUtf(64));
        return new GmPanelPacket(rows, drugs);
    }

    public static void handle(GmPanelPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> faygolover.rpmedicine.client.ClientHandlers.onGmPanel(p)));
        ctx.get().setPacketHandled(true);
    }
}
