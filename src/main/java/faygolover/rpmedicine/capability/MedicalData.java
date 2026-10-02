package faygolover.rpmedicine.capability;

import faygolover.rpmedicine.core.GameplayEffects;
import faygolover.rpmedicine.core.MedicalSettings;
import faygolover.rpmedicine.core.MedicalState;
import net.minecraft.nbt.CompoundTag;

/**
 * Медицинские данные игрока на сервере: сохраняемое состояние плюс служебные поля, которые не
 * пишутся на диск (накопители движения за шаг, что уже отправлено клиенту).
 */
public final class MedicalData {
    public final MedicalState state = new MedicalState();

    // --- накопители между шагами физиологии (не сохраняются) ---
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

    public CompoundTag save() {
        return MedicalNbt.write(state);
    }

    public void load(CompoundTag tag) {
        MedicalNbt.read(state, tag, MedicalSettings.get());
        dirty = true;
    }

    public void markDirty() {
        dirty = true;
    }
}
