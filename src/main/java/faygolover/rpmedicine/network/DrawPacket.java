package faygolover.rpmedicine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Набор шприцем из флакона (решения, п. 1.16). Сервер → клиент: что во флаконе, сколько мл осталось, подсказка
 * (только опытному, иначе 0), умеет ли медик выбирать объём. Клиент → сервер: набранные мл (0 — отмена) и
 * сколько раз промахнулся мимо пробки.
 */
public final class DrawPacket {
    private DrawPacket() {}

    public record Request(Component drug, Component label, float left, float hint, boolean skilled, int level) {
        public static void encode(Request p, FriendlyByteBuf buf) {
            buf.writeComponent(p.drug);
            buf.writeComponent(p.label);
            buf.writeFloat(p.left);
            buf.writeFloat(p.hint);
            buf.writeBoolean(p.skilled);
            buf.writeVarInt(p.level);
        }

        public static Request decode(FriendlyByteBuf buf) {
            return new Request(buf.readComponent(), buf.readComponent(), buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readVarInt());
        }

        public static void handle(Request p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> faygolover.rpmedicine.client.ClientHandlers.onDrawRequest(p)));
            ctx.get().setPacketHandled(true);
        }
    }

    public record Choice(float ml, int misses) {
        public static void encode(Choice p, FriendlyByteBuf buf) {
            buf.writeFloat(p.ml);
            buf.writeVarInt(p.misses);
        }

        public static Choice decode(FriendlyByteBuf buf) {
            return new Choice(buf.readFloat(), buf.readVarInt());
        }

        public static void handle(Choice p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sp = ctx.get().getSender();
            if (sp != null) ctx.get().enqueueWork(() -> faygolover.rpmedicine.server.VialService.onChoice(sp, p.ml, p.misses));
            ctx.get().setPacketHandled(true);
        }
    }
}
