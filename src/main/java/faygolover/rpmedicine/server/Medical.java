package faygolover.rpmedicine.server;

import faygolover.rpmedicine.capability.MedicalCapability;
import faygolover.rpmedicine.capability.MedicalData;
import faygolover.rpmedicine.core.MedicalState;
import faygolover.rpmedicine.core.PatientTraits;
import faygolover.rpmedicine.entity.BodyStubEntity;
import faygolover.rpmedicine.integration.RpPerksCompat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/** Единая точка доступа к медицинскому состоянию игрока или заглушки на сервере. */
public final class Medical {
    private Medical() {}

    /** Состояние «пациента»: игрок или заглушка офлайн-игрока; для остальных сущностей — null. */
    @Nullable
    public static MedicalState state(@Nullable Entity e) {
        if (e instanceof Player p) {
            MedicalData d = MedicalCapability.get(p);
            return d != null ? d.state : null;
        }
        if (e instanceof BodyStubEntity stub) return stub.state();
        return null;
    }

    @Nullable
    public static MedicalData data(@Nullable Player p) {
        return p == null ? null : MedicalCapability.get(p);
    }

    public static boolean isPatient(@Nullable Entity e) {
        return e instanceof Player || e instanceof BodyStubEntity;
    }

    public static PatientTraits traits(Entity e) {
        if (e instanceof Player p) return RpPerksCompat.traits(p);
        if (e instanceof BodyStubEntity stub) return stub.traits();
        return PatientTraits.NONE;
    }

    /** Пометить, что состояние изменилось (нужна синхронизация и, для заглушки, сохранение снимка). */
    public static void changed(Entity e) {
        if (e instanceof Player p) {
            MedicalData d = MedicalCapability.get(p);
            if (d != null) d.markDirty();
        } else if (e instanceof BodyStubEntity stub) {
            stub.markChanged();
        }
    }

    public static boolean isDown(@Nullable Entity e) {
        MedicalState m = state(e);
        return m != null && m.isDown();
    }

    public static int medicineLevel(Player p) {
        return RpPerksCompat.medicineLevel(p);
    }
}
