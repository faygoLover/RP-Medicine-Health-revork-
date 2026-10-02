package faygolover.rpmedicine.server;

import faygolover.rpmedicine.RpMedicine;
import faygolover.rpmedicine.capability.MedicalData;
import faygolover.rpmedicine.config.ServerConfig;
import faygolover.rpmedicine.core.GameplayEffects;
import faygolover.rpmedicine.core.MedicalSettings;
import faygolover.rpmedicine.core.MedicalState;
import faygolover.rpmedicine.core.Physiology;
import faygolover.rpmedicine.core.StepResult;
import faygolover.rpmedicine.entity.BodyStubEntity;
import faygolover.rpmedicine.network.DownedActionPacket;
import faygolover.rpmedicine.network.EntityDownedPacket;
import faygolover.rpmedicine.network.Network;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Лежачий, клиническая смерть, смерть (п. 5 ТЗ): переходы состояний, поза, кнопки лежачего,
 * настоящая смерть через собственные типы урона (их пропускает перехват урона).
 */
public final class DownedService {
    private DownedService() {}

    public static final ResourceKey<DamageType> BRAIN_DEATH = key("brain_death");
    public static final ResourceKey<DamageType> SURRENDER = key("surrender");
    public static final ResourceKey<DamageType> GM_KILL = key("gm_kill");
    public static final ResourceKey<DamageType> FINISHED = key("finished");

    private static ResourceKey<DamageType> key(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(RpMedicine.MODID, name));
    }

    public static boolean isOwnDeathType(DamageSource src) {
        return src.is(BRAIN_DEATH) || src.is(SURRENDER) || src.is(GM_KILL) || src.is(FINISHED);
    }

    // ------------------------------------------------------------------ события шага

    public static void onStep(ServerPlayer sp, MedicalState m, StepResult r) {
        if (r.events.isEmpty()) return;
        if (r.has(StepResult.Event.DIED)) {
            kill(sp, BRAIN_DEATH, null);
            return;
        }
        // Тотем бессмертия в руке поднимает из нокдауна и клинической смерти (второй этап, п. 13).
        if ((r.has(StepResult.Event.CLINICAL_DEATH) || (r.has(StepResult.Event.WENT_DOWN) && m.down == MedicalState.Down.KNOCKDOWN))
                && VanillaEffects.tryTotem(sp, m)) return;
        if (r.has(StepResult.Event.WENT_DOWN)) onWentDown(sp, m);
        if (r.has(StepResult.Event.CLINICAL_DEATH)) {
            onWentDown(sp, m);
            MedcardHooks.clinicalDeath(sp);
            faygolover.rpmedicine.stats.StatsService.event(sp, "clinical", "");
            sp.displayClientMessage(Component.translatable("rpmedicine.msg.clinical_death").withStyle(ChatFormatting.DARK_RED), true);
        }
        if (r.has(StepResult.Event.RESCUED_FROM_CLINICAL)) {
            broadcastDowned(sp, true);
            faygolover.rpmedicine.stats.StatsService.event(sp, "rescue", "");
        }
        if (r.has(StepResult.Event.WOKE_UP)) onWokeUp(sp);
        if (r.has(StepResult.Event.DRESSING_REOPENED))
            sp.displayClientMessage(Component.translatable("rpmedicine.msg.dressing_reopened").withStyle(ChatFormatting.RED), true);
        if (r.has(StepResult.Event.FRACTURE_WORSENED))
            sp.displayClientMessage(Component.translatable("rpmedicine.msg.fracture_worsened").withStyle(ChatFormatting.RED), true);
    }

    /** Упал: прервать всё, что делал, сообщить окружающим (для позы). */
    public static void onWentDown(ServerPlayer sp, MedicalState m) {
        sp.stopUsingItem();
        if (sp.containerMenu != sp.inventoryMenu) sp.closeContainer();
        ActionManager.cancel(sp, "rpmedicine.action.interrupted");
        CarryService.dropCarried(sp);
        if (sp.isPassenger() && !CarryService.isCarried(sp)) sp.stopRiding();
        sp.setSprinting(false);
        broadcastDowned(sp, true);
        Medical.changed(sp);
    }

    public static void onWokeUp(ServerPlayer sp) {
        CarryService.dropIfCarried(sp);
        broadcastDowned(sp, false);
        sp.displayClientMessage(Component.translatable("rpmedicine.msg.woke_up"), true);
        Medical.changed(sp);
    }

    /** Сообщить всем, кто видит игрока (и ему самому), что он лежит или встал. */
    public static void broadcastDowned(ServerPlayer sp, boolean down) {
        Network.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> sp), new EntityDownedPacket(sp.getId(), down, onBed(sp), bedQuarter(sp)));
    }

    /** Сообщить позу по текущему состоянию (лежит, на койке). */
    public static void broadcastPose(ServerPlayer sp) {
        broadcastDowned(sp, Medical.isDown(sp));
    }

    public static byte bedQuarter(ServerPlayer sp) {
        MedicalData d = Medical.data(sp);
        return d != null ? d.bedQuarter : 0;
    }

    private static boolean onBed(ServerPlayer sp) {
        MedicalData d = Medical.data(sp);
        return d != null && d.bedPos != null;
    }

    /** Поза: на койке — на спине (поза сна), лежачий и ползущий — горизонтально (поза плавания), иначе обычная. */
    public static void updatePose(ServerPlayer sp, MedicalState m, GameplayEffects.Mods mods) {
        Pose want = onBed(sp) ? Pose.SLEEPING : (m.isDown() || mods.crawl) ? Pose.SWIMMING : null;
        if (sp.getForcedPose() != want) sp.setForcedPose(want);
    }

    // ------------------------------------------------------------------ смерть

    /**
     * Настоящая смерть игрока: обычная смерть, чтобы работали Corpse и другие моды (п. 5.6 ТЗ).
     */
    public static void kill(ServerPlayer sp, ResourceKey<DamageType> type, @Nullable Entity killer) {
        if (sp.isDeadOrDying() || sp.isRemoved()) return;
        ActionManager.cancel(sp, null);
        CarryService.dropCarried(sp);
        CarryService.dropIfCarried(sp);
        DamageSource src = source(sp.serverLevel(), type, killer);
        faygolover.rpmedicine.stats.StatsService.event(sp, "death", type.location().getPath());
        sp.invulnerableTime = 0;
        sp.hurt(src, Float.MAX_VALUE);
        if (!sp.isDeadOrDying()) {
            // Урон отменён (PvP выключен, другой мод): добиваем напрямую.
            sp.setHealth(0);
            sp.die(src);
        }
    }

    public static DamageSource source(ServerLevel level, ResourceKey<DamageType> type, @Nullable Entity killer) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type), killer, killer);
    }

    /**
     * Летальный исход для лежачего: в режиме «без смерти» — клиническая смерть, иначе смерть.
     * Используется при добивании и мгновенно смертельном попадании.
     */
    public static void lethal(LivingEntity target, ResourceKey<DamageType> type, @Nullable Entity killer) {
        MedicalState m = Medical.state(target);
        if (m == null) return;
        MedicalSettings s = MedicalSettings.get();
        if (target instanceof ServerPlayer tp && m.down != MedicalState.Down.CLINICAL && VanillaEffects.tryTotem(tp, m)) return;
        if (s.noDeathMode) {
            if (m.down == MedicalState.Down.CLINICAL) return;
            Physiology.enterClinical(m);
            Medical.changed(target);
            if (target instanceof ServerPlayer sp) {
                onWentDown(sp, m);
                sp.displayClientMessage(Component.translatable("rpmedicine.msg.clinical_death").withStyle(ChatFormatting.DARK_RED), true);
            }
        } else if (target instanceof ServerPlayer sp) {
            kill(sp, type, killer);
        } else if (target instanceof BodyStubEntity stub) {
            StubService.killStub(stub, type, killer);
        }
    }

    // ------------------------------------------------------------------ кнопки лежачего

    public static void handleAction(ServerPlayer sp, DownedActionPacket p) {
        MedicalState m = Medical.state(sp);
        if (m == null) return;
        switch (p.kind()) {
            case SURRENDER -> {
                if (m.isDown()) kill(sp, SURRENDER, null);
            }
            case CALL_ADMIN -> {
                if (m.down == MedicalState.Down.CLINICAL) callAdmin(sp);
            }
            case FINISH_START -> FinishService.start(sp, p.targetId());
            case FINISH_STOP -> FinishService.stop(sp);
        }
    }

    private static void callAdmin(ServerPlayer sp) {
        MedicalData d = Medical.data(sp);
        if (d == null) return;
        long now = sp.serverLevel().getGameTime();
        long cooldown = (long) (ServerConfig.ADMIN_CALL_COOLDOWN_SECONDS.get() * 20);
        if (now - d.lastAdminCall < cooldown) {
            long left = (cooldown - (now - d.lastAdminCall)) / 20;
            sp.displayClientMessage(Component.translatable("rpmedicine.msg.admin_cooldown", left), true);
            return;
        }
        d.lastAdminCall = now;
        String tp = String.format("/execute in %s run tp @s %.1f %.1f %.1f", sp.level().dimension().location(), sp.getX(), sp.getY(), sp.getZ());
        Component msg = Component.translatable("rpmedicine.msg.admin_call", sp.getDisplayName(),
                        sp.level().dimension().location().toString(), (int) sp.getX(), (int) sp.getY(), (int) sp.getZ())
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, tp)));
        int sent = 0;
        for (ServerPlayer op : sp.server.getPlayerList().getPlayers()) {
            if (op.hasPermissions(2)) {
                op.sendSystemMessage(msg);
                sent++;
            }
        }
        RpMedicine.LOGGER.info("RP Medicine: {} зовёт администратора ({} операторов в сети)", sp.getGameProfile().getName(), sent);
        sp.displayClientMessage(Component.translatable("rpmedicine.msg.admin_called"), true);
    }
}
