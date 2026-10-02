package faygolover.rpmedicine.network;

import faygolover.rpmedicine.RpMedicine;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Сетевой канал мода. Сервер → клиент: только изменения и только то, что клиенту положено видеть. */
public final class Network {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(RpMedicine.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private static int id = 0;

    private Network() {}

    public static void register() {
        // Сервер → клиент
        toClient(SelfStatePacket.class, SelfStatePacket::encode, SelfStatePacket::decode, SelfStatePacket::handle);
        toClient(ExamResultPacket.class, ExamResultPacket::encode, ExamResultPacket::decode, ExamResultPacket::handle);
        toClient(HoverInfoPacket.class, HoverInfoPacket::encode, HoverInfoPacket::decode, HoverInfoPacket::handle);
        toClient(ProgressPacket.class, ProgressPacket::encode, ProgressPacket::decode, ProgressPacket::handle);
        toClient(OpenHudEditorPacket.class, OpenHudEditorPacket::encode, OpenHudEditorPacket::decode, OpenHudEditorPacket::handle);
        toClient(EntityDownedPacket.class, EntityDownedPacket::encode, EntityDownedPacket::decode, EntityDownedPacket::handle);
        // Клиент → сервер
        toServer(RequestExamPacket.class, RequestExamPacket::encode, RequestExamPacket::decode, RequestExamPacket::handle);
        toServer(PanelActionPacket.class, PanelActionPacket::encode, PanelActionPacket::decode, PanelActionPacket::handle);
        toServer(DownedActionPacket.class, DownedActionPacket::encode, DownedActionPacket::decode, DownedActionPacket::handle);
        toServer(HoverRequestPacket.class, HoverRequestPacket::encode, HoverRequestPacket::decode, HoverRequestPacket::handle);
    }

    private static <T> void toClient(Class<T> type, BiConsumer<T, FriendlyByteBuf> enc, Function<FriendlyByteBuf, T> dec,
                                     BiConsumer<T, Supplier<NetworkEvent.Context>> handler) {
        CHANNEL.registerMessage(id++, type, enc, dec, handler, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    private static <T> void toServer(Class<T> type, BiConsumer<T, FriendlyByteBuf> enc, Function<FriendlyByteBuf, T> dec,
                                     BiConsumer<T, Supplier<NetworkEvent.Context>> handler) {
        CHANNEL.registerMessage(id++, type, enc, dec, handler, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void send(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }
}
