package faygolover.rpmedicine.client;

import faygolover.rpmedicine.network.SelfView;
import net.minecraft.client.Minecraft;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** Клиентская половина ограничений рук: те же множители, что на сервере, чтобы не было рассинхрона. */
public final class ClientInteraction {
    private ClientInteraction() {}

    private static boolean isSelf(net.minecraft.world.entity.Entity e) {
        return e == Minecraft.getInstance().player;
    }

    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (!isSelf(event.getEntity())) return;
        SelfView v = ClientState.self;
        if (v.isDown()) {
            event.setNewSpeed(0);
            return;
        }
        if (v.breakSpeedPct < 100) event.setNewSpeed(event.getNewSpeed() * Math.max(0, v.breakSpeedPct) / 100f);
    }

    public static void onUseStart(LivingEntityUseItemEvent.Start event) {
        if (!isSelf(event.getEntity())) return;
        SelfView v = ClientState.self;
        if (v.isDown()) {
            event.setCanceled(true);
            return;
        }
    }

    /** Замедление использования своей рукой (для откатов тиков в {@code InteractionHandler.onUseTick}). */
    public static double useTimeFactor(net.minecraft.world.entity.player.Player p) {
        return isSelf(p) ? Math.max(1.0, ClientState.self.useTimePct / 100.0) : 1.0;
    }

    /** То же с едой: приелось — дольше (вид еды и приевшиеся виды — с сервера). */
    public static double useTimeFactor(net.minecraft.world.entity.player.Player p, net.minecraft.world.item.ItemStack item) {
        double f = useTimeFactor(p);
        if (!isSelf(p) || !item.isEdible()) return f;
        Integer cat = ClientState.FOOD_CATEGORIES.get(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item.getItem()));
        if (cat != null && (ClientState.self.fedUpMask & (1 << cat)) != 0) f *= ClientState.self.monotonyEatSlowPct / 100.0;
        return f;
    }

    /**
     * Запрет прыжка: если прыжок всё-таки случился (автопрыжок, лестница, рывок спринта), гасим и подъём,
     * и толчок вперёд — иначе со сломанной ногой оставался «микрорывок».
     */
    public static void onJump(net.minecraft.world.entity.player.Player p) {
        if (!isSelf(p) || !ClientState.self.noJump) return;
        var v = p.getDeltaMovement();
        double x = v.x;
        double z = v.z;
        if (p.isSprinting()) {
            float yaw = p.getYRot() * ((float) Math.PI / 180F);
            x += net.minecraft.util.Mth.sin(yaw) * 0.2F;
            z -= net.minecraft.util.Mth.cos(yaw) * 0.2F;
        }
        p.setDeltaMovement(x, Math.min(0, v.y), z);
        p.setSprinting(false);
    }

    /** Лежачий ли (для СЛР пустой рукой на клиенте). */
    public static boolean isDowned(net.minecraft.world.entity.Entity e) {
        return e instanceof faygolover.rpmedicine.entity.BodyStubEntity || ClientState.DOWNED.contains(e.getId());
    }

    public static boolean armsDisabled() {
        return ClientState.self.armsDisabled;
    }
}
