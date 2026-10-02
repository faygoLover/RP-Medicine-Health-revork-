package faygolover.rpmedicine.server;

import faygolover.rpmedicine.command.MedCommand;
import faygolover.rpmedicine.core.BodyPartState;
import faygolover.rpmedicine.core.MedicalSettings;
import faygolover.rpmedicine.core.MedicalState;
import faygolover.rpmedicine.entity.BodyStubEntity;
import faygolover.rpmedicine.network.GmActionPacket;
import faygolover.rpmedicine.network.GmPanelPacket;
import faygolover.rpmedicine.network.GmReportPacket;
import faygolover.rpmedicine.network.Network;
import faygolover.rpmedicine.stats.History;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Панель ГМа (ТЗ второго этапа, п. 11.3): список игроков онлайн и тел с цветом состояния; по клику —
 * полный осмотр цифрами и кнопки «Вылечить», «Поднять», «Убить», «Телепорт», «История».
 */
public final class GmPanelService {
    private GmPanelService() {}

    /** Цвет состояния 0–4: здоров, ранен, кровит или перелом, лежит, клиническая смерть. */
    static int stateColor(MedicalState m) {
        if (m.down == MedicalState.Down.CLINICAL) return 4;
        if (m.isDown()) return 3;
        MedicalSettings s = MedicalSettings.get();
        boolean serious = m.totalExternalBleed(s) + m.totalInternalBleed() > 5 || m.sepsis >= 30 || m.bloodFraction(s) < 0.75;
        for (BodyPartState ps : m.parts) if (ps.hasFracture()) serious = true;
        if (serious) return 2;
        return m.isQuiet(s) ? 0 : 1;
    }

    public static void open(ServerPlayer gm) {
        if (!gm.hasPermissions(2)) return;
        MinecraftServer server = gm.server;
        List<GmPanelPacket.Row> rows = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            MedicalState m = Medical.state(p);
            rows.add(new GmPanelPacket.Row(p.getUUID(), p.getGameProfile().getName(), false, m != null ? stateColor(m) : 0,
                    p.level().dimension().location().toString(), p.getBlockX(), p.getBlockY(), p.getBlockZ()));
        }
        StubRegistry reg = StubRegistry.get(server);
        for (var e : reg.all().entrySet()) {
            StubRegistry.Record r = e.getValue();
            BodyStubEntity stub = stubEntity(server, r);
            MedicalState m = stub != null ? stub.state() : null;
            String name = stub != null ? stub.ownerName() : e.getKey().toString().substring(0, 8);
            rows.add(new GmPanelPacket.Row(e.getKey(), name, true, m != null ? stateColor(m) : (r.dead ? 4 : 3),
                    r.dimension.location().toString(), (int) r.pos.x, (int) r.pos.y, (int) r.pos.z));
        }
        Network.send(gm, new GmPanelPacket(rows));
    }

    @Nullable
    private static BodyStubEntity stubEntity(MinecraftServer server, StubRegistry.Record r) {
        ServerLevel level = server.getLevel(r.dimension);
        if (level == null) return null;
        Entity e = level.getEntity(r.entityId);
        return e instanceof BodyStubEntity b && b.isAlive() ? b : null;
    }

    /** Пациент по UUID персонажа: игрок онлайн или его тело (если чанк загружен). */
    @Nullable
    static LivingEntity target(MinecraftServer server, UUID uuid) {
        ServerPlayer p = server.getPlayerList().getPlayer(uuid);
        if (p != null) return p;
        StubRegistry.Record r = StubRegistry.get(server).get(uuid);
        return r != null ? stubEntity(server, r) : null;
    }

    public static void onAction(ServerPlayer gm, GmActionPacket p) {
        if (!gm.hasPermissions(2)) return;
        MinecraftServer server = gm.server;
        LivingEntity t = target(server, p.uuid());
        switch (p.action()) {
            case INSPECT -> {
                List<Component> lines = t != null ? MedicalReports.full(t) : List.of(Component.translatable("rpmedicine.gm.not_loaded"));
                Network.send(gm, new GmReportPacket(p.uuid(), lines));
            }
            case HISTORY -> {
                History.load(server, p.uuid());
                String name = t instanceof BodyStubEntity stub ? stub.ownerName() : t != null ? t.getName().getString() : p.uuid().toString();
                Network.send(gm, new GmReportPacket(p.uuid(), History.graph(p.uuid(), name)));
            }
            case TELEPORT -> {
                if (t != null) gm.teleportTo((ServerLevel) t.level(), t.getX(), t.getY(), t.getZ(), gm.getYRot(), gm.getXRot());
                else {
                    StubRegistry.Record r = StubRegistry.get(server).get(p.uuid());
                    ServerLevel level = r != null ? server.getLevel(r.dimension) : null;
                    if (level != null) gm.teleportTo(level, r.pos.x, r.pos.y, r.pos.z, gm.getYRot(), gm.getXRot());
                }
            }
            case HEAL -> {
                if (t != null) MedCommand.healAll(t);
            }
            case REVIVE -> {
                if (t != null) MedCommand.revive(t);
            }
            case KILL -> {
                if (t != null) MedCommand.gmKill(t, gm);
            }
        }
        if (p.action() != GmActionPacket.Action.INSPECT && p.action() != GmActionPacket.Action.HISTORY) {
            open(gm);
            if (t != null && p.action() != GmActionPacket.Action.KILL) Network.send(gm, new GmReportPacket(p.uuid(), MedicalReports.full(t)));
        }
    }

    /** Для отладки и тестов: цвет состояния сущности. */
    public static int colorOf(LivingEntity e) {
        MedicalState m = Medical.state(e);
        return m != null ? stateColor(m) : 0;
    }
}
