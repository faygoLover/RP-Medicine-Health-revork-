#!/usr/bin/env python3
# Генерирует assets/rpmedicine/lang/ru_ru.json и en_us.json из одной таблицы (ключ: (ru, en)).
import json, os

T = {}
def t(key, ru, en): T[key] = (ru, en)

# Предметы
items = {
 "bandage": ("Бинт", "Bandage", "Слабое и среднее кровотечение; рана заживает быстрее.", "Light and moderate bleeding; the wound heals faster."),
 "pressure_dressing": ("Давящая повязка (ИПП)", "Pressure dressing", "Среднее и сильное кровотечение.", "Moderate and heavy bleeding."),
 "hemostatic_gauze": ("Гемостатическая марля", "Hemostatic gauze", "Сильное и артериальное кровотечение; место после неё не рвётся повторно.", "Heavy and arterial bleeding; the wound does not reopen."),
 "tourniquet": ("Турникет", "Tourniquet", "Полностью перекрывает кровь в руке или ноге. Через 15 минут вредит конечности.", "Stops blood flow in an arm or leg. Harms the limb after 15 minutes."),
 "esmarch": ("Жгут Эсмарха", "Esmarch tourniquet", "То же, что турникет, но может порваться. Одноразовый.", "Like a tourniquet but may tear. Single use."),
 "splint": ("Шина", "Splint", "Фиксирует перелом: меньше боль, заживает, движение не усугубляет.", "Fixes a fracture: less pain, heals, movement does not worsen it."),
 "occlusive_dressing": ("Окклюзионная наклейка", "Occlusive dressing", "Останавливает развитие пневмоторакса.", "Stops a pneumothorax from progressing."),
 "decompression_needle": ("Игла для декомпрессии", "Decompression needle", "Снимает напряжённый пневмоторакс. Медицина 3+.", "Relieves a tension pneumothorax. Medicine 3+."),
 "painkillers": ("Обезболивающее", "Painkillers", "Слабое обезболивание на 10 минут, действует через минуту.", "Mild pain relief for 10 minutes, kicks in after a minute."),
 "morphine": ("Шприц-тюбик морфина", "Morphine autoinjector", "Сильное обезболивание на 15 минут; угнетает дыхание, второй подряд опасен.", "Strong pain relief for 15 minutes; depresses breathing, a second dose is dangerous."),
 "adrenaline": ("Адреналин", "Adrenaline", "Вместе с СЛР запускает сердце, поднимает давление. Здоровому вредит.", "With CPR restarts the heart, raises blood pressure. Harmful when healthy."),
 "txa": ("Транексамовая кислота", "Tranexamic acid", "Замедляет внутреннее и наружное кровотечение на 10 минут.", "Slows internal and external bleeding for 10 minutes."),
 "field_surgery_kit": ("Полевой хирургический набор", "Field surgery kit", "Останавливает внутреннее кровотечение. Временный предмет первого этапа.", "Stops internal bleeding. Temporary stage 1 item."),
 "saline": ("Физраствор 500 мл", "Saline 500 ml", "Возвращает объём крови. Пациент должен лежать или стоять на месте.", "Restores blood volume. The patient must lie or stand still."),
 "ammonia": ("Нашатырь", "Smelling salts", "Будит из обморока.", "Wakes from fainting."),
 "airway": ("Воздуховод", "Airway", "Держит дыхание у лежачего без сознания.", "Keeps an unconscious patient breathing."),
 "ambu_bag": ("Мешок Амбу", "Bag valve mask", "Удерживайте ПКМ: дышит за пациента при остановке дыхания.", "Hold right click: breathes for the patient."),
 "defibrillator": ("Автоматический дефибриллятор", "AED", "Снимает фибрилляцию. На остановленное сердце не действует.", "Treats fibrillation. Does not work on a stopped heart."),
 "pulse_oximeter": ("Пульсоксиметр", "Pulse oximeter", "Показывает пульс и SpO2.", "Shows pulse and SpO2."),
 "tonometer": ("Тонометр", "Blood pressure monitor", "Показывает давление.", "Shows blood pressure."),
 "medical_pouch": ("Медицинский подсумок", "Medical pouch", "Только для медицинских предметов.", "Medical items only."),
 "first_aid_kit": ("Аптечка", "First aid kit", "Только для медицинских предметов.", "Medical items only."),
 "gm_scanner": ("ГМ-сканер", "GM scanner", "Показывает всё цифрами. Только для ГМа.", "Shows everything in numbers. GM only."),
}
for k, (ru, en, dru, den) in items.items():
    t(f"item.rpmedicine.{k}", ru, en)
    t(f"item.rpmedicine.{k}.desc", dru, den)
t("itemGroup.rpmedicine", "RP Medicine", "RP Medicine")
t("entity.rpmedicine.body_stub", "Тело", "Body")

parts = {"head": ("Голова", "Head"), "chest": ("Грудь", "Chest"), "abdomen": ("Живот", "Abdomen"),
 "right_arm": ("Правая рука", "Right arm"), "left_arm": ("Левая рука", "Left arm"),
 "right_leg": ("Правая нога", "Right leg"), "left_leg": ("Левая нога", "Left leg"),
 "right_foot": ("Правая стопа", "Right foot"), "left_foot": ("Левая стопа", "Left foot")}
for k, (ru, en) in parts.items(): t(f"rpmedicine.part.{k}", ru, en)
pain_where = {"head": ("Болит голова", "Head hurts"), "chest": ("Болит грудь", "Chest hurts"), "abdomen": ("Болит живот", "Stomach hurts"),
 "right_arm": ("Болит правая рука", "Right arm hurts"), "left_arm": ("Болит левая рука", "Left arm hurts"),
 "right_leg": ("Болит правая нога", "Right leg hurts"), "left_leg": ("Болит левая нога", "Left leg hurts"),
 "right_foot": ("Болит правая стопа", "Right foot hurts"), "left_foot": ("Болит левая стопа", "Left foot hurts")}
for k, (ru, en) in pain_where.items(): t(f"rpmedicine.exam.complaint_pain_{k}", ru, en)

wounds = {"bruise": ("Ушиб", "Bruise"), "cut": ("Порез", "Cut"), "stab": ("Колотая рана", "Stab wound"),
 "gunshot": ("Огнестрельная рана", "Gunshot wound"), "shrapnel": ("Осколочная рана", "Shrapnel wound"),
 "burn": ("Ожог", "Burn"), "bite": ("Укус", "Bite")}
for k, (ru, en) in wounds.items(): t(f"rpmedicine.wound.{k}", ru, en)

E = "rpmedicine.exam."
for k, ru, en in [
 ("wound", "Рана", "Wound"), ("wound_type", "%s", "%s"), ("wound_typed_severity", "%s, %s", "%s, %s"),
 ("severity_0", "лёгкая", "minor"), ("severity_1", "средняя", "moderate"), ("severity_2", "тяжёлая", "severe"), ("severity_3", "критическая", "critical"),
 ("burn_degree", "  ожог %s степени", "  degree %s burn"),
 ("dressing_bandage", "  перевязано бинтом", "  bandaged"), ("dressing_pressure", "  давящая повязка", "  pressure dressing"),
 ("dressing_hemostatic", "  гемостатик", "  hemostatic gauze"),
 ("bleeding", "Кровит", "Bleeding"), ("bleeding_heavy", "Сильно кровит", "Bleeding heavily"),
 ("bleeding_class_1", "Слабое кровотечение", "Light bleeding"), ("bleeding_class_2", "Среднее кровотечение", "Moderate bleeding"),
 ("bleeding_class_3", "Сильное кровотечение", "Heavy bleeding"), ("bleeding_class_4", "Артериальное кровотечение!", "Arterial bleeding!"),
 ("broken", "Сломано", "Broken"), ("fracture_ribs", "Перелом рёбер", "Broken ribs"), ("chest_pain_breathing", "Боль в груди при дыхании", "Chest pain when breathing"),
 ("fracture_open", "Открытый перелом", "Open fracture"), ("fracture_closed", "Закрытый перелом", "Closed fracture"),
 ("splint", "  наложена шина", "  splinted"), ("tourniquet", "Турникет (%s мин)", "Tourniquet (%s min)"), ("esmarch", "Жгут Эсмарха (%s мин)", "Esmarch tourniquet (%s min)"),
 ("ischemia", "Конечность синеет и холодеет", "The limb is turning blue and cold"), ("occlusive", "Окклюзионная наклейка", "Occlusive dressing"),
 ("suspect_internal", "Подозрение на внутреннее кровотечение", "Suspected internal bleeding"),
 ("suspect_pneumothorax", "Подозрение на пневмоторакс", "Suspected pneumothorax"),
 ("bullet_inside", "Пуля внутри", "Bullet inside"), ("fragments_inside", "Осколки внутри", "Fragments inside"),
 ("consciousness_0", "В сознании", "Conscious"), ("consciousness_1", "Оглушён", "Dazed"), ("consciousness_2", "Без сознания", "Unconscious"), ("consciousness_3", "Не подаёт признаков жизни", "No signs of life"),
 ("skin_0", "Кожа обычного цвета", "Normal skin color"), ("skin_1", "Бледный", "Pale"), ("skin_2", "Очень бледный, холодный пот", "Very pale, cold sweat"), ("skin_3", "Синюшный", "Cyanotic"),
 ("breathing_none", "Не дышит", "Not breathing"), ("breathing_present", "Дышит", "Breathing"),
 ("breathing_rate_0", "Дыхание редкое", "Slow breathing"), ("breathing_rate_1", "Дыхание ровное", "Normal breathing"), ("breathing_rate_2", "Дыхание частое", "Rapid breathing"),
 ("pulse_none", "Пульс не прощупывается", "No palpable pulse"),
 ("pulse_0", "Пульс редкий", "Slow pulse"), ("pulse_1", "Пульс ровный", "Normal pulse"), ("pulse_2", "Пульс частый", "Rapid pulse"),
 ("pulse_0_weak", "Пульс редкий, слабый", "Slow, weak pulse"), ("pulse_1_weak", "Пульс слабый", "Weak pulse"), ("pulse_2_weak", "Пульс частый, слабый", "Rapid, weak pulse"),
 ("complaint_dizzy", "Кружится голова", "Feeling dizzy"), ("complaint_hard_to_breathe", "Трудно дышать", "Hard to breathe"),
 ("complaint_cold_fingers", "Холодеют пальцы", "Fingers are getting cold"), ("complaint_right_arm_numb", "Правая рука не слушается", "Right arm won't obey"),
 ("complaint_left_arm_numb", "Левая рука не слушается", "Left arm won't obey"), ("complaint_nausea", "Тошнит", "Nauseous"),
 ("complaint_ringing", "Звенит в ушах", "Ears are ringing"), ("complaint_heart_pounding", "Сердце колотится", "Heart is pounding"),
 ("complaint_weak", "Слабость", "Weakness"),
 ("dying", "Состояние угрожает жизни", "Life-threatening condition"), ("nothing_visible", "Ничего не видно", "Nothing visible"),
 ("hover_bleeding", "Кровит", "Bleeding"), ("hover_bleeding_heavy", "Сильно кровит!", "Bleeding heavily!"),
 ("hover_unconscious", "Без сознания", "Unconscious"), ("hover_no_signs", "Без признаков жизни", "No signs of life"),
 ("hover_tourniquet", "Наложен жгут (%s мин)", "Tourniquet on (%s min)"), ("hover_knockdown", "Осталось ~%s", "About %s left"),
 ("hover_ok", "Видимых травм нет", "No visible injuries"),
]: t(E + k, ru, en)

R = "rpmedicine.refuse."
for k, ru, en in [
 ("nothing_to_dress", "Перевязывать нечего", "Nothing to dress"), ("no_heavy_bleeding", "Нет сильного кровотечения", "No heavy bleeding"),
 ("tourniquet_limb_only", "Жгут — только на руку или ногу", "Tourniquets go on an arm or leg"), ("tourniquet_already", "Жгут уже наложен", "A tourniquet is already on"),
 ("tourniquet_not_needed", "Жгут здесь не нужен", "No need for a tourniquet"), ("splint_ribs", "Рёбра шиной не фиксируют", "Ribs can't be splinted"),
 ("splint_limb_only", "Шина — только на конечность", "Splints go on limbs"), ("no_fracture", "Перелома нет", "No fracture"),
 ("splint_already", "Шина уже наложена", "Already splinted"), ("chest_only", "Только на грудь", "Chest only"),
 ("occlusive_already", "Наклейка уже есть", "Already sealed"), ("no_chest_wound", "Нет проникающей раны груди", "No penetrating chest wound"),
 ("no_tension", "Напряжённого пневмоторакса нет", "No tension pneumothorax"), ("already_active", "Уже действует", "Already active"),
 ("no_pain", "Обезболивание не нужно", "No pain to treat"), ("no_bleeding", "Кровотечения нет", "No bleeding"),
 ("torso_only", "Только на грудь или живот", "Chest or abdomen only"), ("no_internal", "Внутреннего кровотечения нет", "No internal bleeding"),
 ("already_dripping", "Капельница уже стоит", "A drip is already running"), ("volume_ok", "Объём крови в норме", "Blood volume is fine"),
 ("saline_limit", "Больше физраствора не влить", "No more saline can be given"), ("ammonia_threat", "Нашатырь не поможет — угроза жизни", "Smelling salts won't help — life threatening"),
 ("not_unconscious", "Человек в сознании", "The person is conscious"), ("airway_already", "Воздуховод уже стоит", "Airway already in place"),
 ("breathing_ok", "Дышит сам", "Breathing on their own"), ("no_shockable", "Разряд не рекомендован", "No shock advised"),
 ("pulse_present", "Пульс есть", "Has a pulse"), ("not_needed", "Это здесь не нужно", "Not needed"),
 ("actor_down", "Вы без сознания", "You are unconscious"), ("arms_broken", "Обе руки сломаны", "Both arms are broken"),
 ("too_far", "Слишком далеко", "Too far away"), ("empty_hand", "Нужна пустая рука", "Your hand must be empty"),
 ("clinical_no_finish", "Тело в клинической смерти добить нельзя", "A body in clinical death can't be finished off"),
 ("no_weapon", "Нужно оружие в руке", "You need a weapon in hand"),
]:
    t(R + k, ru, en); t("rpmedicine.refuse." + k, ru, en)
for k, ru, en in [("actor_down", "Вы без сознания", "You are unconscious"), ("arms_broken", "Обе руки сломаны", "Both arms are broken"), ("too_far", "Слишком далеко", "Too far away")]:
    t("rpmedicine.refuse." + k, ru, en)

P = "rpmedicine.treat."
for k, ru, en in [
 ("dressing_poor", "%s: повязка наложена плохо", "%s: the dressing is poorly applied"), ("bandaged", "%s: перевязано", "%s: bandaged"),
 ("pressure_applied", "%s: давящая повязка наложена", "%s: pressure dressing applied"), ("hemostatic_applied", "%s: гемостатик наложен", "%s: hemostatic gauze applied"),
 ("esmarch_broke", "Жгут порвался", "The tourniquet tore"), ("tourniquet_loose", "%s: жгут наложен неправильно", "%s: the tourniquet is loose"),
 ("tourniquet_applied", "%s: жгут наложен", "%s: tourniquet applied"), ("splint_poor", "%s: шина держит плохо", "%s: the splint is loose"),
 ("splinted", "%s: шина наложена", "%s: splinted"), ("occlusive_leaks", "Наклейка не держит", "The seal leaks"),
 ("occlusive_applied", "Окклюзионная наклейка наложена", "Occlusive dressing applied"), ("needle_missed", "Игла прошла мимо", "The needle missed"),
 ("decompressed", "Декомпрессия: воздух вышел", "Decompressed: air released"), ("dose_partial", "Ввели не всю дозу", "Only part of the dose went in"),
 ("painkiller_taken", "Обезболивающее принято", "Painkillers taken"), ("morphine_overdose", "Вторая доза морфина!", "A second dose of morphine!"),
 ("morphine_injected", "Морфин введён", "Morphine injected"), ("adrenaline_harm", "Сердце сбилось с ритма!", "The heart lost its rhythm!"),
 ("adrenaline_injected", "Адреналин введён", "Adrenaline injected"), ("txa_injected", "Транексамовая кислота введена", "Tranexamic acid injected"),
 ("surgery_partial", "%s: кровотечение остановлено не полностью", "%s: bleeding only partly stopped"),
 ("internal_stopped", "%s: внутреннее кровотечение остановлено", "%s: internal bleeding stopped"),
 ("saline_infiltrated", "Капельница стоит плохо", "The drip is poorly placed"), ("saline_started", "Капельница поставлена", "Drip started"),
 ("no_effect", "Не подействовало", "No effect"), ("ammonia_used", "Нашатырь поднесён", "Smelling salts applied"),
 ("airway_failed", "Воздуховод не встал", "The airway didn't go in"), ("airway_placed", "Воздуховод установлен", "Airway placed"),
 ("ventilating", "Вентиляция", "Ventilating"), ("cpr", "СЛР", "CPR"), ("shock_failed", "Электроды наложены неправильно", "Pads placed wrong"),
 ("rhythm_restored", "Ритм восстановлен!", "Rhythm restored!"), ("shock_no_effect", "Разряд — без эффекта", "Shock — no effect"),
 ("oximeter", "Пульс %2$s, SpO2 %3$s%%", "Pulse %2$s, SpO2 %3$s%%"), ("tonometer", "Давление %2$s/%3$s", "Blood pressure %2$s/%3$s"),
 ("tonometer_none", "Давление не определяется", "Blood pressure not measurable"),
]: t(P + k, ru, en)

A = "rpmedicine.action."
for k, ru, en in [("interrupted", "Действие прервано", "Interrupted"), ("interrupted_damage", "Прервано: вас ранили", "Interrupted: you were hurt"),
 ("target_lost", "Прервано: пациент далеко", "Interrupted: patient out of reach"), ("item_changed", "Прервано: предмет сменился", "Interrupted: item changed"),
 ("cpr", "СЛР…", "CPR…"), ("ambu", "Вентиляция…", "Ventilating…"), ("finish", "Добивание…", "Finishing…"), ("search", "Обыск…", "Searching…")]: t(A + k, ru, en)

M = "rpmedicine.msg."
for k, ru, en in [
 ("clinical_death", "Клиническая смерть", "Clinical death"), ("dressing_reopened", "Повязка сбилась, снова кровит", "The dressing slipped, bleeding again"),
 ("fracture_worsened", "Кость хрустнула сильнее", "The bone cracked further"), ("woke_up", "Вы пришли в себя", "You came to"),
 ("admin_cooldown", "Можно позвать снова через %s с", "You can call again in %s s"),
 ("admin_call", "[RP Medicine] %s в клинической смерти зовёт администратора: %s %s %s %s (нажмите, чтобы телепортироваться)", "[RP Medicine] %s is in clinical death and calls an admin: %s %s %s %s (click to teleport)"),
 ("admin_called", "Администраторы оповещены", "Admins notified"), ("treated_by", "%s: %s", "%s: %s"),
 ("carrying", "Вы несёте тело. Присядьте, чтобы сбросить.", "You are carrying a body. Sneak to drop it."),
 ("something_cracked", "Что-то хрустнуло…", "Something cracked…"), ("blood_spurts", "Кровь бьёт струёй!", "Blood is spurting!"),
 ("head_ringing", "В голове звенит", "Your head is ringing"),
 ("removed_dressing", "%s: повязка снята", "%s: dressing removed"), ("removed_tourniquet", "%s: жгут снят", "%s: tourniquet removed"),
 ("removed_splint", "%s: шина снята", "%s: splint removed"), ("removed_occlusive", "%s: наклейка снята", "%s: seal removed"),
]: t(M + k, ru, en)

for k, ru, en in [
 ("rpmedicine.scanner.no_permission", "Сканер работает только у ГМа", "The scanner only works for GMs"),
 ("rpmedicine.report.not_patient", "%s — не пациент", "%s is not a patient"),
 ("rpmedicine.tooltip.charges", "Зарядов: %s", "Charges: %s"), ("rpmedicine.tooltip.container", "Ячеек: %s, только медицина", "%s slots, medical items only"),
 ("rpmedicine.search.title", "Обыск: %s", "Searching: %s"), ("rpmedicine.search.curios", "Снаряжение", "Equipment"),
 ("rpmedicine.cmd.not_patient", "Цель — не игрок и не тело", "Target is not a player or a body"),
 ("rpmedicine.cmd.bad_type", "Тип раны: bruise, cut, stab, gunshot, shrapnel, burn, bite", "Wound type: bruise, cut, stab, gunshot, shrapnel, burn, bite"),
 ("rpmedicine.cmd.bad_part", "Часть тела: head, chest, abdomen, right_arm, left_arm, right_leg, left_leg, right_foot, left_foot", "Body part: head, chest, abdomen, right_arm, left_arm, right_leg, left_leg, right_foot, left_foot"),
 ("rpmedicine.cmd.bad_duration", "Длительность вида 30m, 2h, 1h30m, 600s", "Duration like 30m, 2h, 1h30m, 600s"),
 ("rpmedicine.cmd.reloaded", "RP Medicine: конфиг и датапак перечитаны", "RP Medicine: config and datapack reloaded"),
 ("rpmedicine.cmd.profile", "RP Medicine: %s мкс/тик, шагов физиологии %s/тик (%s мкс на шаг), игроков %s, заглушек %s", "RP Medicine: %s µs/tick, %s physiology steps/tick (%s µs per step), players %s, bodies %s"),
 ("rpmedicine.cmd.injured", "%s: %s в %s, тяжесть %s, итог %s", "%s: %s to %s, severity %s, result %s"),
 ("rpmedicine.cmd.healed", "%s вылечен (%s)", "%s healed (%s)"), ("rpmedicine.cmd.revived", "%s поднят", "%s revived"),
 ("rpmedicine.cmd.killed", "%s убит", "%s killed"), ("rpmedicine.cmd.set", "%s: %s = %s", "%s: %s = %s"),
 ("rpmedicine.cmd.time_added", "Заживление прокручено у %s на %s мин", "Healing advanced for %s by %s min"),
 ("death.attack.rpmedicine.brain_death", "%1$s умер", "%1$s died"), ("death.attack.rpmedicine.brain_death.player", "%1$s умер от рук %2$s", "%1$s was killed by %2$s"),
 ("death.attack.rpmedicine.surrender", "%1$s сдался", "%1$s gave up"), ("death.attack.rpmedicine.surrender.player", "%1$s сдался", "%1$s gave up"),
 ("death.attack.rpmedicine.gm_kill", "%1$s умер", "%1$s died"), ("death.attack.rpmedicine.gm_kill.player", "%1$s умер", "%1$s died"),
 ("death.attack.rpmedicine.finished", "%1$s добит", "%1$s was finished off"), ("death.attack.rpmedicine.finished.player", "%1$s добит игроком %2$s", "%1$s was finished off by %2$s"),
 ("key.categories.rpmedicine", "RP Medicine", "RP Medicine"), ("key.rpmedicine.panel", "Осмотр / меню лежачего", "Examine / downed menu"),
 ("key.rpmedicine.finish", "Добить (удерживать)", "Finish off (hold)"),
 ("rpmedicine.hud.bleed_1", "Кровь: слабо", "Bleeding: light"), ("rpmedicine.hud.bleed_2", "Кровь: средне", "Bleeding: moderate"),
 ("rpmedicine.hud.bleed_3", "Кровь: сильно", "Bleeding: heavy"), ("rpmedicine.hud.bleed_4", "Кровь: артерия!", "Bleeding: arterial!"),
 ("rpmedicine.hud.pain_1", "Боль", "Pain"), ("rpmedicine.hud.pain_2", "Сильная боль", "Severe pain"), ("rpmedicine.hud.pain_3", "Невыносимая боль", "Unbearable pain"),
 ("rpmedicine.hud.fracture", "Перелом", "Fracture"), ("rpmedicine.hud.tourniquet", "Жгут: %s, %s мин", "Tourniquet: %s, %s min"),
 ("rpmedicine.hud.analgesia", "Обезболен", "Painkillers"), ("rpmedicine.hud.dyspnea", "Одышка", "Short of breath"),
 ("rpmedicine.hud.progress_preview", "Прогресс", "Progress"), ("rpmedicine.hud.knockdown", "Нокдаун: %s", "Knocked down: %s"),
 ("rpmedicine.hud.unconscious", "Без сознания", "Unconscious"), ("rpmedicine.hud.downed_hint", "[%s] — меню", "[%s] — menu"),
 ("rpmedicine.hud.editor.title", "Редактор HUD RP Medicine", "RP Medicine HUD editor"),
 ("rpmedicine.hud.editor.hint", "Тащите мышью, колесо — размер, ПКМ — скрыть/показать", "Drag to move, wheel to resize, right click to hide/show"),
 ("rpmedicine.hud.editor.reset", "Сбросить", "Reset"), ("rpmedicine.hud.editor.done", "Готово", "Done"),
 ("rpmedicine.hud.editor.select", "Выберите элемент", "Select an element"), ("rpmedicine.hud.editor.anchor", "Якорь: %s", "Anchor: %s"),
 ("rpmedicine.hud.element.silhouette", "Силуэт", "Silhouette"), ("rpmedicine.hud.element.status", "Значки", "Status"),
 ("rpmedicine.hud.element.progress", "Прогресс", "Progress"), ("rpmedicine.hud.element.knockdown_timer", "Нокдаун", "Knockdown"),
 ("rpmedicine.hud.element.hover", "Осмотр при наведении", "Hover info"),
 ("rpmedicine.panel.title", "Осмотр", "Examination"), ("rpmedicine.panel.self", "Осмотр себя", "Examining yourself"),
 ("rpmedicine.panel.other", "Осмотр: %s", "Examining: %s"), ("rpmedicine.panel.loading", "…", "…"),
 ("rpmedicine.panel.pick_part", "Нажмите на часть тела", "Click a body part"), ("rpmedicine.panel.general", "Общее", "General"),
 ("rpmedicine.panel.drag_hint", "Перетащите предмет на часть тела", "Drag an item onto a body part"),
 ("rpmedicine.panel.remove_dressing", "Снять повязку", "Remove dressing"), ("rpmedicine.panel.remove_tourniquet", "Снять жгут", "Remove tourniquet"),
 ("rpmedicine.panel.remove_splint", "Снять шину", "Remove splint"), ("rpmedicine.panel.remove_occlusive", "Снять наклейку", "Remove seal"),
 ("rpmedicine.panel.search", "Обыскать", "Search"),
 ("rpmedicine.downed.title", "Вы лежите без сознания", "You are lying unconscious"), ("rpmedicine.downed.surrender", "Сдаться", "Give up"),
 ("rpmedicine.downed.surrender_confirm", "Сдаться?", "Give up?"), ("rpmedicine.downed.surrender_detail", "Это настоящая смерть персонажа.", "This is the character's real death."),
 ("rpmedicine.clinical.title", "Клиническая смерть", "Clinical death"),
 ("rpmedicine.clinical.hint", "Тело ещё можно спасти. Ждите медиков.", "The body can still be saved. Wait for medics."),
 ("rpmedicine.clinical.call_admin", "Позвать администратора", "Call an admin"),
]: t(k, ru, en)

sounds = {"heartbeat": ("Стук сердца", "Heartbeat"), "heavy_breathing": ("Тяжёлое дыхание", "Heavy breathing"), "ear_ringing": ("Звон в ушах", "Ears ringing"),
 "bandage": ("Перевязка", "Bandaging"), "injection": ("Укол", "Injection"), "tourniquet": ("Жгут", "Tourniquet"),
 "defib_shock": ("Разряд", "Defibrillator shock"), "bone_break": ("Хруст кости", "Bone cracks"), "pills": ("Таблетки", "Pills")}
for k, (ru, en) in sounds.items(): t(f"subtitles.rpmedicine.{k}", ru, en)

# ---------------------------------------------------------------- второй этап «Госпиталь»
# Госпиталь: койка, монитор
for k, ru, en in [
 ("rpmedicine.bed.lie_down", "Вы легли на койку. Встать — присесть.", "You lie down on the bed. Sneak to get up."),
 ("rpmedicine.bed.stood_up", "Вы встали с койки.", "You got up from the bed."),
 ("rpmedicine.bed.occupied", "Койка занята.", "The bed is occupied."),
 ("rpmedicine.bed.placed", "Вы положили %s на койку.", "You placed %s on the bed."),
 ("rpmedicine.hud.element.monitor", "Монитор", "Monitor"),
 ("rpmedicine.monitor.title", "Монитор", "Monitor"),
 ("rpmedicine.monitor.empty", "Нет пациента", "No patient"),
 ("rpmedicine.monitor.locked", "Не разобрать показания", "You can't make sense of the readings"),
 ("rpmedicine.monitor.hr", "Пульс %s", "HR %s"),
 ("rpmedicine.monitor.bp", "АД %s/%s", "BP %s/%s"),
 ("rpmedicine.monitor.spo2", "SpO₂ %s%%", "SpO₂ %s%%"),
 ("rpmedicine.monitor.rr", "ЧДД %s", "RR %s"),
 ("rpmedicine.monitor.rhythm_0", "Ритм: синусовый", "Rhythm: sinus"),
 ("rpmedicine.monitor.rhythm_1", "Ритм: ФИБРИЛЛЯЦИЯ", "Rhythm: FIBRILLATION"),
 ("rpmedicine.monitor.rhythm_2", "Ритм: АСИСТОЛИЯ", "Rhythm: ASYSTOLE"),
 ("subtitles.rpmedicine.monitor_alarm", "Тревога монитора", "Monitor alarm"),
]: t(k, ru, en)

# Кровь, инфекция, температура
items2 = {
 "empty_blood_bag": ("Пустой пакет для крови", "Empty blood bag", "Забор 450 мл крови у донора. Донор стоит на месте 30 с. Медицина 3+.", "Collects 450 ml of blood from a donor. The donor stands still for 30 s. Medicine 3+."),
 "blood_bag": ("Пакет крови", "Blood bag", "Переливание: настоящая кровь, несёт кислород. Проверяйте группу! Вне холодильника портится.", "Transfusion: real blood that carries oxygen. Check the blood type! Spoils outside a fridge."),
}
for k, (ru, en, dru, den) in items2.items():
    t(f"item.rpmedicine.{k}", ru, en)
    t(f"item.rpmedicine.{k}.desc", dru, den)
for k, ru, en in [
 ("rpmedicine.tooltip.blood_type", "Группа: %s", "Blood type: %s"),
 ("rpmedicine.tooltip.blood_fresh", "Свежая", "Fresh"),
 ("rpmedicine.tooltip.blood_spoiled", "Испорчена!", "Spoiled!"),
 ("rpmedicine.refuse.donor_low", "Донору нельзя: он сам потерял кровь", "The donor has lost blood already"),
 ("rpmedicine.action.target_moved", "Прервано: пациент двинулся", "Interrupted: the patient moved"),
 ("rpmedicine.treat.transfusion_started", "Переливание началось", "Transfusion started"),
 ("rpmedicine.treat.transfusion_infiltrated", "Игла не в вене: половина пакета ушла под кожу", "Missed the vein: half the bag went under the skin"),
 ("rpmedicine.treat.collect_failed", "Не удалось попасть в вену", "Couldn't find the vein"),
 ("rpmedicine.treat.blood_collected", "Кровь взята", "Blood collected"),
 ("rpmedicine.msg.bag_filled", "Пакет крови: группа %s", "Blood bag: type %s"),
 ("rpmedicine.msg.drip_stopped", "Капельница остановлена", "Drip stopped"),
 ("rpmedicine.panel.stop_drip", "Снять капельницу", "Stop the drip"),
 ("rpmedicine.exam.wound_inflamed", "  рана воспалена: покраснение, отёк", "  the wound is inflamed: red and swollen"),
 ("rpmedicine.exam.wound_pus", "  рана гноится", "  the wound is festering"),
 ("rpmedicine.exam.drip_blood", "Капельница: кровь", "Drip: blood"),
 ("rpmedicine.exam.drip_saline", "Капельница: физраствор", "Drip: saline"),
 ("rpmedicine.exam.feels_hot", "На ощупь горячий — жар", "Hot to the touch — fever"),
 ("rpmedicine.exam.feels_cold", "На ощупь холодный", "Cold to the touch"),
 ("rpmedicine.exam.complaint_fever", "Знобит, жарко", "Feverish chills"),
 ("rpmedicine.exam.complaint_back_pain", "Ломит поясницу и грудь", "Aching lower back and chest"),
]: t(k, ru, en)

base = os.path.join(os.path.dirname(__file__), "..", "src/main/resources/assets/rpmedicine/lang")
for idx, name in ((0, "ru_ru"), (1, "en_us")):
    with open(os.path.join(base, name + ".json"), "w", encoding="utf-8") as f:
        json.dump({k: v[idx] for k, v in sorted(T.items())}, f, ensure_ascii=False, indent=2)
        f.write("\n")
print(len(T), "ключей")
