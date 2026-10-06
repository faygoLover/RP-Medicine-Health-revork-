package faygolover.rpmedicine.network;

import faygolover.rpmedicine.core.BodyPart;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Выбор дозы шприцем (решения, п. 1.13). Сервер → клиент: запрос (кому, из какого слота, вес пациента);
 * клиент → сервер: выбранная доза (0 — отмена).
 *
 * @param part часть тела или null — выбрать самому
 */
public final class DosePacket {
    private DosePacket() {}

    /** max — сколько доз осталось в ручке; hint — сколько нужно этому пациенту (подсказка опытному). */
    public record Request(int targetId, int slot, int part, Component drug, int weightKg, float max, float hint) {
        public static void encode(Request p, FriendlyByteBuf buf) {
            buf.writeVarInt(p.targetId + 1);
            buf.writeVarInt(p.slot);
            buf.writeVarInt(p.part + 1);
            buf.writeComponent(p.drug);
            buf.writeVarInt(p.weightKg);
            buf.writeFloat(p.max);
            buf.writeFloat(p.hint);
        }

        public static Request decode(FriendlyByteBuf buf) {
            return new Request(buf.readVarInt() - 1, buf.readVarInt(), buf.readVarInt() - 1, buf.readComponent(), buf.readVarInt(),
                    buf.readFloat(), buf.readFloat());
        }

        public static void handle(Request p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> faygolover.rpmedicine.client.ClientHandlers.onDoseRequest(p)));
            ctx.get().setPacketHandled(true);
        }
    }

    public record Choice(int targetId, int slot, int part, float dose) {
        public static void encode(Choice p, FriendlyByteBuf buf) {
            buf.writeVarInt(p.targetId + 1);
            buf.writeVarInt(p.slot);
            buf.writeVarInt(p.part + 1);
            buf.writeFloat(p.dose);
        }

        public static Choice decode(FriendlyByteBuf buf) {
            return new Choice(buf.readVarInt() - 1, buf.readVarInt(), buf.readVarInt() - 1, buf.readFloat());
        }

        public static void handle(Choice p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sp = ctx.get().getSender();
            if (sp != null) ctx.get().enqueueWork(() -> faygolover.rpmedicine.server.TreatmentService.onDoseChoice(sp, p.targetId, p.slot,
                    p.part < 0 ? null : BodyPart.byOrdinal(p.part), p.dose));
            ctx.get().setPacketHandled(true);
        }
    }
}
