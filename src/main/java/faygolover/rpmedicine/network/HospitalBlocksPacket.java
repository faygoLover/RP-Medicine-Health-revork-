package faygolover.rpmedicine.network;

import faygolover.rpmedicine.hospital.HospitalBlocks;
import faygolover.rpmedicine.hospital.HospitalFunction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Сервер → клиент: какие блоки выполняют функции госпиталя (датапак сервера). Клиенту нужно, чтобы
 * знать, что игрок смотрит на монитор, и не отдавать клик по койке самому блоку.
 */
public record HospitalBlocksPacket(Map<HospitalFunction, List<String>> blocks, Map<HospitalFunction, Integer> radii) {
    public static void encode(HospitalBlocksPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(HospitalFunction.VALUES.length);
        for (HospitalFunction f : HospitalFunction.VALUES) {
            buf.writeUtf(f.id, 32);
            buf.writeVarInt(p.radii.getOrDefault(f, f.defaultRadius));
            List<String> list = p.blocks.getOrDefault(f, List.of());
            buf.writeVarInt(list.size());
            for (String s : list) buf.writeUtf(s, 256);
        }
    }

    public static HospitalBlocksPacket decode(FriendlyByteBuf buf) {
        EnumMap<HospitalFunction, List<String>> blocks = new EnumMap<>(HospitalFunction.class);
        EnumMap<HospitalFunction, Integer> radii = new EnumMap<>(HospitalFunction.class);
        int n = Math.min(64, buf.readVarInt());
        for (int i = 0; i < n; i++) {
            String id = buf.readUtf(32);
            int r = buf.readVarInt();
            int count = Math.min(4096, buf.readVarInt());
            List<String> list = new ArrayList<>(count);
            for (int j = 0; j < count; j++) list.add(buf.readUtf(256));
            HospitalFunction.byId(id).ifPresent(f -> {
                blocks.put(f, list);
                radii.put(f, r);
            });
        }
        return new HospitalBlocksPacket(blocks, radii);
    }

    public static void handle(HospitalBlocksPacket p, Supplier<NetworkEvent.Context> ctx) {
        // На встроенном сервере списки уже те же самые; на выделенном клиент своих не грузит.
        ctx.get().enqueueWork(() -> HospitalBlocks.set(p.blocks, p.radii));
        ctx.get().setPacketHandled(true);
    }
}
