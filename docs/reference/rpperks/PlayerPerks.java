package faygolover.rpperks.capability;

import faygolover.rpperks.Config;
import faygolover.rpperks.perk.Perk;
import faygolover.rpperks.perk.Stat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Данные перков игрока. Существует и на сервере, и на клиенте:
 * клиентская копия заполняется пакетом синхронизации (нужна окну перков, HUD, Анорексичке и Сухопутному).
 */
public class PlayerPerks {
    private int points = 0;
    /** Перк -> на сколько изменился баланс при его выдаче. Возврат идёт ровно на эту сумму, даже если цену потом поменяли в конфиге. */
    private final EnumMap<Perk, Integer> perks = new EnumMap<>(Perk.class);
    private int refuseTokens = 0;
    private int resetTokens = 0;
    /** Перки, которые админ запретил этому игроку брать. */
    private final EnumSet<Perk> blockedPerks = EnumSet.noneOf(Perk.class);
    /** Админ закрыл игроку самостоятельный выбор перков целиком. */
    private boolean selectionLocked = false;
    private int caffeineTimer = 0;
    private int anorexiaTimer = 0;
    private boolean refuseNextMeal = false;
    private int unluckyCooldown = 0;

    /**
     * Кэш последнего пересчёта характеристик (см. PerkEventHandler.computeStats).
     * Только сервер: не сохраняется в NBT и не участвует в синхронизации с клиентом.
     * Обновляется в PerkEventHandler.refreshPlayer при любом изменении перков, чтобы горячие пути
     * (например, PerkMechanicsHandler.onKnockback на каждый удар в ближнем бою) не пересчитывали
     * все статы заново на каждое обращение.
     */
    @Nullable
    private EnumMap<Stat, Double> cachedStats = null;

    // ---------- Баллы ----------
    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }
    public void addPoints(int amount) { this.points += amount; }

    // ---------- Жетоны ----------
    public int getRefuseTokens() { return refuseTokens; }
    public void setRefuseTokens(int tokens) { this.refuseTokens = Math.max(0, tokens); }
    public int getResetTokens() { return resetTokens; }
    public void setResetTokens(int tokens) { this.resetTokens = Math.max(0, tokens); }

    // ---------- Перки ----------

    /** Все перки игрока, включая приостановленные режимом Админа. Порядок стабильный (как в enum). */
    public Set<Perk> getActivePerks() { return Collections.unmodifiableSet(perks.keySet()); }

    /** Перк -> изменение баланса при его выдаче. */
    public Map<Perk, Integer> getPerkDeltas() { return Collections.unmodifiableMap(perks); }

    /** Сколько баллов изменилось при выдаче перка (0, если перка нет). */
    public int getDelta(Perk perk) { return perks.getOrDefault(perk, 0); }

    public boolean owns(Perk perk) { return perks.containsKey(perk); }

    public boolean isAdmin() { return perks.containsKey(Perk.ADMIN); }

    /** Действует ли перк прямо сейчас. Пока активен Админ, все остальные перки «выключены». */
    public boolean hasPerk(Perk perk) {
        if (perk == Perk.ADMIN) return isAdmin();
        return !isAdmin() && perks.containsKey(perk);
    }

    /** Перки, которые реально действуют. */
    public Set<Perk> getEffectivePerks() {
        return isAdmin() ? EnumSet.of(Perk.ADMIN) : getActivePerks();
    }

    /**
     * Выдаёт перк, снимая несовместимые с ним (с возвратом баллов).
     * @param removedConflicts сюда складываются снятые перки (можно null)
     * @return false, если перк уже был
     */
    public boolean grantPerk(Perk perk, @Nullable List<Perk> removedConflicts) {
        if (perks.containsKey(perk)) return false;
        for (Perk conflict : Config.getConflicts(perk)) {
            if (revokePerk(conflict) && removedConflicts != null) removedConflicts.add(conflict);
        }
        int delta = perk.getPointsDelta();
        perks.put(perk, delta);
        points += delta;
        return true;
    }

    /** Снимает перк с возвратом баллов. */
    public boolean revokePerk(Perk perk) {
        Integer delta = perks.remove(perk);
        if (delta == null) return false;
        points -= delta;
        return true;
    }

    /** Перки, которые игрок может сбросить сам (всё, кроме служебных). */
    public List<Perk> getResettablePerks() {
        return perks.keySet().stream().filter(p -> p.getCategory() != Perk.Category.SERVICE).toList();
    }

    /** Сброс игроком: снимает все не служебные перки с возвратом баллов. Возвращает число снятых. */
    public int resetResettable() {
        List<Perk> list = getResettablePerks();
        list.forEach(this::revokePerk);
        return list.size();
    }

    /** Полный сброс карточки администратором. Жетоны и запреты сохраняются. */
    public void reset() {
        perks.clear();
        points = 0;
        caffeineTimer = 0;
        anorexiaTimer = 0;
        refuseNextMeal = false;
        unluckyCooldown = 0;
        cachedStats = null;
    }

    // ---------- Ограничения выбора (ставит админ) ----------
    public Set<Perk> getBlockedPerks() { return Collections.unmodifiableSet(blockedPerks); }
    public boolean isBlocked(Perk perk) { return blockedPerks.contains(perk); }
    /** Переключает запрет на перк. Возвращает новое состояние (true — запрещён). */
    public boolean toggleBlocked(Perk perk) {
        if (blockedPerks.remove(perk)) return false;
        blockedPerks.add(perk);
        return true;
    }
    public boolean isSelectionLocked() { return selectionLocked; }
    public void setSelectionLocked(boolean locked) { this.selectionLocked = locked; }

    // ---------- Таймеры механик ----------
    public int getCaffeineTimer() { return caffeineTimer; }
    public void setCaffeineTimer(int ticks) { this.caffeineTimer = ticks; }

    public int getAnorexiaTimer() { return anorexiaTimer; }
    public void setAnorexiaTimer(int ticks) { this.anorexiaTimer = ticks; }

    public boolean isRefuseNextMeal() { return refuseNextMeal; }
    public void setRefuseNextMeal(boolean refuse) { this.refuseNextMeal = refuse; }

    public int getUnluckyCooldown() { return unluckyCooldown; }
    public void setUnluckyCooldown(int ticks) { this.unluckyCooldown = ticks; }

    // ---------- Кэш характеристик (только сервер, не сохраняется и не синхронизируется) ----------
    @Nullable
    public EnumMap<Stat, Double> getCachedStats() { return cachedStats; }
    public void setCachedStats(@Nullable EnumMap<Stat, Double> stats) { this.cachedStats = stats; }

    // ---------- Синхронизация на клиент ----------
    public void applyClientSync(int points, Map<Perk, Integer> perkDeltas, int refuseTokens, int resetTokens,
                                Collection<Perk> blocked, boolean selectionLocked, int caffeineTimer, int anorexiaTimer, boolean refuseNextMeal) {
        this.points = points;
        this.perks.clear();
        this.perks.putAll(perkDeltas);
        this.refuseTokens = refuseTokens;
        this.resetTokens = resetTokens;
        this.blockedPerks.clear();
        this.blockedPerks.addAll(blocked);
        this.selectionLocked = selectionLocked;
        this.caffeineTimer = caffeineTimer;
        this.anorexiaTimer = anorexiaTimer;
        this.refuseNextMeal = refuseNextMeal;
    }

    // ---------- Копирование при смерти / возвращении из Энда ----------
    public void copyFrom(PlayerPerks source) {
        this.points = source.points;
        this.perks.clear();
        this.perks.putAll(source.perks);
        this.refuseTokens = source.refuseTokens;
        this.resetTokens = source.resetTokens;
        this.blockedPerks.clear();
        this.blockedPerks.addAll(source.blockedPerks);
        this.selectionLocked = source.selectionLocked;
        this.caffeineTimer = source.caffeineTimer;
        this.anorexiaTimer = source.anorexiaTimer;
        this.refuseNextMeal = source.refuseNextMeal;
        this.unluckyCooldown = source.unluckyCooldown;
    }

    // ---------- NBT ----------
    public void saveNBT(CompoundTag nbt) {
        nbt.putInt("points", points);
        nbt.putInt("refuseTokens", refuseTokens);
        nbt.putInt("resetTokens", resetTokens);
        nbt.putBoolean("selectionLocked", selectionLocked);
        ListTag blocked = new ListTag();
        for (Perk perk : blockedPerks) blocked.add(StringTag.valueOf(perk.getId()));
        nbt.put("blocked", blocked);
        nbt.putInt("caffeineTimer", caffeineTimer);
        nbt.putInt("anorexiaTimer", anorexiaTimer);
        nbt.putBoolean("refuseNextMeal", refuseNextMeal);
        nbt.putInt("unluckyCooldown", unluckyCooldown);

        ListTag list = new ListTag();
        perks.forEach((perk, delta) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", perk.getId());
            entry.putInt("delta", delta);
            list.add(entry);
        });
        nbt.put("perk_entries", list);
    }

    public void loadNBT(CompoundTag nbt) {
        this.points = nbt.getInt("points");
        this.refuseTokens = nbt.getInt("refuseTokens");
        this.resetTokens = nbt.getInt("resetTokens");
        this.selectionLocked = nbt.getBoolean("selectionLocked");
        this.blockedPerks.clear();
        ListTag blocked = nbt.getList("blocked", Tag.TAG_STRING);
        for (int i = 0; i < blocked.size(); i++) Perk.fromId(blocked.getString(i)).ifPresent(blockedPerks::add);
        this.caffeineTimer = nbt.getInt("caffeineTimer");
        this.anorexiaTimer = nbt.getInt("anorexiaTimer");
        this.refuseNextMeal = nbt.getBoolean("refuseNextMeal");
        this.unluckyCooldown = nbt.getInt("unluckyCooldown");
        this.perks.clear();

        if (nbt.contains("perk_entries", Tag.TAG_LIST)) {
            ListTag list = nbt.getList("perk_entries", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                Perk.fromId(entry.getString("id")).ifPresent(perk -> perks.put(perk, entry.getInt("delta")));
            }
        } else {
            // Сохранения старой версии: список ID без цен. Баллы тогда списывались по старым ценам,
            // поэтому и возвращать их нужно по старым — иначе баланс «уплывёт».
            ListTag legacy = nbt.getList("perks", Tag.TAG_STRING);
            for (int i = 0; i < legacy.size(); i++) {
                Perk.fromId(legacy.getString(i)).ifPresent(perk -> perks.put(perk, legacyDelta(perk)));
            }
        }
    }

    /** Цены из версии мода до переноса в конфиг. */
    private static int legacyDelta(Perk perk) {
        int cost = switch (perk) {
            case SURVIVOR, ATHLETE -> 10;
            case TOUGH, STRONGMAN, TOURIST, ORGANIZED, LUCKY -> 8;
            case ENDURING, BRAVE, NERD_RAGE, SAILOR, TAILOR, FARMER, COOK -> 6;
            case SOLDIER, BUILDER, MELEE_FIGHTER, FRAGILE -> 7;
            case DRIVER, PILOT, GUNNER, HOMEBODY, UNLUCKY, WEAKLING -> 5;
            case AGILE, ALCOHOLIC, SOLAR_POWERED, LIGHT_TRAVEL, TURTLE, WIMP, SUGGESTIBLE,
                 CAFFEINE_ADDICTION, CLUMSY, TECHNOPHOBE -> 4;
            case DISORGANIZED, DIABETIC, ANOREXIC -> 6;
            case LANDLUBBER, COWARD -> 3;
            case SMOKER -> 2;
            case TEETOTALER -> 1;
            default -> perk.getCost();
        };
        return perk.isPositive() ? -cost : cost;
    }
}
