package faygolover.rpmedicine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Сервер → клиент: открыть редактор HUD (команда {@code /rpmedicine hud}). */
public record OpenHudEditorPacket() {
    public static void encode(OpenHudEditorPacket p, FriendlyByteBuf buf) {}

    public static OpenHudEditorPacket decode(FriendlyByteBuf buf) {
        return new OpenHudEditorPacket();
    }

    public static void handle(OpenHudEditorPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> faygolover.rpmedicine.client.ClientHandlers::openHudEditor));
        ctx.get().setPacketHandled(true);
    }
}
