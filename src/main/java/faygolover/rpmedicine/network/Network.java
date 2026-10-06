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
    private static final String PROTOCOL = "7";

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
        toClient(HospitalBlocksPacket.class, HospitalBlocksPacket::encode, HospitalBlocksPacket::decode, HospitalBlocksPacket::handle);
        toClient(ItemInfoPacket.class, ItemInfoPacket::encode, ItemInfoPacket::decode, ItemInfoPacket::handle);
        toClient(MonitorPacket.class, MonitorPacket::encode, MonitorPacket::decode, MonitorPacket::handle);
        toClient(MinigameStartPacket.class, MinigameStartPacket::encode, MinigameStartPacket::decode, MinigameStartPacket::handle);
        toClient(MedcardDataPacket.class, MedcardDataPacket::encode, MedcardDataPacket::decode, MedcardDataPacket::handle);
        toClient(GmPanelPacket.class, GmPanelPacket::encode, GmPanelPacket::decode, GmPanelPacket::handle);
        toClient(GmReportPacket.class, GmReportPacket::encode, GmReportPacket::decode, GmReportPacket::handle);
        // Клиент → сервер
        toServer(RequestExamPacket.class, RequestExamPacket::encode, RequestExamPacket::decode, RequestExamPacket::handle);
        toServer(PanelActionPacket.class, PanelActionPacket::encode, PanelActionPacket::decode, PanelActionPacket::handle);
        toServer(DownedActionPacket.class, DownedActionPacket::encode, DownedActionPacket::decode, DownedActionPacket::handle);
        toServer(RhythmPacket.class, RhythmPacket::encode, RhythmPacket::decode, RhythmPacket::handle);
        toServer(GmEditPacket.class, GmEditPacket::encode, GmEditPacket::decode, GmEditPacket::handle);
        toServer(ClientPrefsPacket.class, ClientPrefsPacket::encode, ClientPrefsPacket::decode, ClientPrefsPacket::handle);
        toServer(OrganChoicePacket.Choice.class, OrganChoicePacket.Choice::encode, OrganChoicePacket.Choice::decode, OrganChoicePacket.Choice::handle);
        toClient(OrganChoicePacket.Request.class, OrganChoicePacket.Request::encode, OrganChoicePacket.Request::decode, OrganChoicePacket.Request::handle);
        toClient(LimbsVisualPacket.class, LimbsVisualPacket::encode, LimbsVisualPacket::decode, LimbsVisualPacket::handle);
        toClient(NutritionInfoPacket.class, NutritionInfoPacket::encode, NutritionInfoPacket::decode, NutritionInfoPacket::handle);
        toServer(DosePacket.Choice.class, DosePacket.Choice::encode, DosePacket.Choice::decode, DosePacket.Choice::handle);
        toClient(DosePacket.Request.class, DosePacket.Request::encode, DosePacket.Request::decode, DosePacket.Request::handle);
        toServer(HoverRequestPacket.class, HoverRequestPacket::encode, HoverRequestPacket::decode, HoverRequestPacket::handle);
        toServer(MonitorRequestPacket.class, MonitorRequestPacket::encode, MonitorRequestPacket::decode, MonitorRequestPacket::handle);
        toServer(MinigameResultPacket.class, MinigameResultPacket::encode, MinigameResultPacket::decode, MinigameResultPacket::handle);
        toServer(MedcardActionPacket.class, MedcardActionPacket::encode, MedcardActionPacket::decode, MedcardActionPacket::handle);
        toServer(GmActionPacket.class, GmActionPacket::encode, GmActionPacket::decode, GmActionPacket::handle);
        toClient(LabResultPacket.class, LabResultPacket::encode, LabResultPacket::decode, LabResultPacket::handle);
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
