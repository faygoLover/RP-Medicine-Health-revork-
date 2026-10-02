package faygolover.rpmedicine.client;

import faygolover.rpmedicine.client.screen.ClinicalDeathScreen;
import faygolover.rpmedicine.client.screen.HudEditorScreen;
import faygolover.rpmedicine.client.screen.MedicalPanelScreen;
import faygolover.rpmedicine.hospital.BedPose;
import faygolover.rpmedicine.network.EntityDownedPacket;
import faygolover.rpmedicine.network.MonitorPacket;
import faygolover.rpmedicine.network.ExamResultPacket;
import faygolover.rpmedicine.network.HoverInfoPacket;
import faygolover.rpmedicine.network.ProgressPacket;
import faygolover.rpmedicine.network.SelfView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
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

    public static void onGmPanel(faygolover.rpmedicine.network.GmPanelPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof faygolover.rpmedicine.client.screen.GmPanelScreen s) s.update(p);
        else mc.setScreen(new faygolover.rpmedicine.client.screen.GmPanelScreen(p));
    }

    public static void onGmReport(faygolover.rpmedicine.network.GmReportPacket p) {
        if (Minecraft.getInstance().screen instanceof faygolover.rpmedicine.client.screen.GmPanelScreen s) s.report(p);
    }

    public static void onMedcard(faygolover.rpmedicine.network.MedcardDataPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof faygolover.rpmedicine.client.screen.MedcardScreen ms && ms.uuid().equals(p.uuid())) ms.update(p);
        else mc.setScreen(new faygolover.rpmedicine.client.screen.MedcardScreen(p));
    }

    public static void onMinigame(faygolover.rpmedicine.network.MinigameStartPacket p) {
        Minecraft mc = Minecraft.getInstance();
        Screen back = mc.screen instanceof faygolover.rpmedicine.client.screen.MinigameScreen ? null : mc.screen;
        mc.setScreen(new faygolover.rpmedicine.client.screen.MinigameScreen(p, back));
    }

    public static void onProgress(ProgressPacket p) {
        // Сервер остановил действие, пока шла мини-игра (урон, ушли от пациента) — закрыть её.
        if (p.totalTicks() == 0 && Minecraft.getInstance().screen instanceof faygolover.rpmedicine.client.screen.MinigameScreen ms)
            ms.cancelledByServer();
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
        if (p.onBed()) {
            BedPose.CLIENT_ON_BED.add(p.entityId());
            BedPose.CLIENT_BED_YAW.put(p.entityId(), p.bedQuarter() * 90f);
        } else {
            BedPose.CLIENT_ON_BED.remove(p.entityId());
            BedPose.CLIENT_BED_YAW.remove(p.entityId());
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(p.entityId());
        if (e instanceof Player pl) {
            pl.setForcedPose(poseFor(p.entityId(), pl == mc.player && ClientState.self.crawl));
            pl.refreshDimensions();
        }
    }

    /** Поза игрока на этом клиенте: на койке — на спине, лежачий и ползущий — горизонтально. */
    public static Pose poseFor(int entityId, boolean crawl) {
        if (BedPose.CLIENT_ON_BED.contains(entityId)) return Pose.SLEEPING;
        return ClientState.DOWNED.contains(entityId) || crawl ? Pose.SWIMMING : null;
    }

    public static void onMonitor(MonitorPacket p) {
        ClientState.monitor = p;
        ClientState.monitorTime = System.currentTimeMillis();
    }
}
