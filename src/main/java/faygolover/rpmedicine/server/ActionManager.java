package faygolover.rpmedicine.server;

import faygolover.rpmedicine.capability.MedicalData;
import faygolover.rpmedicine.network.Network;
import faygolover.rpmedicine.network.ProgressPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Действия с прогресс-баром (лечение, обыск, добивание). У игрока одно действие за раз.
 * Урон по действующему, смена предмета, уход от цели прерывают действие (п. 6.1 ТЗ).
 */
public final class ActionManager {
    private ActionManager() {}

    /** Действие с прогресс-баром. */
    public abstract static class TimedAction {
        protected final ServerPlayer actor;
        protected final int totalTicks;
        protected int ticks;

        protected TimedAction(ServerPlayer actor, int totalTicks) {
            this.actor = actor;
            this.totalTicks = Math.max(1, totalTicks);
        }

        /** Ключ перевода названия действия для прогресс-бара. */
        public abstract String label();

        /** null — можно продолжать; иначе ключ причины прерывания. */
        @Nullable
        public abstract String checkContinue();

        public abstract void complete();

        /** Вызывается при отмене (снять замедление и т.п.). */
        public void onCancel() {}

        /** Замедлять ли действующего (лечение — да). */
        public boolean slowsActor() {
            return true;
        }
    }

    private static final Map<UUID, TimedAction> ACTIONS = new HashMap<>();

    @Nullable
    public static TimedAction current(ServerPlayer sp) {
        return ACTIONS.get(sp.getUUID());
    }

    public static boolean isBusy(ServerPlayer sp) {
        return ACTIONS.containsKey(sp.getUUID());
    }

    public static void start(TimedAction a) {
        cancel(a.actor, null);
        ACTIONS.put(a.actor.getUUID(), a);
        setTreating(a.actor, a.slowsActor());
        Network.send(a.actor, new ProgressPacket(a.label(), a.totalTicks, 0));
    }

    /** Отменить действие игрока; {@code reasonKey} — сообщение (null — молча). */
    public static void cancel(ServerPlayer sp, @Nullable String reasonKey) {
        TimedAction a = ACTIONS.remove(sp.getUUID());
        if (a == null) return;
        a.onCancel();
        setTreating(sp, false);
        Network.send(sp, ProgressPacket.stop());
        if (reasonKey != null) sp.displayClientMessage(Component.translatable(reasonKey), true);
    }

    /** Каждый тик сервера. */
    public static void tick() {
        if (ACTIONS.isEmpty()) return;
        Iterator<Map.Entry<UUID, TimedAction>> it = ACTIONS.entrySet().iterator();
        while (it.hasNext()) {
            TimedAction a = it.next().getValue();
            if (a.actor.isRemoved() || a.actor.isDeadOrDying()) {
                it.remove();
                a.onCancel();
                continue;
            }
            String reason = a.checkContinue();
            if (reason != null) {
                it.remove();
                a.onCancel();
                setTreating(a.actor, false);
                Network.send(a.actor, ProgressPacket.stop());
                a.actor.displayClientMessage(Component.translatable(reason), true);
                continue;
            }
            if (++a.ticks >= a.totalTicks) {
                it.remove();
                setTreating(a.actor, false);
                Network.send(a.actor, ProgressPacket.stop());
                a.complete();
            }
        }
    }

    public static void clear() {
        ACTIONS.clear();
    }

    private static void setTreating(ServerPlayer sp, boolean on) {
        MedicalData d = Medical.data(sp);
        if (d != null && d.treating != on) {
            d.treating = on;
            d.markDirty();
        }
    }
}
