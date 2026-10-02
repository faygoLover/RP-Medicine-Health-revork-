package faygolover.rpmedicine.network;

import faygolover.rpmedicine.medcard.Medcard;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Сервер → клиент: медкарта целиком (открыть экран или обновить открытый). */
public record MedcardDataPacket(UUID uuid, String name, float height, float weight, String bloodType, String allergies, String chronic,
                                List<Medcard.Entry> entries) {
    private static final int MAX_ENTRIES = 200;

    public static MedcardDataPacket of(Medcard c) {
        List<Medcard.Entry> list = c.entries.size() > MAX_ENTRIES ? c.entries.subList(c.entries.size() - MAX_ENTRIES, c.entries.size()) : c.entries;
        return new MedcardDataPacket(c.uuid, c.name, (float) c.height, (float) c.weight, c.bloodType, c.allergies, c.chronic, new ArrayList<>(list));
    }

    public static void encode(MedcardDataPacket p, FriendlyByteBuf buf) {
        buf.writeUUID(p.uuid);
        buf.writeUtf(p.name, 64);
        buf.writeFloat(p.height);
        buf.writeFloat(p.weight);
        buf.writeUtf(p.bloodType, 8);
        buf.writeUtf(p.allergies, 512);
        buf.writeUtf(p.chronic, 512);
        buf.writeVarInt(p.entries.size());
        for (Medcard.Entry e : p.entries) {
            buf.writeVarInt(e.id);
            buf.writeLong(e.time);
            buf.writeUtf(e.key, 64);
            buf.writeVarInt(e.args.size());
            for (String a : e.args) buf.writeUtf(a, 128);
            buf.writeUtf(e.text, 512);
            buf.writeUtf(e.author, 128);
            buf.writeBoolean(e.proposed);
        }
    }

    public static MedcardDataPacket decode(FriendlyByteBuf buf) {
        UUID uuid = buf.readUUID();
        String name = buf.readUtf(64);
        float h = buf.readFloat();
        float w = buf.readFloat();
        String blood = buf.readUtf(8);
        String allergies = buf.readUtf(512);
        String chronic = buf.readUtf(512);
        int n = Math.min(MAX_ENTRIES, buf.readVarInt());
        List<Medcard.Entry> entries = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Medcard.Entry e = new Medcard.Entry();
            e.id = buf.readVarInt();
            e.time = buf.readLong();
            e.key = buf.readUtf(64);
            int a = Math.min(8, buf.readVarInt());
            for (int j = 0; j < a; j++) e.args.add(buf.readUtf(128));
            e.text = buf.readUtf(512);
            e.author = buf.readUtf(128);
            e.proposed = buf.readBoolean();
            entries.add(e);
        }
        return new MedcardDataPacket(uuid, name, h, w, blood, allergies, chronic, entries);
    }

    public static void handle(MedcardDataPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> faygolover.rpmedicine.client.ClientHandlers.onMedcard(p)));
        ctx.get().setPacketHandled(true);
    }
}
