package faygolover.rpmedicine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Фото для медкарты (замечание 09.10, М2). Скин с Ely.by (заплатка лаунчера) часто есть только у клиента: в профиле
 * игрока на сервере его нет (одиночная игра, сервер без той же заплатки). Поэтому сервер просит клиента прислать
 * свой уже загруженный скин картинкой — один раз, когда карта заводится; дальше карта хранит этот снимок.
 */
public final class MedcardPhotoPacket {
    private MedcardPhotoPacket() {}

    /** PNG скина не больше этого (64×64 — несколько килобайт). */
    public static final int MAX_BYTES = 30000;

    /** Сервер → клиент: сфотографируй свой скин. */
    public record Request() {
        public static void encode(Request p, FriendlyByteBuf buf) {}

        public static Request decode(FriendlyByteBuf buf) {
            return new Request();
        }

        public static void handle(Request p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> faygolover.rpmedicine.client.SkinPhoto::request));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Клиент → сервер: PNG своего скина. */
    public record Photo(byte[] png) {
        public static void encode(Photo p, FriendlyByteBuf buf) {
            buf.writeByteArray(p.png);
        }

        public static Photo decode(FriendlyByteBuf buf) {
            return new Photo(buf.readByteArray(MAX_BYTES));
        }

        public static void handle(Photo p, Supplier<NetworkEvent.Context> ctx) {
            var sp = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (sp != null) faygolover.rpmedicine.medcard.MedcardStore.onPhoto(sp, p.png);
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
