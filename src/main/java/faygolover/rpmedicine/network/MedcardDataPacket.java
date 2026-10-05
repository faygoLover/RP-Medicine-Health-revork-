package faygolover.rpmedicine.network;

import com.google.gson.Gson;
import faygolover.rpmedicine.medcard.Medcard;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.UUID;
import java.util.function.Supplier;

/** Сервер → клиент: медкарта целиком (открыть экран или обновить открытый). Карта едет JSON-ом. */
public record MedcardDataPacket(Medcard card) {
    private static final Gson GSON = new Gson();
    private static final int MAX_ENTRIES = 200;
    private static final int MAX_JSON = 1 << 18;

    public static MedcardDataPacket of(Medcard c) {
        Medcard copy = GSON.fromJson(GSON.toJson(c), Medcard.class);
        copy.uuid = c.uuid;
        if (copy.entries.size() > MAX_ENTRIES) copy.entries = new ArrayList<>(copy.entries.subList(copy.entries.size() - MAX_ENTRIES, copy.entries.size()));
        // Старым картам без даты заведения — дата первой записи.
        if (copy.created <= 0 && !copy.entries.isEmpty()) copy.created = copy.entries.get(0).time;
        return new MedcardDataPacket(copy);
    }

    public UUID uuid() {
        return card.uuid;
    }

    public static void encode(MedcardDataPacket p, FriendlyByteBuf buf) {
        buf.writeUUID(p.card.uuid);
        buf.writeUtf(GSON.toJson(p.card), MAX_JSON);
    }

    public static MedcardDataPacket decode(FriendlyByteBuf buf) {
        UUID uuid = buf.readUUID();
        Medcard c = GSON.fromJson(buf.readUtf(MAX_JSON), Medcard.class);
        if (c == null) c = new Medcard(uuid);
        c.uuid = uuid;
        c.fixNulls();
        return new MedcardDataPacket(c);
    }

    public static void handle(MedcardDataPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> faygolover.rpmedicine.client.ClientHandlers.onMedcard(p)));
        ctx.get().setPacketHandled(true);
    }
}
