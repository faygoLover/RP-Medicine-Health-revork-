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
        if (v.useTimePct > 100) event.setDuration((int) Math.ceil(event.getDuration() * v.useTimePct / 100.0));
    }

    public static boolean armsDisabled() {
        return ClientState.self.armsDisabled;
    }
}
