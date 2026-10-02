package faygolover.rpmedicine.server;

import faygolover.rpmedicine.config.ServerConfig;
import faygolover.rpmedicine.core.BodyPart;
import faygolover.rpmedicine.core.GameplayEffects;
import faygolover.rpmedicine.core.MedicalSettings;
import faygolover.rpmedicine.core.MedicalState;
import faygolover.rpmedicine.core.Minigames;
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
        // Идёт мини-игра — повторные клики (удержание ПКМ) её не перезапускают.
        if (MinigameService.currentSession(actor) > 0) return true;
        MedicalState m = Medical.state(target);
        if (m == null) return false;
        MedicalSettings s = MedicalSettings.get();
        String refuse = actorRefusal(actor, target);
        if (refuse != null) {
            actor.displayClientMessage(Component.translatable(refuse).withStyle(ChatFormatting.YELLOW), true);
            return true;
        }
        TreatmentAction action = spec.action();
        Treatments.Extra extra = extraFor(stack, actor);
        if (action.target == TreatmentAction.Target.HOLD) {
            hold(actor, target, action, spec.minLevel());
            return true;
        }
        BodyPart p = part;
        if (p == null) {
            p = Treatments.bestPart(m, action, s, extra);
            if (p == null) {
                // Нигде не нужен: показать причину для самой подходящей части.
                String why = Treatments.check(m, defaultPart(action), action, s, extra);
                actor.displayClientMessage(Component.translatable("rpmedicine.refuse." + (why != null ? why : "not_needed")).withStyle(ChatFormatting.YELLOW), true);
                return true;
            }
        } else if (action.target == TreatmentAction.Target.PART) {
            String why = Treatments.check(m, p, action, s, extra);
            if (why != null) {
                actor.displayClientMessage(Component.translatable("rpmedicine.refuse." + why).withStyle(ChatFormatting.YELLOW), true);
                return true;
            }
        }
        if (action.target != TreatmentAction.Target.PART) {
            String why = Treatments.check(m, p, action, s, extra);
            if (why != null) {
                actor.displayClientMessage(Component.translatable("rpmedicine.refuse." + why).withStyle(ChatFormatting.YELLOW), true);
                return true;
            }
        }
        if (action == TreatmentAction.HEMOANALYZER && !hasLancet(actor)) {
            actor.displayClientMessage(Component.translatable("rpmedicine.refuse.need_lancet").withStyle(ChatFormatting.YELLOW), true);
            return true;
        }
        int level = Medical.medicineLevel(actor);
        boolean self = actor == target;
        GameplayEffects.Mods mods = Medical.data(actor) != null ? Medical.data(actor).lastMods : new GameplayEffects.Mods();
        double seconds = Skill.applySeconds(spec.seconds(), level, self, mods.useTimeFactor, s);
        boolean fromHand = part == null;
        // Вне боя — мини-игра (второй этап, п. 9); в бою и без мини-игры — прогресс-бар.
        Minigames.Type mg = MinigameService.minigameFor(actor, target, m,
                Minigames.typeFor(action, extra instanceof faygolover.rpmedicine.core.Drug d ? d : null));
        if (mg != null) {
            final BodyPart part0 = p;
            final ItemStack copy = stack.copy();
            TreatmentTimedAction probe = new TreatmentTimedAction(actor, target, part0, spec, slot, copy, 1, level, fromHand);
            MinigameService.start(actor, target, mg, level, spec.minLevel(), copy.getDescriptionId(), q -> {
                if (q >= 0) return new TreatmentTimedAction(actor, target, part0, spec, slot, copy, 1, level, fromHand).withQuality(q);
                return new TreatmentTimedAction(actor, target, part0, spec, slot, copy,
                        (int) Math.round(seconds * 20 * s.minigameRefuseTimeFactor), level, fromHand).withErrorFactor(s.minigameRefuseErrorFactor);
            }, probe::checkItem);
            return true;
        }
        ActionManager.start(new TreatmentTimedAction(actor, target, p, spec, slot, stack.copy(), (int) Math.round(seconds * 20), level, fromHand));
        return true;
    }

    /** Гемоанализатор берёт каплю крови ланцетом — ланцет нужен в инвентаре. */
    static boolean hasLancet(ServerPlayer actor) {
        return actor.getAbilities().instabuild || actor.getInventory().contains(new ItemStack(faygolover.rpmedicine.registry.ModItems.LANCET.get()));
    }

    private static boolean consumeLancet(ServerPlayer actor) {
        if (actor.getAbilities().instabuild) return true;
        var inv = actor.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(faygolover.rpmedicine.registry.ModItems.LANCET.get())) {
                inv.getItem(i).shrink(1);
                return true;
            }
        }
        return false;
    }

    /** Что несёт предмет: пакет крови (группа, годность) или препарат. */
    @Nullable
    static Treatments.Extra extraFor(ItemStack stack, ServerPlayer actor) {
        if (stack.getItem() instanceof faygolover.rpmedicine.item.BloodBagItem)
            return faygolover.rpmedicine.item.BloodBagItem.bag(stack, actor.level().getGameTime());
        if (stack.getItem() instanceof faygolover.rpmedicine.item.SurgicalInstrumentItem)
            return new Treatments.Instrument(faygolover.rpmedicine.item.SurgicalInstrumentItem.isSterile(stack));
        return ItemRules.drugFor(stack);
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

    /**
     * Действие пустой рукой с панели (вправление вывиха): прогресс-бар, проверка «нужно», ошибка по навыку.
     */
    public static void startHandAction(ServerPlayer actor, LivingEntity target, BodyPart part, TreatmentAction action, double seconds, int minLevel) {
        MedicalState m = Medical.state(target);
        if (m == null) return;
        MedicalSettings s = MedicalSettings.get();
        String refuse = actorRefusal(actor, target);
        if (refuse != null) {
            actor.displayClientMessage(Component.translatable(refuse).withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        String why = Treatments.check(m, part, action, s);
        if (why != null) {
            actor.displayClientMessage(Component.translatable("rpmedicine.refuse." + why).withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        int level = Medical.medicineLevel(actor);
        GameplayEffects.Mods mods = Medical.data(actor) != null ? Medical.data(actor).lastMods : new GameplayEffects.Mods();
        double sec = Skill.applySeconds(seconds, level, actor == target, mods.useTimeFactor, s);
        Minigames.Type mg = MinigameService.minigameFor(actor, target, m, Minigames.typeFor(action, null));
        if (mg != null) {
            MinigameService.start(actor, target, mg, level, minLevel, "rpmedicine.action." + action.id, q -> {
                if (q >= 0) return new HandTimedAction(actor, target, part, action, 1, level, minLevel).withQuality(q);
                return new HandTimedAction(actor, target, part, action, (int) Math.round(sec * 20 * s.minigameRefuseTimeFactor), level, minLevel)
                        .withErrorFactor(s.minigameRefuseErrorFactor);
            }, () -> actor.getMainHandItem().isEmpty() ? null : "rpmedicine.action.item_changed");
            return;
        }
        ActionManager.start(new HandTimedAction(actor, target, part, action, (int) Math.round(sec * 20), level, minLevel));
    }

    static final class HandTimedAction extends ActionManager.TimedAction {
        private final LivingEntity target;
        private final BodyPart part;
        private final TreatmentAction action;
        private final int level;
        private final int minLevel;

        /** Качество мини-игры (−1 — прогресс-бар) и множитель ошибки (отказ от мини-игры). */
        private double quality = -1;
        private double errorFactor = 1;

        HandTimedAction withQuality(double q) {
            this.quality = q;
            return this;
        }

        HandTimedAction withErrorFactor(double f) {
            this.errorFactor = f;
            return this;
        }

        HandTimedAction(ServerPlayer actor, LivingEntity target, BodyPart part, TreatmentAction action, int ticks, int level, int minLevel) {
            super(actor, ticks);
            this.target = target;
            this.part = part;
            this.action = action;
            this.level = level;
            this.minLevel = minLevel;
        }

        @Override
        public String label() {
            return "rpmedicine.action." + action.id;
        }

        @Override
        public String checkContinue() {
            if (target.isRemoved() || (target instanceof ServerPlayer tp && tp.isDeadOrDying())) return "rpmedicine.action.target_lost";
            if (actor != target && actor.distanceTo(target) > ServerConfig.INTERACT_DISTANCE.get() + 1.0) return "rpmedicine.action.target_lost";
            if (!actor.getMainHandItem().isEmpty()) return "rpmedicine.action.item_changed";
            return actorRefusal(actor, target);
        }

        @Override
        public void complete() {
            MedicalState m = Medical.state(target);
            if (m == null) return;
            MedicalSettings s = MedicalSettings.get();
            String why = Treatments.check(m, part, action, s);
            if (why != null) {
                actor.displayClientMessage(Component.translatable("rpmedicine.refuse." + why).withStyle(ChatFormatting.YELLOW), true);
                return;
            }
            boolean error = quality >= 0 ? Minigames.failed(quality, s)
                    : RANDOM.nextDouble() < Math.min(s.maxErrorChance, Skill.errorChance(level, minLevel, s) * errorFactor);
            Treatments.Result r = Treatments.apply(m, part, action, error, RANDOM.split(), s, null, quality >= 0 ? quality : 1.0);
            Medical.changed(target);
            if (action == TreatmentAction.REDUCE && r.key.equals("reduction_fracture"))
                target.level().playSound(null, target.getX(), target.getY(), target.getZ(), ModSounds.BONE_BREAK.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
            actor.displayClientMessage(resultMessage(r, part), true);
        }
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

        /** Качество мини-игры (−1 — прогресс-бар) и множитель ошибки (отказ от мини-игры). */
        private double quality = -1;
        private double errorFactor = 1;

        TreatmentTimedAction withQuality(double q) {
            this.quality = q;
            return this;
        }

        TreatmentTimedAction withErrorFactor(double f) {
            this.errorFactor = f;
            return this;
        }

        /** Предмет на месте (для мини-игры, пока идёт). */
        String checkItem() {
            ItemStack now = actor.getInventory().getItem(slot);
            if (!ItemStack.isSameItem(now, original)) return "rpmedicine.action.item_changed";
            if (fromHand && slot < 9 && actor.getInventory().selected != slot) return "rpmedicine.action.item_changed";
            return actorRefusal(actor, target);
        }

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
            var drug = ItemRules.drugFor(original);
            this.still = spec.action().requiresStill() || (drug != null && drug.form() == faygolover.rpmedicine.core.Drug.Form.DRIP);
        }

        /** Пациент должен стоять на месте всё действие. */
        private final boolean still;

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
            if (still && !Medical.isDown(target) && target.position().distanceToSqr(startPos) > 0.35 * 0.35)
                return "rpmedicine.action.target_moved";
            return actorRefusal(actor, target);
        }

        @Override
        public void complete() {
            MedicalState m = Medical.state(target);
            if (m == null) return;
            MedicalSettings s = MedicalSettings.get();
            // Повторная проверка: за время применения состояние могло измениться.
            Treatments.Extra extra = extraFor(actor.getInventory().getItem(slot), actor);
            String why = Treatments.check(m, part, spec.action(), s, extra);
            if (why != null) {
                actor.displayClientMessage(Component.translatable("rpmedicine.refuse." + why).withStyle(ChatFormatting.YELLOW), true);
                return;
            }
            boolean error;
            if (spec.action().isDiagnostic()) {
                // Прибор работает у любого, но без нужного уровня показания не разобрать.
                error = RANDOM.nextDouble() < Math.min(s.maxErrorChance, Math.max(0, spec.minLevel() - level) * s.underLevelErrorPerLevel);
                if (spec.action() == TreatmentAction.HEMOANALYZER && !consumeLancet(actor)) {
                    actor.displayClientMessage(Component.translatable("rpmedicine.refuse.need_lancet").withStyle(ChatFormatting.YELLOW), true);
                    return;
                }
            } else {
                error = quality >= 0 ? Minigames.failed(quality, s)
                        : !spec.action().isInstrument() && RANDOM.nextDouble() < Math.min(s.maxErrorChance, Skill.errorChance(level, spec.minLevel(), s) * errorFactor);
            }
            Treatments.Result r = Treatments.apply(m, part, spec.action(), error, RANDOM.split(), s, extra, quality >= 0 ? quality : 1.0);
            if (r.consumed && spec.consume()) consume();
            if (spec.action() == TreatmentAction.BLOOD_COLLECT && r.applied) BloodService.giveFilledBag(actor, target);
            if (spec.action() == TreatmentAction.BLOOD_SAMPLE && r.applied) LabService.giveSample(actor, target);
            // Предложения записей в медкарту (второй этап, п. 10).
            if (spec.action() == TreatmentAction.BLOOD_BAG && r.applied && extra instanceof Treatments.Bag bag)
                MedcardHooks.transfusion(actor, target, bag.type());
            if (spec.action() == TreatmentAction.TWEEZERS && r.applied)
                MedcardHooks.extraction(actor, target, part, r.key.equals("bullet_removed"));
            // Инструмент побывал в ране — больше не стерилен.
            ItemStack used = actor.getInventory().getItem(slot);
            if (used.getItem() instanceof faygolover.rpmedicine.item.SurgicalInstrumentItem && spec.action() == TreatmentAction.TWEEZERS)
                faygolover.rpmedicine.item.SurgicalInstrumentItem.setSterile(used, false);
            Medical.changed(target);
            sound(spec.action());
            Component msg = resultMessage(r, part);
            // Показания приборов длинные — в чат, остальное — над панелью.
            actor.displayClientMessage(msg, !spec.action().isDiagnostic());
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
                case DRUG -> {
                    var d = ItemRules.drugFor(original);
                    yield d != null && d.form() == faygolover.rpmedicine.core.Drug.Form.PILL ? ModSounds.PILLS.get() : ModSounds.INJECTION.get();
                }
                case DRUG_TOPICAL, SUTURE -> ModSounds.BANDAGE.get();
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
        for (int i = 0; i < r.args.length; i++) {
            double v = r.args[i];
            // Целые — без дробной части; термометр и т.п. — с одним знаком.
            args[i + 1] = Math.abs(v - Math.rint(v)) < 1e-9 ? (Object) (int) Math.rint(v) : String.format(java.util.Locale.ROOT, "%.1f", v);
        }
        ChatFormatting color = r.applied ? ChatFormatting.GREEN : ChatFormatting.RED;
        net.minecraft.network.chat.MutableComponent msg = Component.translatable("rpmedicine.treat." + r.key, args).withStyle(color);
        // Показания приборов словами: «Стетоскоп: дыхание чистое; тоны ритмичные».
        for (int i = 0; i < r.words.length; i++) {
            msg.append(Component.literal(i == 0 ? " " : "; ").withStyle(ChatFormatting.GRAY));
            msg.append(Component.translatable("rpmedicine.word." + r.words[i]).withStyle(ChatFormatting.WHITE));
        }
        return msg;
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
