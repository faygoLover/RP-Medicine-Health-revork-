# Справочник медика — книга Patchouli (python scripts/gen_book.py).
# Книга: data/rpmedicine/patchouli_books/guide/book.json; текст: assets/rpmedicine/patchouli_books/guide/<язык>/.
# Разделы прячутся скрытыми достижениями: book/gm — только операторам (GuideBook.java), book/medicine_N — по уровню
# медицины, если в конфиге book_skill_gating = true (по умолчанию все видят всё).
# Значения из конфига — {{число}} в тексте: подсвечиваются цветом, в книге — стандартные.
import json
import os
import re
import shutil

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "src", "main", "resources")
BOOK_DATA = os.path.join(RES, "data", "rpmedicine", "patchouli_books", "guide")
BOOK_ASSETS = os.path.join(RES, "assets", "rpmedicine", "patchouli_books", "guide")
ADV = os.path.join(RES, "data", "rpmedicine", "advancements", "book")
CFG_COLOR = "#b5531f"


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write("\n")


def fmt(text):
    """{{x}} — значение из конфига (цветом); **x** — жирный; переносы абзацев — $(br2)."""
    text = re.sub(r"\{\{(.+?)\}\}", r"$(cfg)\1$()", text)
    text = re.sub(r"\*\*(.+?)\*\*", r"$(l)\1$()", text)
    # Книга с i18n: Patchouli пропускает текст через String.format — одиночный % давал «format error» (замечание 3).
    text = text.replace("%", "%%")
    return text.replace("\n\n", "$(br2)").replace("\n", "$(br)")


def visible(s):
    return len(re.sub(r"\$\([^)]*\)", "", s))


LINE = 24          # символов в строке страницы (примерно)
PAGE_LINES = 14    # строк на текстовой странице
FIRST_LINES = 11   # первая страница — под заголовком


def lines(text):
    """Сколько строк займёт текст на странице (абзац — пустая строка)."""
    n = 0
    for i, para in enumerate(text.split("\n\n")):
        n += 1 if i else 0
        for ln in para.split("\n"):
            n += max(1, -(-visible(fmt(ln)) // LINE))
    return n


def split(text, first):
    """Разбить текст на страницы: по абзацам, длинный абзац — по строкам списка и предложениям."""
    units = []  # (кусок, разделитель перед ним)
    for i, para in enumerate(text.split("\n\n")):
        sep = "\n\n" if i else ""
        parts = [para] if lines(para) <= PAGE_LINES - 3 else re.split(r"(?<=[.!?…;])\s+|\n", para)
        for j, c in enumerate(parts):
            units.append((c, sep if j == 0 else ("\n" if c.startswith("- ") or re.match(r"\d\.", c) else " ")))
    pages, cur, limit = [], "", FIRST_LINES if first else PAGE_LINES
    for c, sep in units:
        cand = cur + sep + c if cur else c
        if cur and lines(cand) > limit:
            pages.append(cur)
            cur, limit = c, PAGE_LINES
        else:
            cur = cand
    if cur.strip():
        pages.append(cur)
    return pages


# ------------------------------------------------------------------ содержимое
# Запись: (категория, id, иконка, (ru, en) название, страницы, уровень медицины или None, только ГМ)
# Страница: ("text", ru, en) | ("spot", предмет, ru, en) | ("craft", рецепт, ru, en)
CATS = [
    ("basics", "minecraft:book", "Основы", "Basics", "Как устроено здоровье, интерфейс и лечение.", "How health, the interface and treatment work."),
    ("wounds", "rpmedicine:bandage", "Раны и кровотечение", "Wounds and bleeding", "Раны, повязки, жгуты, швы, переломы, инфекции.",
     "Wounds, dressings, tourniquets, sutures, fractures, infections."),
    ("downed", "rpmedicine:defibrillator", "Нокдаун и реанимация", "Knockdown and resuscitation",
     "Потеря сознания, клиническая смерть, СЛР, переноска.", "Unconsciousness, clinical death, CPR, carrying."),
    ("drugs", "rpmedicine:morphine", "Препараты", "Drugs", "Как действуют лекарства, ручки, флаконы, таблетки.",
     "How drugs work, pens, vials, pills."),
    ("blood", "rpmedicine:blood_bag", "Кровь и капельницы", "Blood and IVs", "Кровопотеря, группы, забор, переливание, стойка.",
     "Blood loss, types, donation, transfusion, the IV stand."),
    ("diagnostics", "rpmedicine:stethoscope", "Диагностика", "Diagnostics", "Осмотр, приборы, лаборатория.", "Examination, devices, the lab."),
    ("hospital", "rpmedicine:hospital_bed", "Госпиталь", "Hospital", "Блоки госпиталя и что они дают.", "Hospital blocks and what they do."),
    ("surgery", "rpmedicine:scalpel", "Хирургия", "Surgery", "Наркоз, операции, органы, ампутации.", "Anesthesia, operations, organs, amputations."),
    ("life", "minecraft:bread", "Вещества и питание", "Substances and food", "Алкоголь, табак, опиаты, питание, диабет.",
     "Alcohol, tobacco, opioids, nutrition, diabetes."),
    ("medcard", "rpmedicine:medcard", "Медкарта", "Medical record", "Карта пациента и записи.", "The patient's record and entries."),
    ("gm", "rpmedicine:gm_scanner", "Ведущему (ГМ)", "Game master", "Команды, панель, настройки, датапаки.", "Commands, panel, settings, datapacks."),
]

E = []


def entry(cat, eid, icon, ru, en, pages, level=None, gm=False):
    E.append(dict(cat=cat, id=eid, icon=icon, name=(ru, en), pages=pages, level=level, gm=gm))


def T(ru, en):
    return ("text", ru, en)


def S(item, ru, en, title=None):
    return ("spot", item, ru, en, title)


# ---------------------------------------------------------- Основы
entry("basics", "intro", "minecraft:book", "Как устроено здоровье", "How health works", [
    T("Обычных сердечек здесь нет: ванильное здоровье скрыто и всегда полное. Урон превращается в **раны** на девяти частях тела: голова, грудь, живот, руки, ноги и стопы.\n\n"
      "Настоящая жизнь — **мозг**. Он страдает, когда не хватает кислорода: при большой кровопотере, низком давлении, остановке сердца или дыхания. Мозг на нуле — смерть.\n\n"
      "Вы чувствуете тело, а не цифры: «кружится голова», «трудно дышать», «рука не слушается». Точные числа дают приборы и опыт: с уровня медицины {{4}} в панели видны цифры.",
      "There are no hearts here: vanilla health is hidden and always full. Damage becomes **wounds** on nine body parts: head, chest, abdomen, arms, legs and feet.\n\n"
      "Your real life is the **brain**. It suffers without oxygen: heavy blood loss, low pressure, cardiac or respiratory arrest. Brain at zero means death.\n\n"
      "You feel your body, not numbers: \"dizzy\", \"hard to breathe\", \"the arm won't obey\". Exact numbers come from devices and experience: from Medicine {{4}} the panel shows numbers."),
    T("Что убивает:\n- **кровопотеря** — потеря {{50 %}} крови останавливает сердце;\n- **удушье** — пневмоторакс, остановка дыхания, перекрытые пути;\n"
      "- **травма головы** и мозга;\n- **органы** — сердце, лёгкие, печень, почки;\n- **инфекция и сепсис** — медленно, но верно.\n\n"
      "Почти всё лечится вовремя. Чем раньше остановлено кровотечение, тем больше шансов.",
      "What kills:\n- **blood loss** — losing {{50%}} of blood stops the heart;\n- **suffocation** — pneumothorax, respiratory arrest, blocked airway;\n"
      "- **head and brain trauma**;\n- **organs** — heart, lungs, liver, kidneys;\n- **infection and sepsis** — slowly but surely.\n\n"
      "Almost everything can be treated in time. The sooner bleeding stops, the better the chances."),
])
entry("basics", "interface", "rpmedicine:portable_scanner", "Интерфейс и клавиши", "Interface and keys", [
    T("**HUD**: силуэт тела (цвет части — её состояние), общая полоса состояния, ощущения текстом, прогресс лечения.\n\n"
      "**H** — панель осмотра: своя или того, на кого смотрите. В ней части тела, раны, что стоит на теле, и список ваших медицинских предметов — их можно тащить прямо на часть тела.\n\n"
      "**K** — питание (мод RP Culinary).\n**G** (удерживать) — добить лежачего.",
      "**HUD**: body silhouette (part colour shows its state), overall condition bar, sensations as text, treatment progress.\n\n"
      "**H** — examination panel: your own or the one you look at. It shows body parts, wounds, what is applied, and your medical items — drag them straight onto a body part.\n\n"
      "**K** — nutrition (the RP Culinary mod).\n**G** (hold) — finish off a downed player."),
    T("Положение элементов HUD меняется командой **/rpmedicine hud**.\n\nКлиентские настройки (эффекты экрана, звуки, громкость, «без мини-игр») — Esc → «Моды» → RP Medicine → «Настройки».\n\n"
      "Взгляд на пациента с медицинским предметом в руке показывает короткую сводку: кровит ли, в сознании ли.",
      "Move HUD elements with **/rpmedicine hud**.\n\nClient settings (screen effects, sounds, volume, \"no minigames\") — Esc → Mods → RP Medicine → Config.\n\n"
      "Looking at a patient with a medical item in hand shows a short summary: bleeding, conscious or not."),
])
entry("basics", "treating", "rpmedicine:bandage", "Как лечить", "How to treat", [
    T("ПКМ предметом по пациенту — лечить его, ПКМ в воздух — себя. Часть тела выбирается сама (самая нуждающаяся) или перетаскиванием в панели **H**.\n\n"
      "Лечение — **мини-игра**: чем лучше сыграли, тем лучше результат. Если сервер выключил мини-игры в бою, то в бою (урон не позже {{30}} с назад) — только прогресс-бар.\n\n"
      "Кнопка «Без мини-игры» или Esc — прогресс-бар в {{2}} раза дольше и ошибки чаще.",
      "Right-click a patient with an item to treat them, right-click the air to treat yourself. The body part is picked automatically (the one in most need) or by dragging in the **H** panel.\n\n"
      "Treatment is a **minigame**: the better you play, the better the result. If the server turns minigames off in combat, then in combat (damage within {{30}} s) — a progress bar only.\n\n"
      "The \"No minigame\" button or Esc — a progress bar {{2}} times longer, with more mistakes."),
    T("Если лечение не нужно, придёт отказ: «не нужно». Повторите то же в течение 5 секунд — сделаете всё равно, но с последствиями (жгут на здоровую ногу, лишняя доза).\n\n"
      "Когда предмет требует уровня выше вашего, применять можно, но каждый недостающий уровень даёт {{+30 %}} к шансу ошибки, не больше {{85 %}}. Ошибка вредит.\n\n"
      "Себе лечить дольше в {{1,5}} раза.",
      "If the treatment is not needed, you get a refusal. Repeat it within 5 seconds to do it anyway, with consequences (a tourniquet on a healthy leg, an extra dose).\n\n"
      "Items above your level can still be used, but each missing level adds {{+30%}} error chance, up to {{85%}}. Mistakes hurt.\n\n"
      "Treating yourself takes {{1.5}} times longer."),
])
entry("basics", "skill", "minecraft:experience_bottle", "Навык «Медицина»", "Medicine skill", [
    T("Уровень медицины — от 0 до 10. Сам он не растёт: его выставляет ведущий (или атрибут RP Perks).\n\n"
      "- **0–2** — первая помощь: бинты, жгут, таблетки, ручки.\n- **3** — швы, забор и переливание крови, лаборатория.\n"
      "- **{{4}}** — видно цифры, выбор дозы ручкой и шприцем.\n- **5–6** — антибиотики уколом, атропин, диазепам, интубация.\n"
      "- **7+** — хирургия, наркоз, органы, ампутации.\n\nЧем выше уровень, тем быстрее и точнее, мини-игры легче.",
      "Medicine goes from 0 to 10. It does not grow by itself: the game master sets it (or the RP Perks attribute).\n\n"
      "- **0–2** — first aid: bandages, tourniquet, pills, pens.\n- **3** — sutures, blood donation and transfusion, the lab.\n"
      "- **{{4}}** — numbers are shown, dose selection with pens and syringes.\n- **5–6** — injected antibiotics, atropine, diazepam, intubation.\n"
      "- **7+** — surgery, anesthesia, organs, amputations.\n\nHigher level — faster and more precise, easier minigames."),
])
entry("basics", "body", "minecraft:leather_boots", "Тело и движение", "Body and movement", [
    T("**Сломанная нога** — хромаете, не прыгаете, бежать больно; шаги по сломанной ноге её калечат. Без обеих ног или с двумя сломанными — **ползком**.\n\n"
      "**Сломанная рука** — всё делается медленнее (в {{2,5}} раза главной рукой), удар слабее. Без руки предмет в ней не удержать.\n\n"
      "Сильная боль замедляет и сбивает прицел. Болевой шок (боль выше {{85}}) валит с ног на {{20–30}} с.",
      "**Broken leg** — you limp, cannot jump, running hurts; walking on it makes it worse. Without both legs, or with both broken, you **crawl**.\n\n"
      "**Broken arm** — everything is slower ({{2.5}} times with the main hand), weaker hits. Without an arm you cannot hold an item in it.\n\n"
      "Strong pain slows you and sways your aim. Pain shock (pain above {{85}}) knocks you down for {{20–30}} s."),
])

# ---------------------------------------------------------- Раны
entry("wounds", "types", "rpmedicine:hemostatic_gauze", "Виды ран и кровотечения", "Wounds and bleeding", [
    T("Раны: **порез**, **колотая**, **огнестрельная**, **осколочная**, **ушиб**, **ожог** (II степени — с {{25}}, III — с {{55}} тяжести).\n\n"
      "Кровотечение:\n- слабое — до {{30}} мл/мин, само запекается за ~{{4}} мин;\n- среднее — до {{150}};\n- сильное — до {{300}};\n- **артериальное** — ~{{550}} мл/мин, только жгут или хирургия.\n\n"
      "**Внутреннее** кровотечение снаружи не видно: бледность, слабость, падает давление. Нужен хирург или ТХА.",
      "Wounds: **cut**, **stab**, **gunshot**, **shrapnel**, **bruise**, **burn** (2nd degree from {{25}}, 3rd from {{55}} severity).\n\n"
      "Bleeding:\n- slight — up to {{30}} ml/min, clots by itself in ~{{4}} min;\n- moderate — up to {{150}};\n- heavy — up to {{300}};\n- **arterial** — ~{{550}} ml/min, tourniquet or surgery only.\n\n"
      "**Internal** bleeding is invisible: pale, weak, falling pressure. Needs a surgeon or TXA."),
])
entry("wounds", "dressings", "rpmedicine:bandage", "Повязки", "Dressings", [
    S("rpmedicine:bandage", "Останавливает слабое и среднее кровотечение, защищает рану от грязи. Мини-игра — круги мышью в нужную сторону.",
      "Stops slight and moderate bleeding, protects the wound. Minigame — circles with the mouse in the right direction."),
    S("rpmedicine:pressure_dressing", "Давящая повязка: держит сильное кровотечение лучше бинта.", "Pressure dressing: holds heavy bleeding better than a bandage."),
    S("rpmedicine:hemostatic_gauze", "Гемостатик: тампон с кровоостанавливающим — для глубоких ран.", "Hemostatic gauze for deep wounds."),
    T("Свежая повязка {{5}} мин ненадёжна: бег ({{1 %}} в секунду) и прыжки ({{4 %}}) могут её сорвать, новый урон — с шансом {{50 %}}.\n\n"
      "Повязка снижает риск инфекции на {{80 %}}. Снять повязку — пустой рукой в панели **H**.",
      "A fresh dressing is unreliable for {{5}} min: sprinting ({{1%}} per second) and jumping ({{4%}}) can tear it, new damage — {{50%}} chance.\n\n"
      "A dressing cuts infection risk by {{80%}}. Remove it with an empty hand in the **H** panel."),
])
entry("wounds", "tourniquet", "rpmedicine:tourniquet", "Жгут и Эсмарх", "Tourniquet", [
    S("rpmedicine:tourniquet", "Турникет на руку или ногу: останавливает любое кровотечение ниже, даже артериальное.",
      "On an arm or leg: stops any bleeding below it, even arterial."),
    S("rpmedicine:esmarch", "Резиновый жгут Эсмарха: то же, но может порваться ({{15 %}}).", "Esmarch rubber tourniquet: the same, may snap ({{15%}})."),
    T("Безопасно жгут держится {{15}} мин игрового времени в сети. Дальше конечность страдает от ишемии ({{2}} в минуту), а потом начинается **некроз** — её придётся ампутировать.\n\n"
      "Жгут — временная мера: перевязать, ушить, снять (пустой рукой в панели **H**).",
      "A tourniquet is safe for {{15}} min of online time. Then the limb suffers ischemia ({{2}} per minute) and later **necrosis** — it will have to be amputated.\n\n"
      "A tourniquet is temporary: dress, suture, remove it (empty hand in the **H** panel)."),
])
entry("wounds", "chest", "rpmedicine:occlusive_dressing", "Ранения груди и пневмоторакс", "Chest wounds and pneumothorax", [
    S("rpmedicine:occlusive_dressing", "Герметичная наклейка на открытую рану груди: воздух перестаёт заходить.", "An airtight chest seal on an open chest wound."),
    S("rpmedicine:decompression_needle", "Игла для напряжённого пневмоторакса — выпускает воздух. Уровень 3.", "A needle for tension pneumothorax — releases air. Medicine 3."),
    T("Открытый пневмоторакс: тяжело дышать, падает SpO₂. Закройте наклейкой — через {{2–4}} мин лёгкое начнёт держать.\n\n"
      "**Напряжённый** пневмоторакс: давление падает, через {{3}} мин — остановка сердца. Поможет только игла, потом дренаж в операционной.",
      "Open pneumothorax: hard to breathe, SpO₂ falls. Seal it — in {{2–4}} min the lung holds.\n\n"
      "**Tension** pneumothorax: pressure drops, cardiac arrest in {{3}} min. Only the needle helps, then a chest drain in the OR."),
])
entry("wounds", "fractures", "rpmedicine:splint", "Переломы и вывихи", "Fractures and dislocations", [
    S("rpmedicine:splint", "Шина: обездвиживает перелом, боль меньше в {{2,5}} раза.", "Immobilizes a fracture, pain {{2.5}} times lower."),
    T("Перелом бывает закрытым и **открытым** (кровит, ~{{60}} мл/мин). Срастается {{4–5}} ч в сети; рёбра — ~{{4,5}} ч. Хирург ускоряет остеосинтезом.\n\n"
      "**Вывих**: боль и слабость конечности. Вправляется пустой рукой в панели **H** — очень больно ({{70}}), обезбольте заранее. Иногда при вправлении ломается кость ({{10 %}}).",
      "A fracture can be closed or **open** (bleeds ~{{60}} ml/min). Heals {{4–5}} h online, ribs ~{{4.5}} h. A surgeon speeds it up with osteosynthesis.\n\n"
      "**Dislocation**: pain and a weak limb. Reduce it with an empty hand in the **H** panel — very painful ({{70}}), give painkillers first. Sometimes the bone breaks ({{10%}})."),
])
entry("wounds", "foreign", "rpmedicine:surgical_tweezers", "Пули и осколки", "Bullets and fragments", [
    S("rpmedicine:surgical_tweezers", "Пинцет: достать пулю или осколок. Очень больно ({{90}}). Уровень 4.", "Remove a bullet or fragment. Very painful ({{90}}). Medicine 4."),
    T("Застрявшее в ране мешает заживлению, а через {{6}} ч удваивает риск инфекции. Мини-игра: довести пинцет по каналу раны, не задевая стенок, и вынуть обратно.\n\n"
      "Пинцет после раны нестерилен — верните стерильность в **стерилизаторе**.",
      "Anything stuck in a wound slows healing, and after {{6}} h doubles infection risk. Minigame: guide the tweezers along the wound channel without touching the walls and back.\n\n"
      "After a wound the tweezers are not sterile — use the **sterilizer**."),
])
entry("wounds", "sutures", "rpmedicine:suture_kit", "Швы", "Sutures", [
    S("rpmedicine:suture_kit", "Игла с нитью: закрыть рану. Заживает в {{2}} раза быстрее и не открывается. Уровень 3.",
      "Close a wound. Heals {{2}} times faster and stays closed. Medicine 3."),
    T("Мини-игра: провести иглу от точки к парной точке через рану, не уводя в сторону. Когда все стежки сделаны — **стянуть края**: держите ЛКМ, пока края не сойдутся, и отпустите. Перетянули — ткань рвётся.\n\n"
      "Шов не останавливает артериальное кровотечение — это сосудистая хирургия. Снять шов — ножницами.",
      "Minigame: pull the needle from a point to its pair across the wound without drifting. When all stitches are done — **pull the edges together**: hold LMB until they meet and release. Too hard — the tissue tears.\n\n"
      "A suture does not stop arterial bleeding — that is vascular surgery. Remove sutures with scissors."),
], level=3)
entry("wounds", "infection", "rpmedicine:antiseptic", "Инфекция и сепсис", "Infection and sepsis", [
    S("rpmedicine:antiseptic", "Обработать свежую рану: риск инфекции ×{{0,3}}.", "Treat a fresh wound: infection risk ×{{0.3}}."),
    S("rpmedicine:antibiotic_ointment", "Мазь лечит инфекцию неглубокой раны.", "The ointment cures infection of a shallow wound."),
    T("Каждая открытая рана раз в {{20–60}} мин может воспалиться. Грязь, нестерильный инструмент, застрявшая пуля — риск выше; койка и повязка — ниже.\n\n"
      "Инфекция растёт ({{4}} в час), иммунитет борется ({{3}} в час). Запущенная — **сепсис**: жар, давление падает, страдают органы. Лечат антибиотики: амоксициллин, цефтриаксон.",
      "Each open wound may get infected every {{20–60}} min. Dirt, non-sterile tools, a stuck bullet raise the risk; a bed and a dressing lower it.\n\n"
      "Infection grows ({{4}}/h), immunity fights it ({{3}}/h). Neglected — **sepsis**: fever, falling pressure, organ damage. Antibiotics cure it: amoxicillin, ceftriaxone."),
])

# ---------------------------------------------------------- Нокдаун
entry("downed", "knockdown", "minecraft:skeleton_skull", "Нокдаун", "Knockdown", [
    T("Сознание ниже {{30}} — вы падаете. **Обморок** проходит сам; **нокдаун** — когда жизни что-то угрожает: идёт таймер {{3–5}} мин, по его концу — клиническая смерть.\n\n"
      "Лёжа вы видите небо, говорить можно только «...», можно позвать помощь из меню (**P**). Тело видно лежащим, его можно тащить и обыскивать.\n\n"
      "Таймер идёт медленнее, если состояние лучше (перелили кровь — больше времени).",
      "Consciousness below {{30}} — you fall. **Fainting** passes by itself; **knockdown** happens when life is threatened: a {{3–5}} min timer runs, then clinical death.\n\n"
      "Lying down you see the sky, can only say \"...\", and can call for help from the menu (**P**). Others see you lying, can drag and search you.\n\n"
      "The timer runs slower when you are in better shape (a transfusion buys time)."),
    S("rpmedicine:stabilization_kit", "Набор стабилизации: таймер нокдауна тает вдвое медленнее ({{5}} мин), один раз за нокдаун.",
      "The stabilization kit: the knockdown timer runs at half speed ({{5}} min), once per knockdown."),
])
entry("downed", "death", "minecraft:wither_skeleton_skull", "Клиническая смерть", "Clinical death", [
    T("Сердце остановилось или прошёл таймер нокдауна — **клиническая смерть**. Мозг ещё держится, но без СЛР гибнет. Мозг на нуле — смерть насовсем.\n\n"
      "Чтобы запустить сердце, нужно: вернуть кровь (не меньше ~{{55 %}}), провести СЛР, дать разряд при фибрилляции, адреналин.\n\n"
      "После подъёма {{3}} ч организм слаб: новый нокдаун опаснее.",
      "The heart stopped or the knockdown timer ran out — **clinical death**. The brain still holds but dies without CPR. Brain at zero — death for good.\n\n"
      "To restart the heart: restore blood (at least ~{{55%}}), do CPR, defibrillate fibrillation, give adrenaline.\n\n"
      "For {{3}} h after revival the body is weak: a new knockdown is more dangerous."),
])
entry("downed", "cpr", "rpmedicine:ambu_bag", "СЛР и мешок Амбу", "CPR and the Ambu bag", [
    T("**СЛР**: удерживайте ПКМ пустой рукой на лежачем и жмите **пробел** в ритм (метка в центре). Хорошая СЛР даёт мозгу кислород и иногда ({{15 %}}) сама запускает сердце.\n\n"
      "**Мешок Амбу** — дышать за пациента, когда он не дышит. Тот же ритм. Поднимает SpO₂ выше, чем без него.",
      "**CPR**: hold right-click with an empty hand on a downed player and press **space** in rhythm (mark in the centre). Good CPR keeps the brain oxygenated and sometimes ({{15%}}) restarts the heart.\n\n"
      "**Ambu bag** — breathe for a patient who does not. Same rhythm. Raises SpO₂ higher than without it."),
    S("rpmedicine:ambu_bag", "Держится двумя руками. Уровень 2.", "Held with both hands. Medicine 2."),
])
entry("downed", "defib", "rpmedicine:defibrillator", "Дефибриллятор и адреналин", "Defibrillator and adrenaline", [
    S("rpmedicine:defibrillator", "Разряд при фибрилляции или после цикла СЛР ({{30}} с). Удача ~{{70 %}}. По живому сердцу — ожог и сбой ритма.",
      "A shock for fibrillation or after a CPR cycle ({{30}} s). Success ~{{70%}}. On a beating heart — a burn and arrhythmia."),
    S("rpmedicine:adrenaline", "Адреналин (ручка): поднимает давление и помогает запустить сердце; на {{30–60}} с глушит боль.",
      "Adrenaline (pen): raises pressure, helps restart the heart; numbs pain for {{30–60}} s."),
    T("Не получается поднять? Посмотрите в панели, что мешает: мало крови, напряжённый пневмоторакс, нет СЛР. Подсказка придёт после попытки.",
      "Revival fails? Check the panel for what blocks it: too little blood, tension pneumothorax, no CPR. A hint appears after an attempt."),
])
entry("downed", "airway", "rpmedicine:ammonia", "Нашатырь, воздуховод, интубация", "Ammonia, airway, intubation", [
    S("rpmedicine:ammonia", "Нашатырь будит из обморока. При угрозе жизни и под наркозом не будит.", "Wakes from a faint. Does not work with a threat to life or under anesthesia."),
    S("rpmedicine:airway", "Воздуховод: держит дыхательные пути открытыми у того, кто без сознания. Уровень 2.", "Keeps the airway open in an unconscious patient. Medicine 2."),
    S("rpmedicine:endotracheal_tube", "Интубация: трубка в трахею. Нужен ларингоскоп в инвентаре. Мини-игра: провести трубку между связками, когда раскрыты. Уровень 6.",
      "A tube into the trachea. Needs a laryngoscope in the inventory. Minigame: pass the tube between the cords when they open. Medicine 6."),
])
entry("downed", "carry", "minecraft:lead", "Переноска и обыск", "Carrying and searching", [
    T("**Тащить**: Shift+ПКМ пустой рукой по лежачему. Тело волочится по земле позади, вы идёте медленнее и не бежите.\n\n"
      "**Положить**: Shift+ПКМ по блоку — туда; по койке — на койку.\n\n**Обыск** — из панели **H** лежачего: можно забрать и положить вещи.",
      "**Drag**: Shift+right-click a downed player with an empty hand. The body drags behind you along the ground; you walk slower and cannot run.\n\n"
      "**Put down**: Shift+right-click a block — there; a bed — onto the bed.\n\n**Search** — from the downed player's **H** panel: take and put items."),
])

# ---------------------------------------------------------- Препараты
entry("drugs", "how", "minecraft:potion", "Как действуют препараты", "How drugs work", [
    T("Препарат попадает **в кровь** и выводится постепенно — так получаются срок и сила действия.\n\n"
      "- Ручка (в мышцу) и таблетки — всасываются за 1–2 мин.\n- Шприц в вену — сразу.\n- Капельница — пока капает.\n\n"
      "Сила зависит от количества в крови: около одной дозы — обычное действие, больше — сильнее, но с порога — **передозировка**. Две дозы подряд или доза поверх капельницы её легко дадут.",
      "A drug enters the **blood** and is cleared gradually — that is what sets its strength and duration.\n\n"
      "- A pen (into the muscle) and pills — absorbed in 1–2 min.\n- A syringe into a vein — immediately.\n- A drip — while it drips.\n\n"
      "Strength depends on the amount in the blood: about one dose — normal effect, more — stronger, but past a threshold — **overdose**. Two doses in a row or a dose on top of a drip easily cause it."),
    T("**Вес** важен: доза рассчитана на {{70}} кг — тяжёлому мало, лёгкому много.\n\n**1 мл = 1 стандартная доза.** На этикетке написана концентрация (мг/мл).\n\n"
      "К опиатам, алкоголю и никотину вырабатывается **толерантность** — действуют слабее и короче.",
      "**Weight** matters: a dose is meant for {{70}} kg — too little for a heavy person, too much for a light one.\n\n**1 ml = 1 standard dose.** The label shows the concentration (mg/ml).\n\n"
      "Opioids, alcohol and nicotine build **tolerance** — they work weaker and shorter."),
])
entry("drugs", "pens", "rpmedicine:morphine", "Шприц-ручки", "Injector pens", [
    T("Ручка — для боя и поля: в ней {{4}} мл (4 дозы), полоска показывает остаток, в окошке видно, сколько осталось. Пустая — использованная ручка, её выбрасывают.\n\n"
      "- Уровень ниже {{4}}: дозу выбрать не умеете — уколете 0,75–1,25 дозы наугад.\n"
      "- Уровень {{4}}+: сначала проверка, нужен ли укол (повтор за 5 с — уколете всё равно), потом выбор дозы колёсиком (шаг 0,1) с подсказкой, сколько нужно.",
      "Pens are for combat and the field: {{4}} ml (4 doses), the bar shows what is left, the window shows the level. An empty pen is thrown away.\n\n"
      "- Below Medicine {{4}}: you cannot pick a dose — you inject 0.75–1.25 dose at random.\n"
      "- Medicine {{4}}+: first a check whether it is needed (repeat within 5 s to inject anyway), then pick the dose with the wheel (step 0.1) with a hint how much is needed."),
    T("Потом мини-игра укола и анимация — ручка приставляется и вводится до конца.\n\nРучки: адреналин, морфин, ТХА, кеторолак, налоксон, диазепам, атропин, инсулин.",
      "Then the injection minigame and the animation — the pen is pressed and emptied.\n\nPens: adrenaline, morphine, TXA, ketorolac, naloxone, diazepam, atropine, insulin."),
])
entry("drugs", "vials", "rpmedicine:ceftriaxone", "Флаконы и шприц", "Vials and the syringe", [
    T("Флакон — для госпиталя: {{10}} мл (10 доз). Сам не применяется.\n\n"
      "**Шприц в руку, флакон во вторую, ПКМ** — мини-игра: проткнуть пробку (ЛКМ, когда она над иглой) и набрать мл колёсиком. Подсказка, сколько нужно, — только опытному; неопытный набирает на глаз.\n\n"
      "Набранный шприц — ПКМ по пациенту: в вену. Если стоит катетер — в порт, без мини-игры. После — использованный шприц: стерилизатор вернёт чистым.",
      "A vial is for the hospital: {{10}} ml (10 doses). It is not used by itself.\n\n"
      "**Syringe in hand, vial in the other, right-click** — a minigame: pierce the stopper (LMB when it is over the needle) and draw ml with the wheel. The hint of how much is needed shows only to the experienced; others draw by eye.\n\n"
      "The filled syringe — right-click a patient: into a vein. With a catheter — into the port, no minigame. Afterwards it is a used syringe: the sterilizer cleans it."),
    T("Флаконы: цефтриаксон, лидокаин (местно, в нужную часть тела), кетамин. Норадреналин и пропофол — **в капельницу**: набранным шприцем ПКМ по стойке, в пакет физраствора.",
      "Vials: ceftriaxone, lidocaine (local, into the body part), ketamine. Norepinephrine and propofol go **into a drip**: right-click the IV stand with the filled syringe to add them to a saline bag."),
])
entry("drugs", "pills", "rpmedicine:painkillers", "Таблетки", "Pills", [
    S("rpmedicine:painkillers", "Обезболивающее от небольшой боли.", "For mild pain."),
    S("rpmedicine:paracetamol", "Боль и жар. Много — яд для печени.", "Pain and fever. Too much harms the liver."),
    S("rpmedicine:ibuprofen", "Боль, жар, легче контузия. Передозировка — кровит сильнее.", "Pain, fever, eases concussion. Overdose — more bleeding."),
    S("rpmedicine:tramadol", "Сильная боль. Опиат. Уровень 4.", "Strong pain. An opioid. Medicine 4."),
    S("rpmedicine:amoxicillin", "Антибиотик против инфекции, курс. Уровень 4.", "An antibiotic, a course. Medicine 4."),
    S("rpmedicine:glucose_tablets", "Сахар крови сразу выше — при гипогликемии.", "Raises blood sugar at once — for hypoglycemia."),
    S("rpmedicine:cyclosporine", "Против отторжения пересаженного органа, раз в 12 ч.", "Against transplant rejection, every 12 h."),
    T("Таблетки пьют только в сознании. Действуют через 1–2 минуты.", "Pills only while conscious. They act in 1–2 minutes."),
])
DRUGLIST = [
    ("morphine", "10 мг/мл. Сильное обезболивание, опиат: угнетает дыхание, особенно с диазепамом. Снимает налоксон.",
     "10 mg/ml. Strong analgesia, an opioid: depresses breathing, especially with diazepam. Reversed by naloxone."),
    ("txa", "ТХА, 1000 мг/мл: снижает кровотечение, особенно внутреннее.", "TXA, 1000 mg/ml: reduces bleeding, especially internal."),
    ("ketorolac", "30 мг/мл: обезболивание без опиата. Передозировка — кровит сильнее.", "30 mg/ml: non-opioid analgesia. Overdose — more bleeding."),
    ("naloxone", "0,4 мг/мл: снимает действие опиатов сразу. Уровень 4.", "0.4 mg/ml: reverses opioids at once. Medicine 4."),
    ("diazepam", "5 мг/мл: успокаивает, сонливость, пульс ниже. С опиатом угнетает дыхание.", "5 mg/ml: sedation, drowsiness, lower pulse. With an opioid depresses breathing."),
    ("atropine", "1 мг/мл: учащает редкий пульс. Уровень 5.", "1 mg/ml: speeds up a slow pulse. Medicine 5."),
    ("insulin", "10 ЕД/мл: снижает сахар (диабет). Много — гипогликемия.", "10 IU/ml: lowers blood sugar (diabetes). Too much — hypoglycemia."),
    ("lidocaine", "20 мг/мл, флакон: местное обезболивание части тела — для швов и операций.", "20 mg/ml, vial: local anesthesia of a body part — for sutures and surgery."),
    ("ketamine", "50 мг/мл, флакон: наркоз на несколько минут, дыхание сохраняет. Уровень 7.", "50 mg/ml, vial: anesthesia for a few minutes, keeps breathing. Medicine 7."),
    ("ceftriaxone", "250 мг/мл, флакон: антибиотик уколом, сильнее таблеток. Уровень 5.", "250 mg/ml, vial: injected antibiotic, stronger than pills. Medicine 5."),
    ("propofol", "10 мг/мл, в капельницу: наркоз держится, пока капает; угнетает дыхание — нужна интубация. Уровень 7.",
     "10 mg/ml, into a drip: anesthesia lasts while it drips; depresses breathing — intubate. Medicine 7."),
    ("norepinephrine", "1 мг/мл, в капельницу: держит давление при шоке. Уровень 6.", "1 mg/ml, into a drip: keeps blood pressure in shock. Medicine 6."),
    ("iv_glucose", "В пакет физраствора: калории и сахар. Внутривенное питание — нужен RP Culinary.", "Into a saline bag: calories and sugar. IV feeding — needs RP Culinary."),
    ("iv_amino_acids", "В пакет физраствора: белок.", "Into a saline bag: protein."),
    ("iv_lipids", "В пакет физраствора: жиры, много калорий.", "Into a saline bag: fat, lots of calories."),
    ("iv_vitamins", "В пакет физраствора: витамины.", "Into a saline bag: vitamins."),
]
entry("drugs", "list", "rpmedicine:atropine", "Справочник препаратов", "Drug list", [S(f"rpmedicine:{d}", ru, en) for d, ru, en in DRUGLIST])
entry("drugs", "dosing", "rpmedicine:filled_syringe", "Дозы и передозировка", "Doses and overdose", [
    T("Подсказка дозы учитывает вес пациента. Половина дозы — слабее и короче; полторы — сильнее, но ближе к передозировке.\n\n"
      "Передозировка: у опиатов — остановка дыхания, у диазепама — глубокий сон и угнетение дыхания, у парацетамола — печень, у НПВС — кровотечение, у норадреналина — пульс и давление зашкаливают.\n\n"
      "Налоксон снимает опиаты сразу.",
      "The dose hint accounts for the patient's weight. Half a dose — weaker and shorter; one and a half — stronger, but closer to overdose.\n\n"
      "Overdose: opioids — respiratory arrest, diazepam — deep sleep and depressed breathing, paracetamol — the liver, NSAIDs — bleeding, norepinephrine — racing pulse and pressure.\n\n"
      "Naloxone reverses opioids at once."),
], level=4)

# ---------------------------------------------------------- Кровь
entry("blood", "volume", "rpmedicine:blood_bag", "Кровь и кровопотеря", "Blood and blood loss", [
    T("Крови ~{{70}} мл на кг веса — у человека в {{70}} кг около 4,9 л. Без кровотечения восстанавливается {{500}} мл в час в сети.\n\n"
      "Кровопотеря: бледность, слабость, учащённый пульс, низкое давление, серость в глазах. Потеря {{50 %}} — остановка сердца.\n\n"
      "Группа крови у каждого своя (её видно в медкарте и анализе). Переливать только совместимую.",
      "About {{70}} ml of blood per kg — around 4.9 l for {{70}} kg. Without bleeding it regenerates {{500}} ml per hour online.\n\n"
      "Blood loss: pallor, weakness, fast pulse, low pressure, greying vision. Losing {{50%}} stops the heart.\n\n"
      "Everyone has a blood type (see the medical record and blood tests). Transfuse only compatible blood."),
])
entry("blood", "saline", "rpmedicine:saline", "Физраствор", "Saline", [
    S("rpmedicine:saline", "{{500}} мл: возвращает объём, но не несёт кислород. Заменяет не больше {{1/3}} крови и уходит из сосудов ({{25 %}} в час).",
      "{{500}} ml: restores volume but carries no oxygen. Replaces up to {{1/3}} of blood and leaks out ({{25%}}/h)."),
    T("В поле — пакет в руке, пациент стоит или лежит на месте; капает {{2}} мин. Со стойкой — пациент может ходить.",
      "In the field — the bag in hand, the patient stays still; drips for {{2}} min. With an IV stand the patient can walk."),
])
entry("blood", "donation", "rpmedicine:empty_blood_bag", "Забор и переливание", "Donation and transfusion", [
    S("rpmedicine:empty_blood_bag", "Забор {{450}} мл у донора: стоять на месте ~30 с. Донор теряет не больше {{15 %}}. Уровень 3.",
      "Collects {{450}} ml from a donor, standing still ~30 s. Donor loses at most {{15%}}. Medicine 3."),
    S("rpmedicine:blood_bag", "Пакет подписан группой донора. Переливание {{4}} мин. Вне холодильника портится за {{2}} ч; в холодильнике — не портится.",
      "Labelled with the donor's type. Transfusion {{4}} min. Spoils in {{2}} h out of a fridge; never in a fridge."),
    T("**Несовместимая кровь**: реакция на {{10–20}} мин — жар, боль, падает давление, бьёт по почкам; может остановить сердце. Остановите переливание, помогут физраствор и адреналин.\n\n"
      "**Испорченная кровь** темнеет на иконке. Перелить можно, но это сепсис.",
      "**Incompatible blood**: a {{10–20}} min reaction — fever, pain, falling pressure, kidney damage; may stop the heart. Stop the transfusion; saline and adrenaline help.\n\n"
      "**Spoiled blood** looks dark on the icon. It can be transfused, but that means sepsis."),
], level=3)
entry("blood", "stand", "rpmedicine:iv_stand", "Стойка капельницы", "IV stand", [
    T("1. **Повесить**: ПКМ пакетом по стойке (до 3 пакетов; видно на крючках).\n2. **Катетер**: поставить пациенту в руку (мини-игра вены).\n"
      "3. **Шланг**: ПКМ пустой рукой по стойке, потом ПКМ по пациенту.\n\nКапает первый непустой пакет; опустел — шланг переходит к следующему.",
      "1. **Hang**: right-click the stand with a bag (up to 3, shown on the hooks).\n2. **Catheter**: put it into the patient's arm (vein minigame).\n"
      "3. **Hose**: right-click the stand with an empty hand, then the patient.\n\nThe first non-empty bag drips; when it is empty the hose moves to the next."),
    T("Пациент ходит в пределах {{4}} блоков. Дальше — катетер вырывает, рана на руке.\n\nВзгляд на стойку показывает, что висит и сколько осталось.\n\n"
      "Shift+ПКМ пустой рукой — снять пакет: начатый — с остатком в мл (можно повесить обратно), пустой — в мусор. В панели: «Снять капельницу» (остаток в пакет), «Вынуть катетер».",
      "The patient can walk within {{4}} blocks. Further — the catheter tears out, an arm wound.\n\nLooking at the stand shows what hangs and how much is left.\n\n"
      "Shift+right-click with an empty hand — take a bag off: an opened one keeps its ml (hang it back later), an empty one is trash. Panel: \"Stop drip\" (the rest goes back), \"Remove catheter\"."),
    S("rpmedicine:iv_catheter", "Венозный катетер: через него капает стойка; шприц — в порт без мини-игры.", "The stand drips through it; a syringe goes into its port without a minigame."),
    T("Норадреналин или пропофол набранным шприцем — ПКМ по стойке: в пакет физраствора. Пакет желтеет (норадреналин) или становится молочным (пропофол) и капает {{20}} мин, вводя препарат понемногу.",
      "Norepinephrine or propofol in a filled syringe — right-click the stand: into a saline bag. The bag turns yellow (norepinephrine) or milky (propofol) and drips for {{20}} min, delivering the drug gradually."),
    T("**Внутривенное питание** (нужен RP Culinary): глюкоза, аминокислоты, жировая эмульсия, витамины — так же шприцем в пакет физраствора. "
      "За {{20}} мин капельницы поднимает сытость и нутриенты мимо желудка; потом несколько часов плохой аппетит. Сам физраствор поит.",
      "**IV feeding** (needs RP Culinary): glucose, amino acids, lipid emulsion, vitamins — the same way, by syringe into a saline bag. "
      "Over a {{20}}-min drip it raises satiety and nutrients bypassing the stomach; poor appetite for a few hours afterwards. Saline itself hydrates."),
])

# ---------------------------------------------------------- Диагностика
entry("diagnostics", "exam", "rpmedicine:medcard", "Осмотр", "Examination", [
    T("Панель **H** показывает то, что видно глазами: раны, кровь, бледность, дыхание. Чем выше уровень, тем больше деталей: с {{4}} — цифры.\n\n"
      "Приборы показывают точнее. Без нужного уровня показания легко прочитать неправильно.",
      "The **H** panel shows what the eye sees: wounds, blood, pallor, breathing. The higher your level, the more detail: from {{4}} — numbers.\n\n"
      "Devices are more precise. Below their level readings are easy to misread."),
])
entry("diagnostics", "devices", "rpmedicine:tonometer", "Приборы", "Devices", [
    S("rpmedicine:stethoscope", "Дыхание и сердце. Мини-игра: приложить головку к точкам спереди и сзади и подержать.",
      "Breathing and heart. Minigame: put the chestpiece on the points front and back and hold."),
    S("rpmedicine:tonometer", "Давление. Мини-игра: накачать колёсиком, стравливать, пробелом отметить появление и исчезновение тонов.",
      "Blood pressure. Minigame: pump with the wheel, deflate, press space when the sounds start and stop."),
    S("rpmedicine:pulse_oximeter", "Пульс и SpO₂ — сразу.", "Pulse and SpO₂ — instantly."),
    S("rpmedicine:thermometer", "Температура тела: жар — инфекция или сепсис.", "Body temperature: fever means infection or sepsis."),
    S("rpmedicine:glucometer", "Сахар крови. Нужен ланцет.", "Blood sugar. Needs a lancet."),
    S("rpmedicine:hemoanalyzer", "Карманный анализ крови: гемоглобин, лейкоциты. Нужен ланцет. Уровень 4.", "Pocket blood test. Needs a lancet. Medicine 4."),
    S("rpmedicine:portable_scanner", "Сканер части тела: переломы, органы, инородные тела. Уровень 4.", "Scans a body part: fractures, organs, foreign bodies. Medicine 4."),
])
entry("diagnostics", "lab", "rpmedicine:lab_report", "Лаборатория", "The lab", [
    T("1. **Ланцет** и пустая **пробирка** в инвентаре — ПКМ ланцетом по пациенту: пробирка с кровью, подписана им.\n"
      "2. Пробирка в руке — ПКМ по **лабораторному столу**. Анализ — с уровня {{3}}.\n"
      "3. Бланк открывается сразу и остаётся предметом: перечитать — ПКМ.\n\nПакет крови во второй руке — бланк покажет совместимость.",
      "1. A **lancet** and an empty **test tube** in the inventory — right-click the patient with the lancet: a labelled blood sample.\n"
      "2. The sample in hand — right-click the **lab table**. Tests need Medicine {{3}}.\n"
      "3. The report opens at once and stays as an item: right-click to reread.\n\nA blood bag in the other hand — the report shows compatibility."),
    T("На бланке: группа, гемоглобин, лейкоциты, сепсис, печень (АЛТ), почки (креатинин), сердце (тропонин), питание (альбумин, триглицериды, глюкоза, B12). Вне нормы — красным со стрелкой.\n\n"
      "Проба портится в тепле за {{2}} ч; в термостате — нет. В медкарту бланк попадает, только если нажать «Вложить анализ» в карте пациента.",
      "The report: type, hemoglobin, white cells, sepsis, liver (ALT), kidneys (creatinine), heart (troponin), nutrition (albumin, triglycerides, glucose, B12). Out of range — red with an arrow.\n\n"
      "A sample spoils in {{2}} h when warm; never in the thermostat. It reaches the medical record only via \"Attach test\" in the patient's record."),
], level=3)

# ---------------------------------------------------------- Госпиталь
entry("hospital", "bed", "rpmedicine:hospital_bed", "Койка", "Hospital bed", [
    S("rpmedicine:hospital_bed", "ПКМ пустой рукой — лечь (головой к изголовью). Встать — присесть. Лежачего — Shift+ПКМ.",
      "Right-click with an empty hand to lie down (head to the headboard). Sneak to get up. A dragged patient — Shift+right-click."),
    T("На койке: раны заживают в {{1,5}} раза быстрее, кровь восстанавливается в {{1,5}} раза, мозг — в {{2}} раза, инфекции реже (×{{0,7}}).\n\n"
      "Кровати других модов тоже работают как койки.",
      "On a bed: wounds heal {{1.5}} times faster, blood regenerates {{1.5}} times faster, the brain {{2}} times faster, fewer infections (×{{0.7}}).\n\n"
      "Beds from other mods work as hospital beds too."),
])
entry("hospital", "monitor", "rpmedicine:vitals_monitor", "Монитор и кислород", "Monitor and oxygen", [
    S("rpmedicine:vitals_monitor", "Показывает пациента на койке в {{3}} блоках: пульс, давление, SpO₂, дыхание. Посмотрите на него. Тревога — при SpO₂ ниже {{85}} или остановке.",
      "Shows a patient on a bed within {{3}} blocks: pulse, pressure, SpO₂, breathing. Look at it. Alarm at SpO₂ below {{85}} or arrest."),
    S("rpmedicine:oxygen_tank", "Кислород для пациента на койке в {{3}} блоках: потолок SpO₂ возвращается наполовину.",
      "Oxygen for a patient on a bed within {{3}} blocks: halves the SpO₂ ceiling loss."),
])
entry("hospital", "or", "rpmedicine:operating_table", "Операционный стол", "Operating table", [
    S("rpmedicine:operating_table", "Сам себе монитор, кислород и аппарат ИВЛ. Лучшее место для операции. Можно зафиксировать пациента (панель) — он не встанет и не дёрнется.",
      "Its own monitor, oxygen and ventilator. The best place to operate. The patient can be restrained (panel) — cannot get up or jerk."),
])
entry("hospital", "rooms", "rpmedicine:sterilizer", "Стерилизатор, хранение, лаборатория", "Sterilizer, storage, lab", [
    S("rpmedicine:sterilizer", "ПКМ грязным шприцем, пробиркой или инструментом — снова стерильные.", "Right-click with a used syringe, tube or instrument — sterile again."),
    S("rpmedicine:medicine_cabinet", "Шкафчик: хранит только медицину.", "Stores medical items only."),
    S("rpmedicine:medicine_crate", "Ящик: хранит только медицину.", "Stores medical items only."),
    S("rpmedicine:thermostat", "Термостат: только пробирки с кровью, в нём они не портятся.", "Blood samples only; they do not spoil inside."),
    S("rpmedicine:lab_table", "Лабораторный стол: анализ крови.", "The blood test table."),
    T("Холодильник для крови и органов — из мода мебели (холодильник Refurbished Furniture): в нём кровь и органы не портятся.",
      "The fridge for blood and organs comes from the furniture mod (Refurbished Furniture fridge): nothing spoils inside."),
])

# ---------------------------------------------------------- Хирургия
entry("surgery", "prep", "rpmedicine:surgical_mask", "Подготовка", "Preparation", [
    T("Перед операцией:\n- **наркоз**: кетамин (на минуты) или пропофол капельницей (пока капает); при пропофоле — интубация;\n- или **местно**: лидокаин в нужную часть;\n"
      "- **маска** и **перчатки**, **стерильные** инструменты;\n- место: стол лучше койки, койка лучше земли.\n\nВ сознании без обезболивания каждый шаг — сильная боль ({{80}}).",
      "Before surgery:\n- **anesthesia**: ketamine (minutes) or a propofol drip (while it drips); with propofol — intubate;\n- or **local**: lidocaine into the part;\n"
      "- **mask** and **gloves**, **sterile** instruments;\n- place: a table beats a bed, a bed beats the ground.\n\nAwake without analgesia every step hurts badly ({{80}})."),
    S("rpmedicine:surgical_mask", "Надевается на лицо. Без неё заражение ×{{1,3}}.", "Worn on the face. Without it infection ×{{1.3}}."),
    S("rpmedicine:surgical_gloves", "Без перчаток заражение ×{{1,3}}.", "Without gloves infection ×{{1.3}}."),
], level=7)
entry("surgery", "steps", "rpmedicine:retractor", "Шаги операции", "Surgery steps", [
    S("rpmedicine:scalpel", "1. **Разрез**: провести скальпелем по линии ровно и не спеша.", "1. **Incision**: run the scalpel along the line, steady and slow."),
    S("rpmedicine:hemostat", "2. **Зажимы**: поймать пульсирующие точки кровотечения.", "2. **Clamps**: catch the pulsing bleeding points."),
    S("rpmedicine:retractor", "3. **Ранорасширитель**: медленно развести края — сначала верхний.", "3. **Retractor**: spread the edges slowly — the upper one first."),
    T("Дальше — то, ради чего оперируете: достать пули, ушить внутреннее кровотечение или орган, сосудистый шов, остеосинтез (дрель и набор пластин), дренаж груди, изъятие или пересадка органа.\n\n"
      "В конце — **закрыть** операцию швами. Открытая рана сильно кровит ({{300}} мл/мин без зажимов) и быстро заражается.",
      "Then whatever you operate for: remove bullets, suture internal bleeding or an organ, vascular suture, osteosynthesis (drill and plate kit), chest drain, organ removal or transplant.\n\n"
      "Finally **close** with sutures. An open wound bleeds heavily ({{300}} ml/min without clamps) and gets infected fast."),
    S("rpmedicine:vascular_suture", "Сосудистый шов: останавливает артериальное кровотечение.", "Stops arterial bleeding."),
    S("rpmedicine:surgical_drill", "Дрель с набором остеосинтеза: перелом срастается в {{3}} раза быстрее.", "With the osteosynthesis kit: the fracture heals {{3}} times faster."),
    S("rpmedicine:chest_drain", "Дренаж груди: снимает пневмоторакс.", "Treats pneumothorax."),
], level=7)
entry("surgery", "organs", "rpmedicine:organ_container", "Органы", "Organs", [
    T("Органы: **сердце**, **лёгкие**, **печень**, **почки**, **кишечник** (живот), **селезёнка**. Повреждаются ранениями живота и груди, сепсисом, нехваткой кислорода.\n\n"
      "До {{50 %}} заживают сами, сильнее — только хирург. Лёгкие — SpO₂ ниже; печень — кровит сильнее; почки — страдает мозг; сердце — давление ниже; кишечник на 100 % — перитонит и сепсис; селезёнка — кровотечение в живот, без неё инфекции тяжелее.",
      "Organs: **heart**, **lungs**, **liver**, **kidneys**, **intestines**, **spleen**. Damaged by chest and abdominal wounds, sepsis, lack of oxygen.\n\n"
      "Up to {{50%}} they heal by themselves, worse — only a surgeon. Lungs — lower SpO₂; liver — more bleeding; kidneys — the brain suffers; heart — lower pressure; intestines at 100% — peritonitis and sepsis; spleen — abdominal bleeding, without it infections are worse."),
    T("**Изъятие**: связки режет скальпель (в инвентаре), орган — в контейнер. Мозг тоже можно изъять — это смерть, даже из клинической.\n\n"
      "**Пересадка**: орган донора на место и сшить сосуд. Чужая группа — **отторжение**: циклоспорин раз в 12 ч. Орган в тепле портится за {{1}} ч, в холодильнике — нет.",
      "**Removal**: the scalpel (in the inventory) cuts the ligaments, the organ goes into a container. The brain can be removed too — that is death, even from clinical death.\n\n"
      "**Transplant**: place the donor organ and suture the vessel. A different blood type — **rejection**: cyclosporine every 12 h. An organ spoils in {{1}} h when warm, never in a fridge."),
], level=7)
entry("surgery", "amputation", "rpmedicine:bone_saw", "Ампутация и протезы", "Amputation and prostheses", [
    S("rpmedicine:bone_saw", "Ампутация при некрозе или раздроблении. Пилить ровно, не выходя из распила.", "For necrosis or crushed limbs. Saw evenly without leaving the cut."),
    T("Культя заживает, потом можно поставить протез: стопа, деревянная нога, крюк. Протез стопы — только на живую голень.\n\n"
      "Отрубленную конечность можно пришить в течение {{6}} ч.\n\nСильный взрыв может оторвать конечность сам.",
      "The stump heals, then a prosthesis fits: a foot, a peg leg, a hook. A foot prosthesis only on a living shin.\n\n"
      "A severed limb can be reattached within {{6}} h.\n\nA strong explosion can tear off a limb by itself."),
    S("rpmedicine:peg_leg", "Деревянная нога: ходить можно, но медленнее.", "Walk again, slower."),
], level=7)

# ---------------------------------------------------------- Вещества
entry("life", "substances", "minecraft:honey_bottle", "Алкоголь, табак, кофе, опиаты", "Alcohol, tobacco, coffee, opioids", [
    T("**Алкоголь** опьяняет: сильно — тошнит, совсем сильно — теряешь сознание. **Табак** немного обезболивает, у курильщиков страдают лёгкие. **Кофеин** учащает пульс. **Опиаты** глушат боль и угнетают дыхание.\n\n"
      "Частое употребление даёт **толерантность** и **зависимость**: без вещества начинается ломка — боль, всё медленнее.",
      "**Alcohol** intoxicates: a lot — nausea, too much — you pass out. **Tobacco** slightly numbs pain, smokers' lungs suffer. **Caffeine** speeds up the pulse. **Opioids** numb pain and depress breathing.\n\n"
      "Frequent use builds **tolerance** and **dependence**: without the substance comes withdrawal — pain, everything slower."),
])
entry("life", "food", "minecraft:bread", "Питание", "Nutrition", [
    T("Питание ведёт мод **RP Culinary**: сытость в калориях, вода, белки, жиры, углеводы, витамины и вес. Клавиша **K** — как вы себя чувствуете.\n\n"
      "Как питание действует на тело:\n- Мало белка — раны заживают дольше, удар слабее.\n- Мало жиров — зябко.\n- Мало углеводов — нет сил, быстро устаёте.\n"
      "- Мало витаминов — болеете чаще.\n- Всё в норме — заживление и иммунитет лучше.\n\nТочно — только анализом крови.",
      "Nutrition is handled by the **RP Culinary** mod: satiety in calories, water, protein, fat, carbs, vitamins and body weight. Key **K** — how you feel.\n\n"
      "How nutrition affects the body:\n- Low protein — slow healing, weaker hits.\n- Low fat — chilly.\n- Low carbs — no energy.\n"
      "- Low vitamins — sick more often.\n- Balanced — better healing and immunity.\n\nExact values — only by a blood test."),
    T("**Голод** не убивает: сначала «нет сил», потом голодные обмороки. Голодный хуже заживает. **Обезвоживание** снижает объём крови и давление.\n\n"
      "Кто не может есть — **внутривенное питание** на капельнице (раздел «Стойка капельницы»).",
      "**Hunger** does not kill: first weakness, then hunger faints. The hungry heal worse. **Dehydration** lowers blood volume and pressure.\n\n"
      "Those who cannot eat — **IV feeding** on the drip stand (see \"IV stand\")."),
])
entry("life", "diabetes", "rpmedicine:glucometer", "Диабет", "Diabetes", [
    T("Диабетик (перк) следит за сахаром: глюкометр, инсулин раз в 8–12 ч, еда поднимает сахар.\n\n"
      "Низкий сахар — слабость, обморок, страдает мозг: глюкоза в таблетках. Высокий — жажда, потом кома.",
      "A diabetic (perk) watches blood sugar: glucometer, insulin every 8–12 h, food raises sugar.\n\n"
      "Low sugar — weakness, fainting, brain damage: glucose tablets. High — thirst, then coma."),
])

# ---------------------------------------------------------- Медкарта
entry("medcard", "card", "rpmedicine:medcard", "Медкарта", "Medical record", [
    S("rpmedicine:medcard", "Пустую карту ПКМ по игроку — привязать к нему. Открыть — ПКМ.", "Right-click a player with a blank card to bind it. Right-click to open."),
    T("Титул: ФИО и позывной, дата рождения, пол, группа крови, отдел, аллергии, хронические болезни, импланты, особые отметки. Карандаш в углу — режим правки.\n\n"
      "Анамнез: дата, травма или диагноз, обстоятельства, последствия.",
      "Title page: name and callsign, birth date, sex, blood type, unit, allergies, chronic conditions, implants, special notes. The pencil in the corner — edit mode.\n\n"
      "History: date, injury or diagnosis, circumstances, consequences."),
    T("Записи предлагаются сами, когда врач **с медкартой в инвентаре** осмотрел пациента и нашёл проблему (прибором или осмотром): принять ✓, отклонить ✗ или исправить.\n\n"
      "Бланк анализа — кнопкой «Вложить анализ», если он в инвентаре.",
      "Entries are proposed when a doctor **carrying a medical record** examines the patient and finds a problem (device or examination): accept ✓, decline ✗ or edit.\n\n"
      "A blood test report — the \"Attach test\" button, if it is in your inventory."),
])

# ---------------------------------------------------------- ГМ
entry("gm", "commands", "minecraft:command_block", "Команды", "Commands", [
    T("Все команды — **/rpmedicine ...**, для операторов.\n\n**inspect <цель> [full]** — состояние в чат.\n**heal <цели> [часть]** — вылечить.\n**revive <цели>** — поднять.\n"
      "**kill <цели>** — смерть.\n**skill <игрок> <0–10|reset>** — уровень медицины.",
      "All commands are **/rpmedicine ...**, for operators.\n\n**inspect <target> [full]** — state in chat.\n**heal <targets> [part]** — heal.\n**revive <targets>** — revive.\n"
      "**kill <targets>** — death.\n**skill <player> <0–10|reset>** — Medicine level."),
    T("**injure <цели> <тип> <часть> <тяжесть>** — нанести рану (cut, stab, gunshot, shrapnel, bruise, burn...).\n**amputate / restore <цели> <часть>** — отнять или вернуть конечность.\n"
      "**set <цели> <параметр> <значение>** — кровь, SpO₂, сознание, температура, органы (organ), толерантность, нутриенты, пули...",
      "**injure <targets> <type> <part> <severity>** — inflict a wound (cut, stab, gunshot, shrapnel, bruise, burn...).\n**amputate / restore <targets> <part>** — remove or restore a limb.\n"
      "**set <targets> <param> <value>** — blood, SpO₂, consciousness, temperature, organs, tolerance, nutrients, bullets..."),
    T("**time add <игроки> <время> [offline]** — прокрутить время лечения (заживление, жгут, некроз, сепсис).\n**food add <игроки> <время>** — прокрутить голод и жажду (с RP Culinary — его).\n"
      "**bed <цели> <x y z>** — уложить на койку.\n**card <игрок> <поле> <значение>** — поле медкарты.\n**stats [часы] / stats history <игрок>** — журнал.\n"
      "**panel** — панель ГМа.\n**kit <набор> [игроки]** — выдать набор.\n**reload** — перечитать датапаки.",
      "**time add <players> <time> [offline]** — fast-forward treatment time (healing, tourniquet, necrosis, sepsis).\n**food add <players> <time>** — fast-forward hunger and thirst (RP Culinary's, if installed).\n"
      "**bed <targets> <x y z>** — put on a bed.\n**card <player> <field> <value>** — a medical record field.\n**stats [hours] / stats history <player>** — the log.\n"
      "**panel** — the GM panel.\n**kit <kit> [players]** — give a kit.\n**reload** — reload datapacks."),
    T("Наборы: **field** (полевой медик), **resus** (реанимация и капельницы), **diag** (диагностика), **surgeon** (хирург), **transplant** (органы и протезы), **drugs** (все препараты), "
      "**food**, **substances**, **gm**, **all**.",
      "Kits: **field**, **resus** (resuscitation and IVs), **diag**, **surgeon**, **transplant** (organs and prostheses), **drugs**, **food**, **substances**, **gm**, **all**."),
], gm=True)
entry("gm", "panel", "rpmedicine:gm_scanner", "Панель ГМа и сканер", "GM panel and scanner", [
    S("rpmedicine:gm_scanner", "Инструмент ведущего: ПКМ по игроку — полная панель с цифрами и правкой.", "Right-click a player — the full panel with numbers and editing."),
    T("Панель (**/rpmedicine panel** или сканер): список игроков, вкладки «Осмотр» (по показателю на строку), «Тело» (силуэт: добавить рану, вылечить часть), «Показатели» (кровь, SpO₂, мозг, давление... — правка значений), препараты с дозой.",
      "The panel (**/rpmedicine panel** or the scanner): player list, tabs Examination, Body (silhouette: add a wound, heal a part), Vitals (blood, SpO₂, brain, pressure... — edit values), drugs with a dose."),
], gm=True)
entry("gm", "config", "minecraft:comparator", "Настройки сервера", "Server settings", [
    T("Файл **world/serverconfig/rpmedicine-server.toml** — все числа мода, с комментариями по-русски. В книге значения из конфига выделены {{цветом}} — указаны стандартные.\n\n"
      "Главные:\n- **no_death_mode** — без смерти (тест).\n- **minigames_enabled**, **minigames_in_combat** (мини-игры и в бою: {{true}}).\n- **bleed_multiplier**, **pain_multiplier**, **heal_speed_multiplier**.",
      "File **world/serverconfig/rpmedicine-server.toml** — every number of the mod, commented in Russian. In this book config values are {{coloured}} — the defaults are shown.\n\n"
      "Main ones:\n- **no_death_mode** — no death (testing).\n- **minigames_enabled**, **minigames_in_combat** (minigames in combat too: {{true}}).\n- **bleed_multiplier**, **pain_multiplier**, **heal_speed_multiplier**."),
    T("- **knockdown_min/max_seconds** — таймер нокдауна.\n- **dosing_min_level**, **numbers_min_level**, **lab_min_level**.\n- **iv_hose_length** — шланг стойки.\n"
      "- **book_skill_gating** — разделы этой книги по уровню медицины ({{false}} — всем всё).\n- **organs_enabled**, **infection_enabled**, **nutrition_enabled**, **substances_enabled** — отключаемые системы.",
      "- **knockdown_min/max_seconds** — knockdown timer.\n- **dosing_min_level**, **numbers_min_level**, **lab_min_level**.\n- **iv_hose_length** — IV hose.\n"
      "- **book_skill_gating** — this book's sections by Medicine level ({{false}} — everything for everyone).\n- **organs_enabled**, **infection_enabled**, **nutrition_enabled**, **substances_enabled** — systems that can be switched off."),
], gm=True)
entry("gm", "datapacks", "minecraft:knowledge_book", "Датапаки", "Datapacks", [
    T("Папка **data/<мод>/rpmedicine/...** в датапаке мира:\n- **items** — какой предмет что лечит, время, уровень;\n- **item_aliases** — предметы других модов как наши;\n"
      "- **drugs** — препараты: эффекты, дозы, полувыведение, мг;\n- **hospital_blocks** — какие блоки чего модов — койка, монитор, стерилизатор, холодильник, лаборатория, кислород;",
      "Folder **data/<mod>/rpmedicine/...** in a world datapack:\n- **items** — what an item treats, time, level;\n- **item_aliases** — other mods' items as ours;\n"
      "- **drugs** — effects, doses, half-life, mg;\n- **hospital_blocks** — which blocks are a bed, monitor, sterilizer, fridge, lab, oxygen;"),
    T("- **substances** — алкоголь, табак и т. п. из модов;\n- состав продуктов — в датапаке RP Culinary;\n- **damage_sources** — какой урон какую рану даёт;\n- **mobs** — травмы мобов;\n"
      "- **use_times** — минимальное время применения (длина анимации).\n\nПодробно — docs/datapack.md в исходниках мода.",
      "- **substances** — alcohol, tobacco etc. from mods;\n- food composition — in the RP Culinary datapack;\n- **damage_sources** — which damage makes which wound;\n- **mobs** — mob injuries;\n"
      "- **use_times** — minimum use time (animation length).\n\nDetails — docs/datapack.md in the mod sources."),
], gm=True)


# ------------------------------------------------------------------ запись
def page_json(p, lang, first):
    li = 0 if lang == "ru_ru" else 1
    if p[0] == "text":
        return [{"type": "patchouli:text", "text": fmt(t)} for t in split(p[1 + li], first)]
    if p[0] == "spot":
        return [{"type": "patchouli:spotlight", "item": p[1], "text": fmt(p[2 + li])}]
    raise ValueError(p)


def main():
    if os.path.isdir(BOOK_ASSETS):
        shutil.rmtree(BOOK_ASSETS)
    write(os.path.join(BOOK_DATA, "book.json"), {
        "name": "book.rpmedicine.guide.name", "landing_text": "book.rpmedicine.guide.landing", "subtitle": "RP Medicine",
        "use_resource_pack": True, "i18n": True, "show_progress": False, "version": 1,
        "book_texture": "patchouli:textures/gui/book_red.png", "model": "patchouli:book_red", "creative_tab": "rpmedicine:main",
        "macros": {"$(cfg)": "$(" + CFG_COLOR + ")"},
    })
    # Скрытые достижения: ГМ и уровни медицины (выдаёт GuideBook.java).
    for name in ["gm"] + [f"medicine_{n}" for n in range(1, 11)]:
        write(os.path.join(ADV, f"{name}.json"), {"criteria": {"unlock": {"trigger": "minecraft:impossible"}}})
    for lang, li in (("ru_ru", 0), ("en_us", 1)):
        base = os.path.join(BOOK_ASSETS, lang)
        for i, (cid, icon, ru, en, dru, den) in enumerate(CATS):
            cat = {"name": (ru, en)[li], "description": (dru, den)[li], "icon": icon, "sortnum": i}
            if cid == "gm":
                cat["secret"] = True
            write(os.path.join(base, "categories", f"{cid}.json"), cat)
        order = {}
        for e in E:
            order[e["cat"]] = order.get(e["cat"], -1) + 1
            pages = []
            for p in e["pages"]:
                pages += page_json(p, lang, not pages)
            ej = {"name": e["name"][li], "icon": e["icon"], "category": f"rpmedicine:{e['cat']}", "sortnum": order[e["cat"]], "pages": pages}
            if e["gm"]:
                ej["advancement"] = "rpmedicine:book/gm"
                ej["secret"] = True
            elif e["level"]:
                ej["advancement"] = f"rpmedicine:book/medicine_{e['level']}"
            write(os.path.join(base, "entries", e["cat"], f"{e['id']}.json"), ej)
    # Рецепт: книга + бинт (только если Patchouli установлен).
    write(os.path.join(RES, "data", "rpmedicine", "recipes", "guide_book.json"), {
        "conditions": [{"type": "forge:mod_loaded", "modid": "patchouli"}],
        "type": "patchouli:shapeless_book_recipe", "ingredients": [{"item": "minecraft:book"}, {"item": "rpmedicine:bandage"}],
        "book": "rpmedicine:guide"})
    print("книга:", len(CATS), "разделов,", len(E), "записей")


if __name__ == "__main__":
    main()
