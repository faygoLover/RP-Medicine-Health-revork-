package faygolover.rpmedicine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Сервер → клиент: бланк анализа крови (замечание 06.10: результаты в окне, а не в чате).
 * values — в порядке {@link #ROWS}; compat: -1 пакета нет, 0 группа неизвестна, 1 совместим, 2 нет.
 */
public record LabResultPacket(String patient, String bloodType, double[] values, boolean sepsis, byte compat, String bag) {
    /** Показатели бланка: ключ, норма от, норма до, знаков после запятой. */
    public record Row(String key, double low, double high, int digits) {}

    public static final Row[] ROWS = {
            new Row("hemoglobin", 120, 160, 0), new Row("leukocytes", 4, 9, 1), new Row("alt", 0, 40, 0),
            new Row("creatinine", 60, 110, 0), new Row("troponin", 0, 14, 0), new Row("albumin", 35, 50, 0),
            new Row("triglycerides", 0.5, 1.7, 1), new Row("glucose", 3.9, 6.1, 1), new Row("b12", 200, 900, 0)};

    public static void encode(LabResultPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.patient, 64);
        buf.writeUtf(p.bloodType, 8);
        for (int i = 0; i < ROWS.length; i++) buf.writeFloat((float) p.values[i]);
        buf.writeBoolean(p.sepsis);
        buf.writeByte(p.compat);
        buf.writeUtf(p.bag, 8);
    }

    public static LabResultPacket decode(FriendlyByteBuf buf) {
        String patient = buf.readUtf(64);
        String type = buf.readUtf(8);
        double[] v = new double[ROWS.length];
        for (int i = 0; i < v.length; i++) v[i] = buf.readFloat();
        return new LabResultPacket(patient, type, v, buf.readBoolean(), buf.readByte(), buf.readUtf(8));
    }

    public static void handle(LabResultPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> faygolover.rpmedicine.client.ClientHandlers.openLab(p)));
        ctx.get().setPacketHandled(true);
    }
}
