package faygolover.rpmedicine.core;

import java.util.function.Consumer;

/**
 * Полное медицинское состояние человека: части тела и физиология. Хранится на сервере у игрока
 * (capability) и у заглушки. Время — секунды в сети.
 */
public final class MedicalState {
    public enum Heart {
        NORMAL, FIBRILLATION, ARREST;

        public static Heart byOrdinal(int i) {
            return i >= 0 && i < values().length ? values()[i] : NORMAL;
        }
    }

    public enum Pneumo {
        NONE, OPEN, TENSION;

        public static Pneumo byOrdinal(int i) {
            return i >= 0 && i < values().length ? values()[i] : NONE;
        }
    }

    /** Лежачий: обморок (без таймера), нокдаун (таймер — мозг), клиническая смерть. */
    public enum Down {
        NONE, FAINT, KNOCKDOWN, CLINICAL;

        public static Down byOrdinal(int i) {
            return i >= 0 && i < values().length ? values()[i] : NONE;
        }
    }

    public final BodyPartState[] parts = new BodyPartState[BodyPart.VALUES.length];

    // Кровь
    public double weightKg;
    public double heightCm;
    /** Объём в сосудах, мл, включая физраствор. */
    public double bloodVolume;
    /** Сколько из объёма — физраствор, мл. */
    public double saline;
    /** Осталось влить физраствора, мл, и скорость, мл/с. */
    public double salineDripRemaining;
    public double salineDripRate;

    // Сердце и давление
    public Heart heart = Heart.NORMAL;
    public double fibrillationSeconds;
    public double lowPressureSeconds;
    public double pressure;
    public double heartRate;

    // Дыхание
    public double respRate;
    public double spo2;
    public boolean respiratoryArrest;
    /** Воздуховод установлен (держится, пока человек без сознания). */
    public boolean airway;
    /** Интубирован (третий этап, п. 3): дыхательные пути открыты, мешок или стол вентилируют полностью. */
    public boolean intubated;
    /** Зафиксирован на столе с фиксацией: не встаёт, руки не действуют, можно то, что требует пациента без сознания. */
    public boolean restrained;
    /** Осталось секунд вентиляции мешком Амбу и СЛР (продлеваются, пока медик удерживает действие). */
    public double ambuSeconds;
    public double cprSeconds;
    /** Накоплено секунд СЛР для броска на запуск сердца. */
    public double cprAccum;
    public double hypoxiaSeconds;

    // Боль
    /** Боль после обезболивания и адреналина, 0–100. */
    public double pain;
    /** Боль без учёта обезболивания. */
    public double rawPain;
    public double adrenalineSeconds;
    public double adrenalineInjectionSeconds;
    public double painkillerSeconds;
    public double painkillerDelay;
    public double morphineSeconds;
    public double morphineDelay;
    public double morphineOverdoseSeconds;
    public double txaSeconds;
    /** Набор стабилизации: сколько ещё действует; применён ли в этом нокдауне. */
    public double stabilizedSeconds;
    public boolean stabilizationUsed;
    public double shockAccum;
    public double shockLimit;
    public boolean painShock;

    // Сознание и мозг
    public double consciousness;
    public double brain;
    public double concussion;
    public double concussionKoSeconds;
    public double postClinicalSeconds;

    // Грудь
    public Pneumo pneumo = Pneumo.NONE;
    /** Секунд до перехода открытого пневмоторакса в напряжённый. */
    public double pneumoTimer;
    /** Развитие напряжённого пневмоторакса 0–1 (1 — остановка сердца). */
    public double tensionProgress;

    // Лежачий
    public Down down = Down.NONE;
    /** Таймер пробуждения, секунды; отрицательный — не запущен. */
    public double wakeSeconds = -1;
    /** Нокдаун после клинической смерти идёт без таймера (п. 5.4 ТЗ), пока сердце снова не встанет. */
    public boolean knockdownNoTimer;
    /** Чужое лечение (зелья, другие моды): секунды ускоренного заживления. */
    public double healBoostSeconds;

    // Второй этап: инфекция, температура, лекарства
    /** Сепсис 0–100. */
    public double sepsis;
    /** Температура тела, °C. */
    public double bodyTemp;
    /** Испорченная кровь: сколько ещё секунд она питает сепсис. */
    public double spoiledBloodSeconds;
    /** Группа крови (null — не задана). Как рост и вес, переживает смерть. */
    public BloodType bloodType;
    /** Переливание крови: осталось влить, мл, и скорость, мл/с; группа и годность пакета. */
    public double bloodDripRemaining;
    public double bloodDripRate;
    public BloodType bloodDripType;
    public boolean bloodDripSpoiled;
    /** Реакция на несовместимую кровь: осталось секунд. */
    public double transfusionReactionSeconds;
    /** Своя жажда без LSO: вода 0–100. */
    public double thirst = 100;
    /** Команда food add для офлайн-игроков: сколько сытости и воды снять при входе. */
    public double pendingFoodLoss;
    public double pendingThirstLoss;
    /** Повреждение органов 0–100 (третий этап, п. 2), по {@link Organ#ordinal()}. */
    public final double[] organs = new double[Organ.COUNT];
    /** Изъятые органы: биты {@link Organ#bit()}. */
    public int organsMissing;
    /** Пересаженные органы с несовместимой кровью: отторжение (биты {@link Organ#bit()}). */
    public int organRejection;
    /** Сахар крови, ммоль/л (имеет значение у диабетика; третий этап, п. 8). */
    public double bloodSugar = Metabolism.NORMAL_SUGAR;
    /** Вещества (третий этап, п. 9): толерантность 0–100, зависимость (биты), секунд в сети с последней дозы. */
    public final double[] tolerance = new double[Substance.VALUES.length];
    public int dependence;
    public final double[] sinceDose = new double[Substance.VALUES.length];
    /** Опьянение 0–100; судороги (ломка алкоголя), секунд. */
    public double intoxication;
    public double seizureSeconds;
    /** Питание: запасы белков, жиров, углеводов, витаминов 0–120; калории, съеденные за последние часы. */
    public final double[] nutrients = {60, 60, 60, 60};
    public double kcalEaten;
    /** Тошнота от отравления и до следующей рвоты, секунд (не сохраняется: короткое). */
    public double nauseaSeconds;
    public double vomitTimer;
    /** Глухота после взрыва (баротравма), секунд. */
    public double deafSeconds;
    /** Острая боль от манипуляций (вправление, пинцет) и сколько ещё секунд. */
    public double acutePain;
    public double acutePainSeconds;
    /** Опиаты из датапака (трамадол и т.п.): сколько ещё действуют — для налоксона и угнетения дыхания с седацией. */
    public double opioidSeconds;
    /** Дозы препаратов в окне: id препарата → сколько секунд в сети осталось каждой дозе до выхода из окна. */
    public final java.util.Map<String, java.util.List<Double>> doses = new java.util.HashMap<>();
    /** Действующие эффекты лекарств из датапака. */
    public final java.util.EnumMap<DrugEffect, DrugEffect.Active> effects = new java.util.EnumMap<>(DrugEffect.class);

    public MedicalState() {
        for (BodyPart p : BodyPart.VALUES) parts[p.ordinal()] = new BodyPartState(p);
        reset(MedicalSettings.get());
    }

    public MedicalState(MedicalSettings s) {
        for (BodyPart p : BodyPart.VALUES) parts[p.ordinal()] = new BodyPartState(p);
        reset(s);
    }

    public BodyPartState part(BodyPart p) {
        return parts[p.ordinal()];
    }

    public double normalBlood(MedicalSettings s) {
        return s.normalBlood(weightKg);
    }

    /** Доля объёма от нормы. */
    public double bloodFraction(MedicalSettings s) {
        return bloodVolume / normalBlood(s);
    }

    /** Доля крови, переносящей кислород (физраствор кислород не несёт). */
    public double oxygenCapacity(MedicalSettings s) {
        return Math.max(0, bloodVolume - saline) / normalBlood(s);
    }

    /** Полный сброс к здоровому состоянию (после смерти и по команде ГМа). Рост и вес сохраняются. */
    public void reset(MedicalSettings s) {
        if (weightKg <= 0) weightKg = s.defaultWeightKg;
        if (heightCm <= 0) heightCm = s.defaultHeightCm;
        for (BodyPartState ps : parts) ps.clear();
        bloodVolume = normalBlood(s);
        saline = 0;
        salineDripRemaining = 0;
        salineDripRate = 0;
        heart = Heart.NORMAL;
        fibrillationSeconds = 0;
        lowPressureSeconds = 0;
        pressure = s.normalPressure;
        heartRate = s.normalHeartRate;
        respRate = s.normalRespRate;
        spo2 = s.spo2Normal;
        respiratoryArrest = false;
        airway = false;
        intubated = false;
        restrained = false;
        ambuSeconds = 0;
        cprSeconds = 0;
        cprAccum = 0;
        hypoxiaSeconds = 0;
        pain = 0;
        rawPain = 0;
        adrenalineSeconds = 0;
        adrenalineInjectionSeconds = 0;
        painkillerSeconds = 0;
        painkillerDelay = 0;
        morphineSeconds = 0;
        morphineDelay = 0;
        morphineOverdoseSeconds = 0;
        txaSeconds = 0;
        stabilizedSeconds = 0;
        stabilizationUsed = false;
        shockAccum = 0;
        shockLimit = 0;
        painShock = false;
        consciousness = 100;
        brain = 100;
        concussion = 0;
        concussionKoSeconds = 0;
        postClinicalSeconds = 0;
        pneumo = Pneumo.NONE;
        pneumoTimer = 0;
        tensionProgress = 0;
        down = Down.NONE;
        wakeSeconds = -1;
        knockdownNoTimer = false;
        healBoostSeconds = 0;
        sepsis = 0;
        bodyTemp = s.normalBodyTemp;
        spoiledBloodSeconds = 0;
        bloodDripRemaining = 0;
        bloodDripRate = 0;
        bloodDripType = null;
        bloodDripSpoiled = false;
        transfusionReactionSeconds = 0;
        opioidSeconds = 0;
        acutePain = 0;
        acutePainSeconds = 0;
        thirst = 100;
        nauseaSeconds = 0;
        vomitTimer = 0;
        deafSeconds = 0;
        java.util.Arrays.fill(organs, 0);
        organsMissing = 0;
        organRejection = 0;
        bloodSugar = Metabolism.NORMAL_SUGAR;
        java.util.Arrays.fill(tolerance, 0);
        java.util.Arrays.fill(sinceDose, 0);
        dependence = 0;
        intoxication = 0;
        seizureSeconds = 0;
        java.util.Arrays.fill(nutrients, 60);
        kcalEaten = 0;
        doses.clear();
        effects.clear();
    }

    /** Лечение части или всего тела командой ГМа: убирает травмы, но не сбрасывает лекарства. */
    public void healPart(BodyPart p) {
        BodyPartState ps = part(p);
        ps.clear();
        for (Organ o : Organ.VALUES) {
            if (o.part == p) {
                organs[o.ordinal()] = 0;
                organsMissing &= ~o.bit();
                organRejection &= ~o.bit();
            }
        }
        if (p == BodyPart.CHEST) {
            pneumo = Pneumo.NONE;
            pneumoTimer = 0;
            tensionProgress = 0;
        }
    }

    public boolean isDown() {
        return down != Down.NONE;
    }

    public boolean isUnconscious() {
        return down != Down.NONE;
    }

    /**
     * «Спящий режим»: здоровый человек без ран и отклонений, физиологию считать не нужно.
     * Проверка дешёвая — вызывается каждый шаг.
     */
    public boolean isQuiet(MedicalSettings s) {
        if (down != Down.NONE || heart != Heart.NORMAL || pneumo != Pneumo.NONE || respiratoryArrest) return false;
        for (BodyPartState ps : parts) if (!ps.isHealthy()) return false;
        if (bloodVolume < normalBlood(s) - 0.5 || saline > 0 || salineDripRemaining > 0) return false;
        if (brain < 100 || concussion > 0 || concussionKoSeconds > 0 || postClinicalSeconds > 0) return false;
        if (adrenalineSeconds > 0 || adrenalineInjectionSeconds > 0 || painkillerSeconds > 0 || morphineSeconds > 0
                || morphineOverdoseSeconds > 0 || txaSeconds > 0 || stabilizedSeconds > 0 || ambuSeconds > 0 || cprSeconds > 0) return false;
        if (pain > 0 || shockAccum > 0 || painShock || healBoostSeconds > 0) return false;
        if (bloodDripRemaining > 0 || transfusionReactionSeconds > 0) return false;
        if (opioidSeconds > 0 || !doses.isEmpty() || acutePainSeconds > 0) return false;
        if (nauseaSeconds > 0 || deafSeconds > 0) return false;
        if (organsMissing != 0 || intubated) return false;
        for (double d : organs) if (d > 0) return false;
        if (thirst < s.dehydrationThreshold * 100) return false;
        if (sepsis > 0 || spoiledBloodSeconds > 0 || !effects.isEmpty() || Math.abs(bodyTemp - s.normalBodyTemp) > 0.05) return false;
        return Math.abs(pressure - s.normalPressure) < 0.5 && Math.abs(heartRate - s.normalHeartRate) < 0.5
                && Math.abs(respRate - s.normalRespRate) < 0.5 && Math.abs(spo2 - s.spo2Normal) < 0.5
                && consciousness >= 99.5;
    }

    public double totalExternalBleed(MedicalSettings s) {
        double sum = 0;
        for (BodyPartState ps : parts) sum += Physiology.partExternalBleed(this, ps, s);
        return sum;
    }

    public double totalInternalBleed() {
        double sum = 0;
        for (BodyPartState ps : parts) sum += ps.internalBleed;
        return sum;
    }

    public void forEachWound(Consumer<Wound> c) {
        for (BodyPartState ps : parts) ps.wounds.forEach(c);
    }

    public MedicalState copy() {
        MedicalState m = new MedicalState(MedicalSettings.get());
        m.copyFrom(this);
        return m;
    }

    public void copyFrom(MedicalState o) {
        for (int i = 0; i < parts.length; i++) parts[i].copyFrom(o.parts[i]);
        weightKg = o.weightKg;
        heightCm = o.heightCm;
        bloodVolume = o.bloodVolume;
        saline = o.saline;
        salineDripRemaining = o.salineDripRemaining;
        salineDripRate = o.salineDripRate;
        heart = o.heart;
        fibrillationSeconds = o.fibrillationSeconds;
        lowPressureSeconds = o.lowPressureSeconds;
        pressure = o.pressure;
        heartRate = o.heartRate;
        respRate = o.respRate;
        spo2 = o.spo2;
        respiratoryArrest = o.respiratoryArrest;
        airway = o.airway;
        intubated = o.intubated;
        restrained = o.restrained;
        ambuSeconds = o.ambuSeconds;
        cprSeconds = o.cprSeconds;
        cprAccum = o.cprAccum;
        hypoxiaSeconds = o.hypoxiaSeconds;
        pain = o.pain;
        rawPain = o.rawPain;
        adrenalineSeconds = o.adrenalineSeconds;
        adrenalineInjectionSeconds = o.adrenalineInjectionSeconds;
        painkillerSeconds = o.painkillerSeconds;
        painkillerDelay = o.painkillerDelay;
        morphineSeconds = o.morphineSeconds;
        morphineDelay = o.morphineDelay;
        morphineOverdoseSeconds = o.morphineOverdoseSeconds;
        txaSeconds = o.txaSeconds;
        stabilizedSeconds = o.stabilizedSeconds;
        stabilizationUsed = o.stabilizationUsed;
        shockAccum = o.shockAccum;
        shockLimit = o.shockLimit;
        painShock = o.painShock;
        consciousness = o.consciousness;
        brain = o.brain;
        concussion = o.concussion;
        concussionKoSeconds = o.concussionKoSeconds;
        postClinicalSeconds = o.postClinicalSeconds;
        pneumo = o.pneumo;
        pneumoTimer = o.pneumoTimer;
        tensionProgress = o.tensionProgress;
        down = o.down;
        wakeSeconds = o.wakeSeconds;
        knockdownNoTimer = o.knockdownNoTimer;
        healBoostSeconds = o.healBoostSeconds;
        sepsis = o.sepsis;
        bodyTemp = o.bodyTemp;
        spoiledBloodSeconds = o.spoiledBloodSeconds;
        bloodType = o.bloodType;
        bloodDripRemaining = o.bloodDripRemaining;
        bloodDripRate = o.bloodDripRate;
        bloodDripType = o.bloodDripType;
        bloodDripSpoiled = o.bloodDripSpoiled;
        transfusionReactionSeconds = o.transfusionReactionSeconds;
        opioidSeconds = o.opioidSeconds;
        acutePain = o.acutePain;
        acutePainSeconds = o.acutePainSeconds;
        nauseaSeconds = o.nauseaSeconds;
        vomitTimer = o.vomitTimer;
        deafSeconds = o.deafSeconds;
        System.arraycopy(o.organs, 0, organs, 0, Organ.COUNT);
        organsMissing = o.organsMissing;
        organRejection = o.organRejection;
        bloodSugar = o.bloodSugar;
        System.arraycopy(o.tolerance, 0, tolerance, 0, tolerance.length);
        System.arraycopy(o.sinceDose, 0, sinceDose, 0, sinceDose.length);
        dependence = o.dependence;
        intoxication = o.intoxication;
        seizureSeconds = o.seizureSeconds;
        System.arraycopy(o.nutrients, 0, nutrients, 0, nutrients.length);
        kcalEaten = o.kcalEaten;
        thirst = o.thirst;
        pendingFoodLoss = o.pendingFoodLoss;
        pendingThirstLoss = o.pendingThirstLoss;
        doses.clear();
        for (var e : o.doses.entrySet()) doses.put(e.getKey(), new java.util.ArrayList<>(e.getValue()));
        effects.clear();
        for (var e : o.effects.entrySet()) effects.put(e.getKey(), e.getValue().copy());
    }

    /** Острая боль от манипуляции: сильнее предыдущей — заменяет, дольше — продлевает. */
    public void painSpike(double pain, double seconds) {
        acutePain = Math.max(acutePainSeconds > 0 ? acutePain : 0, pain);
        acutePainSeconds = Math.max(acutePainSeconds, seconds);
    }

    // ------------------------------------------------------------------ лекарства

    /** Действующая сила эффекта (0, если не действует или ещё не начал). */
    public double effect(DrugEffect e) {
        DrugEffect.Active a = effects.get(e);
        return a != null && a.working() ? a.strength : 0;
    }

    public boolean hasEffect(DrugEffect e) {
        DrugEffect.Active a = effects.get(e);
        return a != null && a.seconds > 0;
    }

    /**
     * Добавить эффект дозы: одинаковые эффекты не складываются по силе, а продлеваются по времени
     * (Casualties Unknown); сила — наибольшая из доз.
     */
    public void addEffect(DrugEffect e, double strength, double delay, double seconds) {
        DrugEffect.Active a = effects.get(e);
        if (a == null || a.seconds <= 0) {
            effects.put(e, new DrugEffect.Active(strength, delay, seconds));
            return;
        }
        a.seconds += seconds;
        if (Math.abs(strength) > Math.abs(a.strength)) a.strength = strength;
        a.delay = Math.min(a.delay, delay);
    }

    // ------------------------------------------------------------------ органы (третий этап)

    /** Повреждение органа 0–100; изъятый — 100. */
    public double organ(Organ o) {
        return hasOrgan(o) ? organs[o.ordinal()] : 100;
    }

    public boolean hasOrgan(Organ o) {
        return (organsMissing & o.bit()) == 0;
    }

    /** Добавить повреждение органу (не выше 100). */
    public void damageOrgan(Organ o, double amount) {
        if (amount <= 0 || !hasOrgan(o)) return;
        organs[o.ordinal()] = Math.min(100, organs[o.ordinal()] + amount);
    }

    public void setOrgan(Organ o, double value) {
        organs[o.ordinal()] = Math.max(0, Math.min(100, value));
    }
}
