package faygolover.rpmedicine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Сервер → клиент: собственное состояние игрока (ощущения, HUD, эффекты). Только при изменении. */
public record SelfStatePacket(SelfView view) {
    public static void encode(SelfStatePacket p, FriendlyByteBuf buf) {
        p.view.encode(buf);
    }

    public static SelfStatePacket decode(FriendlyByteBuf buf) {
        return new SelfStatePacket(SelfView.decode(buf));
    }

    public static void handle(SelfStatePacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> faygolover.rpmedicine.client.ClientHandlers.onSelfState(p.view)));
        ctx.get().setPacketHandled(true);
    }
}
