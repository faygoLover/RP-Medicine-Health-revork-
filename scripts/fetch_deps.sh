#!/usr/bin/env bash
# Скачивает jar и исходники чужих модов в external/ (папка не попадает в git).
# Нужен доступ в сеть: cdn.modrinth.com, cursemaven.com, github.com.
# Запуск из корня репозитория: bash scripts/fetch_deps.sh
set -u
cd "$(dirname "$0")/.."
mkdir -p external/jars external/src
fail=0

fetch_jar() { # имя, ссылка, sha1 (может быть пустым)
  local out="external/jars/$1"
  if [ ! -f "$out" ]; then
    echo "скачиваю $1"
    curl -fsSL -o "$out" "$2" || { echo "  НЕ УДАЛОСЬ: $2"; rm -f "$out"; fail=1; return; }
  fi
  if [ -n "${3:-}" ]; then
    local got; got=$(sha1sum "$out" | cut -d" " -f1)
    [ "$got" = "$3" ] || { echo "  sha1 не совпал у $1: $got"; fail=1; }
  fi
}

fetch_src() { # папка, репозиторий, коммит
  local dir="external/src/$1"
  if [ ! -d "$dir/.git" ]; then
    echo "клонирую $2"
    git clone -q "$2" "$dir" || { echo "  НЕ УДАЛОСЬ: $2"; fail=1; return; }
  fi
  git -C "$dir" checkout -q "$3" 2>/dev/null || { git -C "$dir" fetch -q origin "$3" && git -C "$dir" checkout -q "$3"; } || { echo "  нет коммита $3 в $1"; fail=1; }
}

# --- jar с Modrinth (версии как в сборке автора, sha1 сверен с его файлами) ---
fetch_jar "tacz-1.20.1-1.1.8-hotfix.jar" "https://cdn.modrinth.com/data/SzzJttH8/versions/yOVIzIJR/tacz-1.20.1-1.1.8-hotfix.jar" "bddafeea4c9c1132ed720c30fbaedfe5ab25e846"
fetch_jar "ZeroContact-main-build-72-36c6a8f.jar" "https://cdn.modrinth.com/data/KfjYGgAD/versions/EnLUirjM/ZeroContact-main-build-72-36c6a8f.jar" "f5f68920df4ae5224f0de499bd79182199b611e2"
fetch_jar "curios-forge-5.14.1+1.20.1.jar" "https://cdn.modrinth.com/data/vvuO3ImH/versions/IPQlZkz1/curios-forge-5.14.1%2B1.20.1.jar" "452175b95ad3db6ff58bb8968f6bf7a9d1e0f480"
fetch_jar "carryon-forge-1.20.1-2.1.2.7.jar" "https://cdn.modrinth.com/data/joEfVgkn/versions/edGQD16r/carryon-forge-1.20.1-2.1.2.7.jar" "9999714ca3dd2f2401f0396ca44f3b27c3784c4f"
fetch_jar "player-animation-lib-forge-1.0.2-rc1+1.20.jar" "https://cdn.modrinth.com/data/gedNE4y2/versions/xe2EVE6q/player-animation-lib-forge-1.0.2-rc1%2B1.20.jar" "16808f94a41d45d8e986b4e4ea6b02ba57fa058a"
fetch_jar "superbwarfare-0.8.9.1-hotfix-mc1.20.1-993063bed-all.jar" "https://cdn.modrinth.com/data/Cd3DYqzn/versions/mHmtMuKF/superbwarfare-0.8.9.1-hotfix-mc1.20.1-993063bed-all.jar" "cd143b04ea8245243624c720ddd482c3aea0521e"
fetch_jar "lrtactical-1.20.1-0.4.3.jar" "https://cdn.modrinth.com/data/AiwNM9O0/versions/eygQmqIl/lrtactical-1.20.1-0.4.3.jar" "49670df2d364b48c538de5dd44d464d5ed6f8817"

# --- jar, которых нет на Modrinth (CurseForge через cursemaven) ---
fetch_jar "legendarysurvivaloverhaul-1.20.1-2.4.2.jar" "https://cursemaven.com/curse/maven/legendary-survival-overhaul-840254/7603852/legendary-survival-overhaul-840254-7603852.jar" ""
fetch_jar "corpse-forge-1.20.1-1.0.23.jar" "https://cursemaven.com/curse/maven/corpse-316582/7018272/corpse-316582-7018272.jar" ""

# --- ассеты под MIT (scripts/foreign_assets.py, docs/assets_credits.md): прямо с CDN CurseForge ---
fetch_jar "tacmed-1.1.0.jar" "https://edge.forgecdn.net/files/8657/363/tacmed-1.1.0.jar" "51e1695c11d98dffae37ddcf2fe5c6515ad07714"
fetch_jar "health_and_disease-1.4.2-forge-1.20.1.jar" "https://edge.forgecdn.net/files/6291/844/health_and_disease-1.4.2-forge-1.20.1.jar" "7cffb8651a8d5ebf3773d3831a9bd68381b636ed"

# --- исходники для чтения (не копировать в мод: GPL) ---
fetch_src tacz https://github.com/MCModderAnchor/TACZ.git b43eb84c38e9768d8e73c8b14f0b845669704b38
fetch_src zerocontact https://github.com/TuDouNi92/ZeroContact.git 36c6a8f07e95a3e17b09309aedc379ff0d903525
fetch_src lso https://github.com/sfiomn/LegendarySurvivalOverhaul.git 013b06b733bbebea590c9d1ad45547db695ee71a
fetch_src curios https://github.com/TheIllusiveC4/Curios.git 1.20.x
fetch_src voicechat https://github.com/henkelmax/simple-voice-chat.git 1.20.1

[ "$fail" = 0 ] && echo "готово: external/jars, external/src" || { echo "часть файлов не скачалась, см. выше"; exit 1; }
