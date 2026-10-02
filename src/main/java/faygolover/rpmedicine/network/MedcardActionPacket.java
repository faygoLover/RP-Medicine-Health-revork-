package faygolover.rpmedicine.network;

import faygolover.rpmedicine.medcard.MedcardService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Клиент → сервер: правка медкарты. Сервер проверяет, что у игрока карта этого персонажа. */
public record MedcardActionPacket(UUID uuid, Op op, int entryId, String text) {
    public enum Op { ADD, EDIT, ACCEPT, DECLINE, ALLERGIES, CHRONIC }

    public static void encode(MedcardActionPacket p, FriendlyByteBuf buf) {
        buf.writeUUID(p.uuid);
        buf.writeEnum(p.op);
        buf.writeVarInt(p.entryId);
        buf.writeUtf(p.text, 512);
    }

    public static MedcardActionPacket decode(FriendlyByteBuf buf) {
        return new MedcardActionPacket(buf.readUUID(), buf.readEnum(Op.class), buf.readVarInt(), buf.readUtf(512));
    }

    public static void handle(MedcardActionPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sp = ctx.get().getSender();
        if (sp != null) ctx.get().enqueueWork(() -> MedcardService.onAction(sp, p));
        ctx.get().setPacketHandled(true);
    }
}
