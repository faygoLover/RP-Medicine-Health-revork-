package faygolover.rpmedicine.network;

import faygolover.rpmedicine.server.GmPanelService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** ГМ → сервер: действие панели ГМа. Сервер проверяет права оператора. */
public record GmActionPacket(UUID uuid, Action action) {
    public enum Action { INSPECT, HEAL, REVIVE, KILL, TELEPORT, HISTORY }

    public static void encode(GmActionPacket p, FriendlyByteBuf buf) {
        buf.writeUUID(p.uuid);
        buf.writeEnum(p.action);
    }

    public static GmActionPacket decode(FriendlyByteBuf buf) {
        return new GmActionPacket(buf.readUUID(), buf.readEnum(Action.class));
    }

    public static void handle(GmActionPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sp = ctx.get().getSender();
        if (sp != null) ctx.get().enqueueWork(() -> GmPanelService.onAction(sp, p));
        ctx.get().setPacketHandled(true);
    }
}
