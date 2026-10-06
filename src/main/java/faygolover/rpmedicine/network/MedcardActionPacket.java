package faygolover.rpmedicine.network;

import faygolover.rpmedicine.medcard.MedcardService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Клиент → сервер: правка медкарты. Сервер проверяет, что у игрока карта этого персонажа.
 * {@code ADD} и {@code EDIT}: значения — диагноз, обстоятельства, последствия; {@code SET}: поле титула и значение.
 */
public record MedcardActionPacket(UUID uuid, Op op, int entryId, String field, List<String> values) {
    public enum Op { ADD, EDIT, ACCEPT, DECLINE, SET, ATTACH_LAB }

    public static MedcardActionPacket set(UUID uuid, String field, String value) {
        return new MedcardActionPacket(uuid, Op.SET, -1, field, List.of(value));
    }

    public String value(int i) {
        return i < values.size() ? values.get(i) : "";
    }

    public static void encode(MedcardActionPacket p, FriendlyByteBuf buf) {
        buf.writeUUID(p.uuid);
        buf.writeEnum(p.op);
        buf.writeVarInt(p.entryId);
        buf.writeUtf(p.field, 32);
        buf.writeVarInt(p.values.size());
        for (String v : p.values) buf.writeUtf(v, 512);
    }

    public static MedcardActionPacket decode(FriendlyByteBuf buf) {
        UUID uuid = buf.readUUID();
        Op op = buf.readEnum(Op.class);
        int id = buf.readVarInt();
        String field = buf.readUtf(32);
        int n = Math.min(4, buf.readVarInt());
        List<String> values = new ArrayList<>(n);
        for (int i = 0; i < n; i++) values.add(buf.readUtf(512));
        return new MedcardActionPacket(uuid, op, id, field, values);
    }

    public static void handle(MedcardActionPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sp = ctx.get().getSender();
        if (sp != null) ctx.get().enqueueWork(() -> MedcardService.onAction(sp, p));
        ctx.get().setPacketHandled(true);
    }
}
