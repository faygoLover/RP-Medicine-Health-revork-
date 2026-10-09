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
        // Новое применение (не продолжение идущего действия или мини-игры) — старый выбор органа забыть,
        // иначе сорвавшаяся попытка навсегда давала «органа здесь нет» на другой части (замечание живого теста 12).
        if (!(ActionManager.current(actor) instanceof TreatmentTimedAction) && MinigameService.currentSession(actor) <= 0)
            ORGAN_PICKS.remove(actor.getUUID());
        return startWithItem(actor, target, slot, part, null);
    }

    /** Выбранный медиком орган для изъятия (до конца действия). */
    private static final java.util.Map<java.util.UUID, faygolover.rpmedicine.core.Organ> ORGAN_PICKS = new java.util.HashMap<>();

    /** Ответ на выбор органа: пусто — отмена. */
    public static void onOrganChoice(ServerPlayer actor, int targetId, int slot, BodyPart part, String organ) {
        var o = faygolover.rpmedicine.core.Organ.byId(organ).orElse(null);
        ORGAN_PICKS.remove(actor.getUUID());
        if (o == null || o.part != part || slot < 0 || slot >= actor.getInventory().getContainerSize()) return;
        net.minecraft.world.entity.Entity e = targetId < 0 ? actor : actor.level().getEntity(targetId);
        if (!(e instanceof LivingEntity target) || !Medical.isPatient(target)) return;
        ORGAN_PICKS.put(actor.getUUID(), o);
        startWithItem(actor, target, slot, part, null);
    }

    /** Ждём выбора дозы ручкой: проверка «нужно ли» уже пройдена (forced — продавлена повтором). */
    private record PendingDose(int targetId, int slot, @Nullable BodyPart part, boolean forced, long tick) {}
    private static final Map<UUID, PendingDose> PENDING_DOSE = new HashMap<>();

    /** Ответ на выбор дозы шприц-ручкой: 0 — отмена. */
    public static void onDoseChoice(ServerPlayer actor, int targetId, int slot, @Nullable BodyPart part, float dose) {
        PendingDose pd = PENDING_DOSE.remove(actor.getUUID());
        if (dose <= 0 || slot < 0 || slot >= actor.getInventory().getContainerSize()) return;
        net.minecraft.world.entity.Entity e = targetId < 0 ? actor : actor.level().getEntity(targetId);
        if (!(e instanceof LivingEntity target) || !Medical.isPatient(target)) return;
        ItemStack st = actor.getInventory().getItem(slot);
        double max = faygolover.rpmedicine.registry.ModItems.isPen(st) ? faygolover.rpmedicine.registry.ModItems.remainingDoses(st) : 3;
        double d = Math.max(0.1, Math.min(max, Math.round(dose * 10) / 10.0));
        boolean same = pd != null && pd.targetId == targetId && pd.slot == slot && actor.level().getGameTime() - pd.tick < 20 * 60;
        startWithItem(actor, target, slot, same ? pd.part : part, d, same ? pd.forced : null);
    }

    /** {@code dose} — выбранная доза (null — спросить опытного или случайная у неопытного). */
    public static boolean startWithItem(ServerPlayer actor, LivingEntity target, int slot, @Nullable BodyPart part, @Nullable Double dose) {
        return startWithItem(actor, target, slot, part, dose, null);
    }

    /** preForced не null — проверка «нужно ли» уже пройдена до выбора дозы. */
    static boolean startWithItem(ServerPlayer actor, LivingEntity target, int slot, @Nullable BodyPart part, @Nullable Double dose,
                                 @Nullable Boolean preForced) {
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
        // Флакон сам не применяется: шприц в руку, флакон во вторую — набрать (решения, п. 1.16).
        if (faygolover.rpmedicine.registry.ModItems.isVial(stack)) {
            actor.displayClientMessage(Component.translatable("rpmedicine.refuse.vial_use_syringe").withStyle(ChatFormatting.YELLOW), true);
            return true;
        }
        Treatments.Extra extra = extraFor(stack, actor, target, action);
        boolean pen = faygolover.rpmedicine.registry.ModItems.isPen(stack);
        boolean filled = stack.getItem() instanceof faygolover.rpmedicine.item.FilledSyringeItem;
        // Набранный шприц: доза уже выбрана при наборе, вводится в вену.
        if (filled) dose = faygolover.rpmedicine.item.FilledSyringeItem.ml(stack);
        extra = withDose(extra, action, dose, filled);
        if (action.target == TreatmentAction.Target.HOLD) {
            hold(actor, target, action, spec.minLevel());
            return true;
        }
        BodyPart p = part;
        // Скальпель ПКМ сам выбирает часть: если что-то уже вскрыто, новую часть молча не режем — назвать вскрытую
        // и отправить в панель, где часть выбирают явно (замечание живого теста 14).
        if (action == TreatmentAction.INCISE && p == null) {
            for (faygolover.rpmedicine.core.BodyPartState ps : m.parts) {
                if (ps.surgery == faygolover.rpmedicine.core.BodyPartState.SurgeryStage.NONE) continue;
                actor.displayClientMessage(Component.translatable("rpmedicine.refuse.incise_other",
                        Component.translatable(ps.part.translationKey())).withStyle(ChatFormatting.YELLOW), true);
                return true;
            }
        }
        boolean forced = false;
        String why;
        // Изъятие: в части несколько органов — медик выбирает, какой (п. 7.1).
        if (action == TreatmentAction.ORGAN_REMOVE && !(extra instanceof Treatments.OrganPick)) {
            BodyPart cand = p != null ? p : Treatments.bestPart(m, action, s, extra);
            if (cand != null) {
                java.util.List<String> present = new java.util.ArrayList<>();
                for (var o : faygolover.rpmedicine.core.Organ.VALUES) if (o.part == cand && m.hasOrgan(o)) present.add(o.id);
                if (present.size() > 1) {
                    faygolover.rpmedicine.network.Network.send(actor, new faygolover.rpmedicine.network.OrganChoicePacket.Request(
                            target == actor ? -1 : target.getId(), slot, cand.ordinal(), present));
                    return true;
                }
                if (present.size() == 1) {
                    ORGAN_PICKS.put(actor.getUUID(), faygolover.rpmedicine.core.Organ.byId(present.get(0)).orElseThrow());
                    extra = extraFor(stack, actor, target, action);
                }
            }
        }
        if (preForced != null) {
            // Проверено до выбора дозы.
            if (p == null) p = Treatments.bestPart(m, action, s, extra);
            if (p == null) p = defaultPart(action);
            forced = preForced;
            why = null;
        } else if (p == null) {
            p = Treatments.bestPart(m, action, s, extra);
            why = p == null ? Treatments.check(m, defaultPart(action), action, s, extra) : null;
            if (p == null && why == null) why = "not_needed";
            if (p == null) p = defaultPart(action);
        } else {
            why = Treatments.check(m, p, action, s, extra);
        }
        if (why != null) {
            // Отказ «не нужно» можно продавить повторным применением — с последствиями (решения, п. 1.13).
            if (Treatments.FORCEABLE.contains(why) && confirmForce(actor, target, action)) {
                forced = true;
            } else {
                var msg = Component.translatable("rpmedicine.refuse." + why).withStyle(ChatFormatting.YELLOW);
                if (Treatments.FORCEABLE.contains(why))
                    msg.append(Component.translatable("rpmedicine.refuse.force_hint").withStyle(ChatFormatting.GRAY));
                actor.displayClientMessage(msg, true);
                return true;
            }
        }
        if (action == TreatmentAction.INTUBATE && !actor.getAbilities().instabuild
                && !actor.getInventory().contains(new ItemStack(faygolover.rpmedicine.registry.ModItems.LARYNGOSCOPE.get()))) {
            actor.displayClientMessage(Component.translatable("rpmedicine.refuse.need_laryngoscope").withStyle(ChatFormatting.YELLOW), true);
            return true;
        }
        // Изъятие органа: связки и сосуды режет скальпель, контейнер только принимает орган (замечание 86).
        if (action == TreatmentAction.ORGAN_REMOVE && !hasItem(actor, faygolover.rpmedicine.registry.ModItems.SCALPEL.get())) {
            actor.displayClientMessage(Component.translatable("rpmedicine.refuse.need_scalpel").withStyle(ChatFormatting.YELLOW), true);
            return true;
        }
        if (action == TreatmentAction.BLOOD_SAMPLE && !hasItem(actor, faygolover.rpmedicine.registry.ModItems.TEST_TUBE.get())) {
            actor.displayClientMessage(Component.translatable("rpmedicine.refuse.need_test_tube").withStyle(ChatFormatting.YELLOW), true);
            return true;
        }
        if (action == TreatmentAction.HEMOANALYZER && !hasLancet(actor)) {
            actor.displayClientMessage(Component.translatable("rpmedicine.refuse.need_lancet").withStyle(ChatFormatting.YELLOW), true);
            return true;
        }
        // Доза шприц-ручкой — после проверки «нужно ли» (решения, п. 1.16): опытный выбирает колёсиком,
        // неопытный не умеет — колет случайно 0,75–1,25 дозы.
        if (pen && dose == null && (Treatments.drugOf(extra) != null || Treatments.DOSE_ACTIONS.contains(action))) {
            double left = faygolover.rpmedicine.registry.ModItems.remainingDoses(stack);
            if (Medical.medicineLevel(actor) >= s.dosingMinLevel) {
                PENDING_DOSE.put(actor.getUUID(), new PendingDose(target == actor ? -1 : target.getId(), slot, part == null ? null : p, forced,
                        actor.level().getGameTime()));
                double hint = 1.0;
                if (Treatments.drugOf(extra) != null)
                    hint = 1.0 / Math.max(0.1, faygolover.rpmedicine.core.Drugs.effectiveDose(m, Treatments.drugOf(extra), 1.0, s));
                faygolover.rpmedicine.network.Network.send(actor, new faygolover.rpmedicine.network.DosePacket.Request(
                        target == actor ? -1 : target.getId(), slot, part == null ? -1 : p.ordinal(), stack.getHoverName(), (int) Math.round(m.weightKg),
                        (float) left, (float) Math.min(left, Math.round(hint * 10) / 10.0)));
                return true;
            }
            dose = Math.min(left, 0.75 + actor.getRandom().nextDouble() * 0.5);
            extra = withDose(extraFor(stack, actor, target, action), action, dose, false);
        }
        int level = Medical.medicineLevel(actor);
        boolean self = actor == target;
        GameplayEffects.Mods mods = Medical.data(actor) != null ? Medical.data(actor).lastMods : new GameplayEffects.Mods();
        // Не быстрее, чем отыгрывает анимация предмета в руке.
        double seconds = Math.max(Skill.applySeconds(spec.seconds(), level, self, mods.useTimeFactor, s),
                faygolover.rpmedicine.data.UseTimes.min(stack));
        boolean fromHand = part == null;
        // Вне боя — мини-игра (второй этап, п. 9); в бою и без мини-игры — прогресс-бар.
        // Операция — всегда мини-игрой на сцене тела (решения, п. 1.14), даже в бою.
        Minigames.Type surgical = p != null ? Minigames.surgeryType(action, m, m.part(p)) : null;
        // Набранный шприц при стоящем катетере — в порт, без мини-игры вены.
        boolean port = filled && m.catheterPart >= 0;
        Minigames.Type mg = port ? null : surgical != null ? (s.minigamesEnabled && !MinigameService.prefersBar(actor) ? surgical : null) : MinigameService.minigameFor(actor, target, m,
                Minigames.typeFor(action, extra instanceof faygolover.rpmedicine.core.Drug d ? d : null));
        // Продавленное «не нужно» — без мини-игры, кроме укола: шприц-ручку колют всегда одинаково (замечание 09.10, М4).
        if (forced && mg != Minigames.Type.INJECTION) mg = null;
        Minigames.Scene scene = surgical != null ? Minigames.Scene.of(m, p, target.getUUID().getLeastSignificantBits() ^ p.ordinal() * 0x9E3779B97F4A7C15L)
                : Minigames.Scene.NONE;
        if (extra instanceof Treatments.OrganPick op) scene = scene.withOrgan(op.organ());
        // Тонометр: тоны — по настоящему давлению (верхнее и нижнее — в полях сцены).
        if (mg == Minigames.Type.BP_CUFF) {
            int sys = m.heart == faygolover.rpmedicine.core.MedicalState.Heart.NORMAL ? (int) Math.round(m.pressure) : 0;
            scene = new Minigames.Scene(-1, 0, 0, 0, sys, (int) Math.round(sys * 0.65), (int) Math.round(m.heartRate));
        }
        if (extra instanceof Treatments.DonorOrgan dn) scene = scene.withOrgan(dn.organ());
        if (mg != null) {
            final BodyPart part0 = p;
            final ItemStack copy = stack.copy();
            TreatmentTimedAction probe = new TreatmentTimedAction(actor, target, part0, spec, slot, copy, 1, level, fromHand);
            final Double dose0 = dose;
            MinigameService.start(actor, target, mg, level, spec.minLevel(), copy.getDescriptionId(), scene, q -> {
                // Забор крови: игла в вене — дальше кровь набирается сама, стоять на месте (замечание 38).
                int after = action == TreatmentAction.BLOOD_COLLECT ? (int) Math.round(seconds * 20) : 1;
                // После мини-игры — всегда короткое введение под анимацию предмета в руке (одинаково у всех).
                after = Math.max(after, (int) Math.round(faygolover.rpmedicine.data.UseTimes.min(copy) * 20));
                if (q >= 0) return new TreatmentTimedAction(actor, target, part0, spec, slot, copy, after, level, fromHand).withQuality(q).withDose(dose0);
                return new TreatmentTimedAction(actor, target, part0, spec, slot, copy,
                        (int) Math.round(seconds * 20 * s.minigameRefuseTimeFactor), level, fromHand).withErrorFactor(s.minigameRefuseErrorFactor).withDose(dose0);
            }, probe::checkItem);
            return true;
        }
        ActionManager.start(new TreatmentTimedAction(actor, target, p, spec, slot, stack.copy(), (int) Math.round(seconds * 20), level, fromHand)
                .withForced(forced).withDose(dose));
        return true;
    }

    /** Обернуть препарат дозой и путём: набранный шприц — в вену, ручка — в мышцу. */
    static Treatments.Extra withDose(Treatments.Extra extra, TreatmentAction action, @Nullable Double dose, boolean intravenous) {
        if (dose == null) return extra;
        if (extra instanceof faygolover.rpmedicine.core.Drug drug)
            return new Treatments.Dosed(drug, dose, intravenous ? faygolover.rpmedicine.core.DrugLevels.Route.IV : faygolover.rpmedicine.core.DrugLevels.routeOf(drug));
        if (Treatments.DOSE_ACTIONS.contains(action)) return new Treatments.ActionDose(dose);
        return extra;
    }

    private record ForcePending(int targetId, TreatmentAction action, long tick) {}
    private static final Map<UUID, ForcePending> FORCE = new HashMap<>();

    /** Второй раз подряд то же действие на того же пациента за 5 секунд — применить вопреки отказу. */
    private static boolean confirmForce(ServerPlayer actor, LivingEntity target, TreatmentAction action) {
        long now = actor.serverLevel().getGameTime();
        ForcePending prev = FORCE.get(actor.getUUID());
        if (prev != null && prev.targetId() == target.getId() && prev.action() == action && now - prev.tick() <= 100) {
            FORCE.remove(actor.getUUID());
            return true;
        }
        FORCE.put(actor.getUUID(), new ForcePending(target.getId(), action, now));
        return false;
    }

    /** Гемоанализатор берёт каплю крови ланцетом — ланцет нужен в инвентаре. */
    static boolean hasLancet(ServerPlayer actor) {
        return actor.getAbilities().instabuild || actor.getInventory().contains(new ItemStack(faygolover.rpmedicine.registry.ModItems.LANCET.get()));
    }

    static boolean hasItem(ServerPlayer actor, net.minecraft.world.item.Item item) {
        return actor.getAbilities().instabuild || actor.getInventory().contains(new ItemStack(item));
    }

    /** Потратить один предмет из инвентаря (в творческом — не тратится). */
    /** Отдать использованный предмет (грязный шприц, пробирку) — в инвентарь или под ноги. */
    static void giveWaste(ServerPlayer actor, net.minecraft.world.item.Item item) {
        if (actor.getAbilities().instabuild) return;
        ItemStack st = new ItemStack(item);
        if (!actor.getInventory().add(st)) actor.drop(st, false);
    }

    static boolean consumeItem(ServerPlayer actor, net.minecraft.world.item.Item item) {
        if (actor.getAbilities().instabuild) return true;
        var inv = actor.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(item)) {
                inv.getItem(i).shrink(1);
                return true;
            }
        }
        return false;
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
    /** То же для действия: шаги хирургии несут обстановку операции (место, стерильность, экипировка). */
    static Treatments.Extra extraFor(ItemStack stack, ServerPlayer actor, LivingEntity target, TreatmentAction a) {
        long now = actor.level().getGameTime();
        switch (a) {
            case ORGAN_REMOVE -> {
                var o = ORGAN_PICKS.get(actor.getUUID());
                return o != null ? new Treatments.OrganPick(o) : null;
            }
            case TRANSPLANT -> {
                return faygolover.rpmedicine.item.OrganItem.donorOrgan(stack, now);
            }
            case REATTACH -> {
                BodyPart lp = faygolover.rpmedicine.item.SeveredLimbItem.part(stack);
                return lp != null ? new Treatments.Limb(lp, !faygolover.rpmedicine.item.SeveredLimbItem.spoiled(stack, now)) : null;
            }
            default -> { }
        }
        if (faygolover.rpmedicine.core.Surgery.isSurgical(a)) return SurgeryService.context(actor, target, stack);
        return extraFor(stack, actor);
    }

    /** Шанс ошибки с учётом места операции: множитель успеха уменьшает шанс сделать всё правильно. */
    static double surgeryError(double chance, Treatments.Extra extra) {
        if (!(extra instanceof faygolover.rpmedicine.core.Surgery.Context c)) return chance;
        return 1 - (1 - Math.min(1, chance)) * c.successFactor();
    }

    static Treatments.Extra extraFor(ItemStack stack, ServerPlayer actor) {
        if (stack.getItem() instanceof faygolover.rpmedicine.item.BloodBagItem)
            return faygolover.rpmedicine.item.BloodBagItem.bag(stack, actor.level().getGameTime());
        if (stack.getItem() instanceof faygolover.rpmedicine.item.SurgicalInstrumentItem)
            return new Treatments.Instrument(faygolover.rpmedicine.item.SurgicalInstrumentItem.isSterile(stack));
        if (stack.getItem() instanceof faygolover.rpmedicine.item.ProstheticItem pi) return new Treatments.Prosthetic(pi.type);
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
        if (own != null && own.restrained) return "rpmedicine.refuse.actor_restrained";
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
        public LivingEntity patient() {
            return target;
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
            if (r.applied) MedicineXp.award(actor, target, action, minLevel, level, error);
            faygolover.rpmedicine.stats.StatsService.treatment(actor, target, "hand", action.id, part, r.key, error);
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

        /** Применение вопреки отказу проверки. */
        private boolean forced;
        /** Доза шприцем (null — стандартная). */
        @Nullable
        private Double dose;

        TreatmentTimedAction withDose(@Nullable Double d) {
            this.dose = d;
            return this;
        }

        TreatmentTimedAction withForced(boolean f) {
            this.forced = f;
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
        public ItemStack icon() {
            return original;
        }

        @Override
        public LivingEntity patient() {
            return target;
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
            // Состояние изменилось по ходу (поставили другую капельницу, кончилась СЛР) — прервать сразу.
            if (!forced) {
                MedicalState m = Medical.state(target);
                if (m != null) {
                    String why = Treatments.check(m, part, spec.action(), MedicalSettings.get(),
                            extraFor(actor.getInventory().getItem(slot), actor, target, spec.action()));
                    if (why != null && !why.equals("not_needed")) return "rpmedicine.refuse." + why;
                }
            }
            return actorRefusal(actor, target);
        }

        @Override
        public void complete() {
            MedicalState m = Medical.state(target);
            if (m == null) return;
            MedicalSettings s = MedicalSettings.get();
            // Повторная проверка: за время применения состояние могло измениться.
            Treatments.Extra extra = extraFor(actor.getInventory().getItem(slot), actor, target, spec.action());
            // Набранный шприц — в вену (в порт катетера), ручка — в мышцу (решения, п. 1.16).
            extra = TreatmentService.withDose(extra, spec.action(), dose, original.getItem() instanceof faygolover.rpmedicine.item.FilledSyringeItem);
            String why = Treatments.check(m, part, spec.action(), s, extra);
            if (forced && why != null && Treatments.FORCEABLE.contains(why)) {
                Treatments.Result fr = Treatments.applyForced(m, part, spec.action(), RANDOM.split(), s, extra);
                faygolover.rpmedicine.stats.StatsService.treatment(actor, target, String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(original.getItem())),
                        spec.action().id, part, fr.key, true);
                if (fr.consumed && spec.consume()) consume();
                Medical.changed(target);
                sound(spec.action());
                actor.displayClientMessage(resultMessage(fr, part), true);
                return;
            }
            if (why != null) {
                actor.displayClientMessage(Component.translatable("rpmedicine.refuse." + why).withStyle(ChatFormatting.YELLOW), true);
                return;
            }
            boolean error;
            if (spec.action().isDiagnostic()) MedcardHooks.examined(actor, target);
            if (spec.action().isDiagnostic()) {
                // Прибор работает у любого, но без нужного уровня показания не разобрать.
                error = quality >= 0 ? Minigames.failed(quality, s)
                        : RANDOM.nextDouble() < Math.min(s.maxErrorChance, Math.max(0, spec.minLevel() - level) * s.underLevelErrorPerLevel);
                if (spec.action() == TreatmentAction.HEMOANALYZER && !consumeLancet(actor)) {
                    actor.displayClientMessage(Component.translatable("rpmedicine.refuse.need_lancet").withStyle(ChatFormatting.YELLOW), true);
                    return;
                }
            } else {
                error = quality >= 0 ? Minigames.failed(quality, s)
                        : !spec.action().isInstrument() && RANDOM.nextDouble() < surgeryError(Math.min(s.maxErrorChance, Skill.errorChance(level, spec.minLevel(), s) * errorFactor), extra);
            }
            // Остеосинтез тратит набор (пластины и винты) из инвентаря хирурга.
            if (spec.action() == TreatmentAction.OSTEOSYNTHESIS && !hasItem(actor, faygolover.rpmedicine.registry.ModItems.OSTEOSYNTHESIS_KIT.get())) {
                actor.displayClientMessage(Component.translatable("rpmedicine.refuse.need_osteosynthesis_kit").withStyle(ChatFormatting.YELLOW), true);
                return;
            }
            // Изъятие: повреждение органа до того, как он покинет тело.
            faygolover.rpmedicine.core.Organ taken = spec.action() == TreatmentAction.ORGAN_REMOVE
                    ? (extra instanceof Treatments.OrganPick op ? op.organ() : faygolover.rpmedicine.core.Surgery.firstOrgan(m, part)) : null;
            double takenDamage = taken != null ? m.organ(taken) : 0;
            Treatments.Result r = Treatments.apply(m, part, spec.action(), error, RANDOM.split(), s, extra, quality >= 0 ? quality : 1.0);
            // Опыт «Медицины» (рост навыков в RP Perks): удачное и нужное лечение.
            if (r.applied && !forced) MedicineXp.award(actor, target, spec.action(), spec.minLevel(), level, error);
            if (spec.action() == TreatmentAction.ORGAN_REMOVE) ORGAN_PICKS.remove(actor.getUUID());
            if (r.applied && r.key.equals("organ_removed") && taken != null) {
                ItemStack organ = faygolover.rpmedicine.item.OrganItem.create(faygolover.rpmedicine.registry.ModItems.ORGAN.get(), taken, takenDamage,
                        m.bloodType, target.getName().getString(), actor.level().getGameTime());
                if (!actor.getInventory().add(organ)) actor.drop(organ, false);
            }
            if (r.applied && spec.action() == TreatmentAction.TRANSPLANT) {
                ItemStack empty = new ItemStack(faygolover.rpmedicine.registry.ModItems.ORGAN_CONTAINER.get());
                if (!actor.getInventory().add(empty)) actor.drop(empty, false);
            }
            faygolover.rpmedicine.stats.StatsService.treatment(actor, target, String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(original.getItem())),
                    spec.action().id, part, r.key, error);
            if (r.consumed && spec.consume()) {
                consume();
                // Шприц для забора после укола — грязный, а не исчезает.
                if (original.is(faygolover.rpmedicine.registry.ModItems.BLOOD_DRAW_SYRINGE.get())
                        || original.is(faygolover.rpmedicine.registry.ModItems.SYRINGE.get()))
                    giveWaste(actor, faygolover.rpmedicine.registry.ModItems.DIRTY_SYRINGE.get());
            }
            if (spec.action() == TreatmentAction.BLOOD_COLLECT && r.applied) BloodService.giveFilledBag(actor, target);
            if (spec.action() == TreatmentAction.BLOOD_SAMPLE && r.applied) {
                // Пустая пробирка становится пробиркой с кровью.
                if (!consumeItem(actor, faygolover.rpmedicine.registry.ModItems.TEST_TUBE.get())) {
                    actor.displayClientMessage(Component.translatable("rpmedicine.refuse.need_test_tube").withStyle(ChatFormatting.YELLOW), true);
                    return;
                }
                LabService.giveSample(actor, target);
            }
            // Предложения записей в медкарту (второй этап, п. 10).
            if (spec.action() == TreatmentAction.BLOOD_BAG && r.applied && extra instanceof Treatments.Bag bag)
                MedcardHooks.transfusion(actor, target, bag.type());
            if (spec.action() == TreatmentAction.TWEEZERS && r.applied)
                MedcardHooks.extraction(actor, target, part, r.key.equals("bullet_removed"));
            if ((spec.action() == TreatmentAction.DRUG || spec.action() == TreatmentAction.DRUG_TOPICAL) && r.applied && Treatments.drugOf(extra) != null)
                MedcardHooks.drug(actor, target, original, Treatments.drugOf(extra), extra instanceof Treatments.Dosed ds ? ds.dose() : 1.0);
            if (spec.action() == TreatmentAction.INTUBATE && r.applied) MedcardHooks.intubation(actor, target);
            // Инструмент побывал в ране — больше не стерилен.
            ItemStack used = actor.getInventory().getItem(slot);
            if (used.getItem() instanceof faygolover.rpmedicine.item.SurgicalInstrumentItem
                    && (spec.action() == TreatmentAction.TWEEZERS || faygolover.rpmedicine.core.Surgery.isSurgical(spec.action())))
                faygolover.rpmedicine.item.SurgicalInstrumentItem.setSterile(used, false);
            if (spec.action() == TreatmentAction.OSTEOSYNTHESIS && r.applied) consumeItem(actor, faygolover.rpmedicine.registry.ModItems.OSTEOSYNTHESIS_KIT.get());
            if (r.applied && MedcardHooks.SURGERY_KEYS.contains(r.key)) MedcardHooks.surgery(actor, target, part, r.key);
            if (r.applied && r.key.equals("amputated")) LimbDrops.drop(target, part);
            Medical.changed(target);
            sound(spec.action());
            Component msg = resultMessage(r, part);
            // Опытный хирург (6+) сразу видит следующий шаг операции (замечание живого теста 20).
            var ops = m.part(part);
            if (r.applied && faygolover.rpmedicine.core.Surgery.isSurgical(spec.action()) && level >= 6
                    && ops.surgery != faygolover.rpmedicine.core.BodyPartState.SurgeryStage.NONE) {
                msg = msg.copy().append(Component.literal(" ·").withStyle(ChatFormatting.GRAY)).append(Component.translatable(
                        "rpmedicine.exam.next_step_" + faygolover.rpmedicine.core.Examination.nextSurgeryStep(m, ops)).withStyle(ChatFormatting.AQUA));
            }
            // Показания приборов длинные — в чат, остальное — над панелью.
            actor.displayClientMessage(msg, !spec.action().isDiagnostic());
            if (target != actor && target instanceof ServerPlayer tp && !(spec.action().isInstrument())) {
                tp.displayClientMessage(Component.translatable("rpmedicine.msg.treated_by", actor.getDisplayName(), Component.translatable(original.getDescriptionId())), true);
            }
        }

        private void consume() {
            ItemStack stack = actor.getInventory().getItem(slot);
            if (actor.getAbilities().instabuild) return;
            if (stack.getItem() instanceof faygolover.rpmedicine.item.FilledSyringeItem) {
                // Шприц после укола — использованный (многоразовый: стерилизатор вернёт чистым).
                stack.shrink(1);
                giveWaste(actor, faygolover.rpmedicine.registry.ModItems.DIRTY_SYRINGE.get());
                return;
            }
            if (stack.isDamageableItem()) {
                // Ручки считают десятые доли дозы (1 мл = 1 доза).
                boolean dosed = faygolover.rpmedicine.registry.ModItems.isPen(stack);
                int units = dosed ? Math.max(1, (int) Math.round((dose != null ? dose : 1.0) * 10)) : 1;
                boolean pen = faygolover.rpmedicine.registry.ModItems.isPen(stack);
                stack.hurtAndBreak(units, actor, p -> {
                    if (pen) giveWaste(actor, faygolover.rpmedicine.registry.ModItems.USED_PEN.get());
                });
            } else {
                stack.shrink(1);
            }
        }

        private void sound(TreatmentAction a) {
            SoundEvent ev = switch (a) {
                case BANDAGE, PRESSURE_DRESSING, HEMOSTATIC, OCCLUSIVE -> ModSounds.BANDAGE.get();
                case SPLINT -> ModSounds.SPLINT.get();
                case AIRWAY, INTUBATE -> ModSounds.SURGERY_TRACHEA.get();
                case TOURNIQUET, ESMARCH -> ModSounds.TOURNIQUET.get();
                case MORPHINE, ADRENALINE, TXA, NEEDLE, SALINE, BLOOD_BAG, BLOOD_COLLECT, CATHETER -> ModSounds.INJECTION.get();
                case DRUG -> {
                    var d = ItemRules.drugFor(original);
                    yield d != null && d.form() == faygolover.rpmedicine.core.Drug.Form.PILL ? ModSounds.PILLS.get() : ModSounds.INJECTION.get();
                }
                case DRUG_TOPICAL -> ModSounds.BANDAGE.get();
                case SUTURE -> ModSounds.SURGERY_STITCH.get();
                case PAINKILLER -> ModSounds.PILLS.get();
                case AMMONIA -> ModSounds.AMMONIA.get();
                case SURGICAL_KIT -> ModSounds.SURGERY_CAUTERY.get();
                case TWEEZERS -> ModSounds.SURGERY_RETRACT.get();
                case INCISE -> ModSounds.SURGERY_CUT.get();
                case CLAMP -> ModSounds.SURGERY_CLAMP.get();
                case RETRACT -> ModSounds.SURGERY_RETRACT.get();
                case VESSEL_SUTURE -> ModSounds.SURGERY_STITCH.get();
                case OSTEOSYNTHESIS -> ModSounds.BONE_DRILL.get();
                case DRAIN -> ModSounds.SURGERY_SUCTION.get();
                case AMPUTATE -> ModSounds.BONE_SAW.get();
                case INSTALL_PROSTHESIS -> ModSounds.SPLINT.get();
                case SCANNER -> ModSounds.SCANNER.get();
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
        long now = actor.serverLevel().getGameTime();
        // Само удержание даёт лишь слабый эффект; настоящая СЛР и вдохи — в ритм пробелом (onRhythm).
        if (action == TreatmentAction.CPR) m.cprSeconds = Math.max(m.cprSeconds, 0.25);
        else if (action == TreatmentAction.AMBU) m.ambuSeconds = Math.max(m.ambuSeconds, 0.25);
        HOLD_INFO.put(actor.getUUID(), new HoldInfo(target, action, minLevel));
        Medical.changed(target);
        Long last = HOLDS.put(actor.getUUID(), now);
        if (last == null || now - last > 10) {
            String label = action == TreatmentAction.CPR ? "rpmedicine.action.cpr" : "rpmedicine.action.ambu";
            // В «сделано» — уровень медицины: от него ширина окна попадания в ритм.
            faygolover.rpmedicine.network.Network.send(actor, new faygolover.rpmedicine.network.ProgressPacket(label, -1, Medical.medicineLevel(actor)));
        }
    }

    /** Когда в последний раз подсказывали медику, что мешает запустить сердце (тики). */
    private static final Map<UUID, Long> LAST_HINT = new HashMap<>();

    private record HoldInfo(LivingEntity target, TreatmentAction action, int minLevel) {}

    private static final Map<UUID, HoldInfo> HOLD_INFO = new HashMap<>();

    /**
     * Такт удерживаемого действия: нажатие пробела в ритм при СЛР (100–120 в минуту) или вдох мешком
     * Амбу. Точность 0–1 даёт силу компрессии или вдоха; ошибка по навыку — вдвое слабее.
     */
    public static void onRhythm(ServerPlayer actor, boolean ambu, float quality) {
        Long last = HOLDS.get(actor.getUUID());
        HoldInfo info = HOLD_INFO.get(actor.getUUID());
        if (last == null || info == null || actor.serverLevel().getGameTime() - last > 12) return;
        if ((info.action() == TreatmentAction.AMBU) != ambu) return;
        MedicalState m = Medical.state(info.target());
        if (m == null) return;
        MedicalSettings s = MedicalSettings.get();
        double q = Math.max(0, Math.min(1, quality));
        if (RANDOM.nextDouble() < Skill.errorChance(Medical.medicineLevel(actor), info.minLevel(), s)) q *= 0.5;
        // Компрессии: сильному проще продавить грудную клетку, полного пациента — тяжелее.
        if (!ambu) q = Math.min(1, q * Math.max(0.6, faygolover.rpmedicine.integration.CoreCompat.strengthFactor(actor))
                * (faygolover.rpmedicine.core.Body.obese(m) ? 0.85 : 1.0));
        if (ambu) m.ambuSeconds = Math.max(m.ambuSeconds, 1.5 + 3.5 * q);
        else {
            m.cprSeconds = Math.max(m.cprSeconds, 0.35 + 0.9 * q);
            m.cprRecentSeconds = s.defibAfterCprSeconds;
            // Сердце не заведётся, пока есть причина: подсказать медику, что мешает (замечание 11).
            String blocker = m.heart != MedicalState.Heart.NORMAL ? faygolover.rpmedicine.core.Physiology.restartBlocker(m, s) : null;
            long now = actor.serverLevel().getGameTime();
            if (blocker != null && now - LAST_HINT.getOrDefault(actor.getUUID(), 0L) > 120) {
                LAST_HINT.put(actor.getUUID(), now);
                actor.displayClientMessage(Component.translatable("rpmedicine.hint.restart_" + blocker).withStyle(ChatFormatting.GOLD), true);
            }
            // Компрессия видна: рука опускается только на нажатие пробела (замечание 16).
            actor.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        }
        Medical.changed(info.target());
    }

    /** Каждый тик: удержание, которое не продлевали, закончилось. */
    public static void tickHolds(net.minecraft.server.MinecraftServer server) {
        if (HOLDS.isEmpty()) return;
        long now = server.overworld().getGameTime();
        HOLDS.entrySet().removeIf(e -> {
            if (now - e.getValue() <= 10) return false;
            ServerPlayer sp = server.getPlayerList().getPlayer(e.getKey());
            if (sp != null && !ActionManager.isBusy(sp)) faygolover.rpmedicine.network.Network.send(sp, faygolover.rpmedicine.network.ProgressPacket.stop());
            HOLD_INFO.remove(e.getKey());
            return true;
        });
    }
}
