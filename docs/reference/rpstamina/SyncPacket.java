package faygolover.rpstamina.network;

import faygolover.rpstamina.client.ClientStamina;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Сервер → клиент. Кроме значения передаётся скорость изменения за тик: между пакетами клиент сам
 * продвигает полоску, поэтому пакеты нужны только при разовых тратах, смене состояния и подстройке.
 */
public class SyncPacket {
    public static final int FLAG_EXHAUSTED = 1;
    public static final int FLAG_ACTIVE = 2;
    public static final int FLAG_NO_FOOD_SPRINT_LIMIT = 4;

    private final float stamina;
    private final float max;
    private final float rate;
    private final byte flags;
    private final byte mineMultPercent;

    public SyncPacket(float stamina, float max, float rate, byte flags, byte mineMultPercent) {
        this.stamina = stamina;
        this.max = max;
        this.rate = rate;
        this.flags = flags;
        this.mineMultPercent = mineMultPercent;
    }

    public static void encode(SyncPacket p, FriendlyByteBuf buf) {
        buf.writeFloat(p.stamina);
        buf.writeFloat(p.max);
        buf.writeFloat(p.rate);
        buf.writeByte(p.flags);
        buf.writeByte(p.mineMultPercent);
    }

    public static SyncPacket decode(FriendlyByteBuf buf) {
        return new SyncPacket(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readByte(), buf.readByte());
    }

    public static void handle(SyncPacket p, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> ClientStamina.update(p.stamina, p.max, p.rate,
                (p.flags & FLAG_EXHAUSTED) != 0,
                (p.flags & FLAG_ACTIVE) != 0,
                (p.flags & FLAG_NO_FOOD_SPRINT_LIMIT) != 0,
                p.mineMultPercent / 100f));
        c.setPacketHandled(true);
    }
}
