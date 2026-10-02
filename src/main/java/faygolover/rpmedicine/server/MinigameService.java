package faygolover.rpmedicine.server;

import faygolover.rpmedicine.config.ServerConfig;
import faygolover.rpmedicine.core.MedicalSettings;
import faygolover.rpmedicine.core.MedicalState;
import faygolover.rpmedicine.core.Minigames;
import faygolover.rpmedicine.network.MinigameStartPacket;
import faygolover.rpmedicine.network.Network;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.SplittableRandom;
import java.util.function.DoubleFunction;

/**
 * Мини-игры (ТЗ второго этапа, п. 9): вне боя действие открывает мини-игру у медика, в бою — прогресс-бар.
 * Сервер выдаёт задание и ждёт ответ; ответ быстрее физически возможного считается провалом, качество
 * ограничено 0–1. Отказ — прогресс-бар дольше и с большим шансом ошибки.
 */
public final class MinigameService {
    private MinigameService() {}

    private static final SplittableRandom RANDOM = new SplittableRandom();
    private static int nextSession;
    /** Мини-игра длится не дольше, тиков. */
    private static final int TIMEOUT_TICKS = 20 * 90;

    /** Недавно ранен (для правила «в бою — прогресс-бар»). */
    public static boolean recentlyHurt(LivingEntity e) {
        long last = Medical.lastHurtTick(e);
        return e.level().getGameTime() - last < MedicalSettings.get().combatSeconds * 20;
    }

    /** Нужна ли мини-игра: включены, у действия есть мини-игра, и это не бой. */
    @Nullable
    public static Minigames.Type minigameFor(ServerPlayer actor, LivingEntity target, MedicalState m, @Nullable Minigames.Type type) {
        MedicalSettings s = MedicalSettings.get();
        if (!s.minigamesEnabled || type == null) return null;
        if (Minigames.combat(m, recentlyHurt(actor) || recentlyHurt(target), s)) return null;
        return type;
    }

    /**
     * Начать мини-игру. {@code next} по качеству (≥ 0) или отказу (−1) даёт действие, которое выполнит
     * лечение: с качеством — сразу, при отказе — прогресс-бар.
     */
    public static void start(ServerPlayer actor, LivingEntity target, Minigames.Type type, int level, int minLevel, String itemKey,
                             DoubleFunction<ActionManager.TimedAction> next, java.util.function.Supplier<String> checkContinue) {
        int session = ++nextSession;
        float ease = (float) Minigames.ease(level, minLevel);
        ActionManager.start(new MinigameAction(actor, target, type, session, next, checkContinue));
        Network.send(actor, new MinigameStartPacket(session, type, RANDOM.nextLong(), ease, MedicalSettings.get().minigameRefuseAllowed, itemKey));
    }

    /** Номер идущей мини-игры игрока или −1 (для тестов и отладки). */
    public static int currentSession(ServerPlayer sp) {
        return ActionManager.current(sp) instanceof MinigameAction a ? a.session : -1;
    }

    public static void onResult(ServerPlayer sp, int session, float quality) {
        if (!(ActionManager.current(sp) instanceof MinigameAction a) || a.session != session) return;
        MedicalSettings s = MedicalSettings.get();
        double q = quality;
        if (q < 0) {
            if (!s.minigameRefuseAllowed) {
                ActionManager.cancel(sp, "rpmedicine.action.interrupted");
                return;
            }
        } else {
            // Быстрее физически возможного — подделка или случайность: провал.
            double elapsed = a.ticks / 20.0;
            if (elapsed + 0.15 < a.type.minSeconds || Double.isNaN(q)) q = 0;
            q = Math.max(0, Math.min(1, q));
        }
        ActionManager.TimedAction then = a.next.apply(q);
        if (then != null) ActionManager.start(then);
        else ActionManager.cancel(sp, null);
    }

    static final class MinigameAction extends ActionManager.TimedAction {
        final LivingEntity target;
        final Minigames.Type type;
        final int session;
        final DoubleFunction<ActionManager.TimedAction> next;
        final java.util.function.Supplier<String> checkContinue;

        MinigameAction(ServerPlayer actor, LivingEntity target, Minigames.Type type, int session, DoubleFunction<ActionManager.TimedAction> next,
                       java.util.function.Supplier<String> checkContinue) {
            super(actor, TIMEOUT_TICKS);
            this.target = target;
            this.type = type;
            this.session = session;
            this.next = next;
            this.checkContinue = checkContinue;
        }

        @Override
        public String label() {
            return "rpmedicine.action.minigame";
        }

        @Override
        public String checkContinue() {
            if (target.isRemoved() || (target instanceof ServerPlayer tp && tp.isDeadOrDying())) return "rpmedicine.action.target_lost";
            if (actor != target && actor.distanceTo(target) > ServerConfig.INTERACT_DISTANCE.get() + 1.0) return "rpmedicine.action.target_lost";
            return checkContinue.get();
        }

        @Override
        public void complete() {
            actor.displayClientMessage(net.minecraft.network.chat.Component.translatable("rpmedicine.action.minigame_timeout"), true);
        }
    }
}
