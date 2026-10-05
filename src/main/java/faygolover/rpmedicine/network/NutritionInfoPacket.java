package faygolover.rpmedicine.network;

import faygolover.rpmedicine.core.Nutrition;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Сервер → клиент: состав еды (ККАЛ, БЖУ, витамины) для подсказок. Шлётся при синхронизации датапаков. */
public record NutritionInfoPacket(Map<ResourceLocation, Nutrition.Food> foods) {
    public static void encode(NutritionInfoPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.foods.size());
        for (var e : p.foods.entrySet()) {
            buf.writeResourceLocation(e.getKey());
            Nutrition.Food f = e.getValue();
            buf.writeFloat((float) f.kcal());
            buf.writeFloat((float) f.protein());
            buf.writeFloat((float) f.fat());
            buf.writeFloat((float) f.carbs());
            buf.writeFloat((float) f.vitamins());
        }
    }

    public static NutritionInfoPacket decode(FriendlyByteBuf buf) {
        int n = Math.min(50000, buf.readVarInt());
        Map<ResourceLocation, Nutrition.Food> m = new HashMap<>(n);
        for (int i = 0; i < n; i++)
            m.put(buf.readResourceLocation(), new Nutrition.Food(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()));
        return new NutritionInfoPacket(m);
    }

    public static void handle(NutritionInfoPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            faygolover.rpmedicine.client.ClientState.FOODS.clear();
            faygolover.rpmedicine.client.ClientState.FOODS.putAll(p.foods);
        }));
        ctx.get().setPacketHandled(true);
    }
}
