package faygolover.rpmedicine.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * Износ одноразовой защиты хирурга (замечание 09.10, И38): перчатки грязнеют при смене пациента или после многих
 * шагов операции, маска отсыревает после часа на лице. Грязная не защищает (а перчатки ещё и переносят заразу) —
 * её надо снять и выбросить, новая чистая.
 */
public final class Wear {
    private Wear() {}

    public static final String DIRTY = "RpmDirty";
    private static final String PATIENT = "RpmPatient";
    private static final String USES = "RpmUses";
    private static final String WORN = "RpmWornSec";

    public static boolean dirty(ItemStack st) {
        return st.hasTag() && st.getTag().getBoolean(DIRTY);
    }

    private static void setDirty(ItemStack st) {
        st.getOrCreateTag().putBoolean(DIRTY, true);
    }

    /** Грязные для этого пациента: уже грязные или надевались на другом (без изменения предмета). */
    public static boolean dirtyFor(ItemStack gloves, UUID patient) {
        if (dirty(gloves)) return true;
        CompoundTag t = gloves.getTag();
        return t != null && t.contains(PATIENT) && !t.getString(PATIENT).equals(patient.toString());
    }

    /**
     * Шаг операции в этих перчатках у пациента {@code patient}. Другой пациент — перчатки сразу грязные
     * (перенос заразы), иначе грязнеют после {@code maxUses} шагов. Возвращает: грязные ли теперь.
     */
    public static boolean useGloves(ItemStack gloves, UUID patient, int maxUses) {
        if (dirty(gloves)) return true;
        CompoundTag t = gloves.getOrCreateTag();
        String id = patient.toString();
        if (t.contains(PATIENT) && !t.getString(PATIENT).equals(id)) {
            setDirty(gloves);
            return true;
        }
        t.putString(PATIENT, id);
        int uses = t.getInt(USES) + 1;
        t.putInt(USES, uses);
        if (uses >= maxUses) setDirty(gloves);
        return false;
    }

    /** Маска пробыла на лице ещё {@code seconds}; true — только что отсырела. Пишется редко (NBT надетого предмета
     *  уходит клиенту при каждом изменении). */
    public static boolean wearMask(ItemStack mask, int seconds, int maxSeconds) {
        if (dirty(mask)) return false;
        CompoundTag t = mask.getOrCreateTag();
        int s = t.getInt(WORN) + seconds;
        t.putInt(WORN, s);
        if (s >= maxSeconds) {
            setDirty(mask);
            return true;
        }
        return false;
    }
}
