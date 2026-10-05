package faygolover.rpmedicine.client.render;

import faygolover.rpmedicine.client.ClientState;
import faygolover.rpmedicine.config.ClientConfig;
import faygolover.rpmedicine.core.BodyPart;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraftforge.client.event.RenderPlayerEvent;

/**
 * Отсутствующая конечность не рисуется на модели игрока (ТЗ третьего этапа, п. 12). Протез рисуется как
 * обычная конечность. Отключается в клиентских настройках.
 */
public final class MissingLimbs {
    private MissingLimbs() {}

    private static boolean has(int mask, BodyPart p) {
        return (mask & (1 << p.ordinal())) != 0;
    }

    public static void onRenderPre(RenderPlayerEvent.Pre e) {
        if (!ClientConfig.SHOW_MISSING_LIMBS.get()) return;
        Integer mask = ClientState.MISSING_LIMBS.get(e.getEntity().getId());
        if (mask == null || mask == 0) return;
        PlayerModel<AbstractClientPlayer> model = e.getRenderer().getModel();
        if (has(mask, BodyPart.RIGHT_ARM)) {
            model.rightArm.visible = false;
            model.rightSleeve.visible = false;
        }
        if (has(mask, BodyPart.LEFT_ARM)) {
            model.leftArm.visible = false;
            model.leftSleeve.visible = false;
        }
        if (has(mask, BodyPart.RIGHT_LEG)) {
            model.rightLeg.visible = false;
            model.rightPants.visible = false;
        }
        if (has(mask, BodyPart.LEFT_LEG)) {
            model.leftLeg.visible = false;
            model.leftPants.visible = false;
        }
    }

    public static void onRenderPost(RenderPlayerEvent.Post e) {
        Integer mask = ClientState.MISSING_LIMBS.get(e.getEntity().getId());
        if (mask == null || mask == 0) return;
        PlayerModel<AbstractClientPlayer> model = e.getRenderer().getModel();
        model.rightArm.visible = true;
        model.leftArm.visible = true;
        model.rightLeg.visible = true;
        model.leftLeg.visible = true;
        // Слои одежды: видимость задаёт настройка скина при следующей отрисовке.
        model.rightSleeve.visible = true;
        model.leftSleeve.visible = true;
        model.rightPants.visible = true;
        model.leftPants.visible = true;
    }
}
