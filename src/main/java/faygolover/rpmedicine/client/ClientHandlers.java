package faygolover.rpmedicine.client;

import faygolover.rpmedicine.client.screen.ClinicalDeathScreen;
import faygolover.rpmedicine.client.screen.HudEditorScreen;
import faygolover.rpmedicine.client.screen.MedicalPanelScreen;
import faygolover.rpmedicine.network.EntityDownedPacket;
import faygolover.rpmedicine.network.ExamResultPacket;
import faygolover.rpmedicine.network.HoverInfoPacket;
import faygolover.rpmedicine.network.ProgressPacket;
import faygolover.rpmedicine.network.SelfView;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

/** Обработка пакетов на клиенте. Вызывается только на физическом клиенте. */
public final class ClientHandlers {
    private ClientHandlers() {}

    public static void onSelfState(SelfView v) {
        boolean wasClinical = ClientState.self.down == 3;
        ClientState.self = v;
        ClientState.selfTime = System.currentTimeMillis();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            if (v.isDown()) ClientState.DOWNED.add(mc.player.getId());
            else ClientState.DOWNED.remove(mc.player.getId());
        }
        if (v.down == 3 && !(mc.screen instanceof ClinicalDeathScreen)) mc.setScreen(new ClinicalDeathScreen());
        if (wasClinical && v.down != 3 && mc.screen instanceof ClinicalDeathScreen) mc.setScreen(null);
    }

    public static void onExam(ExamResultPacket p) {
        ClientState.exam = p;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof MedicalPanelScreen panel) panel.onExam(p);
    }

    public static void onHover(HoverInfoPacket p) {
        ClientState.hoverTarget = p.targetId();
        ClientState.hoverLines = p.lines();
    }

    public static void onProgress(ProgressPacket p) {
        ClientState.progressLabel = p.labelKey();
        ClientState.progressTotal = p.totalTicks();
        ClientState.progressDone = p.doneTicks();
        ClientState.progressStart = System.currentTimeMillis();
    }

    public static void openHudEditor() {
        Minecraft.getInstance().setScreen(new HudEditorScreen());
    }

    public static void onEntityDowned(EntityDownedPacket p) {
        if (p.down()) ClientState.DOWNED.add(p.entityId());
        else ClientState.DOWNED.remove(p.entityId());
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(p.entityId());
        if (e instanceof Player pl) pl.setForcedPose(p.down() ? Pose.SWIMMING : null);
    }
}
