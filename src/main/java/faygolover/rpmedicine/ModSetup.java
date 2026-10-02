package faygolover.rpmedicine;

import faygolover.rpmedicine.capability.MedicalCapability;
import faygolover.rpmedicine.capability.MedicalData;
import faygolover.rpmedicine.command.MedCommand;
import faygolover.rpmedicine.config.ServerConfig;
import faygolover.rpmedicine.core.MedicalSettings;
import faygolover.rpmedicine.core.MedicalState;
import faygolover.rpmedicine.data.DamageRules;
import faygolover.rpmedicine.data.ItemRules;
import faygolover.rpmedicine.data.MobRules;
import faygolover.rpmedicine.integration.Integrations;
import faygolover.rpmedicine.network.EntityDownedPacket;
import faygolover.rpmedicine.network.Network;
import faygolover.rpmedicine.server.ActionManager;
import faygolover.rpmedicine.server.CarryService;
import faygolover.rpmedicine.server.ChatHandler;
import faygolover.rpmedicine.server.DamageHandler;
import faygolover.rpmedicine.server.ExamService;
import faygolover.rpmedicine.server.InteractionHandler;
import faygolover.rpmedicine.server.Medical;
import faygolover.rpmedicine.server.MobInjuries;
import faygolover.rpmedicine.server.PatientTicker;
import faygolover.rpmedicine.server.Profiler;
import faygolover.rpmedicine.server.SelfSync;
import faygolover.rpmedicine.server.StubService;
import faygolover.rpmedicine.server.TreatmentService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.network.PacketDistributor;

/** Подписка на события игровой шины Forge. Всё явно, без аннотаций — видно в одном месте. */
public final class ModSetup {
    private ModSetup() {}

    public static void commonSetup() {
        Integrations.init();
    }

    public static void registerForgeListeners(IEventBus bus) {
        // Урон и смерть
        bus.addListener(EventPriority.LOWEST, DamageHandler::onHurt);
        bus.addListener(DamageHandler::onHeal);
        bus.addListener(EventPriority.HIGH, DamageHandler::onDeath);
        bus.addListener(EventPriority.LOWEST, ModSetup::onDeathCleanup);
        // Тики
        bus.addListener(ModSetup::onPlayerTick);
        bus.addListener(ModSetup::onLivingTick);
        bus.addListener(ModSetup::onServerTick);
        // Вход, выход, смерть, слежение
        bus.addListener(ModSetup::onLogin);
        bus.addListener(ModSetup::onLogout);
        bus.addListener(ModSetup::onClone);
        bus.addListener(ModSetup::onRespawn);
        bus.addListener(ModSetup::onStartTracking);
        bus.addListener(CarryService::onMount);
        // Взаимодействия
        bus.addListener(EventPriority.HIGHEST, InteractionHandler::onEntityInteract);
        bus.addListener(EventPriority.HIGHEST, InteractionHandler::onEntityInteractSpecific);
        bus.addListener(EventPriority.HIGH, InteractionHandler::onRightClickItem);
        bus.addListener(InteractionHandler::onRightClickBlock);
        bus.addListener(InteractionHandler::onLeftClickBlock);
        bus.addListener(InteractionHandler::onBreakSpeed);
        bus.addListener(InteractionHandler::onAttack);
        bus.addListener(InteractionHandler::onToss);
        bus.addListener(InteractionHandler::onPickup);
        bus.addListener(InteractionHandler::onUseStart);
        bus.addListener(InteractionHandler::onJump);
        // Чат
        bus.addListener(EventPriority.HIGH, ChatHandler::onChat);
        bus.addListener(EventPriority.HIGH, ChatHandler::onCommand);
        // Датапак, команды, сервер
        bus.addListener(ModSetup::onReloadListeners);
        bus.addListener(ModSetup::onCommands);
        bus.addListener(ModSetup::onServerStarted);
        bus.addListener(ModSetup::onServerStopped);
    }

    private static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer sp)) return;
        PatientTicker.tickPlayer(sp);
        CarryService.tickCarrier(sp);
    }

    private static void onLivingTick(LivingEvent.LivingTickEvent e) {
        if (!(e.getEntity() instanceof Player) && !e.getEntity().level().isClientSide) MobInjuries.tick(e.getEntity());
    }

    private static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        long t0 = System.nanoTime();
        ActionManager.tick();
        TreatmentService.tickHolds(e.getServer());
        ExamService.tick(e.getServer());
        Integrations.endServerTick();
        Profiler.recordOther(System.nanoTime() - t0);
        Profiler.serverTick();
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        StubService.onLogin(sp);
        SelfSync.forceSync(sp);
        MedicalState m = Medical.state(sp);
        if (m != null && m.isDown()) faygolover.rpmedicine.server.DownedService.broadcastDowned(sp, true);
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        StubService.onLogout(sp);
        ExamService.onLogout(sp);
        faygolover.rpmedicine.integration.voice.VoiceState.remove(sp.getUUID());
    }

    /** Смерть: после возрождения здоровье чистое; что сохранить — по конфигу (п. 5.6 ТЗ). */
    private static void onClone(PlayerEvent.Clone e) {
        Player oldP = e.getOriginal();
        oldP.reviveCaps();
        MedicalData oldD = MedicalCapability.get(oldP);
        MedicalData newD = MedicalCapability.get(e.getEntity());
        if (oldD != null && newD != null) {
            if (!e.isWasDeath()) {
                newD.state.copyFrom(oldD.state);
            } else {
                MedicalSettings s = MedicalSettings.get();
                MedicalState o = oldD.state;
                MedicalState n = newD.state;
                n.weightKg = o.weightKg;
                n.heightCm = o.heightCm;
                n.reset(s);
                if (ServerConfig.keepAfterDeath("brain")) n.brain = Math.max(1, o.brain);
                if (ServerConfig.keepAfterDeath("blood")) n.bloodVolume = Math.max(n.normalBlood(s) * 0.6, o.bloodVolume);
                if (ServerConfig.keepAfterDeath("post_clinical")) n.postClinicalSeconds = o.postClinicalSeconds;
                for (int i = 0; i < n.parts.length; i++) {
                    if (ServerConfig.keepAfterDeath("fractures")) {
                        n.parts[i].fracture = o.parts[i].fracture;
                        n.parts[i].fractureHeal = o.parts[i].fractureHeal;
                    }
                    if (ServerConfig.keepAfterDeath("wounds")) {
                        for (var w : o.parts[i].wounds) n.parts[i].wounds.add(w.copy());
                    }
                }
            }
            newD.markDirty();
        }
        oldP.invalidateCaps();
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            sp.setForcedPose(null);
            SelfSync.forceSync(sp);
            faygolover.rpmedicine.server.DownedService.broadcastDowned(sp, false);
        }
    }

    private static void onDeathCleanup(LivingDeathEvent e) {
        if (e.isCanceled() || !(e.getEntity() instanceof ServerPlayer sp)) return;
        ActionManager.cancel(sp, null);
        CarryService.dropCarried(sp);
        CarryService.dropIfCarried(sp);
    }

    /** Новый наблюдатель увидел лежачего игрока — сообщить ему позу. */
    private static void onStartTracking(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof ServerPlayer target && e.getEntity() instanceof ServerPlayer viewer && Medical.isDown(target)) {
            Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), new EntityDownedPacket(target.getId(), true));
        }
    }

    private static void onReloadListeners(AddReloadListenerEvent e) {
        e.addListener(DamageRules.INSTANCE);
        e.addListener(ItemRules.ITEMS);
        e.addListener(ItemRules.ALIASES);
        e.addListener(MobRules.INSTANCE);
    }

    private static void onCommands(RegisterCommandsEvent e) {
        MedCommand.register(e.getDispatcher());
    }

    private static void onServerStarted(ServerStartedEvent e) {
        ServerConfig.apply();
        Integrations.warnConflicts();
    }

    private static void onServerStopped(ServerStoppedEvent e) {
        ActionManager.clear();
        CarryService.clear();
        ExamService.clear();
        Integrations.serverStopped();
        faygolover.rpmedicine.integration.voice.VoiceState.clear();
    }
}
