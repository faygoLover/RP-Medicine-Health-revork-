package faygolover.rpmedicine.network;

import faygolover.rpmedicine.capability.MedicalData;
import faygolover.rpmedicine.server.Medical;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Клиент → сервер: личные настройки игрока, которые нужны серверу. Сейчас одна: оставлять ли тело-заглушку
 * при выходе из игры (решения, п. 1.14). Отправляется при входе и при смене настройки.
 */
public record ClientPrefsPacket(boolean leaveBody, boolean noMinigames) {
    public static void encode(ClientPrefsPacket p, FriendlyByteBuf buf) {
        buf.writeBoolean(p.leaveBody);
        buf.writeBoolean(p.noMinigames);
    }

    public static ClientPrefsPacket decode(FriendlyByteBuf buf) {
        return new ClientPrefsPacket(buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(ClientPrefsPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sp = ctx.get().getSender();
        if (sp != null) ctx.get().enqueueWork(() -> {
            MedicalData d = Medical.data(sp);
            if (d != null) {
                d.leaveBody = p.leaveBody;
                d.noMinigames = p.noMinigames;
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
