package faygolover.rpmedicine.item;

import faygolover.rpmedicine.core.BodyPartState.Prosthesis;

/** Протез (ТЗ третьего этапа, п. 6.2): ставит медик на зажившую культю, снимается на панели. */
public class ProstheticItem extends MedicalItem {
    public final Prosthesis type;

    public ProstheticItem(Prosthesis type, Properties props) {
        super(props);
        this.type = type;
    }
}
