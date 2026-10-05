package faygolover.rpmedicine.network;

import faygolover.rpmedicine.core.Drug;
import faygolover.rpmedicine.core.DrugEffect;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Сервер → клиент: свойства медицинских предметов из датапака сервера (время, уровень, препарат) — для
 * подробной подсказки по Shift. Отправляется при входе и после {@code /reload}.
 */
public record ItemInfoPacket(List<Info> items) {
    /**
     * @param analogOf наш предмет, аналогом которого считается чужой (или null)
     * @param drug     препарат (или null)
     */
    public record Info(ResourceLocation item, String action, double seconds, int minLevel, boolean consume,
                       @Nullable ResourceLocation analogOf, @Nullable Drug drug) {}

    public static void encode(ItemInfoPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.items.size());
        for (Info i : p.items) {
            buf.writeResourceLocation(i.item);
            buf.writeUtf(i.action, 64);
            buf.writeFloat((float) i.seconds);
            buf.writeVarInt(i.minLevel);
            buf.writeBoolean(i.consume);
            buf.writeBoolean(i.analogOf != null);
            if (i.analogOf != null) buf.writeResourceLocation(i.analogOf);
            buf.writeBoolean(i.drug != null);
            if (i.drug != null) writeDrug(buf, i.drug);
        }
    }

    public static ItemInfoPacket decode(FriendlyByteBuf buf) {
        int n = Math.min(4096, buf.readVarInt());
        List<Info> out = new ArrayList<>(n);
        for (int k = 0; k < n; k++) {
            ResourceLocation item = buf.readResourceLocation();
            String action = buf.readUtf(64);
            double sec = buf.readFloat();
            int lvl = buf.readVarInt();
            boolean consume = buf.readBoolean();
            ResourceLocation analog = buf.readBoolean() ? buf.readResourceLocation() : null;
            Drug drug = buf.readBoolean() ? readDrug(buf) : null;
            out.add(new Info(item, action, sec, lvl, consume, analog, drug));
        }
        return new ItemInfoPacket(out);
    }

    private static void writeDrug(FriendlyByteBuf buf, Drug d) {
        buf.writeUtf(d.id(), 64);
        buf.writeEnum(d.form());
        writeDoses(buf, d.effects());
        buf.writeVarInt(d.doseLimit());
        buf.writeFloat((float) d.doseWindowSeconds());
        writeDoses(buf, d.overdose());
        buf.writeFloat((float) d.overdoseArrestChance());
        buf.writeBoolean(d.opioid());
        buf.writeEnum(d.special());
    }

    private static Drug readDrug(FriendlyByteBuf buf) {
        String id = buf.readUtf(64);
        Drug.Form form = buf.readEnum(Drug.Form.class);
        List<Drug.Dose> eff = readDoses(buf);
        int limit = buf.readVarInt();
        double window = buf.readFloat();
        List<Drug.Dose> od = readDoses(buf);
        double arrest = buf.readFloat();
        boolean opioid = buf.readBoolean();
        Drug.Special sp = buf.readEnum(Drug.Special.class);
        return new Drug(id, form, eff, limit, window, od, arrest, opioid, sp);
    }

    private static void writeDoses(FriendlyByteBuf buf, List<Drug.Dose> doses) {
        buf.writeVarInt(doses.size());
        for (Drug.Dose d : doses) {
            buf.writeVarInt(d.effect().ordinal());
            buf.writeFloat((float) d.strength());
            buf.writeFloat((float) d.delay());
            buf.writeFloat((float) d.seconds());
        }
    }

    private static List<Drug.Dose> readDoses(FriendlyByteBuf buf) {
        int n = Math.min(32, buf.readVarInt());
        List<Drug.Dose> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++)
            out.add(new Drug.Dose(DrugEffect.byOrdinal(buf.readVarInt()), buf.readFloat(), buf.readFloat(), buf.readFloat()));
        return out;
    }

    public static void handle(ItemInfoPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> faygolover.rpmedicine.client.ItemTooltips.set(p)));
        ctx.get().setPacketHandled(true);
    }
}
