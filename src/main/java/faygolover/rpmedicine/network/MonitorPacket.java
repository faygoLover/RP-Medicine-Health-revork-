package faygolover.rpmedicine.network;

import faygolover.rpmedicine.core.MedicalState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Сервер → клиент: что показывает монитор (п. 2.3 ТЗ второго этапа). Цифры только тому, кто смотрит
 * на монитор, и только пока смотрит. −1 — прибор не может измерить (нет пульса).
 *
 * @param state  0 — пациент есть, 1 — койка пуста, 2 — не хватает уровня «Медицины»
 * @param rhythm 0 — синусовый, 1 — фибрилляция, 2 — асистолия
 */
public record MonitorPacket(BlockPos pos, byte state, short hr, short sys, short dia, short spo2, short rr, byte rhythm) {
    public static MonitorPacket of(BlockPos pos, MedicalState m) {
        boolean pulse = m.heart == MedicalState.Heart.NORMAL;
        byte rhythm = (byte) switch (m.heart) {
            case NORMAL -> 0;
            case FIBRILLATION -> 1;
            case ARREST -> 2;
        };
        short hr = (short) (pulse ? Math.round(m.heartRate) : 0);
        // Давление при остановке — только от компрессий СЛР; оксиметр без пульса не меряет.
        short sys = (short) (m.pressure >= 20 ? Math.round(m.pressure) : (pulse ? Math.round(m.pressure) : 0));
        short dia = (short) Math.round(sys * 0.65);
        short spo2 = (short) (pulse ? Math.round(m.spo2) : -1);
        short rr = (short) Math.round(m.respRate);
        return new MonitorPacket(pos, (byte) 0, hr, sys, dia, spo2, rr, rhythm);
    }

    public static MonitorPacket empty(BlockPos pos) {
        return new MonitorPacket(pos, (byte) 1, (short) 0, (short) 0, (short) 0, (short) 0, (short) 0, (byte) 0);
    }

    public static MonitorPacket locked(BlockPos pos) {
        return new MonitorPacket(pos, (byte) 2, (short) 0, (short) 0, (short) 0, (short) 0, (short) 0, (byte) 0);
    }

    public static void encode(MonitorPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.pos);
        buf.writeByte(p.state);
        buf.writeShort(p.hr);
        buf.writeShort(p.sys);
        buf.writeShort(p.dia);
        buf.writeShort(p.spo2);
        buf.writeShort(p.rr);
        buf.writeByte(p.rhythm);
    }

    public static MonitorPacket decode(FriendlyByteBuf buf) {
        return new MonitorPacket(buf.readBlockPos(), buf.readByte(), buf.readShort(), buf.readShort(), buf.readShort(),
                buf.readShort(), buf.readShort(), buf.readByte());
    }

    public static void handle(MonitorPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> faygolover.rpmedicine.client.ClientHandlers.onMonitor(p)));
        ctx.get().setPacketHandled(true);
    }
}
