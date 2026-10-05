package faygolover.rpmedicine.network;

import faygolover.rpmedicine.core.BodyPart;
import faygolover.rpmedicine.core.Examination;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Сервер → клиент: результат осмотра для панели. Строки уже отфильтрованы по уровню медицины
 * осматривающего — цифр и скрытого состояния здесь нет.
 *
 * @param removable по частям: биты 1 — повязка, 2 — жгут, 4 — шина, 8 — окклюзионная наклейка, 16 — вправить вывих
 * @param downState 0 — на ногах, 1 — обморок, 2 — нокдаун, 3 — клиническая смерть
 * @param bars      состояние частей 0–100 (целостность), как видит осматривающий
 * @param overall   общий запас 0–100
 * @param numbers   показывать цифры (медицина 4+ или ГМ)
 * @param knockdownSeconds секунд до конца нокдауна, −1 — не показывать
 * @param general   общие кнопки: 1 — извлечь трубку, 2 — убрать воздуховод
 */
public record ExamResultPacket(int targetId, boolean self, Component name, Examination.View view, byte[] removable, byte downState,
                               byte[] bars, byte overall, boolean numbers, short knockdownSeconds, byte general) {
    public boolean sameAs(ExamResultPacket o) {
        return o != null && view.equals(o.view) && java.util.Arrays.equals(removable, o.removable) && downState == o.downState
                && java.util.Arrays.equals(bars, o.bars) && overall == o.overall && numbers == o.numbers
                && knockdownSeconds == o.knockdownSeconds && general == o.general;
    }

    public static void encode(ExamResultPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.targetId);
        buf.writeBoolean(p.self);
        buf.writeComponent(p.name);
        buf.writeVarInt(p.view.level());
        buf.writeVarInt(p.view.parts().size());
        for (Examination.PartView pv : p.view.parts()) {
            buf.writeByte(pv.part().ordinal());
            buf.writeByte(pv.color());
            writeLines(buf, pv.lines());
        }
        writeLines(buf, p.view.general());
        buf.writeByteArray(p.removable);
        buf.writeByte(p.downState);
        buf.writeByteArray(p.bars);
        buf.writeByte(p.overall);
        buf.writeBoolean(p.numbers);
        buf.writeShort(p.knockdownSeconds);
        buf.writeByte(p.general);
    }

    public static ExamResultPacket decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        boolean self = buf.readBoolean();
        Component name = buf.readComponent();
        int level = buf.readVarInt();
        int n = Math.min(9, buf.readVarInt());
        List<Examination.PartView> parts = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            BodyPart part = BodyPart.byOrdinal(buf.readByte());
            int color = buf.readByte();
            parts.add(new Examination.PartView(part, color, readLines(buf)));
        }
        List<Examination.Line> general = readLines(buf);
        byte[] removable = buf.readByteArray(9);
        byte down = buf.readByte();
        byte[] bars = buf.readByteArray(9);
        byte overall = buf.readByte();
        boolean numbers = buf.readBoolean();
        short kd = buf.readShort();
        byte gen = buf.readByte();
        return new ExamResultPacket(id, self, name, new Examination.View(parts, general, level), removable, down, bars, overall, numbers, kd, gen);
    }

    static void writeLines(FriendlyByteBuf buf, List<Examination.Line> lines) {
        buf.writeVarInt(lines.size());
        for (Examination.Line l : lines) {
            buf.writeUtf(l.key(), 64);
            buf.writeVarIntArray(l.args());
        }
    }

    static List<Examination.Line> readLines(FriendlyByteBuf buf) {
        int n = Math.min(64, buf.readVarInt());
        List<Examination.Line> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(new Examination.Line(buf.readUtf(64), buf.readVarIntArray(8)));
        return out;
    }

    public static void handle(ExamResultPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> faygolover.rpmedicine.client.ClientHandlers.onExam(p)));
        ctx.get().setPacketHandled(true);
    }
}
