package faygolover.rpmedicine.capability;

import faygolover.rpmedicine.core.GameplayEffects;
import faygolover.rpmedicine.core.MedicalSettings;
import faygolover.rpmedicine.core.MedicalState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * Медицинские данные игрока на сервере: сохраняемое состояние плюс служебные поля, которые не
 * пишутся на диск (накопители движения за шаг, что уже отправлено клиенту).
 */
public final class MedicalData {
    public final MedicalState state = new MedicalState();

    // --- накопители между шагами физиологии (не сохраняются) ---
    /** Свой счётчик тиков для шага физиологии (не зависит от tickCount, который трогают другие моды). */
    public int ticks;
    public double sprintTicks;
    public int jumps;
    public double distance;
    public double lastX = Double.NaN;
    public double lastZ;

    // --- синхронизация ---
    /** Хеш последнего отправленного клиенту собственного состояния. */
    public int lastSelfHash;
    /** Последний применённый набор модификаторов (чтобы не трогать атрибуты каждый шаг). */
    public int lastModsHash;
    /** Тик, когда в последний раз звали администратора. */
    public long lastAdminCall = Long.MIN_VALUE / 2;
    /** Состояние изменилось: пересчитать последствия и синхронизировать. */
    public boolean dirty = true;
    /** Последнее отправленное клиенту собственное состояние. */
    public Object lastSelf;
    /** Последние посчитанные последствия травм (для проверок в событиях). */
    public GameplayEffects.Mods lastMods = new GameplayEffects.Mods();
    /** Несёт тело (замедление, без бега и стрельбы). */
    public boolean carrying;
    /** Идёт лечение с прогресс-баром (замедление). */
    public boolean treating;

    // --- госпиталь ---
    /** Койка, на которой лежит игрок (null — не на койке). Сохраняется. */
    @Nullable
    public BlockPos bedPos;
    /** Куда смотрит тело на койке: четверть оборота 0–3 (юг, запад, север, восток). */
    public byte bedQuarter;
    /** Кэш поиска блоков рядом (п. 14 ТЗ второго этапа: раз в 2 с, не каждый тик). */
    @Nullable
    public BlockPos monitorPos;
    public boolean nearIvStand;
    public long hospitalScanTick = Long.MIN_VALUE / 2;
    public long lastAlarmTick = Long.MIN_VALUE / 2;
    /** Когда последний раз получил урон (правило «в бою — прогресс-бар»). */
    public long lastHurtTick = Long.MIN_VALUE / 2;

    public CompoundTag save() {
        CompoundTag t = MedicalNbt.write(state);
        if (bedPos != null) t.putLong("bed", bedPos.asLong());
        return t;
    }

    public void load(CompoundTag tag) {
        MedicalNbt.read(state, tag, MedicalSettings.get());
        bedPos = tag.contains("bed") ? BlockPos.of(tag.getLong("bed")) : null;
        dirty = true;
    }

    public void markDirty() {
        dirty = true;
    }
}
