# Сервер для живого теста с игроками: папка run_live (python scripts/live_world.py, потом gradlew runServer -Plive=true).
# Голая сборка: RP Medicine + RP Perks + Patchouli. Офлайн-режим, порт 25565, RCON выключен, оператор — obj_a-001.
# Мир — плоский, госпиталь у точки появления строится сам при первом запуске (датапак rpm_live, потом его можно убрать:
# python scripts/live_world.py --clean).
import hashlib
import json
import os
import shutil
import sys
import uuid

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..")
LIVE = os.path.join(ROOT, "run_live")
PACK = os.path.join(LIVE, "world", "datapacks", "rpm_live")
OPS = ["obj_a-001"]
Y = -60  # уровень пола плоского мира (трава на -61)


def offline_uuid(name):
    h = bytearray(hashlib.md5(("OfflinePlayer:" + name).encode("utf-8")).digest())
    h[6] = (h[6] & 0x0F) | 0x30
    h[8] = (h[8] & 0x3F) | 0x80
    return str(uuid.UUID(bytes=bytes(h)))


def w(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


# ------------------------------------------------------------------ команды постройки
L = []


def at(x, y, z):
    return f"{x} {Y + y} {z}"


def fill(x1, y1, z1, x2, y2, z2, block, mode=""):
    L.append(f"fill {at(x1, y1, z1)} {at(x2, y2, z2)} {block} {mode}".rstrip())


def put(x, y, z, block):
    L.append(f"setblock {at(x, y, z)} {block} replace")


def sign_nbt(lines, color="black"):
    msgs = ",".join("'" + json.dumps({"text": t, "bold": i == 0, "color": color if i == 0 else "black"}, ensure_ascii=False) + "'"
                    for i, t in enumerate((lines + ["", "", "", ""])[:4]))
    return f"{{front_text:{{messages:[{msgs}]}},is_waxed:1b}}"


def wall_sign(x, y, z, facing, lines, wood="birch", color="dark_red"):
    put(x, y, z, f"minecraft:{wood}_wall_sign[facing={facing}]{sign_nbt(lines, color)}")


def bed(block, hx, z, facing):
    """Двухблочная мебель вдоль: изголовье в (hx, z), ноги — против facing."""
    dx = {"west": 1, "east": -1}.get(facing, 0)
    dz = {"north": 1, "south": -1}.get(facing, 0)
    put(hx + dx, 0, z + dz, f"{block}[facing={facing},head=false]")
    put(hx, 0, z, f"{block}[facing={facing},head=true]")


def tall(block, x, z, facing, prop="head", y=0):
    put(x, y, z, f"{block}[facing={facing},{prop}=false]")
    put(x, y + 1, z, f"{block}[facing={facing},{prop}=true]")


def door(x, z, facing, hinge="left"):
    put(x, 0, z, f"minecraft:birch_door[facing={facing},half=lower,hinge={hinge}]")
    put(x, 1, z, f"minecraft:birch_door[facing={facing},half=upper,hinge={hinge}]")


def counter_button(x, z, command, lines, block="minecraft:polished_diorite"):
    """Стойка: блок с кнопкой сверху, табличка спереди (с севера), командный блок — под полом."""
    put(x, -1, z, "minecraft:command_block{Command:" + json.dumps(command, ensure_ascii=False) + "}")
    put(x, 0, z, block)
    put(x, 1, z, "minecraft:polished_blackstone_button[face=floor,facing=north]")
    wall_sign(x, 0, z - 1, "north", lines)


def build():
    L.append("# Госпиталь RP Medicine для живого теста. Вход — с севера, точка появления — перед входом.")
    # Площадка: расчистить, газон, дорожка.
    fill(-30, 0, -24, 30, 12, 4, "minecraft:air")
    fill(-30, 0, 5, 30, 12, 32, "minecraft:air")
    fill(-30, -1, -24, 30, -1, 32, "minecraft:grass_block")
    fill(-2, -1, -20, 2, -1, -1, "minecraft:stone_bricks")
    fill(-1, -1, -20, 1, -1, -1, "minecraft:polished_andesite")
    for z in (-18, -12, -6):
        for x in (-3, 3):
            put(x, 0, z, "minecraft:stone_brick_wall")
            put(x, 1, z, "minecraft:stone_brick_wall")
            put(x, 2, z, "minecraft:lantern")
    for x, z, fl in [(-6, -10, "minecraft:poppy"), (-7, -8, "minecraft:dandelion"), (6, -9, "minecraft:cornflower"), (7, -12, "minecraft:oxeye_daisy"),
                     (-8, -14, "minecraft:azure_bluet"), (8, -6, "minecraft:poppy"), (-5, -5, "minecraft:lily_of_the_valley"), (5, -15, "minecraft:dandelion")]:
        put(x, 0, z, fl)
    for x, z in [(-10, -12), (10, -14)]:
        fill(x, 0, z, x, 3, z, "minecraft:birch_log")
        fill(x - 2, 4, z - 2, x + 2, 5, z + 2, "minecraft:birch_leaves[persistent=true]", "keep")
        fill(x - 1, 6, z - 1, x + 1, 6, z + 1, "minecraft:birch_leaves[persistent=true]", "keep")
    fill(-3, 0, -20, 3, 0, -20, "minecraft:stone_brick_wall")
    put(0, 0, -20, "minecraft:air")
    put(-1, 0, -20, "minecraft:air")
    put(1, 0, -20, "minecraft:air")

    # Коробка здания: x -12..12, z 0..20, пол y=-1, стены 0..3, потолок 4, парапет 5.
    fill(-13, -1, -1, 13, 5, 21, "minecraft:air")
    fill(-12, -1, 0, 12, -1, 20, "minecraft:light_gray_concrete")
    fill(-12, 0, 0, 12, 3, 20, "minecraft:white_concrete", "outline")
    fill(-11, 0, 1, 11, 3, 19, "minecraft:air")
    fill(-12, 4, 0, 12, 4, 20, "minecraft:smooth_quartz")
    fill(-12, 5, 0, 12, 5, 20, "minecraft:quartz_slab[type=bottom]", "outline")
    # Цоколь и углы — светло-серые.
    fill(-12, 0, 0, 12, 0, 0, "minecraft:light_gray_concrete")
    fill(-12, 0, 20, 12, 0, 20, "minecraft:light_gray_concrete")
    fill(-12, 0, 0, -12, 0, 20, "minecraft:light_gray_concrete")
    fill(12, 0, 0, 12, 0, 20, "minecraft:light_gray_concrete")
    for x in (-12, 12):
        for z in (0, 20):
            fill(x, 0, z, x, 4, z, "minecraft:quartz_pillar")
    # Окна.
    for x in list(range(-10, -3, 2)) + list(range(4, 11, 2)):
        fill(x, 1, 0, x, 2, 0, "minecraft:white_stained_glass_pane")
        fill(x, 1, 20, x, 2, 20, "minecraft:white_stained_glass_pane")
    for z in range(3, 19, 2):
        fill(-12, 1, z, -12, 2, z, "minecraft:white_stained_glass_pane")
        fill(12, 1, z, 12, 2, z, "minecraft:white_stained_glass_pane")
    # Вход и красный крест над ним.
    fill(-1, 0, 0, 1, 2, 0, "minecraft:air")
    fill(-2, 0, 0, -2, 3, 0, "minecraft:quartz_pillar")
    fill(2, 0, 0, 2, 3, 0, "minecraft:quartz_pillar")
    fill(-3, 5, 0, 3, 8, 0, "minecraft:white_concrete")
    fill(-3, 9, 0, 3, 9, 0, "minecraft:quartz_slab[type=bottom]")
    fill(0, 5, -1, 0, 8, -1, "minecraft:red_concrete")
    fill(-1, 6, -1, 1, 7, -1, "minecraft:red_concrete")
    fill(-2, 4, -1, 2, 4, -1, "minecraft:smooth_quartz_slab[type=top]")
    put(-2, 3, -1, "minecraft:lantern[hanging=true]")
    put(2, 3, -1, "minecraft:lantern[hanging=true]")
    wall_sign(0, 3, -1, "north", ["ГОСПИТАЛЬ", "RP Medicine", "тестовый сервер", ""])
    # Свет в потолке.
    for x in range(-10, 11, 4):
        for z in range(3, 19, 4):
            put(x, 4, z, "minecraft:sea_lantern")
    for z in range(3, 20, 4):
        put(0, 4, z, "minecraft:sea_lantern")

    # Внутренние стены: вестибюль z 1..5, коридор x -1..1, палаты по сторонам.
    fill(-11, 0, 6, 11, 3, 6, "minecraft:white_concrete")
    fill(-1, 0, 6, 1, 2, 6, "minecraft:air")
    fill(-2, 0, 6, -2, 3, 19, "minecraft:white_concrete")
    fill(2, 0, 6, 2, 3, 19, "minecraft:white_concrete")
    fill(-11, 0, 14, -3, 3, 14, "minecraft:white_concrete")
    fill(3, 0, 14, 11, 3, 14, "minecraft:white_concrete")
    fill(-1, -1, 6, 1, -1, 19, "minecraft:polished_andesite")
    fill(-11, -1, 1, 11, -1, 5, "minecraft:smooth_quartz")
    fill(3, -1, 7, 11, -1, 13, "minecraft:white_concrete")
    fill(-11, -1, 15, -3, -1, 19, "minecraft:white_concrete")
    # Полоса вдоль стен коридора.
    fill(-1, 0, 7, -1, 0, 19, "minecraft:light_blue_carpet")
    fill(1, 0, 7, 1, 0, 19, "minecraft:light_blue_carpet")
    door(-2, 10, "west")
    door(-2, 17, "west")
    door(2, 10, "east")
    door(2, 17, "east")
    wall_sign(-1, 2, 9, "east", ["ПАЛАТА", "койки, мониторы,", "капельницы,", "кислород"])
    wall_sign(-1, 2, 16, "east", ["ЛАБОРАТОРИЯ", "анализ крови,", "термостат,", "стерилизатор"])
    wall_sign(1, 2, 9, "west", ["ОПЕРАЦИОННАЯ", "стол, монитор,", "капельница,", "инструменты"])
    wall_sign(1, 2, 16, "west", ["ПЕРЕВЯЗОЧНАЯ", "две койки,", "монитор,", "капельница"])

    # Вестибюль: стойка наборов (запад) и тестовая стойка (восток); командные блоки — под полом.
    kits = [("field", "Полевой", "медик"), ("resus", "Реанимация", "и капельницы"), ("diag", "Диагностика", ""),
            ("surgeon", "Хирург", ""), ("transplant", "Органы", "и протезы"), ("drugs", "Препараты", "все"),
            ("substances", "Вещества", ""), ("food", "Еда", "")]
    x = -10
    for kit, a, b in kits:
        counter_button(x, 3, f"rpmedicine kit {kit} @p", ["НАБОР", a, b, "нажми кнопку"])
        x += 1
    counter_button(x, 3, 'give @p patchouli:guide_book{"patchouli:book":"rpmedicine:guide"}', ["КНИГА", "Справочник", "медика", "нажми кнопку"])
    tests = [("rpmedicine heal @p", ["ЛЕЧЕНИЕ", "вылечить", "меня", ""]),
             ("rpmedicine revive @p", ["ПОДНЯТЬ", "из нокдауна", "", ""]),
             ("rpmedicine skill @p 0", ["МЕДИЦИНА", "уровень 0", "", ""]),
             ("rpmedicine skill @p 3", ["МЕДИЦИНА", "уровень 3", "", ""]),
             ("rpmedicine skill @p 5", ["МЕДИЦИНА", "уровень 5", "", ""]),
             ("rpmedicine skill @p 7", ["МЕДИЦИНА", "уровень 7", "", ""]),
             ("rpmedicine skill @p 10", ["МЕДИЦИНА", "уровень 10", "", ""]),
             ("rpmedicine time add @p 30m", ["ВРЕМЯ", "+30 минут", "лечения", ""]),
             ("rpmedicine food add @p 2h", ["ГОЛОД", "+2 часа", "", ""])]
    x = 2
    for cmd, lines in tests:
        counter_button(x, 3, cmd, lines, "minecraft:polished_andesite")
        x += 1
    # За стойками — шкафчики и ящики.
    for xx in (-10, -8, -6, -4):
        put(xx, 0, 5, "rpmedicine:medicine_crate[facing=north]")
    for xx in (-9, -5):
        put(xx, 0, 5, "rpmedicine:medicine_cabinet[facing=north]")
    for xx in (4, 6, 8, 10):
        put(xx, 0, 5, "minecraft:potted_azalea_bush")
    wall_sign(-11, 2, 2, "east", ["ВЫДАЧА", "наборы — кнопки", "на стойке слева", ""])
    wall_sign(11, 2, 2, "west", ["ТЕСТ", "лечение, навык,", "время — кнопки", "на стойке справа"])
    wall_sign(0, 3, 5, "north", ["КОРИДОР", "палата, операционная,", "лаборатория,", "перевязочная"])
    for xx in (-11, 11):
        put(xx, 0, 1, "minecraft:potted_fern")

    # Палата (x -11..-3, z 7..13): три койки изголовьем к западной стене, мониторы, капельницы, кислород.
    for z in (8, 10, 12):
        bed("rpmedicine:hospital_bed", -11, z, "west")
        tall("rpmedicine:vitals_monitor", -11, z + 1, "east")
        tall("rpmedicine:iv_stand", -9, z + 1, "west", "upper")
    put(-11, 0, 7, "rpmedicine:oxygen_tank[facing=east]")
    put(-8, 0, 11, "rpmedicine:oxygen_tank[facing=west]")
    put(-3, 0, 7, "rpmedicine:medicine_crate[facing=west]")
    put(-3, 1, 7, "rpmedicine:medicine_cabinet[facing=west]")
    put(-3, 0, 13, "rpmedicine:medicine_crate[facing=west]")
    put(-4, 0, 13, "minecraft:potted_azalea_bush")

    # Операционная (x 3..11, z 7..13): стол по центру, монитор, капельница, стерилизатор, ящики.
    bed("rpmedicine:operating_table", 7, 9, "north")
    tall("rpmedicine:vitals_monitor", 6, 9, "east")
    tall("rpmedicine:iv_stand", 8, 9, "west", "upper")
    put(6, 0, 11, "rpmedicine:oxygen_tank[facing=east]")
    put(11, 0, 8, "rpmedicine:sterilizer[facing=west]")
    put(11, 0, 9, "rpmedicine:medicine_crate[facing=west]")
    put(11, 0, 10, "rpmedicine:medicine_crate[facing=west]")
    put(11, 1, 10, "rpmedicine:medicine_cabinet[facing=west]")
    put(11, 0, 12, "rpmedicine:medicine_cabinet[facing=west]")
    for x in (5, 7, 9):
        put(x, 4, 10, "minecraft:sea_lantern")
    put(7, 4, 8, "minecraft:sea_lantern")

    # Лаборатория (x -11..-3, z 15..19).
    put(-11, 0, 16, "rpmedicine:lab_table[facing=east]")
    put(-11, 0, 17, "rpmedicine:lab_table[facing=east]")
    put(-11, 0, 18, "minecraft:polished_diorite")
    put(-11, 1, 18, "rpmedicine:thermostat[facing=east]")
    put(-11, 0, 19, "rpmedicine:sterilizer[facing=east]")
    put(-7, 0, 19, "rpmedicine:medicine_crate[facing=north]")
    put(-6, 0, 19, "rpmedicine:medicine_cabinet[facing=north]")
    put(-11, 0, 15, "rpmedicine:medicine_cabinet[facing=east]")
    wall_sign(-8, 2, 15, "south", ["ЛАБОРАТОРИЯ", "пробирка с кровью", "(ланцетом) — ПКМ", "по столу. Ур. 3+"])

    # Перевязочная (x 3..11, z 15..19): две койки изголовьем к восточной стене.
    for z in (16, 18):
        bed("rpmedicine:hospital_bed", 11, z, "east")
    tall("rpmedicine:vitals_monitor", 11, 17, "west")
    tall("rpmedicine:iv_stand", 9, 17, "east", "upper")
    put(11, 0, 15, "rpmedicine:oxygen_tank[facing=west]")
    put(3, 0, 15, "rpmedicine:medicine_crate[facing=east]")
    put(3, 1, 15, "rpmedicine:medicine_cabinet[facing=east]")
    put(3, 0, 19, "rpmedicine:medicine_crate[facing=east]")

    # Вышки для падений (восточнее здания): 4 и 8 блоков, лестница с севера.
    fill(15, -1, 1, 25, -1, 9, "minecraft:coarse_dirt")
    for x0, h in ((16, 4), (21, 8)):
        fill(x0, 0, 4, x0 + 2, h - 1, 6, "minecraft:stone_bricks")
        fill(x0, h, 4, x0 + 2, h, 6, "minecraft:stone_brick_wall", "outline")
        put(x0 + 1, h, 4, "minecraft:air")
        fill(x0 + 1, 0, 3, x0 + 1, h, 3, "minecraft:ladder[facing=north]")
        wall_sign(x0 + 2, 1, 7, "south", ["ВЫШКА", f"{h} блоков", "травмы падения", ""])
    # Мир: точка появления, правила.
    L.extend([
        f"setworldspawn 0 {Y} -8",
        "gamerule spawnRadius 0",
        "gamerule doDaylightCycle false",
        "time set 6000",
        "gamerule doWeatherCycle false",
        "weather clear",
        "gamerule doMobSpawning false",
        "gamerule doPatrolSpawning false",
        "gamerule doTraderSpawning false",
        "gamerule doInsomnia false",
        "gamerule keepInventory true",
        "gamerule announceAdvancements false",
        "gamerule commandBlockOutput false",
        "kill @e[type=!player]",
    ])


def main():
    if "--clean" in sys.argv:
        shutil.rmtree(PACK, ignore_errors=True)
        print("датапак rpm_live убран")
        return
    if "--no-rcon" in sys.argv:
        p = os.path.join(LIVE, "server.properties")
        lines = open(p, encoding="utf-8").read().splitlines()
        lines = ["enable-rcon=false" if ln.startswith("enable-rcon=") else "rcon.password=" if ln.startswith("rcon.password=") else ln for ln in lines]
        w(p, "\n".join(lines) + "\n")
        print("RCON выключен")
        return
    if os.path.isdir(os.path.join(LIVE, "world", "region")):
        print("мир run_live/world уже есть — удалите его, чтобы собрать заново")
        return
    os.makedirs(LIVE, exist_ok=True)
    w(os.path.join(LIVE, "eula.txt"), "eula=true\n")
    w(os.path.join(LIVE, "server.properties"), "\n".join([
        "motd=RP Medicine \\u2014 \\u0442\\u0435\\u0441\\u0442",
        "server-port=25565",
        "online-mode=false",
        # RCON — только для проверки постройки (--rcon); на тесте выключен.
        "enable-rcon=" + ("true\nrcon.port=25575\nrcon.password=rpmlive" if "--rcon" in sys.argv else "false"),
        "enable-command-block=true",
        "level-name=world",
        "level-type=minecraft\\:flat",
        'generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}',
        "generate-structures=false",
        "difficulty=normal",
        "gamemode=survival",
        "max-players=20",
        "spawn-protection=0",
        "view-distance=10",
        "allow-flight=true",
        "white-list=false",
        "",
    ]))
    w(os.path.join(LIVE, "ops.json"), json.dumps([{"uuid": offline_uuid(n), "name": n, "level": 4, "bypassesPlayerLimit": True} for n in OPS], indent=2) + "\n")
    build()
    w(os.path.join(PACK, "pack.mcmeta"), json.dumps({"pack": {"pack_format": 15, "description": "RP Medicine: постройка госпиталя для живого теста"}}, ensure_ascii=False))
    w(os.path.join(PACK, "data", "rpm_live", "functions", "build.mcfunction"), "\n".join(L) + "\n")
    w(os.path.join(PACK, "data", "rpm_live", "functions", "once.mcfunction"),
      "scoreboard objectives add rpm_live dummy\n"
      "execute unless score #built rpm_live matches 1 run function rpm_live:build\n"
      "scoreboard players set #built rpm_live 1\n")
    w(os.path.join(PACK, "data", "minecraft", "tags", "functions", "load.json"), json.dumps({"values": ["rpm_live:once"]}))
    print("run_live готов:", len(L), "команд постройки; оператор:", ", ".join(f"{n} ({offline_uuid(n)})" for n in OPS))


if __name__ == "__main__":
    main()
