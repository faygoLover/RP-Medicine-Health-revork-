package faygolover.rpmedicine.network;

import faygolover.rpmedicine.core.BodyPart;
import faygolover.rpmedicine.server.GmPanelService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * ГМ → сервер: правка тела с силуэта панели ГМа — нанести рану ({@code arg} — тип, {@code value} — тяжесть),
 * вылечить часть, ввести препарат ({@code arg} — id препарата, {@code value} — доза). Только операторам.
 */
public record GmEditPacket(UUID uuid, Op op, BodyPart part, String arg, float value) {
    public enum Op { INJURE, HEAL_PART, DRUG }

    public static void encode(GmEditPacket p, FriendlyByteBuf buf) {
        buf.writeUUID(p.uuid);
        buf.writeEnum(p.op);
        buf.writeEnum(p.part);
        buf.writeUtf(p.arg, 64);
        buf.writeFloat(p.value);
    }

    public static GmEditPacket decode(FriendlyByteBuf buf) {
        return new GmEditPacket(buf.readUUID(), buf.readEnum(Op.class), buf.readEnum(BodyPart.class), buf.readUtf(64), buf.readFloat());
    }

    public static void handle(GmEditPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sp = ctx.get().getSender();
        if (sp != null) ctx.get().enqueueWork(() -> GmPanelService.onEdit(sp, p));
        ctx.get().setPacketHandled(true);
    }
}
