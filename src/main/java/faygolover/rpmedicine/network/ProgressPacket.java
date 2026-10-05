package faygolover.rpmedicine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Сервер → клиент: прогресс-бар действия. {@code totalTicks} ≤ 0 — действие закончилось или прервано.
 * Клиент сам продвигает полосу между пакетами.
 *
 * @param labelKey ключ перевода названия действия
 * @param icon     предмет, который применяют (иконка перед названием), может быть пустым
 * @param subtitle на кого (у медика) или кто лечит (у пациента)
 * @param incoming это прогресс лечения, которое применяют к самому игроку
 */
public record ProgressPacket(String labelKey, int totalTicks, int doneTicks, ItemStack icon, Component subtitle, boolean incoming) {
    public ProgressPacket(String labelKey, int totalTicks, int doneTicks) {
        this(labelKey, totalTicks, doneTicks, ItemStack.EMPTY, Component.empty(), false);
    }

    public static void encode(ProgressPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.labelKey, 128);
        buf.writeVarInt(p.totalTicks);
        buf.writeVarInt(p.doneTicks);
        buf.writeItem(p.icon);
        buf.writeComponent(p.subtitle);
        buf.writeBoolean(p.incoming);
    }

    public static ProgressPacket decode(FriendlyByteBuf buf) {
        return new ProgressPacket(buf.readUtf(128), buf.readVarInt(), buf.readVarInt(), buf.readItem(), buf.readComponent(), buf.readBoolean());
    }

    public static ProgressPacket stop() {
        return new ProgressPacket("", 0, 0);
    }

    public static ProgressPacket stopIncoming() {
        return new ProgressPacket("", 0, 0, ItemStack.EMPTY, Component.empty(), true);
    }

    public static void handle(ProgressPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> faygolover.rpmedicine.client.ClientHandlers.onProgress(p)));
        ctx.get().setPacketHandled(true);
    }
}
