package faygolover.rpmedicine.item;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Equipable;

/** Хирургическая маска: надевается в слот головы (ПКМ), снижает заражение при операции. */
public class SurgicalMaskItem extends MedicalItem implements Equipable {
    public SurgicalMaskItem(Properties props) {
        super(props);
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<net.minecraft.world.item.ItemStack> use(net.minecraft.world.level.Level level,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        return swapWithEquipmentSlot(this, level, player, hand);
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.HEAD;
    }
}
