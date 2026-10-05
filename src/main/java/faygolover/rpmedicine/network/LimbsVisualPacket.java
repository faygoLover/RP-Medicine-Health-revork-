package faygolover.rpmedicine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Сервер → клиенты: каких конечностей у игрока не видно (ТЗ третьего этапа, п. 12). Биты — части тела
 * ({@code BodyPart.ordinal()}): отсутствует и без протеза.
 */
public record LimbsVisualPacket(int entityId, int mask) {
    public static void encode(LimbsVisualPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.entityId);
        buf.writeVarInt(p.mask);
    }

    public static LimbsVisualPacket decode(FriendlyByteBuf buf) {
        return new LimbsVisualPacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(LimbsVisualPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (p.mask == 0) faygolover.rpmedicine.client.ClientState.MISSING_LIMBS.remove(p.entityId);
            else faygolover.rpmedicine.client.ClientState.MISSING_LIMBS.put(p.entityId, p.mask);
        }));
        ctx.get().setPacketHandled(true);
    }
}
