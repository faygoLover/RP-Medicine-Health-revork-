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
    public static net.minecraft.world.item.ItemStack progressIcon = net.minecraft.world.item.ItemStack.EMPTY;
    public static net.minecraft.network.chat.Component progressSubtitle = net.minecraft.network.chat.Component.empty();
    // Лечение, которое применяют к самому игроку (видно и лежачему, и под наркозом).
    public static String incomingLabel = "";
    public static int incomingTotal;
    public static long incomingStart;
    public static net.minecraft.world.item.ItemStack incomingIcon = net.minecraft.world.item.ItemStack.EMPTY;
    public static net.minecraft.network.chat.Component incomingSubtitle = net.minecraft.network.chat.Component.empty();
    /** Последнее сообщение над панелью быстрого доступа: дублируется в окнах мода, которые его закрывают. */
    public static net.minecraft.network.chat.Component lastOverlay;
    public static long lastOverlayTime;

    /** Лежачие игроки рядом (id сущностей) — для позы на этом клиенте. */
    public static final Set<Integer> DOWNED = new HashSet<>();
    /** Каких конечностей не видно у игроков (третий этап, п. 12): id сущности → биты частей. */
    public static final java.util.Map<Integer, Integer> MISSING_LIMBS = new java.util.HashMap<>();
    /** Состав еды для подсказок (с сервера). */
    public static final java.util.Map<net.minecraft.resources.ResourceLocation, faygolover.rpmedicine.core.Nutrition.Food> FOODS = new java.util.HashMap<>();

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
        incomingTotal = 0;
        lastOverlay = null;
        DOWNED.clear();
        MISSING_LIMBS.clear();
        faygolover.rpmedicine.hospital.BedPose.CLIENT_ON_BED.clear();
        faygolover.rpmedicine.hospital.BedPose.CLIENT_BED_YAW.clear();
        monitor = null;
    }

    /** Показывать ли монитор: ответ свежий (запрос повторяется, пока игрок смотрит). */
    public static boolean monitorActive() {
        return monitor != null && System.currentTimeMillis() - monitorTime < 1500;
    }

    public static boolean progressActive() {
        return progressTotal != 0;
    }

    public static boolean incomingActive() {
        return incomingTotal > 0 && (System.currentTimeMillis() - incomingStart) / 50f < incomingTotal + 20;
    }

    /** Свежее сообщение над панелью (до 4 секунд). */
    @Nullable
    public static net.minecraft.network.chat.Component recentOverlay() {
        return lastOverlay != null && System.currentTimeMillis() - lastOverlayTime < 4000 ? lastOverlay : null;
    }
}
