package faygolover.rpmedicine.client;

import faygolover.rpmedicine.RpMedicine;
import faygolover.rpmedicine.client.screen.ClinicalDeathScreen;
import faygolover.rpmedicine.client.screen.DownedScreen;
import faygolover.rpmedicine.client.screen.MedicalPanelScreen;
import faygolover.rpmedicine.config.ClientConfig;
import faygolover.rpmedicine.entity.BodyStubEntity;
import faygolover.rpmedicine.menu.MedicalContainerMenu;
import faygolover.rpmedicine.network.DownedActionPacket;
import faygolover.rpmedicine.hospital.BedPose;
import faygolover.rpmedicine.hospital.HospitalBlocks;
import faygolover.rpmedicine.hospital.HospitalFunction;
import faygolover.rpmedicine.network.HoverRequestPacket;
import faygolover.rpmedicine.network.MonitorRequestPacket;
import faygolover.rpmedicine.network.Network;
import faygolover.rpmedicine.network.RequestExamPacket;
import faygolover.rpmedicine.network.SelfView;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/** Клиентские события: клавиши, ввод лежачего, наведение, эффекты, чат клинической смерти. */
@Mod.EventBusSubscriber(modid = RpMedicine.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    private static int lastHoverTarget = -1;
    private static int finishTarget = -1;
    private static int tick;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        tick++;
        SelfView v = ClientState.self;

        while (ClientSetup.PANEL.consumeClick()) openPanel(mc, v);
        handleFinishKey(mc, v);
        if (tick % 5 == 0) updateHover(mc, v);

        if ((v.noSprint || v.isDown()) && p.isSprinting()) p.setSprinting(false);
        // Поза: свой лежачий/ползущий, на койке и лежачие рядом. Лежачего рисует DownedPose — поза стоя.
        Pose want = ClientHandlers.poseFor(p.getId(), v.crawl);
        if (p.getForcedPose() != want) {
            p.setForcedPose(want);
            p.refreshDimensions();
        }
        for (Player other : mc.level.players()) {
            if (other == p) continue;
            Pose op = ClientHandlers.poseFor(other.getId(), false);
            if (other.getForcedPose() != op) {
                other.setForcedPose(op);
                other.refreshDimensions();
            }
        }
        faygolover.rpmedicine.client.render.DownedPose.tick(mc);
        if (tick % 10 == 0) updateMonitor(mc, v);
        if (mc.screen == null) AimSway.tick(p, v);
        ClientSounds.tick(mc, v);
        PostEffects.tick();
    }

    private static void openPanel(Minecraft mc, SelfView v) {
        if (v.down == 3) return;
        if (v.isDown()) {
            mc.setScreen(new DownedScreen());
            return;
        }
        Entity target = crosshairPatient(mc, 4.5);
        int id = target != null ? target.getId() : -1;
        mc.setScreen(new MedicalPanelScreen(id));
        Network.sendToServer(new RequestExamPacket(id, true));
    }

    private static void handleFinishKey(Minecraft mc, SelfView v) {
        boolean held = ClientSetup.FINISH.isDown() && mc.screen == null && !v.isDown();
        if (held) {
            Entity t = crosshairPatient(mc, 4.0);
            int id = t != null && isDowned(t) ? t.getId() : -1;
            if (id != finishTarget) {
                if (finishTarget >= 0) Network.sendToServer(new DownedActionPacket(DownedActionPacket.Kind.FINISH_STOP, -1));
                if (id >= 0) Network.sendToServer(new DownedActionPacket(DownedActionPacket.Kind.FINISH_START, id));
                finishTarget = id;
            }
        } else if (finishTarget >= 0) {
            Network.sendToServer(new DownedActionPacket(DownedActionPacket.Kind.FINISH_STOP, -1));
            finishTarget = -1;
        }
    }

    private static boolean isDowned(Entity e) {
        return e instanceof BodyStubEntity || ClientState.DOWNED.contains(e.getId());
    }

    private static void updateHover(Minecraft mc, SelfView v) {
        int id = -1;
        LocalPlayer p = mc.player;
        boolean medical = p.getMainHandItem().is(MedicalContainerMenu.MEDICAL_ITEMS) || p.getOffhandItem().is(MedicalContainerMenu.MEDICAL_ITEMS);
        if (medical && !v.isDown()) {
            Entity t = crosshairPatient(mc, 8.0);
            if (t != null) id = t.getId();
        }
        if (id != lastHoverTarget) {
            lastHoverTarget = id;
            Network.sendToServer(new HoverRequestPacket(id));
            if (id < 0) {
                ClientState.hoverTarget = -1;
                ClientState.hoverLines = java.util.List.of();
            }
        }
    }

    /** Смотрит на монитор показателей — запрашивать цифры, пока смотрит (п. 2.3 ТЗ второго этапа). */
    private static void updateMonitor(Minecraft mc, SelfView v) {
        if (v.isDown() || !(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult bhr)
                || bhr.getType() != HitResult.Type.BLOCK) return;
        var pos = bhr.getBlockPos();
        var looked = mc.level.getBlockState(pos);
        // Операционный стол — сам себе монитор (третий этап).
        if (!HospitalBlocks.is(looked, HospitalFunction.MONITOR) && !HospitalBlocks.is(looked, HospitalFunction.OPERATING_TABLE)) return;
        if (mc.player.getEyePosition().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > 8) return;
        Network.sendToServer(new MonitorRequestPacket(pos));
    }

    /** Игрок или заглушка под прицелом в пределах дистанции. */
    @Nullable
    public static Entity crosshairPatient(Minecraft mc, double maxDist) {
        HitResult hr = mc.hitResult;
        if (hr instanceof EntityHitResult ehr) {
            Entity e = ehr.getEntity();
            if ((e instanceof Player || e instanceof BodyStubEntity) && e.distanceTo(mc.player) <= maxDist) return e;
        }
        // Лежачий низко и часто не попадает в луч прицела: ищем вдоль взгляда сами.
        LocalPlayer p = mc.player;
        var eye = p.getEyePosition();
        var end = eye.add(p.getLookAngle().scale(maxDist));
        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : mc.level.getEntities(p, p.getBoundingBox().inflate(maxDist), x -> x instanceof Player || x instanceof BodyStubEntity)) {
            var hit = e.getBoundingBox().inflate(0.3).clip(eye, end);
            if (hit.isPresent()) {
                double d = hit.get().distanceToSqr(eye);
                if (d < bestDist) {
                    bestDist = d;
                    best = e;
                }
            }
        }
        return best;
    }

    /** Каждый кадр: плавная сила постэффекта. */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase == TickEvent.Phase.START) PostEffects.frame();
    }

    @SubscribeEvent
    public static void onInput(MovementInputUpdateEvent e) {
        SelfView v = ClientState.self;
        var in = e.getInput();
        boolean onBed = Minecraft.getInstance().player != null && BedPose.CLIENT_ON_BED.contains(Minecraft.getInstance().player.getId());
        if (v.isDown() || onBed) {
            in.forwardImpulse = 0;
            in.leftImpulse = 0;
            in.up = in.down = in.left = in.right = false;
            in.jumping = false;
            // На койке в сознании встать — присесть.
            if (v.isDown()) in.shiftKeyDown = false;
        } else if (v.noJump) {
            in.jumping = false;
        }
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre e) {
        if (e.getOverlay() == VanillaGuiOverlay.PLAYER_HEALTH.type() && ClientConfig.HIDE_VANILLA_HEALTH.get()) e.setCanceled(true);
        if (e.getOverlay() == VanillaGuiOverlay.CHAT_PANEL.type() && ClientState.self.down == 3) e.setCanceled(true);
    }

    /** Игроки на койке, для которых в {@link #onRenderPlayerPre} сдвинута матрица. */
    private static final java.util.Set<Integer> BED_SHIFTED = new java.util.HashSet<>();

    /**
     * На койке: поза сна ванили рисует тело от ног вдоль поворота тела. Поворот фиксируем (иначе тело
     * крутится вслед за взглядом), а тело сдвигаем так, чтобы его середина была над койкой.
     */
    @SubscribeEvent
    public static void onRenderPlayerPre(net.minecraftforge.client.event.RenderPlayerEvent.Pre e) {
        Player p = e.getEntity();
        Float yaw = BedPose.CLIENT_BED_YAW.get(p.getId());
        if (yaw == null || !p.hasPose(Pose.SLEEPING)) return;
        p.yBodyRot = yaw;
        p.yBodyRotO = yaw;
        double a = Math.toRadians(yaw);
        e.getPoseStack().pushPose();
        e.getPoseStack().translate(0.9 * Math.cos(a), 0, -0.9 * Math.sin(a));
        BED_SHIFTED.add(p.getId());
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(net.minecraftforge.client.event.RenderPlayerEvent.Post e) {
        if (BED_SHIFTED.remove(e.getEntity().getId())) e.getPoseStack().popPose();
    }

    /** Лежачий ничего не держит в руках — руку не рисуем. */
    @SubscribeEvent
    public static void onRenderHand(net.minecraftforge.client.event.RenderHandEvent e) {
        if (ClientState.self.isDown()) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles e) {
        Minecraft mc = Minecraft.getInstance();
        if (ClientState.self.isDown() && ClientConfig.LOOK_UP_WHEN_DOWNED.get() && mc.options.getCameraType() == CameraType.FIRST_PERSON) {
            e.setPitch(-80f);
        }
    }

    @SubscribeEvent
    public static void onScreenOpen(ScreenEvent.Opening e) {
        SelfView v = ClientState.self;
        if (Minecraft.getInstance().player == null) return;
        if (v.down == 3 && !(e.getNewScreen() instanceof ClinicalDeathScreen) && e.getNewScreen() != null
                && !(e.getNewScreen() instanceof net.minecraft.client.gui.screens.PauseScreen)
                && !(e.getNewScreen() instanceof net.minecraft.client.gui.screens.ConfirmScreen)) {
            e.setCanceled(true);
            return;
        }
        if (v.isDown() && (e.getNewScreen() instanceof AbstractContainerScreen<?> || (v.down == 3 && e.getNewScreen() instanceof ChatScreen))) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onChatReceived(ClientChatReceivedEvent e) {
        // Сообщения над панелью запоминаем: окна мода закрывают их и показывают у себя.
        if (e instanceof ClientChatReceivedEvent.System sys && sys.isOverlay()) {
            ClientState.lastOverlay = e.getMessage();
            ClientState.lastOverlayTime = System.currentTimeMillis();
        }
        // В клинической смерти чат не виден (п. 5.4 ТЗ).
        if (ClientState.self.down == 3) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onSound(PlaySoundEvent e) {
        SoundInstance s = e.getSound();
        if (s == null || !ClientConfig.MUFFLED_SOUND.get() || s instanceof TickableSoundInstance) return;
        if (s.getLocation().getNamespace().equals(RpMedicine.MODID)) return;
        SelfView v = ClientState.self;
        float muffle = v.down == 3 ? 0.15f : (v.isDown() || v.gray >= 4) ? 0.45f : 1f;
        // Глухота после взрыва (второй этап, п. 13): почти ничего не слышно, к концу — лучше.
        if (v.deaf > 0) muffle = Math.min(muffle, 0.12f + 0.5f * Math.max(0, 1 - v.deaf / 15f));
        if (muffle >= 1f || !(s instanceof SimpleSoundInstance)) return;
        s.resolve(Minecraft.getInstance().getSoundManager());
        e.setSound(new SimpleSoundInstance(s.getLocation(), s.getSource(), s.getVolume() * muffle, s.getPitch() * 0.9f,
                net.minecraft.util.RandomSource.create(), s.isLooping(), s.getDelay(), s.getAttenuation(), s.getX(), s.getY(), s.getZ(), s.isRelative()));
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        ClientState.reset();
        PostEffects.reset();
        faygolover.rpmedicine.client.render.DownedPose.reset();
        lastHoverTarget = -1;
        finishTarget = -1;
    }

    /** Цель панели для экрана (живой ли ещё). */
    @Nullable
    public static LivingEntity entity(int id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        if (id < 0) return mc.player;
        return mc.level.getEntity(id) instanceof LivingEntity le ? le : null;
    }
}
