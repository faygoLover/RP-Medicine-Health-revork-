package faygolover.rpmedicine.integration;

import faygolover.rpcore.api.BodyProvider;
import faygolover.rpcore.api.RpIds;
import faygolover.rpmedicine.core.BodyPart;
import faygolover.rpmedicine.core.BodyPartState;
import faygolover.rpmedicine.core.MedicalSettings;
import faygolover.rpmedicine.core.MedicalState;
import faygolover.rpmedicine.core.Wound;
import faygolover.rpmedicine.core.WoundType;
import faygolover.rpmedicine.server.ExamService;
import faygolover.rpmedicine.server.Medical;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.Set;

/**
 * RP Medicine — мод тела для RP Core. Перки RP Perks при Medicine не выключаются, а работают через неё:
 * здоровье «Живучего» — черта, «Бешенство ботаника» — от общего состояния, «Неудачливый» подворачивает
 * голеностоп настоящим вывихом, кофеиновая ломка — физиология веществ.
 */
public final class CoreBody implements BodyProvider {
    private static final Set<ResourceLocation> HANDLES = Set.of(RpIds.BODY_HEALTH, RpIds.BODY_CAFFEINE, RpIds.BODY_SPRAIN);

    @Override
    public boolean handles(ResourceLocation feature) {
        return HANDLES.contains(feature);
    }

    @Override
    public float condition(Player player) {
        MedicalState m = Medical.state(player);
        if (m == null) return 1f;
        return (float) Math.max(0, Math.min(1, ExamService.overall(m, MedicalSettings.get()) / 100.0));
    }

    /** Неудачное падение: вывих голеностопа (вправляет медик) с ушибом и острой болью. */
    @Override
    public boolean sprain(Player player) {
        MedicalState m = Medical.state(player);
        if (m == null) return false;
        BodyPart foot = player.getRandom().nextBoolean() ? BodyPart.RIGHT_FOOT : BodyPart.LEFT_FOOT;
        BodyPartState ps = m.part(foot);
        if (ps.missing || ps.prosthesis != BodyPartState.Prosthesis.NONE) {
            foot = foot == BodyPart.RIGHT_FOOT ? BodyPart.LEFT_FOOT : BodyPart.RIGHT_FOOT;
            ps = m.part(foot);
            if (ps.missing || ps.prosthesis != BodyPartState.Prosthesis.NONE) return false;
        }
        if (!ps.hasFracture()) ps.dislocated = true;
        ps.wounds.add(new Wound(WoundType.BRUISE, 10));
        m.painSpike(35, 30);
        Medical.changed(player);
        return true;
    }
}
