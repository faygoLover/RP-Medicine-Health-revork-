package faygolover.rpmedicine.gametest;

import faygolover.rpmedicine.RpMedicine;
import faygolover.rpmedicine.core.BodyPart;
import faygolover.rpmedicine.core.BodyPartState;
import faygolover.rpmedicine.core.MedicalSettings;
import faygolover.rpmedicine.core.MedicalState;
import faygolover.rpmedicine.core.Wound;
import faygolover.rpmedicine.core.WoundType;
import faygolover.rpmedicine.entity.BodyStubEntity;
import faygolover.rpmedicine.menu.SearchMenu;
import faygolover.rpmedicine.registry.ModItems;
import faygolover.rpmedicine.server.CarryService;
import faygolover.rpmedicine.server.FinishService;
import faygolover.rpmedicine.server.Medical;
import faygolover.rpmedicine.server.PatientTicker;
import faygolover.rpmedicine.server.SearchService;
import faygolover.rpmedicine.server.StubService;
import faygolover.rpmedicine.server.TreatmentService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Серверные сценарии из п. 13 ТЗ. Запуск: {@code ./gradlew runGameTestServer}. Игроки — фальшивые
 * (без клиента), поэтому физиология прокручивается вызовом тикера вручную.
 */
@GameTestHolder(RpMedicine.MODID)
@PrefixGameTestTemplate(false)
public final class MedicalGameTests {
    private MedicalGameTests() {}

    static final String T = "platform";

    private static int counter;

    /** Тестовый игрок с настоящим соединением на EmbeddedChannel (пакеты уходят в никуда). */
    /** Режим «без смерти» для теста: пакеты тестов идут по очереди, поэтому задаём явно в начале каждого. */
    static void noDeath(boolean on) {
        MedicalSettings.get().noDeathMode = on;
    }

    /**
     * Медик без ошибок: у фальшивого игрока уровень «Медицины» 0, шанс ошибки 25 % сделал бы сценарий
     * лечения случайным. Ошибки медика проверяют юнит-тесты.
     */
    static void noErrors() {
        MedicalSettings s = MedicalSettings.get();
        s.skillError = new double[s.skillError.length];
        s.underLevelErrorPerLevel = 0;
        // Мини-игры (второй этап) играет клиент; у фальшивого игрока его нет — только прогресс-бар.
        s.minigamesEnabled = false;
    }

    static ServerPlayer player(GameTestHelper h, double x, double z) {
        var server = h.getLevel().getServer();
        var profile = new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "rpm-test-" + (counter++));
        ServerPlayer p = new ServerPlayer(server, h.getLevel(), profile);
        net.minecraft.network.Connection conn = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(conn);
        server.getPlayerList().placeNewPlayer(conn, p);
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        // Ванильная защита после входа (3 с) мешает проверять урон — снимаем.
        net.minecraftforge.fml.util.ObfuscationReflectionHelper.setPrivateValue(ServerPlayer.class, p, 0, "f_8921_");
        Vec3 pos = h.absoluteVec(new Vec3(x, 1, z));
        p.moveTo(pos.x, pos.y, pos.z, 0, 0);
        p.yBodyRot = 0;
        p.yHeadRot = 0;
        return p;
    }

    static MedicalState state(ServerPlayer p) {
        return Medical.state(p);
    }

    /** Убрать фальшивого игрока: здоровым, чтобы не оставлять заглушку. */
    static void remove(GameTestHelper h, ServerPlayer... ps) {
        for (ServerPlayer p : ps) {
            if (state(p) != null) state(p).reset(MedicalSettings.get());
            if (!p.isRemoved()) h.getLevel().getServer().getPlayerList().remove(p);
        }
    }

    static void tickPatients(ServerPlayer... ps) {
        for (ServerPlayer p : ps) PatientTicker.tickPlayer(p);
    }

    static int command(GameTestHelper h, String cmd) {
        CommandSourceStack src = h.getLevel().getServer().createCommandSourceStack().withPermission(4).withSuppressedOutput();
        return h.getLevel().getServer().getCommands().performPrefixedCommand(src, cmd);
    }

    static double severity(MedicalState m, BodyPart part) {
        return m.part(part).totalSeverity();
    }

    // ------------------------------------------------------------------ урон

    @GameTest(template = T, timeoutTicks = 200)
    public static void damageKeepsVanillaHealthFull(GameTestHelper h) {
        noDeath(false);
        ServerPlayer p = player(h, 3, 3);
        p.hurt(p.damageSources().generic(), 4f);
        h.assertTrue(p.getHealth() == p.getMaxHealth(), "ванильное здоровье должно остаться полным");
        double total = 0;
        for (BodyPartState ps : state(p).parts) total += ps.totalSeverity();
        h.assertTrue(Math.abs(total - 20) < 0.01, "4 урона = рана тяжестью 20, было " + total);
        remove(h, p);
        h.succeed();
    }

    /** Пуля (стрела) в ногу без брони: рана в ноге полной тяжести. */
    @GameTest(template = T, timeoutTicks = 200)
    public static void arrowToLegWithoutArmor(GameTestHelper h) {
        noDeath(false);
        ServerPlayer p = player(h, 3, 3);
        Arrow arrow = new Arrow(EntityType.ARROW, h.getLevel());
        arrow.setPos(p.getX() + 0.1, p.getY() + 0.5, p.getZ() + 2);
        arrow.setDeltaMovement(0, 0, -1.5);
        p.hurt(p.damageSources().arrow(arrow, null), 5f);
        MedicalState m = state(p);
        boolean leg = severity(m, BodyPart.LEFT_LEG) + severity(m, BodyPart.RIGHT_LEG) > 0;
        h.assertTrue(leg, "стрела на высоте 0,5 должна попасть в ногу");
        boolean stab = m.part(BodyPart.LEFT_LEG).wounds.stream().anyMatch(w -> w.type == WoundType.STAB)
                || m.part(BodyPart.RIGHT_LEG).wounds.stream().anyMatch(w -> w.type == WoundType.STAB);
        h.assertTrue(stab, "стрела даёт колотую рану");
        remove(h, p);
        h.succeed();
    }

    /** Броня защищает только свою часть: шлем не спасает ногу. */
    @GameTest(template = T, timeoutTicks = 200)
    public static void armorProtectsOnlyCoveredPart(GameTestHelper h) {
        noDeath(false);
        ServerPlayer p = player(h, 3, 3);
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        p.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        Arrow arrow = new Arrow(EntityType.ARROW, h.getLevel());
        arrow.setPos(p.getX() + 0.1, p.getY() + 0.5, p.getZ() + 2);
        arrow.setDeltaMovement(0, 0, -1.5);
        p.hurt(p.damageSources().arrow(arrow, null), 6f);
        MedicalState m = state(p);
        double legs = severity(m, BodyPart.LEFT_LEG) + severity(m, BodyPart.RIGHT_LEG);
        h.assertTrue(Math.abs(legs - 30) < 0.5, "нога не закрыта бронёй: полный урон (30), было " + legs);
        remove(h, p);
        h.succeed();
    }

    @GameTest(template = T, timeoutTicks = 200)
    public static void fallHitsFeetAndLegs(GameTestHelper h) {
        noDeath(false);
        ServerPlayer p = player(h, 3, 3);
        p.hurt(p.damageSources().fall(), 6f);
        MedicalState m = state(p);
        double lower = 0;
        double other = 0;
        for (BodyPartState ps : m.parts) {
            if (ps.part.isLowerLimb()) lower += ps.totalSeverity();
            else other += ps.totalSeverity();
        }
        h.assertTrue(lower > 0 && other == 0, "падение с небольшой высоты — только стопы и ноги");
        remove(h, p);
        h.succeed();
    }

    @GameTest(template = T, timeoutTicks = 200)
    public static void explosionHitsSeveralParts(GameTestHelper h) {
        noDeath(false);
        ServerPlayer p = player(h, 3, 3);
        p.hurt(p.damageSources().explosion(null, null), 12f);
        int parts = 0;
        for (BodyPartState ps : state(p).parts) if (!ps.wounds.isEmpty()) parts++;
        h.assertTrue(parts >= 2, "взрыв ранит несколько частей, было " + parts);
        remove(h, p);
        h.succeed();
    }

    // ------------------------------------------------------------------ лечение

    /** Артериальное кровотечение и жгут: быстрый способ сам выбирает ногу. */
    @GameTest(template = T, timeoutTicks = 480)
    public static void arterialBleedingAndTourniquet(GameTestHelper h) {
        noDeath(false);
        noErrors();
        ServerPlayer patient = player(h, 2, 3);
        ServerPlayer medic = player(h, 3, 3);
        MedicalState m = state(patient);
        BodyPartState foot = m.part(BodyPart.LEFT_FOOT);
        foot.wounds.add(new Wound(WoundType.CUT, 30));
        foot.arterial = true;
        medic.getInventory().selected = 0;
        medic.getInventory().setItem(0, new ItemStack(ModItems.TOURNIQUET.get()));
        h.assertTrue(TreatmentService.startWithItem(medic, patient, 0, null), "лечение должно начаться");
        h.succeedWhen(() -> {
            h.assertTrue(m.part(BodyPart.LEFT_LEG).hasTourniquet(), "жгут на левой ноге");
            h.assertTrue(m.totalExternalBleed(MedicalSettings.get()) == 0, "кровотечение перекрыто");
            h.assertTrue(medic.getInventory().getItem(0).isEmpty(), "жгут потрачен");
            remove(h, patient, medic);
        });
    }

    // ------------------------------------------------------------------ нокдаун

    /** Нокдаун от кровопотери: поза лёжа; подъём командой ГМа. */
    @GameTest(template = T, timeoutTicks = 280)
    public static void knockdownAndRevive(GameTestHelper h) {
        noDeath(false);
        ServerPlayer p = player(h, 3, 3);
        MedicalState m = state(p);
        MedicalSettings s = MedicalSettings.get();
        m.bloodVolume = m.normalBlood(s) * 0.55;
        m.pressure = 40;
        h.onEachTick(() -> tickPatients(p));
        h.runAfterDelay(60, () -> {
            h.assertTrue(m.down == MedicalState.Down.KNOCKDOWN, "должен быть нокдаун, было " + m.down + ", кровь " + (int) m.bloodVolume
                    + ", давление " + (int) m.pressure + ", сознание " + (int) m.consciousness + ", тик " + p.tickCount);
            h.assertTrue(p.getForcedPose() == Pose.SWIMMING, "лежачий — горизонтальная поза");
            h.assertTrue(command(h, "rpmedicine revive " + p.getStringUUID()) > 0, "команда revive");
        });
        h.runAfterDelay(80, () -> {
            h.assertTrue(m.down == MedicalState.Down.NONE, "после revive на ногах, было " + m.down);
            h.assertTrue(p.getForcedPose() == null, "поза обычная");
            remove(h, p);
            h.succeed();
        });
    }

    /** Режим «без смерти»: клиническая смерть, СЛР с адреналином. */
    @GameTest(template = T, timeoutTicks = 3080, batch = "nodeath")
    public static void clinicalDeathAndResuscitation(GameTestHelper h) {
        noDeath(true);
        noErrors();
        ServerPlayer p = player(h, 2, 3);
        ServerPlayer medic = player(h, 3, 3);
        MedicalState m = state(p);
        m.heart = MedicalState.Heart.ARREST;
        m.down = MedicalState.Down.KNOCKDOWN;
        m.brain = 1;
        h.onEachTick(() -> {
            tickPatients(p);
            if (m.down == MedicalState.Down.CLINICAL) {
                medic.getInventory().selected = 0;
                if (m.adrenalineInjectionSeconds <= 0 && !faygolover.rpmedicine.server.ActionManager.isBusy(medic)) {
                    // Как с панели: адреналин из слота 1 на грудь. Уровень 0 — укол может быть слабее, повторяем.
                    medic.getInventory().setItem(1, new ItemStack(ModItems.ADRENALINE.get()));
                    TreatmentService.startWithItem(medic, p, 1, BodyPart.CHEST);
                }
                TreatmentService.hold(medic, p, faygolover.rpmedicine.core.TreatmentAction.CPR, 0);
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(m.down != MedicalState.Down.CLINICAL && m.heart == MedicalState.Heart.NORMAL, "реанимация");
            h.assertTrue(!p.isDeadOrDying(), "жив");
            remove(h, p, medic);
        });
    }

    // ------------------------------------------------------------------ выход из игры

    /** Обморок: заглушка с инвентарём; вход — на месте тела, вещи вернулись. */
    @GameTest(template = T, timeoutTicks = 180)
    public static void logoutInFaintLeavesStub(GameTestHelper h) {
        noDeath(false);
        ServerPlayer p = player(h, 3, 3);
        p.getInventory().setItem(5, new ItemStack(Items.APPLE, 7));
        MedicalState m = state(p);
        m.down = MedicalState.Down.FAINT;
        m.painShock = true;
        StubService.onLogout(p);
        h.assertTrue(p.getInventory().getItem(5).isEmpty(), "инвентарь ушёл в заглушку");
        List<BodyStubEntity> stubs = h.getLevel().getEntitiesOfClass(BodyStubEntity.class, p.getBoundingBox().inflate(3));
        h.assertTrue(stubs.size() == 1, "одна заглушка, было " + stubs.size());
        BodyStubEntity stub = stubs.get(0);
        h.assertTrue(stub.state().down == MedicalState.Down.FAINT, "в заглушке тот же обморок");
        h.assertTrue(stub.inventory().getItem(5).getCount() == 7, "вещи в заглушке");
        // Пока игрок «офлайн», тело ранят.
        stub.hurt(stub.damageSources().generic(), 4f);
        h.assertTrue(stub.state().part(BodyPart.CHEST).totalSeverity() + total(stub.state()) > 0, "заглушку можно ранить");
        h.runAfterDelay(5, () -> {
            StubService.onLogin(p);
            h.assertTrue(p.getInventory().getItem(5).getCount() == 7, "вещи вернулись");
            h.assertTrue(total(state(p)) > 0, "раны заглушки перешли игроку");
            h.assertTrue(stub.isRemoved(), "заглушка исчезла");
            remove(h, p);
            h.succeed();
        });
    }

    static double total(MedicalState m) {
        double t = 0;
        for (BodyPartState ps : m.parts) t += ps.totalSeverity();
        return t;
    }

    /** Нокдаун без режима «без смерти»: выход — смерть. */
    @GameTest(template = T, timeoutTicks = 200)
    public static void logoutInKnockdownKills(GameTestHelper h) {
        noDeath(false);
        ServerPlayer p = player(h, 3, 3);
        MedicalState m = state(p);
        m.down = MedicalState.Down.KNOCKDOWN;
        m.heart = MedicalState.Heart.ARREST;
        StubService.onLogout(p);
        h.assertTrue(p.isDeadOrDying(), "выход в нокдауне без режима «без смерти» — смерть");
        h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    /** Нокдаун в режиме «без смерти»: выход — клиническая смерть и заглушка. */
    @GameTest(template = T, batch = "nodeath2", timeoutTicks = 200)
    public static void logoutInKnockdownNoDeathMode(GameTestHelper h) {
        noDeath(true);
        ServerPlayer p = player(h, 3, 3);
        MedicalState m = state(p);
        m.down = MedicalState.Down.KNOCKDOWN;
        StubService.onLogout(p);
        List<BodyStubEntity> stubs = h.getLevel().getEntitiesOfClass(BodyStubEntity.class, p.getBoundingBox().inflate(3));
        h.assertTrue(stubs.size() == 1 && stubs.get(0).state().down == MedicalState.Down.CLINICAL, "заглушка в клинической смерти");
        StubService.onLogin(p);
        h.assertTrue(state(p).down == MedicalState.Down.CLINICAL, "игрок вошёл в клинической смерти");
        remove(h, p);
        h.succeed();
    }

    // ------------------------------------------------------------------ переноска, обыск, добивание

    @GameTest(template = T, timeoutTicks = 480)
    public static void carrySearchFinish(GameTestHelper h) {
        noDeath(false);
        ServerPlayer victim = player(h, 2, 3);
        ServerPlayer other = player(h, 3, 3);
        MedicalState m = state(victim);
        m.down = MedicalState.Down.FAINT;
        m.painShock = true;
        victim.getInventory().setItem(0, new ItemStack(Items.GOLD_INGOT));
        h.assertTrue(CarryService.pickUp(other, victim), "поднять лежачего");
        h.assertTrue(victim.getVehicle() == other, "лежачий на плече");
        CarryService.dropCarried(other);
        h.assertTrue(victim.getVehicle() == null, "сброшен");
        SearchService.start(other, victim);
        h.runAfterDelay(220, () -> {
            h.assertTrue(other.containerMenu instanceof SearchMenu, "после 10 с открыт обыск");
            other.closeContainer();
            other.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            FinishService.start(other, victim.getId());
        });
        h.runAfterDelay(340, () -> {
            h.assertTrue(victim.isDeadOrDying(), "добит (режим «без смерти» выключен)");
            remove(h, other);
            h.getLevel().getServer().getPlayerList().remove(victim);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ нагрузка

    /** 30 раненых игроков: средняя нагрузка шагов физиологии на тик. */
    @GameTest(template = T, timeoutTicks = 480, batch = "load")
    public static void thirtyWoundedPlayersLoad(GameTestHelper h) {
        noDeath(false);
        List<ServerPlayer> ps = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            ServerPlayer p = player(h, 1 + (i % 5), 1 + (i / 5) % 5);
            MedicalState m = state(p);
            m.part(BodyPart.values()[i % 9]).wounds.add(new Wound(WoundType.CUT, 20 + i));
            if (i % 3 == 0) m.part(BodyPart.CHEST).internalBleed = 30;
            ps.add(p);
        }
        final long[] nanos = {0};
        final int[] ticks = {0};
        h.onEachTick(() -> {
            long t0 = System.nanoTime();
            tickPatients(ps.toArray(new ServerPlayer[0]));
            nanos[0] += System.nanoTime() - t0;
            ticks[0]++;
        });
        h.runAfterDelay(300, () -> {
            double micros = nanos[0] / 1000.0 / ticks[0];
            RpMedicine.LOGGER.info("RP Medicine GameTest: 30 раненых игроков — {} мкс на тик", String.format("%.1f", micros));
            h.assertTrue(micros < 2000, "нагрузка больше 2 мс на тик: " + micros);
            remove(h, ps.toArray(new ServerPlayer[0]));
            h.succeed();
        });
    }
}
