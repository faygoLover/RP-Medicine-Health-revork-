package faygolover.rpmedicine.network;

import faygolover.rpmedicine.core.BodyPart;
import faygolover.rpmedicine.server.PanelActions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Клиент → сервер: действие с панели. Сервер проверяет всё заново: цель рядом, предмет на месте,
 * лечение нужно.
 *
 * @param slot слот инвентаря медика с предметом (для {@link Kind#APPLY_ITEM})
 */
public record PanelActionPacket(int targetId, Kind kind, BodyPart part, int slot) {
    public enum Kind {
        APPLY_ITEM, REMOVE_DRESSING, REMOVE_TOURNIQUET, REMOVE_SPLINT, REMOVE_OCCLUSIVE, SEARCH, CANCEL,
        /** Остановить капельницу (второй этап). */
        STOP_DRIP,
        /** Вправить вывих пустой рукой (второй этап). */
        REDUCE
    }

    public static void encode(PanelActionPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.targetId + 1);
        buf.writeEnum(p.kind);
        buf.writeByte(p.part.ordinal());
        buf.writeVarInt(p.slot);
    }

    public static PanelActionPacket decode(FriendlyByteBuf buf) {
        return new PanelActionPacket(buf.readVarInt() - 1, buf.readEnum(Kind.class), BodyPart.byOrdinal(buf.readByte()), buf.readVarInt());
    }

    public static void handle(PanelActionPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sp = ctx.get().getSender();
        if (sp != null) ctx.get().enqueueWork(() -> PanelActions.handle(sp, p));
        ctx.get().setPacketHandled(true);
    }
}
