package faygolover.rpmedicine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Сервер → клиент: прогресс-бар действия. {@code totalTicks} ≤ 0 — действие закончилось или прервано.
 * Клиент сам продвигает полосу между пакетами.
 *
 * @param labelKey ключ перевода названия действия
 */
public record ProgressPacket(String labelKey, int totalTicks, int doneTicks) {
    public static void encode(ProgressPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.labelKey, 128);
        buf.writeVarInt(p.totalTicks);
        buf.writeVarInt(p.doneTicks);
    }

    public static ProgressPacket decode(FriendlyByteBuf buf) {
        return new ProgressPacket(buf.readUtf(128), buf.readVarInt(), buf.readVarInt());
    }

    public static ProgressPacket stop() {
        return new ProgressPacket("", 0, 0);
    }

    public static void handle(ProgressPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> faygolover.rpmedicine.client.ClientHandlers.onProgress(p)));
        ctx.get().setPacketHandled(true);
    }
}
