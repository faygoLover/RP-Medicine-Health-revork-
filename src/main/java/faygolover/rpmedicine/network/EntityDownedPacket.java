package faygolover.rpmedicine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Сервер → клиенты рядом: игрок лежит или встал (для позы на чужих клиентах). Только факт, без причин. */
public record EntityDownedPacket(int entityId, boolean down) {
    public static void encode(EntityDownedPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.entityId);
        buf.writeBoolean(p.down);
    }

    public static EntityDownedPacket decode(FriendlyByteBuf buf) {
        return new EntityDownedPacket(buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(EntityDownedPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> faygolover.rpmedicine.client.ClientHandlers.onEntityDowned(p)));
        ctx.get().setPacketHandled(true);
    }
}
