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
 ("carrying", "Вы несёте тело. Положить — Shift+ПКМ по блоку или по койке.", "You are carrying a body. Shift+right-click a block or a bed to put it down."),
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
 ("rpmedicine.cmd.food_added", "Голод и жажда прокручены у %s на %s мин", "Hunger and thirst advanced for %s by %s min"),
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
items2.update({
 "paracetamol": ("Парацетамол", "Paracetamol", "Таблетки: слабое обезболивание, снимает жар. Не больше 4 в сутки.", "Pills: mild pain relief, lowers fever. No more than 4 a day."),
 "ibuprofen": ("Ибупрофен", "Ibuprofen", "Таблетки: обезболивание, снимает жар, контузия проходит быстрее. Не больше 3 в сутки.", "Pills: pain relief, lowers fever, concussion passes faster. No more than 3 a day."),
 "ketorolac": ("Кеторолак", "Ketorolac", "Укол: среднее обезболивание на 20 минут. Медицина 3+.", "Injection: moderate pain relief for 20 minutes. Medicine 3+."),
 "tramadol": ("Трамадол", "Tramadol", "Таблетки: среднее обезболивание на 40 минут, слегка угнетает дыхание. Опиат. Медицина 4+.", "Pills: moderate pain relief for 40 minutes, slightly depresses breathing. Opioid. Medicine 4+."),
 "naloxone": ("Налоксон", "Naloxone", "Укол: сразу снимает действие опиатов — и обезболивание, и угнетение дыхания. Медицина 4+.", "Injection: instantly reverses opioids — both pain relief and breathing depression. Medicine 4+."),
 "amoxicillin": ("Амоксициллин", "Amoxicillin", "Таблетки: антибиотик на 8 часов в сети, раз в 8 часов. Медицина 4+.", "Pills: antibiotic for 8 hours online, once every 8 hours. Medicine 4+."),
 "ceftriaxone": ("Цефтриаксон", "Ceftriaxone", "Укол: сильный антибиотик на 12 часов. Медицина 5+.", "Injection: strong antibiotic for 12 hours. Medicine 5+."),
 "diazepam": ("Диазепам", "Diazepam", "Укол: снижает болевой шок и пульс, сонливость. С опиатами угнетает дыхание. Медицина 5+.", "Injection: eases pain shock and heart rate, drowsiness. With opioids depresses breathing. Medicine 5+."),
 "norepinephrine": ("Норадреналин", "Norepinephrine", "Капельница: поднимает давление на 20 минут. Пациент на месте. Медицина 6+.", "Drip: raises blood pressure for 20 minutes. The patient stays still. Medicine 6+."),
 "atropine": ("Атропин", "Atropine", "Укол: учащает редкий пульс. Медицина 5+.", "Injection: speeds up a slow heart rate. Medicine 5+."),
 "antiseptic": ("Антисептик", "Antiseptic", "Спрей на рану: шанс заражения ниже на 70 %.", "Spray on a wound: 70% lower chance of infection."),
 "antibiotic_ointment": ("Мазь с антибиотиком", "Antibiotic ointment", "Лечит заражение неглубокой раны и ожога. Медицина 2+.", "Treats infection of a shallow wound or a burn. Medicine 2+."),
})
items2.update({
 "stethoscope": ("Стетоскоп", "Stethoscope", "Дыхание и сердце словами: хрипы, ослабленное дыхание, неровный ритм. Медицина 2+.", "Breathing and heart in words: crackles, weak breathing, irregular rhythm. Medicine 2+."),
 "thermometer": ("Термометр", "Thermometer", "Температура тела.", "Body temperature."),
 "portable_scanner": ("Портативный сканер", "Portable scanner", "На часть тела: внутреннее кровотечение, пули и осколки, перелом, вывих. Медицина 4+.", "On a body part: internal bleeding, bullets and fragments, fracture, dislocation. Medicine 4+."),
 "hemoanalyzer": ("Гемоанализатор", "Hemoanalyzer", "Капля крови (нужен ланцет): группа, гемоглобин, признаки инфекции и сепсиса. Медицина 4+.", "A drop of blood (needs a lancet): blood type, hemoglobin, signs of infection and sepsis. Medicine 4+."),
 "lancet": ("Ланцет", "Lancet", "Капля крови для гемоанализатора.", "A drop of blood for the hemoanalyzer."),
 "blood_draw_syringe": ("Шприц для забора крови", "Blood draw syringe", "Кровь пациента в пустую пробирку (нужна в инвентаре) — для лаборатории. Медицина 2+.", "Draws blood into an empty test tube (needed in the inventory) for the lab. Medicine 2+."),
 "blood_sample": ("Пробирка крови", "Blood sample", "На лабораторном столе — полный анализ за минуту.", "Full analysis at a lab table in a minute."),
})
items2.update({
 "surgical_tweezers": ("Хирургический пинцет", "Surgical tweezers", "Извлечь пулю или осколок. Без обезболивания — болевой шок. После работы нестерилен — в стерилизатор. Медицина 4+.", "Removes a bullet or fragment. Without pain relief — pain shock. Not sterile after use — use a sterilizer. Medicine 4+."),
 "suture_kit": ("Набор для швов", "Suture kit", "Зашить рану: кровотечение останавливается, заживает вдвое быстрее. Сначала извлеките пули. Медицина 3+.", "Sutures a wound: bleeding stops, heals twice as fast. Remove bullets first. Medicine 3+."),
 "scissors": ("Ножницы", "Scissors", "Снять швы.", "Remove sutures."),
})
items2.update({
 "medcard": ("Медкарта", "Medical record", "Пустая: ПКМ по игроку — завести его карту, ПКМ в воздух — свою. Привязанная: ПКМ — открыть.", "Blank: right click a player to bind it to them, right click the air for your own. Bound: right click to open."),
})
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
 ("rpmedicine.exam.complaint_thirsty", "Сухо во рту, хочется пить", "Dry mouth, thirsty"),
 ("rpmedicine.exam.complaint_freezing", "Трясёт от холода", "Shivering with cold"),
 ("rpmedicine.exam.complaint_deaf", "Ничего не слышно, звенит в ушах", "Can't hear a thing, ears ringing"),
 ("rpmedicine.msg.vomit", "Тебя вырвало", "You threw up"),
 ("subtitles.rpmedicine.vomit", "Рвота", "Vomiting"),
 ("rpmedicine.refuse.must_be_conscious", "Таблетку можно дать только тому, кто в сознании", "Pills only for a conscious patient"),
 ("rpmedicine.refuse.no_opioids", "Опиатов в крови нет", "No opioids in the system"),
 ("rpmedicine.refuse.no_open_wound", "Здесь нет открытой раны", "No open wound here"),
 ("rpmedicine.refuse.no_infection_here", "Здесь нет заражённой раны", "No infected wound here"),
 ("rpmedicine.refuse.infection_too_deep", "Рана слишком глубокая для мази", "The wound is too deep for ointment"),
 ("rpmedicine.treat.pill_taken", "Таблетка принята", "Pill taken"),
 ("rpmedicine.treat.drug_injected", "Укол сделан", "Injection given"),
 ("rpmedicine.treat.drug_drip_started", "Капельница с препаратом поставлена", "Drug drip started"),
 ("rpmedicine.treat.drug_applied", "%s: обработано", "%s: treated"),
 ("rpmedicine.treat.drug_overdose", "Передозировка!", "Overdose!"),
 ("rpmedicine.treat.antidote_given", "Действие опиатов снято", "Opioids reversed"),
 ("rpmedicine.treat.antiseptic_applied", "%s: рана обработана антисептиком", "%s: wound disinfected"),
 ("rpmedicine.treat.antiseptic_poor", "%s: обработано наспех", "%s: hastily disinfected"),
 ("rpmedicine.treat.ointment_applied", "%s: мазь наложена", "%s: ointment applied"),
 ("rpmedicine.treat.ointment_partial", "%s: мазь наложена плохо", "%s: ointment poorly applied"),
 # диагностика
 ("rpmedicine.refuse.need_lancet", "Нужен ланцет", "You need a lancet"),
 ("rpmedicine.treat.unclear", "Не разобрать, что показывает прибор", "You can't make sense of the reading"),
 ("rpmedicine.treat.stethoscope", "Стетоскоп:", "Stethoscope:"),
 ("rpmedicine.treat.thermometer", "Термометр: %2$s °C", "Thermometer: %2$s °C"),
 ("rpmedicine.treat.scanner", "Сканер, %1$s:", "Scanner, %1$s:"),
 ("rpmedicine.treat.hemoanalyzer", "Гемоанализатор:", "Hemoanalyzer:"),
 ("rpmedicine.treat.sample_taken", "Пробирка набрана", "Sample taken"),
 ("rpmedicine.tooltip.sample_of", "Пациент: %s", "Patient: %s"),
 ("rpmedicine.action.lab", "Анализ…", "Analysing…"),
 ("rpmedicine.action.lab_left", "Прервано: вы отошли от стола", "Interrupted: you left the lab table"),
 ("rpmedicine.lab.failed", "Анализ не удался: вы не разобрались в приборах", "The analysis failed: you couldn't work the equipment"),
 ("rpmedicine.lab.title", "Анализ крови: %s", "Blood test: %s"),
 ("rpmedicine.lab.blood_type", "  Группа крови: %s", "  Blood type: %s"),
 ("rpmedicine.lab.hemoglobin", "  Гемоглобин: %s г/л (норма 120–160)", "  Hemoglobin: %s g/L (normal 120–160)"),
 ("rpmedicine.lab.leukocytes", "  Лейкоциты: %s ×10⁹/л (норма 4–9)", "  White blood cells: %s ×10⁹/L (normal 4–9)"),
 ("rpmedicine.lab.sepsis_yes", "  Признаки сепсиса: есть", "  Signs of sepsis: yes"),
 ("rpmedicine.lab.sepsis_no", "  Признаки сепсиса: нет", "  Signs of sepsis: no"),
 ("rpmedicine.lab.compat_yes", "  Пакет %s: совместим", "  Bag %s: compatible"),
 ("rpmedicine.lab.compat_no", "  Пакет %s: НЕСОВМЕСТИМ", "  Bag %s: INCOMPATIBLE"),
 ("rpmedicine.lab.compat_unknown", "  Совместимость с пакетом: группа не известна", "  Bag compatibility: unknown blood type"),
]: t(k, ru, en)

# Вывихи, пинцет, швы
for k, ru, en in [
 ("rpmedicine.refuse.no_dislocation", "Вывиха нет", "No dislocation"),
 ("rpmedicine.refuse.no_foreign_body", "Здесь нет пуль и осколков", "No bullets or fragments here"),
 ("rpmedicine.refuse.nothing_to_suture", "Нечего зашивать", "Nothing to suture"),
 ("rpmedicine.refuse.foreign_body_first", "Сначала извлеките пулю или осколок", "Remove the bullet or fragment first"),
 ("rpmedicine.refuse.no_sutures", "Швов нет", "No sutures"),
 ("rpmedicine.treat.reduced", "%s: вывих вправлен", "%s: dislocation reduced"),
 ("rpmedicine.treat.reduction_failed", "%s: вправить не удалось", "%s: failed to reduce"),
 ("rpmedicine.treat.reduction_fracture", "%s: хруст — кость сломана!", "%s: a crack — the bone broke!"),
 ("rpmedicine.treat.bullet_removed", "%s: пуля извлечена", "%s: bullet removed"),
 ("rpmedicine.treat.fragment_removed", "%s: осколок извлечён", "%s: fragment removed"),
 ("rpmedicine.treat.extraction_failed", "%s: задели стенку канала, кровит", "%s: you nicked the wound channel, bleeding"),
 ("rpmedicine.treat.sutured", "%s: рана зашита", "%s: wound sutured"),
 ("rpmedicine.treat.suture_weak", "%s: шов слабый", "%s: weak suture"),
 ("rpmedicine.treat.sutures_removed", "%s: швы сняты", "%s: sutures removed"),
 ("rpmedicine.action.reduce", "Вправление…", "Reducing…"),
 ("rpmedicine.panel.reduce", "Вправить", "Reduce"),
 ("rpmedicine.tooltip.sterile", "Стерилен", "Sterile"),
 ("rpmedicine.tooltip.not_sterile", "Не стерилен", "Not sterile"),
 ("rpmedicine.msg.sterilized", "Инструмент стерилизован", "Instrument sterilized"),
 ("rpmedicine.exam.sutured", "  зашито", "  sutured"),
 ("rpmedicine.exam.suture_weak", "  шов слабый", "  weak suture"),
 ("rpmedicine.exam.joint_deformed", "Сустав деформирован", "The joint is deformed"),
 ("rpmedicine.exam.dislocation_arm", "Вывих плеча или локтя", "Dislocated shoulder or elbow"),
 ("rpmedicine.exam.dislocation_leg", "Вывих колена", "Dislocated knee"),
 ("rpmedicine.exam.dislocation_foot", "Вывих голеностопа", "Dislocated ankle"),
]: t(k, ru, en)

# Медкарта
for k, ru, en in [
 ("rpmedicine.medcard.title", "Медкарта", "Medical record"), ("rpmedicine.medcard.title_of", "Медкарта: %s", "Medical record: %s"),
 ("rpmedicine.medcard.vitals", "Рост %s см   Вес %s кг   Группа крови %s", "Height %s cm   Weight %s kg   Blood type %s"),
 ("rpmedicine.medcard.allergies", "Аллергии:", "Allergies:"), ("rpmedicine.medcard.chronic", "Хронические:", "Chronic:"),
 ("rpmedicine.medcard.entries", "Записи", "Entries"), ("rpmedicine.medcard.no_entries", "Записей нет", "No entries"),
 ("rpmedicine.medcard.save", "Сохранить", "Save"), ("rpmedicine.medcard.add", "Добавить запись", "Add entry"),
 ("rpmedicine.medcard.new_entry", "Новая запись", "New entry"), ("rpmedicine.medcard.editing", "Правка записи:", "Editing entry:"),
 ("rpmedicine.medcard.edit", "Изменить", "Edit"), ("rpmedicine.medcard.accept", "Принять", "Accept"), ("rpmedicine.medcard.decline", "Отклонить", "Decline"),
 ("rpmedicine.medcard.proposed", " (предложено)", " (suggested)"),
 ("rpmedicine.medcard.blank", "Пустая карта", "Blank record"), ("rpmedicine.medcard.of", "Пациент: %s", "Patient: %s"),
 ("rpmedicine.medcard.bound", "Медкарта: %s", "Medical record: %s"),
 ("rpmedicine.medcard.yes", "есть", "yes"), ("rpmedicine.medcard.no", "нет", "no"),
 ("rpmedicine.medcard.entry.fracture", "Перелом: %s", "Fracture: %s"),
 ("rpmedicine.medcard.entry.open_fracture", "Открытый перелом: %s", "Open fracture: %s"),
 ("rpmedicine.medcard.entry.rib_fracture", "Перелом рёбер", "Rib fracture"),
 ("rpmedicine.medcard.entry.arterial", "Артериальное кровотечение: %s", "Arterial bleeding: %s"),
 ("rpmedicine.medcard.entry.pneumothorax", "Пневмоторакс", "Pneumothorax"),
 ("rpmedicine.medcard.entry.internal", "Внутреннее кровотечение: %s", "Internal bleeding: %s"),
 ("rpmedicine.medcard.entry.dislocation", "Вывих: %s", "Dislocation: %s"),
 ("rpmedicine.medcard.entry.gunshot", "Огнестрельное ранение: %s", "Gunshot wound: %s"),
 ("rpmedicine.medcard.entry.shrapnel", "Осколочное ранение: %s", "Shrapnel wound: %s"),
 ("rpmedicine.medcard.entry.clinical_death", "Клиническая смерть", "Clinical death"),
 ("rpmedicine.medcard.entry.transfusion", "Переливание крови, пакет %s", "Blood transfusion, bag %s"),
 ("rpmedicine.medcard.entry.bullet_removed", "Извлечена пуля: %s", "Bullet removed: %s"),
 ("rpmedicine.medcard.entry.fragment_removed", "Извлечён осколок: %s", "Fragment removed: %s"),
 ("rpmedicine.medcard.entry.lab", "Анализ крови: группа %s, гемоглобин %s г/л, лейкоциты %s, сепсис: %s", "Blood test: type %s, hemoglobin %s g/L, WBC %s, sepsis: %s"),
 ("rpmedicine.cmd.card_set", "Медкарта %s: %s = %s", "Medical record %s: %s = %s"),
 ("rpmedicine.cmd.card_bad", "Медкарта: неверное значение %s (%s)", "Medical record: bad value for %s (%s)"),
]: t(k, ru, en)

# ГМ: статистика, история, панель, skill
for k, ru, en in [
 ("rpmedicine.gm.title", "Панель ГМа — RP Medicine", "GM panel — RP Medicine"),
 ("rpmedicine.gm.empty", "Никого", "Nobody"), ("rpmedicine.gm.pick", "Выберите игрока или тело слева", "Pick a player or body on the left"),
 ("rpmedicine.gm.not_loaded", "Тело в незагруженном чанке — телепортируйтесь к нему", "The body is in an unloaded chunk — teleport to it"),
 ("rpmedicine.gm.heal", "Вылечить", "Heal"), ("rpmedicine.gm.revive", "Поднять", "Revive"), ("rpmedicine.gm.kill", "Убить", "Kill"),
 ("rpmedicine.gm.teleport", "К нему", "Go to"), ("rpmedicine.gm.history", "История", "History"), ("rpmedicine.gm.inspect", "Осмотр", "Inspect"),
 ("rpmedicine.cmd.skill_attribute", "%s: «Медицина» %s (атрибут RP Perks), сейчас %s", "%s: Medicine %s (RP Perks attribute), now %s"),
 ("rpmedicine.cmd.skill_own", "%s: «Медицина» %s (свой уровень мода), сейчас %s", "%s: Medicine %s (mod's own level), now %s"),
 ("rpmedicine.stats.title", "Статистика %s за %s ч", "Stats for %s over %s h"),
 ("rpmedicine.stats.treat_given", "  Лечил: %s раз, ошибок %s", "  Treated others: %s times, %s errors"),
 ("rpmedicine.stats.treat_received", "  Лечили его: %s раз, ошибок %s", "  Was treated: %s times, %s errors"),
 ("rpmedicine.stats.injuries", "  Травм: %s", "  Injuries: %s"),
 ("rpmedicine.stats.outcomes", "  Клинических смертей %s, вытащили %s, смертей %s", "  Clinical deaths %s, rescued %s, deaths %s"),
 ("rpmedicine.stats.history_title", "История %s, последние %s мин (столбец — минута)", "History of %s, last %s min (one column per minute)"),
 ("rpmedicine.stats.no_history", "Истории нет", "No history"),
 ("rpmedicine.stats.metric_blood", "Кровь %  ", "Blood %  "), ("rpmedicine.stats.metric_bp", "Давление ", "BP       "),
 ("rpmedicine.stats.metric_hr", "Пульс    ", "HR       "), ("rpmedicine.stats.metric_spo2", "SpO₂     ", "SpO₂     "),
 ("rpmedicine.stats.metric_consciousness", "Сознание ", "Conscious"), ("rpmedicine.stats.metric_brain", "Мозг     ", "Brain    "),
 ("rpmedicine.stats.metric_temp", "Темп.    ", "Temp     "),
]: t(k, ru, en)

# Мини-игры
for k, ru, en in [
 ("rpmedicine.minigame.injection", "укол", "injection"), ("rpmedicine.minigame.vein", "в вену", "into the vein"),
 ("rpmedicine.minigame.bandage", "перевязка", "bandaging"), ("rpmedicine.minigame.tourniquet", "жгут", "tourniquet"),
 ("rpmedicine.minigame.tweezers", "пинцет", "tweezers"), ("rpmedicine.minigame.suture", "швы", "sutures"),
 ("rpmedicine.minigame.reduce", "вправление", "reduction"),
 ("rpmedicine.minigame.hint_injection", "Нажмите, когда метка в зелёном окне (клик или пробел)", "Click when the marker is in the green window (or Space)"),
 ("rpmedicine.minigame.hint_vein", "Попадите в вену дважды", "Hit the vein twice"),
 ("rpmedicine.minigame.hint_reduce", "Резкий рывок — точно в окне", "A sharp jerk — right in the window"),
 ("rpmedicine.minigame.hint_bandage", "Зажмите кнопку мыши и ровно ведите три круга по зелёной линии", "Hold the mouse button and draw three even circles along the green line"),
 ("rpmedicine.minigame.hint_tourniquet", "Держите кнопку — затягивать, отпустите — ослабить. 3 с в зелёной зоне", "Hold to tighten, release to loosen. 3 s in the green zone"),
 ("rpmedicine.minigame.hint_tweezers", "От зелёной точки проведите пинцет по каналу, не задевая стенок", "From the green dot, guide the tweezers along the channel without touching the walls"),
 ("rpmedicine.minigame.hint_suture", "Кликайте по жёлтым точкам вдоль раны", "Click the yellow points along the wound"),
 ("rpmedicine.minigame.refuse", "Без мини-игры (дольше)", "Skip (takes longer)"),
 ("rpmedicine.minigame.hits", "Попаданий: %s / %s", "Hits: %s / %s"),
 ("rpmedicine.minigame.turns", "Кругов: %s / 3", "Turns: %s / 3"),
 ("rpmedicine.minigame.hold", "%s / 3 с", "%s / 3 s"),
 ("rpmedicine.minigame.touches", "Касаний стенок: %s", "Wall touches: %s"),
 ("rpmedicine.action.minigame", "Лечение…", "Treating…"),
 ("rpmedicine.action.minigame_timeout", "Слишком долго — бросили", "Took too long — gave up"),
]: t(k, ru, en)

# Слова показаний приборов: rpmedicine.word.<k>
W = "rpmedicine.word."
for k, ru, en in [
 ("breath_none", "дыхания нет", "no breath sounds"), ("breath_one_side_weak", "дыхание с одной стороны ослаблено", "breath sounds weak on one side"),
 ("breath_crackles", "хрипы", "crackles"), ("breath_shallow", "дыхание поверхностное, редкое", "shallow, slow breathing"),
 ("breath_fast", "дыхание частое", "rapid breathing"), ("breath_normal", "дыхание чистое", "clear breath sounds"),
 ("heart_none", "тоны сердца не слышны", "no heart sounds"), ("heart_irregular", "ритм беспорядочный, шумы", "chaotic rhythm, murmurs"),
 ("heart_fast", "тоны частые", "rapid heartbeat"), ("heart_slow", "тоны редкие", "slow heartbeat"), ("heart_normal", "тоны ритмичные", "regular heartbeat"),
 ("scan_internal_bleeding", "внутреннее кровотечение", "internal bleeding"), ("scan_no_internal", "кровотечения внутри нет", "no internal bleeding"),
 ("scan_no_foreign", "инородных тел нет", "no foreign bodies"),
 ("scan_rib_fracture", "перелом рёбер", "rib fracture"), ("scan_fracture_open", "открытый перелом", "open fracture"),
 ("scan_fracture_closed", "закрытый перелом", "closed fracture"), ("scan_no_fracture", "кости целы", "bones intact"),
 ("scan_dislocation", "вывих", "dislocation"),
 ("hb_normal", "гемоглобин в норме", "hemoglobin normal"), ("hb_low", "гемоглобин понижен", "hemoglobin low"), ("hb_very_low", "гемоглобин очень низкий", "hemoglobin very low"),
 ("infection_none", "признаков инфекции нет", "no signs of infection"), ("infection_local", "воспаление", "inflammation"),
 ("infection_systemic", "признаки сепсиса", "signs of sepsis"), ("blood_unknown", "группа не определяется", "blood type unclear"),
]: t(W + k, ru, en)
for n in range(1, 6):
    t(W + f"scan_bullets_{n}", f"пуль: {n}" + ("+" if n == 5 else ""), f"bullets: {n}" + ("+" if n == 5 else ""))
    t(W + f"scan_fragments_{n}", f"осколков: {n}" + ("+" if n == 5 else ""), f"fragments: {n}" + ("+" if n == 5 else ""))
for bt, label in [("o-", "O−"), ("o+", "O+"), ("a-", "A−"), ("a+", "A+"), ("b-", "B−"), ("b+", "B+"), ("ab-", "AB−"), ("ab+", "AB+")]:
    t(W + f"blood_{bt}", f"группа {label}", f"type {label}")

# ---------------------------------------------------------------- третий этап
# Органы (п. 2): названия, сканер, осмотр, жалобы, лаборатория.
organs = {"heart": ("сердце", "heart", "сердца", "heart"), "lungs": ("лёгкие", "lungs", "лёгких", "lungs"),
          "liver": ("печень", "liver", "печени", "liver"), "kidneys": ("почки", "kidneys", "почек", "kidneys"),
          "intestines": ("кишечник", "intestines", "кишечника", "intestines")}
for k, (ru, en, gen_ru, gen_en) in organs.items():
    t(f"rpmedicine.organ.{k}", ru, en)
    t(W + f"scan_{k}_bruise", f"ушиб {gen_ru}", f"{gen_en} contusion")
    t(W + f"scan_{k}_damage", f"повреждение {gen_ru}", f"{gen_en} damage")
    t(W + f"scan_{k}_severe", f"тяжёлое повреждение {gen_ru}", f"severe {gen_en} damage")
    t(W + f"scan_{k}_missing", f"{gen_ru} нет" if k not in ("lungs", "kidneys") else f"{ru} отсутствуют",
      f"no {en}" if k in ("lungs", "kidneys", "intestines") else f"no {en}")
t(W + "scan_organs_ok", "органы без повреждений", "organs intact")
t("rpmedicine.medcard.entry.organ", "Травма органа: %s", "Organ injury: %s")
t(W + "heart_uneven", "тоны неровные", "uneven heartbeat")
for k, ru, en in [
 ("jaundice", "Кожа и глаза желтоватые", "Yellowish skin and eyes"),
 ("edema", "Отёки на ногах", "Swollen legs"),
 ("abdomen_tense", "Живот напряжён, болезненный", "Abdomen tense and tender"),
 ("complaint_cough", "Кашель", "Coughing"),
 ("complaint_side_pain", "Тянет в правом боку", "Dull ache in the right side"),
]: t("rpmedicine.exam." + k, ru, en)
# Анестезия (п. 3).
items3 = {
 "lidocaine": ("Лидокаин", "Lidocaine", "Местная анестезия части тела, куда сделан укол, на 20 минут: боль почти не чувствуется, можно оперировать. Медицина 4+.",
               "Local anesthesia of the injected body part for 20 minutes: pain is barely felt, surgery is possible. Medicine 4+."),
 "ketamine": ("Кетамин", "Ketamine", "Полевой наркоз на 5 минут: без сознания, дышит сам, давление выше. Медицина 7+.",
              "Field anesthesia for 5 minutes: unconscious, breathes on their own, raises blood pressure. Medicine 7+."),
 "propofol": ("Пропофол", "Propofol", "Общий наркоз на 10 минут. Угнетает дыхание — нужна интубация и стол или мешок Амбу. Медицина 7+.",
              "General anesthesia for 10 minutes. Depresses breathing — needs intubation and a table, or a bag valve mask. Medicine 7+."),
 "laryngoscope": ("Ларингоскоп", "Laryngoscope", "Нужен в инвентаре для интубации.", "Needed in the inventory for intubation."),
 "endotracheal_tube": ("Интубационная трубка", "Endotracheal tube", "Интубация человека без сознания (нужен ларингоскоп): дыхательные пути открыты, на операционном столе дышит аппарат. Медицина 6+.",
                       "Intubates an unconscious person (needs a laryngoscope): airway secured, an operating table breathes for them. Medicine 6+."),
}
for k, (ru, en, dru, den) in items3.items():
    t(f"item.rpmedicine.{k}", ru, en)
    t(f"item.rpmedicine.{k}.desc", dru, den)
for k, ru, en in [("intubated_already", "Трубка уже стоит", "Already intubated"),
                  ("under_anesthesia", "Под наркозом — не разбудить", "Under anesthesia — can't be woken"),
                  ("need_laryngoscope", "Нужен ларингоскоп в инвентаре", "You need a laryngoscope in your inventory")]:
    t(R + k, ru, en)
for k, ru, en in [("intubated", "Интубация выполнена", "Intubated"),
                  ("intubation_failed", "Трубка ушла в пищевод — вынули, трубка испорчена", "The tube went into the esophagus — pulled out, tube wasted")]:
    t(P + k, ru, en)
t("rpmedicine.exam.anesthesia", "Под наркозом", "Under anesthesia")
t("rpmedicine.exam.intubated", "Интубирован", "Intubated")
t("rpmedicine.exam.local_anesthesia", "Местная анестезия", "Local anesthesia")
t("rpmedicine.action.intubate", "Интубация", "Intubation")
t("rpmedicine.lab.alt", "  АЛТ (печень): %s ед/л (норма до 40)", "  ALT (liver): %s U/L (normal under 40)")
t("rpmedicine.lab.creatinine", "  Креатинин (почки): %s мкмоль/л (норма 60–110)", "  Creatinine (kidneys): %s µmol/L (normal 60–110)")
t("rpmedicine.lab.troponin", "  Тропонин (сердце): %s нг/л (норма до 14)", "  Troponin (heart): %s ng/L (normal under 14)")

# --- Пачка правок 05.10.2026 ---
t("rpmedicine.msg.arms_disabled", "Обе руки сломаны — не ударить", "Both arms are broken — you can't strike")
t("rpmedicine.msg.put_down", "Положили", "Put down")
E = "rpmedicine.exam."
for k, ru, en in [
    ("bleeding_kind", "%s %s кровотечение", "%s %s bleeding"),
    ("bleed_strength_1", "Слабое", "Light"), ("bleed_strength_2", "Среднее", "Moderate"),
    ("bleed_strength_3", "Сильное", "Heavy"), ("bleed_strength_4", "Обильное", "Massive"),
    ("bleed_kind_0", "капиллярное", "capillary"), ("bleed_kind_1", "венозное", "venous"), ("bleed_kind_2", "артериальное", "arterial"),
    ("bleeding_rate", "  ≈ %s мл/мин", "  ≈ %s ml/min"),
    ("dressing_seeping", "  под повязкой: %s", "  under the dressing: %s"),
    ("dressing_soaking", "  повязка промокает", "  the dressing is soaking through"),
    ("dressing_bandage_q", "  перевязано бинтом — %s", "  bandaged — %s"),
    ("dressing_pressure_q", "  давящая повязка — %s", "  pressure dressing — %s"),
    ("dressing_hemostatic_q", "  гемостатик — %s", "  hemostatic gauze — %s"),
    ("dressing_quality_1", "наложено так себе", "so-so"), ("dressing_quality_2", "наложено плохо", "poorly applied")]:
    t(E + k, ru, en)
t("rpmedicine.refuse.force_hint", " — ещё раз, чтобы применить всё равно", " — again to apply anyway")
for k, ru, en in [
    ("forced_needle", "%s: игла в здоровую грудь — пневмоторакс", "%s: needle into a healthy chest — pneumothorax"),
    ("forced_defib", "%s: разряд впустую, ожог кожи", "%s: wasted shock, skin burn"),
    ("forced_defib_fibrillation", "%s: разряд по бьющемуся сердцу — фибрилляция!", "%s: shock on a beating heart — fibrillation!"),
    ("forced_surgery", "%s: полезли внутрь без нужды — кровотечение", "%s: you cut in without need — bleeding"),
    ("forced_wasted", "%s: потрачено без пользы", "%s: wasted")]:
    t("rpmedicine.treat." + k, ru, en)
t("rpmedicine.refuse.no_shockable", "Разряд не рекомендован: ритм не для дефибриллятора. При остановке сердца — СЛР и адреналин", "No shock advised: non-shockable rhythm. For cardiac arrest — CPR and adrenaline")
for k, ru, en in [("heartbeat_fast", "Частое сердцебиение", "Racing heartbeat"), ("gasp", "Судорожный вдох", "Gasping"),
    ("cough", "Кашель", "Coughing"), ("pain_groan", "Стон от боли", "Groan of pain"), ("pain_moan", "Стон", "Moaning"),
    ("splint", "Накладывают шину", "Splint applied"), ("eardrum_burst", "Звон в ушах", "Eardrum ringing"),
    ("flatline", "Писк монитора", "Flatline"), ("heart_stopping", "Сердце останавливается", "Heart stopping"),
    ("scanner", "Сканер", "Scanner"), ("ammonia", "Нашатырь", "Smelling salts"), ("wake_up", "Приходит в себя", "Coming to"),
    ("surgery_cut", "Разрез", "Incision"), ("surgery_stitch", "Шов", "Stitching"), ("surgery_clamp", "Зажим", "Clamp"),
    ("surgery_retract", "Ретрактор", "Retractor"), ("surgery_suction", "Отсос", "Suction"), ("surgery_bleed", "Кровь", "Bleeding"),
    ("surgery_bone_set", "Кость встала на место", "Bone set"), ("surgery_vessel_cut", "Сосуд пересечён", "Vessel cut"),
    ("surgery_cautery", "Коагуляция", "Cautery"), ("surgery_error", "Ошибка хирурга", "Surgical error"),
    ("surgery_trachea", "Интубация", "Intubation"), ("bone_saw", "Пила", "Bone saw"), ("bone_drill", "Дрель", "Bone drill"),
    ("organ_move", "Орган", "Organ"), ("minigame_ok", "Готово", "Done"), ("minigame_slip", "Сорвалось", "Slipped"),
    ("body_fall", "Падение тела", "Body falls")]:
    t("subtitles.rpmedicine." + k, ru, en)
t("rpmedicine.msg.golden_apple", "Прилив сил, боль притупилась", "A rush of energy, the pain dulls")
t("item.rpmedicine.stabilization_kit", "Набор стабилизации", "Stabilization kit")
t("item.rpmedicine.stabilization_kit.desc", "Кислород и противошоковый пакет: лежачий дольше держится до прихода медика. Один раз за нокдаун.", "Oxygen and an anti-shock pack: a downed patient holds on longer until a medic arrives. Once per knockdown.")
t("rpmedicine.refuse.not_knocked_down", "Только для лежачего в нокдауне", "Only for a knocked-down patient")
t("rpmedicine.refuse.stabilization_used", "Набор уже применён в этом нокдауне", "Already used in this knockdown")
t("rpmedicine.treat.stabilized", "Состояние стабилизировано — время до гибели идёт медленнее", "Stabilized — time runs out slower")
t("rpmedicine.treat.stabilization_partial", "Стабилизация вышла неполной", "Stabilization only partly worked")
t("rpmedicine.action.stabilize", "Стабилизация", "Stabilization")
t("item.rpmedicine.test_tube", "Пустая пробирка", "Empty test tube")
t("item.rpmedicine.test_tube.desc", "Для забора крови шприцем: станет пробиркой крови.", "For drawing blood with a syringe: becomes a blood sample.")
t("rpmedicine.refuse.need_test_tube", "Нужна пустая пробирка", "You need an empty test tube")
for k, ru, en in [("self", "на себя", "on yourself"), ("on", "→ %s", "→ %s"), ("by", "Лечит: %s", "Treated by: %s")]:
    t("rpmedicine.progress." + k, ru, en)
t("rpmedicine.panel.remove_tube", "Извлечь трубку", "Extubate")
t("rpmedicine.panel.remove_airway", "Убрать воздуховод", "Remove airway")
t("rpmedicine.panel.overall", "Общее состояние", "Overall")
t("rpmedicine.msg.extubated", "Трубка извлечена", "Extubated")
t("rpmedicine.msg.airway_removed", "Воздуховод убран", "Airway removed")
WHAT = {'adrenaline': ('Ампула адреналина со шприцем.', 'Adrenaline ampoule with a syringe.'), 'airway': ('Пластиковая трубка в рот или нос.', 'A plastic tube for the mouth or nose.'), 'ambu_bag': ('Дыхательный мешок с маской.', 'A breathing bag with a mask.'), 'ammonia': ('Ватка с нашатырным спиртом.', 'Cotton soaked in smelling salts.'), 'amoxicillin': ('Антибиотик в таблетках.', 'Antibiotic tablets.'), 'antibiotic_ointment': ('Тюбик мази с антибиотиком.', 'A tube of antibiotic ointment.'), 'antiseptic': ('Флакон антисептика.', 'A bottle of antiseptic.'), 'atropine': ('Ампула атропина.', 'Atropine ampoule.'), 'bandage': ('Стерильный бинт.', 'A sterile bandage roll.'), 'blood_bag': ('Пакет донорской крови.', 'A bag of donor blood.'), 'blood_draw_syringe': ('Шприц для забора крови.', 'A syringe for drawing blood.'), 'blood_sample': ('Пробирка с кровью.', 'A tube of blood.'), 'ceftriaxone': ('Флакон антибиотика для укола.', 'An injectable antibiotic vial.'), 'decompression_needle': ('Длинная толстая игла с катетером.', 'A long thick needle with a catheter.'), 'defibrillator': ('Автоматический наружный дефибриллятор.', 'Automated external defibrillator.'), 'diazepam': ('Ампула успокоительного.', 'Sedative ampoule.'), 'empty_blood_bag': ('Пустой пакет для крови.', 'An empty blood bag.'), 'endotracheal_tube': ('Трубка для интубации.', 'An intubation tube.'), 'esmarch': ('Резиновый жгут.', 'A rubber tourniquet.'), 'field_surgery_kit': ('Набор полевого хирурга.', "A field surgeon's kit."), 'first_aid_kit': ('Сумка для медикаментов.', 'A bag for medical supplies.'), 'gm_scanner': ('Инструмент ведущего.', "A game master's tool."), 'hemoanalyzer': ('Карманный анализатор крови.', 'A pocket blood analyzer.'), 'hemostatic_gauze': ('Марля с кровоостанавливающим средством.', 'Gauze with a clotting agent.'), 'ibuprofen': ('Таблетки ибупрофена.', 'Ibuprofen tablets.'), 'ketamine': ('Ампула кетамина.', 'Ketamine ampoule.'), 'ketorolac': ('Ампула кеторолака.', 'Ketorolac ampoule.'), 'lancet': ('Одноразовый ланцет.', 'A disposable lancet.'), 'laryngoscope': ('Ларингоскоп с клинком.', 'A laryngoscope with a blade.'), 'lidocaine': ('Ампула лидокаина.', 'Lidocaine ampoule.'), 'medcard': ('Медицинская карта.', 'A medical record.'), 'medical_pouch': ('Подсумок для медикаментов.', 'A pouch for medical supplies.'), 'morphine': ('Шприц-тюбик с морфином.', 'A morphine autoinjector.'), 'naloxone': ('Ампула налоксона.', 'Naloxone ampoule.'), 'norepinephrine': ('Флакон норадреналина для капельницы.', 'A norepinephrine vial for a drip.'), 'occlusive_dressing': ('Герметичная наклейка на рану груди.', 'An airtight chest seal.'), 'painkillers': ('Блистер обезболивающих таблеток.', 'A blister of painkiller tablets.'), 'paracetamol': ('Таблетки парацетамола.', 'Paracetamol tablets.'), 'portable_scanner': ('Портативный медицинский сканер.', 'A portable medical scanner.'), 'pressure_dressing': ('Индивидуальный перевязочный пакет.', 'A pressure dressing pack.'), 'propofol': ('Флакон пропофола.', 'Propofol vial.'), 'pulse_oximeter': ('Прищепка на палец.', 'A finger clip.'), 'saline': ('Пакет физраствора с системой.', 'A saline bag with a line.'), 'scissors': ('Медицинские ножницы.', 'Medical scissors.'), 'splint': ('Шина для конечности.', 'A limb splint.'), 'stabilization_kit': ('Кислородный баллончик и противошоковый пакет.', 'An oxygen can and an anti-shock pack.'), 'stethoscope': ('Стетоскоп.', 'A stethoscope.'), 'surgical_tweezers': ('Хирургический пинцет.', 'Surgical tweezers.'), 'suture_kit': ('Игла с хирургической нитью.', 'A needle with surgical thread.'), 'test_tube': ('Пустая пробирка.', 'An empty test tube.'), 'thermometer': ('Медицинский термометр.', 'A medical thermometer.'), 'tonometer': ('Тонометр с манжетой.', 'A blood pressure cuff.'), 'tourniquet': ('Турникет CAT.', 'A CAT tourniquet.'), 'tramadol': ('Таблетки трамадола.', 'Tramadol tablets.'), 'txa': ('Ампула транексамовой кислоты.', 'Tranexamic acid ampoule.')}
for k, (ru, en) in WHAT.items():
    t(f"item.rpmedicine.{k}.what", ru, en)
for k, ru, en in [
    ("tooltip.level", "Медицина: %s+", "Medicine: %s+"),
    ("tooltip.anyone", "Может применить любой", "Anyone can use it"),
    ("tooltip.shift", "Shift — подробнее", "Shift — details"),
    ("tooltip.no_skill", "Как это действует — не разбираетесь (медицина 2+)", "You don't know how it works (medicine 2+)"),
    ("tooltip.analog", "Аналог: %s", "Same as: %s"),
    ("tooltip.time", "Время применения: %s с", "Time to apply: %s s"),
    ("tooltip.effect", "• %s %s, %s", "• %s %s, %s"),
    ("tooltip.after", "через %s", "after %s"),
    ("tooltip.instant", "сразу", "at once"),
    ("tooltip.for", "на %s", "for %s"),
    ("tooltip.dose_limit", "Не больше %s доз за %s ч", "No more than %s doses per %s h"),
    ("tooltip.overdose", "Передозировка:", "Overdose:"),
    ("tooltip.arrest", "• шанс остановки дыхания %s%%", "• %s%% chance of respiratory arrest"),
    ("tooltip.opioid", "Опиат: угнетает дыхание, снимается налоксоном", "Opioid: depresses breathing, reversed by naloxone"),
    ("tooltip.form_pill", "Таблетки — только в сознании", "Tablets — conscious patients only"),
    ("tooltip.form_injection", "Укол", "Injection"), ("tooltip.form_drip", "Капельница — пациент на месте", "Drip — the patient stays still"),
    ("tooltip.form_topical", "Наружно, на часть тела", "Topical, on a body part")]:
    t("rpmedicine." + k, ru, en)
for k, ru, en in [("analgesia", "Обезболивание", "Pain relief"), ("antipyretic", "Жаропонижающее", "Fever reduction"),
    ("antibiotic", "Антибиотик", "Antibiotic"), ("sedation", "Седация", "Sedation"), ("pressure", "Давление", "Blood pressure"),
    ("heart_rate", "Пульс", "Heart rate"), ("resp_depression", "Угнетение дыхания", "Respiratory depression"),
    ("coagulation", "Свёртывание", "Clotting"), ("concussion_relief", "Снятие контузии", "Concussion relief"),
    ("liver_toxicity", "Яд для печени", "Liver toxicity"), ("anesthesia", "Наркоз", "Anesthesia"),
    ("local_anesthesia", "Местная анестезия", "Local anesthesia")]:
    t("rpmedicine.effect." + k, ru, en)
t("rpmedicine.settings.title", "RP Medicine — настройки", "RP Medicine — settings")
for k, ru, en in [("hide_vanilla_health", "Скрыть сердца", "Hide hearts"), ("pain_vignette", "Виньетка от боли", "Pain vignette"),
    ("pain_blur", "Размытие от боли", "Pain blur"), ("low_pressure_darken", "Потемнение (давление)", "Low pressure darkening"),
    ("blood_loss_tunnel", "Сужение обзора", "Tunnel vision"), ("dazed_gray", "Серость при оглушении", "Dazed greyness"),
    ("muffled_sound", "Глухой звук", "Muffled sound"), ("concussion_ringing", "Звон в ушах", "Ear ringing"),
    ("heartbeat", "Стук сердца", "Heartbeat"), ("heavy_breathing", "Тяжёлое дыхание", "Heavy breathing"),
    ("aim_sway", "Дрожь прицела", "Aim sway"), ("look_up_when_downed", "Лёжа — взгляд в небо", "Look up when downed"),
    ("sensation_messages", "Ощущения текстом", "Sensation messages"),
    ("effect_strength", "Сила эффектов: %s", "Effect strength: %s"), ("heartbeat_volume", "Громкость сердца: %s", "Heartbeat volume: %s"),
    ("breathing_volume", "Громкость дыхания: %s", "Breathing volume: %s"), ("ringing_volume", "Громкость звона: %s", "Ringing volume: %s"),
    ("hud_editor", "Редактор HUD…", "HUD editor…")]:
    t("rpmedicine.settings." + k, ru, en)
t("entity.rpmedicine.vomit", "Рвота", "Vomit")
for k, ru, en in [
    ("injection", "Инъекция", "Injection"), ("vein", "Венепункция", "Venipuncture"), ("bandage", "Перевязка", "Bandaging"),
    ("tourniquet", "Наложение жгута", "Tourniquet"), ("tweezers", "Извлечение пули", "Bullet extraction"),
    ("suture", "Наложение швов", "Suturing"), ("reduce", "Вправление вывиха", "Joint reduction"),
    ("hint_injection", "Наведите иглу на место укола и нажмите. Потом держите ЛКМ и давите на поршень ровно — в плывущей зелёной зоне",
     "Aim the needle at the injection site and click. Then hold LMB and press the plunger evenly — inside the moving green zone"),
    ("hint_vein", "Зажмите ЛКМ у зелёной метки и ведите иглу по вене до конца, не выходя за стенки. Потом держите неподвижно",
     "Hold LMB at the green mark and guide the needle along the vein without touching the walls. Then hold still"),
    ("hint_bandage", "Зажмите ЛКМ и ведите по кругу в указанную сторону — направление меняется. Не спешите и не сходите с линии",
     "Hold LMB and circle in the shown direction — it changes. Don't rush and stay on the line"),
    ("hint_tourniquet", "ЛКМ — затянуть, отпустите — ослабить. Держите стрелку в плывущей зелёной зоне, не перетягивайте",
     "LMB tightens, release loosens. Keep the marker in the moving green zone, don't overtighten"),
    ("hint_tweezers", "Зажмите ЛКМ у зелёной метки, доведите пинцет до пули и вытащите её обратно по каналу, не задевая стенок",
     "Hold LMB at the green mark, reach the bullet and pull it back out along the channel without touching the walls"),
    ("hint_suture", "Зажмите ЛКМ на жёлтой точке, протяните нить к парной точке и отпустите. Не уводите иглу в сторону",
     "Hold LMB on the yellow point, pull the thread to its pair and release. Don't drift sideways"),
    ("hint_reduce", "Держите ЛКМ: вытяжение в зелёной зоне. Затем рывок (ЛКМ или пробел), когда метка в окне",
     "Hold LMB: traction in the green zone. Then jerk (LMB or Space) when the marker is in the window"),
    ("inj_aim", "Наведите иглу на место укола", "Aim at the injection site"), ("inj_too_fast", "Слишком резко — больно!", "Too fast — it hurts!"),
    ("injected", "Введено", "Injected"), ("vein_lost", "Игла вышла из вены", "The needle left the vein"),
    ("hold_still", "Держите неподвижно", "Hold still"), ("dir_cw", "По часовой ⟳", "Clockwise ⟳"), ("dir_ccw", "⟲ Против часовой", "⟲ Counter-clockwise"),
    ("wrapped", "Намотано", "Wrapped"), ("wrong_direction", "Не в ту сторону!", "Wrong direction!"),
    ("too_fast", "Слишком быстро — соскальзывает", "Too fast — it slips"), ("off_line", "Сошли с линии", "Off the line"),
    ("too_tight", "Перетянули!", "Too tight!"), ("held", "Удержано", "Held"), ("wall", "Задели стенку!", "Hit the wall!"),
    ("skin_torn", "Порвали кожу!", "Tore the skin!"), ("missed", "Мимо!", "Missed!"), ("too_hard", "Слишком сильно!", "Too hard!"),
    ("traction", "Вытяжение", "Traction"), ("jerk", "Рывок — когда метка в зелёном!", "Jerk — when the marker is in the green!"),
    ("dropped", "Отпустили инструмент — сначала", "Dropped the instrument — start over"), ("errors", "Ошибок: %s", "Errors: %s")]:
    t("rpmedicine.minigame." + k, ru, en)
for k, ru, en in [("cpr", "СЛР: пробел в такт", "CPR: Space on the beat"), ("ambu", "Амбу: сжать пробелом до зелёного", "Bag: squeeze with Space to green"),
    ("exhale", "выдох…", "exhale…"), ("great", "Отлично", "Great"), ("good", "Хорошо", "Good"), ("miss", "Мимо", "Miss"),
    ("space", "держите ПКМ, пробел — компрессия", "hold RMB, Space — compression")]:
    t("rpmedicine.rhythm." + k, ru, en)
t("item.rpmedicine.syringe", "Шприц", "Syringe")
t("item.rpmedicine.syringe.what", "Пустой одноразовый шприц.", "An empty disposable syringe.")
t("item.rpmedicine.syringe.desc", "С ампулой препарата медик сам выбирает дозу: половина, стандартная, полторы, двойная. Тратится на каждый укол. Медицина 4+.", "With a drug ampoule a medic chooses the dose: half, standard, one and a half, double. Used up per injection. Medicine 4+.")
t("rpmedicine.refuse.need_syringe", "Нужен шприц", "You need a syringe")
t("rpmedicine.dose.title", "Доза", "Dose")
t("rpmedicine.dose.weight", "Вес пациента ≈ %s кг (стандартная доза — на 70 кг)", "Patient weighs ≈ %s kg (a standard dose is for 70 kg)")
for i, (ru, en) in enumerate([("½ дозы", "½ dose"), ("1 доза", "1 dose"), ("1½ дозы", "1½ doses"), ("2 дозы", "2 doses")]):
    t(f"rpmedicine.dose.option_{i}", ru, en)
for k, ru, en in [
    ("entry.drug", "Введено: %s, доза %s", "Given: %s, dose %s"), ("entry.intubation", "Интубация трахеи", "Tracheal intubation"),
    ("page_title", "МЕДИЦИНСКАЯ КАРТА", "MEDICAL RECORD"), ("page_history", "История травм и лечения", "Injuries and treatment"),
    ("full_name", "Ф. И. О.:", "Full name:"), ("age", "Возраст:", "Age:"), ("gender", "Пол:", "Sex:"),
    ("gender.m", "муж.", "male"), ("gender.f", "жен.", "female"), ("gender.none", "—", "—"),
    ("height", "Рост, см:", "Height, cm:"), ("weight", "Вес, кг:", "Weight, kg:"), ("blood", "Группа крови:", "Blood type:"),
    ("created", "Карта заведена %s", "Record opened %s"), ("no_photo", "нет фото", "no photo"),
    ("select_hint", "Щёлкните запись: принять, отклонить, изменить", "Click an entry to accept, decline or edit it"),
    ("gm_only", "Рост, вес и группу меняет ГМ", "Height, weight and blood type are set by the GM")]:
    t("rpmedicine.medcard." + k, ru, en)
for k, ru, en in [
    ("drug_given", "Введено %s, доза %s: %s", "Given %s, dose %s: %s"), ("body_pick", "Щёлкните часть тела", "Click a body part"),
    ("injure_btn", "Нанести", "Inflict"), ("heal_part_btn", "Лечить часть", "Heal part"), ("drug_btn", "Ввести", "Give"),
    ("severity", "Тяжесть %s", "Severity %s"), ("dose", "Доза %s", "Dose %s"), ("no_drugs", "нет препаратов", "no drugs")]:
    t("rpmedicine.gm." + k, ru, en)
for k, ru, en in [
    ("bed.restrained", "Вы зафиксированы — встать не получится", "You are restrained and cannot get up"),
    ("refuse.not_on_restraint_table", "Зафиксировать можно только на столе с фиксацией", "Can only restrain on a restraint table"),
    ("msg.restrained", "Пациент зафиксирован", "Patient restrained"), ("msg.released", "Пациент освобождён", "Patient released"),
    ("msg.you_restrained", "Вас зафиксировали", "You have been restrained"), ("msg.you_released", "Вас освободили", "You have been released"),
    ("refuse.actor_restrained", "Вы зафиксированы", "You are restrained"),
    ("panel.restrain", "Зафиксировать", "Restrain"), ("panel.release", "Освободить", "Release")]:
    t("rpmedicine." + k, ru, en)
# Пошаговая хирургия (третий этап, п. 4).
for k, ru, en, dru, den in [
    ("scalpel", "Скальпель", "Scalpel", "Вскрыть часть тела. Пациент без сознания, под местной анестезией этой части или зафиксирован. Без обезболивания — сильная боль. Медицина 7+.",
     "Opens a body part. The patient must be unconscious, under local anesthesia of that part, or restrained. Without pain relief — severe pain. Medicine 7+."),
    ("hemostat", "Зажим", "Hemostat", "Зажать сосуды вскрытой части: кровотечение почти прекращается. Медицина 7+.",
     "Clamps the vessels of an opened part: bleeding almost stops. Medicine 7+."),
    ("retractor", "Ретрактор", "Retractor", "Раскрыть зажатую рану — дальше специальные шаги: швы на кровотечение и органы, сосудистый шов, остеосинтез, дренаж, пинцет. Медицина 7+.",
     "Opens a clamped wound for special steps: sutures for bleeding and organs, vascular suture, osteosynthesis, chest drain, tweezers. Medicine 7+."),
    ("vascular_suture", "Сосудистый шов", "Vascular suture", "Сшить артерию на раскрытой части: артериальное кровотечение снято, жгут можно снимать. Медицина 8+.",
     "Repairs an artery on an opened part: arterial bleeding stops, the tourniquet can come off. Medicine 8+."),
    ("surgical_drill", "Хирургическая дрель", "Surgical drill", "Остеосинтез на раскрытой части (нужен набор для остеосинтеза): перелом заживает втрое быстрее, без шины. Медицина 8+.",
     "Osteosynthesis on an opened part (needs an osteosynthesis kit): the fracture heals three times faster, no splint needed. Medicine 8+."),
    ("osteosynthesis_kit", "Набор для остеосинтеза", "Osteosynthesis kit", "Пластины и винты; тратится дрелью.", "Plates and screws; used up by the drill."),
    ("chest_drain", "Дренаж груди", "Chest drain", "На раскрытой груди: пневмоторакс снимается окончательно. Медицина 7+.",
     "On an opened chest: removes a pneumothorax for good. Medicine 7+."),
    ("surgical_mask", "Хирургическая маска", "Surgical mask", "Надеть (ПКМ): меньше заражение при операции.", "Wear it (right click): less infection during surgery."),
    ("surgical_gloves", "Хирургические перчатки", "Surgical gloves", "В инвентаре хирурга: меньше заражение при операции.", "In the surgeon's inventory: less infection during surgery."),
]:
    t(f"item.rpmedicine.{k}", ru, en)
    t(f"item.rpmedicine.{k}.desc", dru, den)
for k, ru, en in [("scalpel", "Хирургический скальпель.", "A surgical scalpel."), ("hemostat", "Кровоостанавливающий зажим.", "A hemostatic clamp."),
    ("retractor", "Ранорасширитель.", "A wound retractor."), ("vascular_suture", "Тонкая нить для сосудов.", "A fine thread for vessels."),
    ("surgical_drill", "Аккумуляторная хирургическая дрель.", "A cordless surgical drill."), ("osteosynthesis_kit", "Пластины и винты.", "Plates and screws."),
    ("chest_drain", "Трубка для дренажа грудной полости.", "A chest drainage tube."), ("surgical_mask", "Одноразовая маска.", "A disposable mask."),
    ("surgical_gloves", "Стерильные перчатки.", "Sterile gloves.")]:
    t(f"item.rpmedicine.{k}.what", ru, en)
for k, ru, en in [
    ("incised", "%s: вскрыто", "%s: opened"), ("clamped", "%s: сосуды зажаты", "%s: vessels clamped"), ("retracted", "%s: рана раскрыта", "%s: wound retracted"),
    ("artery_repaired", "%s: артерия сшита", "%s: artery repaired"), ("bone_fixated", "%s: перелом зафиксирован", "%s: fracture fixed"),
    ("chest_drained", "Дренаж стоит: воздух и кровь отходят", "Drain placed: air and blood are draining"),
    ("organ_repaired", "%s: орган восстановлен", "%s: organ repaired"), ("surgery_closed", "%s: операция закончена, зашито", "%s: surgery done, closed"),
    ("surgery_closed_weak", "%s: зашито наспех", "%s: closed hastily"), ("foreign_all_removed", "%s: извлечено всё", "%s: everything removed"),
    ("surgery_slip", "%s: рука дрогнула — задеты ткани", "%s: the hand slipped — tissue damaged")]:
    t("rpmedicine.treat." + k, ru, en)
for k, ru, en in [
    ("already_open", "Уже вскрыто", "Already opened"), ("patient_moves", "Пациент в сознании дёргается — нужен наркоз, местная анестезия или фиксация", "The conscious patient flinches — needs anesthesia, local anesthesia or restraint"),
    ("not_open", "Сначала вскрыть", "Open it first"), ("already_clamped", "Уже зажато", "Already clamped"), ("clamp_first", "Сначала зажать", "Clamp first"),
    ("already_retracted", "Уже раскрыто", "Already retracted"), ("not_retracted", "Сначала раскрыть ретрактором", "Retract it first"),
    ("no_arterial", "Артерия цела", "The artery is intact"), ("already_fixated", "Уже зафиксировано", "Already fixed"),
    ("chest_only", "Только на грудь", "Chest only"), ("no_pneumothorax", "Пневмоторакса нет", "No pneumothorax"),
    ("need_osteosynthesis_kit", "Нужен набор для остеосинтеза", "You need an osteosynthesis kit")]:
    t("rpmedicine.refuse." + k, ru, en)
for k, ru, en in [
    ("surgery_open", "Вскрыто, не зажато (%s мин)", "Opened, not clamped (%s min)"), ("surgery_clamped", "Вскрыто, зажато (%s мин)", "Opened, clamped (%s min)"),
    ("surgery_retracted", "Раскрыто ретрактором (%s мин)", "Retracted (%s min)"), ("fixated", "  остеосинтез", "  internal fixation")]:
    t("rpmedicine.exam." + k, ru, en)
for k, ru, en in [
    ("incised", "Операция: вскрыто — %s", "Surgery: opened — %s"), ("surgery_closed", "Операция закончена, зашито — %s", "Surgery finished, closed — %s"),
    ("surgery_closed_weak", "Операция закончена, зашито наспех — %s", "Surgery finished, closed hastily — %s"),
    ("internal_stopped", "Операция: остановлено внутреннее кровотечение — %s", "Surgery: internal bleeding stopped — %s"),
    ("organ_repaired", "Операция: восстановлен орган — %s", "Surgery: organ repaired — %s"),
    ("artery_repaired", "Операция: сшита артерия — %s", "Surgery: artery repaired — %s"),
    ("bone_fixated", "Операция: остеосинтез — %s", "Surgery: osteosynthesis — %s"),
    ("chest_drained", "Операция: дренаж груди", "Surgery: chest drain"),
    ("foreign_all_removed", "Операция: извлечены пули и осколки — %s", "Surgery: bullets and fragments removed — %s")]:
    t("rpmedicine.medcard.entry.surgery_" + k, ru, en)
t("rpmedicine.settings.leave_body", "Оставлять тело при выходе", "Leave body on logout")
# Медкарта по форме «Медицинская карта пациента» (решения, п. 1.14).
for k, ru, en in [
    ("form_title", "МЕДИЦИНСКАЯ КАРТА ПАЦИЕНТА", "PATIENT MEDICAL RECORD"),
    ("registration", "Регистрационные данные", "Registration"), ("number", "Медицинская карта №", "Medical record No."),
    ("opened", "Дата открытия", "Opened"), ("status", "Статус карты", "Status"),
    ("status_active", "активна", "active"), ("status_closed", "закрыта", "closed"),
    ("patient_info", "Сведения о пациенте", "Patient information"),
    ("full_name", "ФИО", "Full name"), ("callsign", "Позывной", "Callsign"), ("service_date", "Вступил на службу", "Joined service"),
    ("birth_date", "Дата рождения", "Date of birth"), ("gender", "Пол", "Sex"), ("department", "Отдел", "Department"),
    ("gender.m", "Мужской", "Male"), ("gender.f", "Женский", "Female"), ("gender.none", "—", "—"),
    ("blood", "Группа крови:", "Blood type:"), ("height", "Рост, см:", "Height, cm:"), ("weight", "Вес, кг:", "Weight, kg:"),
    ("dept.health", "Здравоохранения", "Health"), ("dept.security", "Управления безопасностью", "Security"),
    ("dept.research", "Научно-исследовательский", "Research"), ("dept.engineering", "Инженерно-технический", "Engineering"),
    ("dept.admin", "Административный", "Administration"),
    ("medical_info", "Медицинские сведения", "Medical information"),
    ("allergies", "Аллергические реакции", "Allergies"), ("chronic", "Хронические заболевания", "Chronic diseases"),
    ("medications", "Постоянный приём препаратов", "Regular medication"),
    ("implants", "Металлоконструкции, импланты, инородные тела", "Metal implants, implants, foreign bodies"),
    ("disability", "Инвалидность", "Disability"),
    ("marks", "Особые отметки", "Special notes"), ("mark_psych", "Психиатрическое наблюдение", "Psychiatric observation"),
    ("mark_incapacity", "Ограничение дееспособности", "Limited legal capacity"), ("mark_high_risk", "Повышенный риск для жизни", "Elevated risk to life"),
    ("comment", "Комментарий", "Comment"),
    ("confidential", "Документ конфиденциален", "Confidential document"),
    ("history", "Анамнез", "Medical history"), ("col_date", "Дата", "Date"), ("col_diag", "Травма, диагноз", "Injury, diagnosis"),
    ("col_circ", "Обстоятельства", "Circumstances"), ("col_cons", "Последствия", "Consequences"),
    ("new_entry", "Новая запись:", "New entry:"), ("editing", "Правка:", "Editing:"),
    ("no_photo", "нет фото", "no photo"), ("gm_only", "Рост, вес и группу меняет ГМ", "Height, weight and blood type are set by the GM")]:
    t("rpmedicine.medcard." + k, ru, en)
# Мини-игры операции.
for k, ru, en, hru, hen in [
    ("incision", "Разрез", "Incision", "Зажмите у зелёной метки и ведите скальпель по разметке ровно и не спеша.", "Press at the green mark and draw the scalpel along the line, steady and slow."),
    ("clamp", "Зажимы", "Clamps", "Щёлкайте по точкам кровотечения, когда кровь не брызжет.", "Click the bleeding points between spurts."),
    ("retract", "Ретракторы", "Retractors", "Захватите край раны и медленно отведите до зелёной метки — сначала один, потом другой.", "Grab a wound edge and slowly pull it to the green mark — one, then the other."),
    ("close", "Закрытие раны", "Closing", "Стежки: зажмите у жёлтой точки и протяните нить к парной, не уводя иглу.", "Stitches: press at the yellow dot and pull the thread to its pair without straying."),
    ("bleed_suture", "Источник кровотечения", "Bleeding source", "Удерживайте иглу на источнике, пока он не ушит: он смещается с каждым ударом сердца.", "Keep the needle on the source until it is sutured: it moves with each heartbeat."),
    ("organ_suture", "Шов органа", "Organ repair", "Мелкие стежки поперёк разрыва органа.", "Small stitches across the organ tear."),
    ("vessel", "Сосудистый шов", "Vascular suture", "Ведите нить от одного конца артерии к другому, не выходя из канала.", "Lead the thread from one end of the artery to the other without leaving the channel."),
    ("drill", "Остеосинтез", "Osteosynthesis", "Наведите дрель на отверстие пластины и сверлите, держа усилие в зелёной зоне. Четыре винта.", "Aim the drill at a plate hole and drill keeping the force in the green zone. Four screws."),
    ("drain", "Дренаж", "Chest drain", "Попадите в межрёберный промежуток и вводите трубку с ровным усилием.", "Hit the gap between the ribs and push the tube with even force."),
    ("extract", "Инородные тела", "Foreign bodies", "Пинцетом перенесите каждую пулю и осколок на лоток.", "Move every bullet and fragment to the tray with the tweezers.")]:
    t("rpmedicine.minigame." + k, ru, en)
    t("rpmedicine.minigame.hint_" + k, hru, hen)
for k, ru, en in [("tissue_cut", "Задеты ткани", "Tissue damaged"), ("too_rough", "Слишком резко", "Too rough"),
    ("blood_hidden", "Не видно — кровь", "Can't see — blood"), ("tissue_tear", "Ткани рвутся", "Tissue tearing"),
    ("over_pull", "Перерастянуто", "Over-stretched"), ("lost_source", "Источник упущен", "Lost the source"),
    ("drill_slip", "Сверло соскочило", "The drill slipped"), ("rib_hit", "Попали в ребро", "Hit a rib"),
    ("sutured", "Ушито", "Sutured"), ("drilling", "Сверление", "Drilling"), ("draining", "Дренирование", "Draining")]:
    t("rpmedicine.minigame." + k, ru, en)
# Некроз, ампутация, протезы (третий этап, п. 5–6).
for k, ru, en, dru, den in [
    ("bone_saw", "Хирургическая пила", "Bone saw", "Ампутация раскрытой конечности: рука, нога (со стопой), стопа. Культя под швами. Медицина 8+.",
     "Amputates an opened limb: arm, leg (with the foot), foot. The stump is sutured. Medicine 8+."),
    ("prosthetic_foot", "Протез стопы", "Prosthetic foot", "На зажившую культю стопы: ходьба медленнее. Медицина 7+.", "On a healed foot stump: slower walking. Medicine 7+."),
    ("peg_leg", "Деревянная нога", "Peg leg", "На зажившую культю ноги: заметно медленнее, бега нет. Медицина 7+.", "On a healed leg stump: much slower, no sprinting. Medicine 7+."),
    ("prosthetic_hook", "Крюк", "Hook", "На зажившую культю руки: рука работает вдвое хуже, но держит. Медицина 7+.", "On a healed arm stump: the arm works at half strength but holds. Medicine 7+."),
    ("severed_limb", "Конечность", "Severed limb", "Отнятая часть тела. Портится; в холодильнике дольше.", "A severed body part. Spoils; lasts longer in a fridge.")]:
    t(f"item.rpmedicine.{k}", ru, en)
    t(f"item.rpmedicine.{k}.desc", dru, den)
t("item.rpmedicine.severed_limb.of", "Конечность: %s", "Severed limb: %s")
t("rpmedicine.tooltip.limb_owner", "Чья: %s", "Whose: %s")
for k, ru, en in [("bone_saw", "Пила для костей.", "A bone saw."), ("prosthetic_foot", "Протез стопы.", "A prosthetic foot."),
    ("peg_leg", "Деревянная нога.", "A wooden leg."), ("prosthetic_hook", "Протез-крюк.", "A hook prosthesis."), ("severed_limb", "Отнятая конечность.", "A severed limb.")]:
    t(f"item.rpmedicine.{k}.what", ru, en)
for k, ru, en in [("amputated", "%s: ампутация, культя зашита", "%s: amputated, stump sutured"),
    ("prosthesis_installed", "%s: протез установлен", "%s: prosthesis fitted"), ("prosthesis_poor", "%s: протез не сел", "%s: the prosthesis didn't fit")]:
    t("rpmedicine.treat." + k, ru, en)
for k, ru, en in [("part_missing", "Этой части нет", "This part is missing"), ("limb_only", "Только рука, нога или стопа", "Arm, leg or foot only"),
    ("not_missing", "Часть на месте — протез не нужен", "The part is there — no prosthesis needed"),
    ("prosthesis_already", "Протез уже стоит", "A prosthesis is already fitted"), ("prosthesis_wrong_part", "Этот протез не для этой части", "This prosthesis is for another part"),
    ("stump_not_healed", "Культя ещё не зажила", "The stump has not healed yet")]:
    t("rpmedicine.refuse." + k, ru, en)
for k, ru, en in [("stump", "Культя (свежая)", "Stump (fresh)"), ("stump_healed", "Культя зажила", "Healed stump"),
    ("prosthesis_foot", "  протез стопы", "  prosthetic foot"), ("prosthesis_peg_leg", "  деревянная нога", "  peg leg"), ("prosthesis_hook", "  крюк", "  hook"),
    ("necrosis", "Некроз тканей", "Tissue necrosis"), ("skin_black", "Кожа тёмная, холодная, не чувствует", "Skin dark, cold, numb"),
    ("necrosis_reversible", "Некроз %s%% — ещё обратим: антибиотик и убрать причину", "Necrosis %s%% — still reversible: antibiotics, remove the cause"),
    ("necrosis_irreversible", "Некроз %s%% — необратим, нужна ампутация", "Necrosis %s%% — irreversible, needs amputation")]:
    t("rpmedicine.exam." + k, ru, en)
t("rpmedicine.panel.remove_prosthesis", "Снять протез", "Remove prosthesis")
t("rpmedicine.msg.prosthesis_removed", "Протез снят", "Prosthesis removed")
t("rpmedicine.minigame.amputation", "Ампутация", "Amputation")
t("rpmedicine.minigame.hint_amputation", "Пилите ровными движениями влево-вправо, не выходя из полосы распила.", "Saw with steady left-right strokes, staying in the cut line.")
t("rpmedicine.minigame.sawing", "Распил", "Sawing")
for k, ru, en in [("amputated", "Операция: ампутация — %s", "Surgery: amputation — %s"), ("prosthesis_installed", "Установлен протез — %s", "Prosthesis fitted — %s")]:
    t("rpmedicine.medcard.entry.surgery_" + k, ru, en)
t("rpmedicine.medcard.entry.traumatic_amputation", "Травматическая ампутация: %s", "Traumatic amputation: %s")
# Органы и конечности вне тела (третий этап, п. 7).
for k, ru, en, dru, den in [
    ("organ_container", "Контейнер для органа", "Organ container", "Изъять орган раскрытой груди или живота (несколько — выбор). Медицина 9+.",
     "Removes an organ from an opened chest or abdomen (several — choose). Medicine 9+."),
    ("organ", "Орган в контейнере", "Organ in a container", "Пересадить на место отсутствующего органа. Несовместимая кровь — отторжение, нужен циклоспорин. Вне холодильника портится за час. Медицина 10+.",
     "Transplant in place of a missing organ. Incompatible blood — rejection, needs cyclosporine. Spoils in an hour outside a fridge. Medicine 10+."),
    ("cyclosporine", "Циклоспорин", "Cyclosporine", "Иммуносупрессор: останавливает отторжение пересаженного органа на 12 часов. Медицина 4+.",
     "Immunosuppressant: stops rejection of a transplanted organ for 12 hours. Medicine 4+.")]:
    t(f"item.rpmedicine.{k}", ru, en)
    t(f"item.rpmedicine.{k}.desc", dru, den)
for k, ru, en in [("organ_container", "Охлаждающий контейнер.", "A cooling container."), ("organ", "Донорский орган.", "A donor organ."),
    ("cyclosporine", "Таблетки циклоспорина.", "Cyclosporine tablets.")]:
    t(f"item.rpmedicine.{k}.what", ru, en)
t("item.rpmedicine.organ.of", "Орган: %s", "Organ: %s")
t("rpmedicine.tooltip.organ_donor", "Донор: %s, группа %s", "Donor: %s, blood type %s")
t("rpmedicine.tooltip.organ_spoiled", "Испорчено", "Spoiled")
t("rpmedicine.organ_choice.title", "Какой орган изъять?", "Which organ to remove?")
for k, ru, en in [("organ_removed", "%s: орган изъят", "%s: organ removed"), ("organ_transplanted", "%s: орган пересажен", "%s: organ transplanted"),
    ("organ_transplanted_dead", "%s: орган пересажен, но он мёртв", "%s: organ transplanted, but it is dead"),
    ("limb_reattached", "%s: конечность пришита", "%s: limb reattached"), ("limb_reattached_poor", "%s: пришито наспех", "%s: reattached hastily")]:
    t("rpmedicine.treat." + k, ru, en)
for k, ru, en in [("torso_only", "Только грудь или живот", "Chest or abdomen only"), ("no_organ", "Органа здесь нет", "No organ here"),
    ("organ_wrong_part", "Этот орган не отсюда", "This organ belongs elsewhere"), ("organ_present", "Орган на месте", "The organ is in place"),
    ("limb_wrong_part", "Это другая конечность", "That is a different limb"), ("limb_spoiled", "Конечность испорчена", "The limb has spoiled")]:
    t("rpmedicine.refuse." + k, ru, en)
t("rpmedicine.exam.organ_rejection", "Признаки отторжения пересаженного органа: %s", "Signs of transplant rejection: %s")
for k, ru, en in [("organ_removed", "Операция: изъят орган — %s", "Surgery: organ removed — %s"), ("organ_transplanted", "Операция: пересадка органа — %s", "Surgery: organ transplant — %s"),
    ("organ_transplanted_dead", "Операция: пересажен нежизнеспособный орган — %s", "Surgery: non-viable organ transplanted — %s"),
    ("limb_reattached", "Операция: пришита конечность — %s", "Surgery: limb reattached — %s")]:
    t("rpmedicine.medcard.entry.surgery_" + k, ru, en)
for k, ru, en, hru, hen in [
    ("harvest", "Изъятие органа", "Organ removal", "Пересеките сосуды между толчками крови, затем перенесите орган в контейнер.", "Cut the vessels between spurts, then move the organ into the container."),
    ("plant", "Пересадка", "Transplant", "Уложите орган на место, затем сшейте сосуд, не выходя из канала.", "Place the organ, then suture the vessel without leaving the channel.")]:
    t("rpmedicine.minigame." + k, ru, en)
    t("rpmedicine.minigame.hint_" + k, hru, hen)
# Диабет и хронические состояния (третий этап, п. 8).
for k, ru, en, dru, den in [
    ("insulin", "Инсулин", "Insulin", "Снижает сахар крови примерно на 6 ммоль/л за 2 часа. Можно колоть себе.", "Lowers blood sugar by about 6 mmol/L over 2 hours. Can be self-injected."),
    ("glucose_tablets", "Таблетки глюкозы", "Glucose tablets", "Сахар крови сразу выше — при гипогликемии.", "Raises blood sugar at once — for hypoglycemia."),
    ("glucometer", "Глюкометр", "Glucometer", "Сахар крови цифрой.", "Blood sugar as a number.")]:
    t(f"item.rpmedicine.{k}", ru, en)
    t(f"item.rpmedicine.{k}.desc", dru, den)
for k, ru, en in [("insulin", "Шприц-ручка с инсулином.", "An insulin pen."), ("glucose_tablets", "Жевательные таблетки глюкозы.", "Chewable glucose tablets."),
    ("glucometer", "Карманный глюкометр.", "A pocket glucometer.")]:
    t(f"item.rpmedicine.{k}.what", ru, en)
t("rpmedicine.treat.glucometer", "Глюкометр: %2$s ммоль/л", "Glucometer: %2$s mmol/L")
t("rpmedicine.exam.cold_sweat", "Холодный пот, бледность, дрожь", "Cold sweat, pallor, trembling")
t("rpmedicine.exam.acetone_breath", "Запах ацетона изо рта, сухость", "Acetone breath, dryness")
# Медкарта, команды, интубация (третий этап, п. 10–11).
for k, ru, en in [("chronic_diabetic", "Хроническое: сахарный диабет", "Chronic: diabetes"), ("chronic_smoker", "Хроническое: курение, бронхит курильщика", "Chronic: smoking, smoker's bronchitis"),
    ("chronic_alcoholic", "Хроническое: алкоголизм, поражение печени", "Chronic: alcoholism, liver damage")]:
    t("rpmedicine.medcard.entry." + k, ru, en)
t("rpmedicine.cmd.amputated", "%s: убрана часть тела — %s", "%s: body part removed — %s")
t("rpmedicine.cmd.restored", "%s: часть тела возвращена — %s", "%s: body part restored — %s")
t("rpmedicine.minigame.intubation", "Интубация", "Intubation")
t("rpmedicine.minigame.hint_intubation", "Наведите трубку на щель между связками и нажмите, когда они раскроются; продвигайте на вдохе, держите по центру.",
  "Aim the tube at the gap between the vocal cords and click when they open; advance on each breath, keep it centred.")
for k, ru, en in [("tube_depth", "Глубина трубки", "Tube depth"), ("esophagus", "Мимо — в пищевод", "Missed — into the esophagus"),
    ("cords_closed", "Связки сомкнуты", "The cords are closed")]:
    t("rpmedicine.minigame." + k, ru, en)
t("rpmedicine.settings.show_missing_limbs", "Скрывать отнятые конечности", "Hide missing limbs")
# Вещества (третий этап, п. 9).
for k, ru, en in [("opioid", "опиаты", "opioids"), ("benzo", "успокоительные", "benzodiazepines"), ("alcohol", "алкоголь", "alcohol"),
    ("nicotine", "никотин", "nicotine"), ("caffeine", "кофеин", "caffeine"), ("stimulant", "стимуляторы", "stimulants")]:
    t("rpmedicine.substance." + k, ru, en)
for k, ru, en in [("opioid", "Ломит всё тело. Нужна доза...", "Your whole body aches. You need a dose..."),
    ("benzo", "Тревога не отпускает, руки дрожат.", "Anxiety won't let go, your hands tremble."),
    ("alcohol", "Трясёт. Хочется выпить.", "You're shaking. You want a drink."),
    ("nicotine", "Тянет закурить.", "You crave a smoke."),
    ("caffeine", "Голова раскалывается, нужен кофе.", "Splitting headache — you need coffee."),
    ("stimulant", "Сил нет ни на что.", "No energy for anything.")]:
    t("rpmedicine.craving." + k, ru, en)
for k, ru, en in [("smells_alcohol", "Запах алкоголя", "Smells of alcohol"), ("drunk", "Пьян: шатается, речь невнятная", "Drunk: staggering, slurred speech"),
    ("withdrawal_signs", "Пот, дрожь, беспокойство", "Sweating, tremor, restlessness"), ("withdrawal", "Абстиненция: %s", "Withdrawal: %s"),
    ("seizure", "Судороги!", "Seizure!")]:
    t("rpmedicine.exam." + k, ru, en)
# Питание.
for k, ru, en in [("title", "Питание", "Nutrition"), ("protein", "Белки", "Protein"), ("fat", "Жиры", "Fats"), ("carbs", "Углеводы", "Carbohydrates"),
    ("vitamins", "Витамины и клетчатка", "Vitamins and fibre"), ("state_low", "мало", "low"), ("state_mid", "так себе", "so-so"),
    ("state_ok", "норма", "good"), ("state_high", "избыток", "excess"),
    ("kcal_recent", "Съедено за последние часы: %s ккал", "Eaten over the last hours: %s kcal"),
    ("tooltip", "%s ккал · Б %s · Ж %s · У %s", "%s kcal · P %s · F %s · C %s")]:
    t("rpmedicine.nutrition." + k, ru, en)
t("key.rpmedicine.nutrition", "Питание", "Nutrition")
t("rpmedicine.exam.malnourished", "Истощён: худой, слабый", "Malnourished: thin and weak")
t("rpmedicine.exam.undernourished", "Недоедает: вялый, бледный", "Undernourished: sluggish and pale")
# «Приелось».
for k, ru, en, mru, men in [
    ("meat", "мясо", "meat", "Опять мясо... кусок в горло не лезет.", "Meat again... you can barely swallow it."),
    ("fish", "рыба", "fish", "Снова рыба. Уже воротит.", "Fish again. It's getting sickening."),
    ("grain", "хлеб и каши", "bread and grains", "Опять каша да хлеб. Надоело.", "Porridge and bread again. You're sick of it."),
    ("vegetables", "овощи", "vegetables", "Одни овощи... хочется чего-то другого.", "Only vegetables... you want something else."),
    ("fruit", "фрукты", "fruit", "Фрукты уже приелись.", "You're tired of fruit."),
    ("sweet", "сладкое", "sweets", "Приторно. От сладкого уже мутит.", "Too sweet. The sweets are making you queasy."),
    ("dairy", "молочное и яйца", "dairy and eggs", "Опять молочное. Без аппетита.", "Dairy again. No appetite.")]:
    t("rpmedicine.food_category." + k, ru, en)
    t("rpmedicine.monotony." + k, mru, men)
t("rpmedicine.nutrition.fed_up", "Приелось: %s", "Fed up with: %s")
t("rpmedicine.exam.poor_appetite", "Плохой аппетит", "Poor appetite")
# Питание — ощущения и анализ.
for k, ru, en in [("feel_low_protein", "Слабость в мышцах, ссадины и раны заживают долго.", "Weak muscles; scrapes and wounds heal slowly."),
    ("feel_low_fat", "Всё время зябко, кожа сухая.", "You feel chilly all the time; dry skin."),
    ("feel_low_carbs", "Нет сил, быстро выдыхаешься.", "No energy; you get winded fast."),
    ("feel_low_vitamins", "Бледность, ломкие ногти, легко простужаешься.", "Pale, brittle nails, you catch colds easily."),
    ("feel_heavy", "Тяжесть в теле, одышка.", "Heavy body, short of breath."),
    ("feel_balanced", "Чувствуешь себя бодро и сыто.", "You feel well fed and lively."),
    ("feel_ok", "Ничего особенного.", "Nothing in particular."),
    ("lab_hint", "Точно — только анализом крови в лаборатории.", "Exact values — only by a lab blood test.")]:
    t("rpmedicine.nutrition." + k, ru, en)
for k, ru, en in [("albumin", "Альбумин: %s г/л (норма 35–50)", "Albumin: %s g/L (normal 35–50)"),
    ("triglycerides", "Триглицериды: %s ммоль/л (норма 0,5–1,7)", "Triglycerides: %s mmol/L (normal 0.5–1.7)"),
    ("glucose", "Глюкоза: %s ммоль/л (норма 3,9–6,1)", "Glucose: %s mmol/L (normal 3.9–6.1)"),
    ("b12", "Витамин B12: %s пг/мл (норма 200–900)", "Vitamin B12: %s pg/mL (normal 200–900)")]:
    t("rpmedicine.lab." + k, ru, en)
t("rpmedicine.medcard.entry.lab_nutrition", "Анализ крови: альбумин %s г/л, триглицериды %s, глюкоза %s, B12 %s", "Blood test: albumin %s g/L, triglycerides %s, glucose %s, B12 %s")
# --- конец пачки 05.10 ---
# --- пачка 06.10: совместная проверка и внешний вид ---
t("rpmedicine.medcard.vitals", "Кровь, рост, вес (меняет ГМ)", "Blood, height, weight (set by the GM)")
t("rpmedicine.settings.no_minigames", "Без мини-игр (прогресс-бар)", "No minigames (progress bar)")
t("item.rpmedicine.dirty_syringe", "Использованный шприц", "Used syringe")
t("item.rpmedicine.dirty_syringe.desc", "Нестерильный. Стерилизатор (ПКМ) вернёт чистым.", "Not sterile. A sterilizer (right-click) makes it clean again.")
t("item.rpmedicine.dirty_test_tube", "Использованная пробирка", "Used test tube")
t("item.rpmedicine.dirty_test_tube.desc", "После анализа. Стерилизатор (ПКМ) вернёт чистой.", "After a test. A sterilizer (right-click) makes it clean again.")
t("rpmedicine.tooltip.shelf_cold", "В холоде: годен ещё ~%s", "Chilled: good for ~%s more")
t("rpmedicine.tooltip.shelf_warm", "В тепле: годен ещё ~%s (без холодильника — %s)", "Warm: good for ~%s more (%s without a fridge)")
t("rpmedicine.unit.days", "дн.", "d")
t("rpmedicine.unit.hours", "ч", "h")
t("rpmedicine.unit.minutes", "мин", "min")
t("rpmedicine.refuse.defib_cpr_first", "Сердце остановлено: сначала СЛР, разряд — сразу после компрессий", "Cardiac arrest: CPR first, shock right after compressions")
t("rpmedicine.hint.restart_low_blood", "Сердце не заведётся: слишком мало крови — перелейте кровь", "The heart won't restart: too little blood — transfuse")
t("rpmedicine.hint.restart_tension_pneumo", "Сердце не заведётся: напряжённый пневмоторакс — декомпрессия", "The heart won't restart: tension pneumothorax — decompress")
t("rpmedicine.hint.restart_heart_destroyed", "Сердце разрушено — запустить нельзя", "The heart is destroyed — it can't be restarted")
t("rpmedicine.hint.restart_arterial", "Сердце не заведётся: артериальное кровотечение — остановите его", "The heart won't restart: arterial bleeding — stop it first")
t("rpmedicine.treat.shock_blocked_low_blood", "Разряд без толку: слишком мало крови — перелейте кровь", "Shock had no effect: too little blood — transfuse")
t("rpmedicine.treat.shock_blocked_tension_pneumo", "Разряд без толку: напряжённый пневмоторакс — декомпрессия", "Shock had no effect: tension pneumothorax — decompress")
t("rpmedicine.treat.shock_blocked_heart_destroyed", "Разряд без толку: сердце разрушено", "Shock had no effect: the heart is destroyed")
t("rpmedicine.treat.shock_blocked_arterial", "Разряд без толку: артериальное кровотечение — остановите его", "Shock had no effect: arterial bleeding — stop it first")
t("rpmedicine.refuse.inventory_full", "Нет места в инвентаре, чтобы достать из аптечки", "No inventory space to take it out of the kit")
# --- конец пачки 06.10 ---

base = os.path.join(os.path.dirname(__file__), "..", "src/main/resources/assets/rpmedicine/lang")
for idx, name in ((0, "ru_ru"), (1, "en_us")):
    with open(os.path.join(base, name + ".json"), "w", encoding="utf-8") as f:
        json.dump({k: v[idx] for k, v in sorted(T.items())}, f, ensure_ascii=False, indent=2)
        f.write("\n")
print(len(T), "ключей")
