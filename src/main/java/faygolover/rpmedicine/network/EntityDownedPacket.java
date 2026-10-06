package faygolover.rpmedicine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Сервер → клиенты рядом: игрок лежит или встал, лежит ли на койке (для позы на чужих клиентах). Только факт, без причин. */
public record EntityDownedPacket(int entityId, boolean down, boolean onBed, byte bedQuarter, boolean crawl) {
    public EntityDownedPacket(int entityId, boolean down, boolean onBed) {
        this(entityId, down, onBed, (byte) 0, false);
    }

    public EntityDownedPacket(int entityId, boolean down, boolean onBed, byte bedQuarter) {
        this(entityId, down, onBed, bedQuarter, false);
    }

    public static void encode(EntityDownedPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.entityId);
        buf.writeByte((p.down ? 1 : 0) | (p.onBed ? 2 : 0) | ((p.bedQuarter & 3) << 2) | (p.crawl ? 16 : 0));
    }

    public static EntityDownedPacket decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        int flags = buf.readByte();
        return new EntityDownedPacket(id, (flags & 1) != 0, (flags & 2) != 0, (byte) ((flags >> 2) & 3), (flags & 16) != 0);
    }

    public static void handle(EntityDownedPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> faygolover.rpmedicine.client.ClientHandlers.onEntityDowned(p)));
        ctx.get().setPacketHandled(true);
    }
}
