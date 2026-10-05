package faygolover.rpmedicine.network;

import faygolover.rpmedicine.core.Minigames;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Сервер → клиент: задание мини-игры (ТЗ второго этапа, п. 9).
 *
 * @param session номер задания (ответ без него не принимается)
 * @param ease    сложность 0–1 (1 — легче)
 * @param refuse  можно ли отказаться и делать прогресс-баром
 */
public record MinigameStartPacket(int session, Minigames.Type type, long seed, float ease, boolean refuse, String itemKey, Minigames.Scene scene) {
    public static void encode(MinigameStartPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.session);
        buf.writeByte(p.type.ordinal());
        buf.writeLong(p.seed);
        buf.writeFloat(p.ease);
        buf.writeBoolean(p.refuse);
        buf.writeUtf(p.itemKey, 128);
        Minigames.Scene s = p.scene;
        buf.writeByte(s.part());
        buf.writeByte(s.stage());
        buf.writeLong(s.seed());
        buf.writeVarInt(s.flags());
        buf.writeByte(s.bullets());
        buf.writeByte(s.fragments());
        buf.writeByte(s.organ());
    }

    public static MinigameStartPacket decode(FriendlyByteBuf buf) {
        return new MinigameStartPacket(buf.readVarInt(), Minigames.Type.byOrdinal(buf.readByte()), buf.readLong(), buf.readFloat(),
                buf.readBoolean(), buf.readUtf(128),
                new Minigames.Scene(buf.readByte(), buf.readByte(), buf.readLong(), buf.readVarInt(), buf.readByte(), buf.readByte(), buf.readByte()));
    }

    public static void handle(MinigameStartPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> faygolover.rpmedicine.client.ClientHandlers.onMinigame(p)));
        ctx.get().setPacketHandled(true);
    }
}
