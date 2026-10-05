package faygolover.rpmedicine.network;

import faygolover.rpmedicine.core.BodyPart;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Какой орган изъять (ТЗ третьего этапа, п. 7.1): сервер → клиент — органы части; клиент → сервер — выбор (пусто — отмена). */
public final class OrganChoicePacket {
    private OrganChoicePacket() {}

    public record Request(int targetId, int slot, int part, List<String> organs) {
        public static void encode(Request p, FriendlyByteBuf buf) {
            buf.writeVarInt(p.targetId + 1);
            buf.writeVarInt(p.slot);
            buf.writeVarInt(p.part);
            buf.writeVarInt(p.organs.size());
            for (String o : p.organs) buf.writeUtf(o, 32);
        }

        public static Request decode(FriendlyByteBuf buf) {
            int t = buf.readVarInt() - 1;
            int slot = buf.readVarInt();
            int part = buf.readVarInt();
            int n = Math.min(8, buf.readVarInt());
            List<String> o = new ArrayList<>();
            for (int i = 0; i < n; i++) o.add(buf.readUtf(32));
            return new Request(t, slot, part, o);
        }

        public static void handle(Request p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> faygolover.rpmedicine.client.ClientHandlers.onOrganChoice(p)));
            ctx.get().setPacketHandled(true);
        }
    }

    public record Choice(int targetId, int slot, int part, String organ) {
        public static void encode(Choice p, FriendlyByteBuf buf) {
            buf.writeVarInt(p.targetId + 1);
            buf.writeVarInt(p.slot);
            buf.writeVarInt(p.part);
            buf.writeUtf(p.organ, 32);
        }

        public static Choice decode(FriendlyByteBuf buf) {
            return new Choice(buf.readVarInt() - 1, buf.readVarInt(), buf.readVarInt(), buf.readUtf(32));
        }

        public static void handle(Choice p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sp = ctx.get().getSender();
            if (sp != null) ctx.get().enqueueWork(() -> faygolover.rpmedicine.server.TreatmentService.onOrganChoice(sp, p.targetId, p.slot,
                    p.part >= 0 && p.part < BodyPart.VALUES.length ? BodyPart.VALUES[p.part] : BodyPart.ABDOMEN, p.organ));
            ctx.get().setPacketHandled(true);
        }
    }
}
