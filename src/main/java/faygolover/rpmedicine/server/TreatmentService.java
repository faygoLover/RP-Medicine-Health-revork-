package faygolover.rpmedicine.server;

import faygolover.rpmedicine.config.ServerConfig;
import faygolover.rpmedicine.core.BodyPart;
import faygolover.rpmedicine.core.GameplayEffects;
import faygolover.rpmedicine.core.MedicalSettings;
import faygolover.rpmedicine.core.MedicalState;
import faygolover.rpmedicine.core.Skill;
import faygolover.rpmedicine.core.TreatmentAction;
import faygolover.rpmedicine.core.Treatments;
import faygolover.rpmedicine.data.ItemRules;
import faygolover.rpmedicine.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * Лечение (п. 6 ТЗ): быстрый способ (ПКМ по себе или игроку — часть выбирается сама), перетаскивание
 * на панели (часть выбрана), удерживаемые действия (СЛР, мешок Амбу).
 */
public final class TreatmentService {
    private TreatmentService() {}

    private static final SplittableRandom RANDOM = new SplittableRandom();
    /** Удерживаемые действия: последний тик, когда медик продлил действие. */
    private static final Map<UUID, Long> HOLDS = new HashMap<>();

    /**
     * Начать лечение предметом из слота {@code slot} инвентаря медика.
     *
     * @param part часть тела; null — выбрать автоматически
     * @return true, если событие взаимодействия нужно поглотить
     */
    public static boolean startWithItem(ServerPlayer actor, LivingEntity target, int slot, @Nullable BodyPart part) {
        ItemStack stack = actor.getInventory().getItem(slot);
        ItemRules.Spec spec = ItemRules.specFor(stack);
        if (spec == null) return false;
        // Пока зажата ПКМ, взаимодействие повторяется каждые 4 тика — идущее лечение тем же предметом не перезапускаем.
        if (ActionManager.current(actor) instanceof TreatmentTimedAction cur && cur.target == target && cur.slot == slot
                && ItemStack.isSameItem(cur.original, stack)) return true;
        MedicalState m = Medical.state(target);
        if (m == null) return false;
        MedicalSettings s = MedicalSettings.get();
        String refuse = actorRefusal(actor, target);
        if (refuse != null) {
            actor.displayClientMessage(Component.translatable(refuse).withStyle(ChatFormatting.YELLOW), true);
            return true;
        }
        TreatmentAction action = spec.action();
        if (action.target == TreatmentAction.Target.HOLD) {
            hold(actor, target, action, spec.minLevel());
            return true;
        }
        BodyPart p = part;
        if (p == null) {
            p = Treatments.bestPart(m, action, s);
            if (p == null) {
                // Нигде не нужен: показать причину для самой подходящей части.
                String why = Treatments.check(m, defaultPart(action), action, s);
                actor.displayClientMessage(Component.translatable("rpmedicine.refuse." + (why != null ? why : "not_needed")).withStyle(ChatFormatting.YELLOW), true);
                return true;
            }
        } else if (action.target == TreatmentAction.Target.PART) {
            String why = Treatments.check(m, p, action, s);
            if (why != null) {
                actor.displayClientMessage(Component.translatable("rpmedicine.refuse." + why).withStyle(ChatFormatting.YELLOW), true);
                return true;
            }
        }
        if (action.target != TreatmentAction.Target.PART) {
            String why = Treatments.check(m, p, action, s);
            if (why != null) {
                actor.displayClientMessage(Component.translatable("rpmedicine.refuse." + why).withStyle(ChatFormatting.YELLOW), true);
                return true;
            }
        }
        int level = Medical.medicineLevel(actor);
        boolean self = actor == target;
        GameplayEffects.Mods mods = Medical.data(actor) != null ? Medical.data(actor).lastMods : new GameplayEffects.Mods();
        double seconds = Skill.applySeconds(spec.seconds(), level, self, mods.useTimeFactor, s);
        boolean fromHand = part == null;
        ActionManager.start(new TreatmentTimedAction(actor, target, p, spec, slot, stack.copy(), (int) Math.round(seconds * 20), level, fromHand));
        return true;
    }

    /** Часть, по которой объясняется отказ, если предмет нигде не нужен. */
    static BodyPart defaultPart(TreatmentAction a) {
        return switch (a) {
            case SPLINT, TOURNIQUET, ESMARCH -> BodyPart.RIGHT_LEG;
            case SURGICAL_KIT -> BodyPart.ABDOMEN;
            default -> BodyPart.CHEST;
        };
    }

    /** Почему медик не может лечить (или null). */
    @Nullable
    static String actorRefusal(ServerPlayer actor, LivingEntity target) {
        MedicalState own = Medical.state(actor);
        if (own != null && own.isDown()) return "rpmedicine.refuse.actor_down";
        var d = Medical.data(actor);
        if (d != null && d.lastMods.armsDisabled) return "rpmedicine.refuse.arms_broken";
        if (actor != target && actor.distanceTo(target) > ServerConfig.INTERACT_DISTANCE.get() + 0.5) return "rpmedicine.refuse.too_far";
        return null;
    }

    /** Лечение предметом с прогресс-баром. */
    static final class TreatmentTimedAction extends ActionManager.TimedAction {
        final LivingEntity target;
        private final BodyPart part;
        private final ItemRules.Spec spec;
        final int slot;
        final ItemStack original;
        private final int level;
        private final boolean fromHand;
        /** Где стоял пациент в начале (забор крови и капельница — стоять на месте). */
        private final net.minecraft.world.phys.Vec3 startPos;

        TreatmentTimedAction(ServerPlayer actor, LivingEntity target, BodyPart part, ItemRules.Spec spec, int slot, ItemStack original, int ticks, int level, boolean fromHand) {
            super(actor, ticks);
            this.target = target;
            this.part = part;
            this.spec = spec;
            this.slot = slot;
            this.original = original;
            this.level = level;
            this.fromHand = fromHand;
            this.startPos = target.position();
        }

        @Override
        public String label() {
            return original.getDescriptionId();
        }

        @Override
        public String checkContinue() {
            if (target.isRemoved() || (target instanceof ServerPlayer tp && tp.isDeadOrDying())) return "rpmedicine.action.target_lost";
            if (actor != target && actor.distanceTo(target) > ServerConfig.INTERACT_DISTANCE.get() + 1.0) return "rpmedicine.action.target_lost";
            ItemStack now = actor.getInventory().getItem(slot);
            if (!ItemStack.isSameItem(now, original)) return "rpmedicine.action.item_changed";
            // Быстрый способ: предмет в руке, смена слота прерывает.
            if (fromHand && slot < 9 && actor.getInventory().selected != slot) return "rpmedicine.action.item_changed";
            if (spec.action().requiresStill() && !Medical.isDown(target) && target.position().distanceToSqr(startPos) > 0.35 * 0.35)
                return "rpmedicine.action.target_moved";
            return actorRefusal(actor, target);
        }

        @Override
        public void complete() {
            MedicalState m = Medical.state(target);
            if (m == null) return;
            MedicalSettings s = MedicalSettings.get();
            // Повторная проверка: за время применения состояние могло измениться.
            String why = Treatments.check(m, part, spec.action(), s);
            if (why != null) {
                actor.displayClientMessage(Component.translatable("rpmedicine.refuse." + why).withStyle(ChatFormatting.YELLOW), true);
                return;
            }
            boolean error = !spec.action().isInstrument() && RANDOM.nextDouble() < Skill.errorChance(level, spec.minLevel(), s);
            Treatments.Bag bag = spec.action() == TreatmentAction.BLOOD_BAG
                    ? faygolover.rpmedicine.item.BloodBagItem.bag(actor.getInventory().getItem(slot), actor.level().getGameTime()) : null;
            Treatments.Result r = Treatments.apply(m, part, spec.action(), error, RANDOM.split(), s, bag);
            if (r.consumed && spec.consume()) consume();
            if (spec.action() == TreatmentAction.BLOOD_COLLECT && r.applied) BloodService.giveFilledBag(actor, target);
            Medical.changed(target);
            sound(spec.action());
            Component msg = resultMessage(r, part);
            actor.displayClientMessage(msg, true);
            if (target != actor && target instanceof ServerPlayer tp && !(spec.action().isInstrument())) {
                tp.displayClientMessage(Component.translatable("rpmedicine.msg.treated_by", actor.getDisplayName(), Component.translatable(original.getDescriptionId())), true);
            }
        }

        private void consume() {
            ItemStack stack = actor.getInventory().getItem(slot);
            if (actor.getAbilities().instabuild) return;
            if (stack.isDamageableItem()) stack.hurtAndBreak(1, actor, p -> {});
            else stack.shrink(1);
        }

        private void sound(TreatmentAction a) {
            SoundEvent ev = switch (a) {
                case BANDAGE, PRESSURE_DRESSING, HEMOSTATIC, OCCLUSIVE, SPLINT -> ModSounds.BANDAGE.get();
                case TOURNIQUET, ESMARCH -> ModSounds.TOURNIQUET.get();
                case MORPHINE, ADRENALINE, TXA, NEEDLE, SALINE, BLOOD_BAG, BLOOD_COLLECT -> ModSounds.INJECTION.get();
                case PAINKILLER, AMMONIA -> ModSounds.PILLS.get();
                case DEFIBRILLATOR -> ModSounds.DEFIB_SHOCK.get();
                default -> null;
            };
            if (ev != null) target.level().playSound(null, target.getX(), target.getY(), target.getZ(), ev, SoundSource.PLAYERS, 0.8f, 1.0f);
        }
    }

    static Component resultMessage(Treatments.Result r, BodyPart part) {
        Object[] args = new Object[r.args.length + 1];
        args[0] = Component.translatable(part.translationKey());
        for (int i = 0; i < r.args.length; i++) args[i + 1] = (int) Math.round(r.args[i]);
        ChatFormatting color = r.applied ? ChatFormatting.GREEN : ChatFormatting.RED;
        return Component.translatable("rpmedicine.treat." + r.key, args).withStyle(color);
    }

    // ------------------------------------------------------------------ удержание (СЛР, Амбу)

    /** Одно продление удерживаемого действия (взаимодействие повторяется, пока зажата ПКМ). */
    public static void hold(ServerPlayer actor, LivingEntity target, TreatmentAction action, int minLevel) {
        MedicalState m = Medical.state(target);
        if (m == null) return;
        MedicalSettings s = MedicalSettings.get();
        String refuse = actorRefusal(actor, target);
        if (refuse != null) {
            actor.displayClientMessage(Component.translatable(refuse).withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        if (actor == target) return;
        String why = Treatments.check(m, BodyPart.CHEST, action, s);
        if (why != null) {
            actor.displayClientMessage(Component.translatable("rpmedicine.refuse." + why).withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        int level = Medical.medicineLevel(actor);
        boolean error = RANDOM.nextDouble() < Skill.errorChance(level, minLevel, s);
        Treatments.apply(m, BodyPart.CHEST, action, error, RANDOM.split(), s);
        Medical.changed(target);
        long now = actor.serverLevel().getGameTime();
        Long last = HOLDS.put(actor.getUUID(), now);
        if (last == null || now - last > 10) {
            String label = action == TreatmentAction.CPR ? "rpmedicine.action.cpr" : "rpmedicine.action.ambu";
            faygolover.rpmedicine.network.Network.send(actor, new faygolover.rpmedicine.network.ProgressPacket(label, -1, 0));
        }
    }

    /** Каждый тик: удержание, которое не продлевали, закончилось. */
    public static void tickHolds(net.minecraft.server.MinecraftServer server) {
        if (HOLDS.isEmpty()) return;
        long now = server.overworld().getGameTime();
        HOLDS.entrySet().removeIf(e -> {
            if (now - e.getValue() <= 10) return false;
            ServerPlayer sp = server.getPlayerList().getPlayer(e.getKey());
            if (sp != null && !ActionManager.isBusy(sp)) faygolover.rpmedicine.network.Network.send(sp, faygolover.rpmedicine.network.ProgressPacket.stop());
            return true;
        });
    }
}
