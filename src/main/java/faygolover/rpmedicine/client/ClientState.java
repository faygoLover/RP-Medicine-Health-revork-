package faygolover.rpmedicine.client;

import faygolover.rpmedicine.core.Examination;
import faygolover.rpmedicine.network.ExamResultPacket;
import faygolover.rpmedicine.network.MonitorPacket;
import faygolover.rpmedicine.network.SelfView;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Что клиент знает о медицине: только присланное сервером. */
public final class ClientState {
    private ClientState() {}

    public static SelfView self = new SelfView();
    /** Время прихода последнего пакета (для плавности эффектов). */
    public static long selfTime;

    @Nullable
    public static ExamResultPacket exam;

    public static int hoverTarget = -1;
    public static List<Examination.Line> hoverLines = List.of();

    // Прогресс-бар
    public static String progressLabel = "";
    public static int progressTotal;
    public static int progressDone;
    public static long progressStart;

    /** Лежачие игроки рядом (id сущностей) — для позы на этом клиенте. */
    public static final Set<Integer> DOWNED = new HashSet<>();

    /** Последний ответ монитора, на который смотрит игрок. */
    @Nullable
    public static MonitorPacket monitor;
    public static long monitorTime;

    public static void reset() {
        self = new SelfView();
        exam = null;
        hoverTarget = -1;
        hoverLines = List.of();
        progressTotal = 0;
        DOWNED.clear();
        faygolover.rpmedicine.hospital.BedPose.CLIENT_ON_BED.clear();
        monitor = null;
    }

    /** Показывать ли монитор: ответ свежий (запрос повторяется, пока игрок смотрит). */
    public static boolean monitorActive() {
        return monitor != null && System.currentTimeMillis() - monitorTime < 1500;
    }

    public static boolean progressActive() {
        return progressTotal != 0;
    }
}
