package faygolover.rpmedicine.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Медицинский предмет. Применение (ПКМ по себе или по игроку, перетаскивание на панели) обрабатывает
 * {@code TreatmentManager} через события — так же работают и чужие предметы-аналоги из датапака.
 */
public class MedicalItem extends Item {
    public MedicalItem(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        // Коротко: что это. Как действует — по Shift (ItemTooltips).
        String what = getDescriptionId() + ".what";
        tooltip.add(Component.translatable(net.minecraft.locale.Language.getInstance().has(what) ? what : getDescriptionId() + ".desc")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        if (stack.isDamageableItem()) {
            int left = stack.getMaxDamage() - stack.getDamageValue();
            tooltip.add(Component.translatable("rpmedicine.tooltip.charges", left).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
    }

    /** 3D-модель в руке (если для предмета она есть в rpgeo/items.json) — {@code GeoItemRenderer}. */
    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return faygolover.rpmedicine.client.geo.GeoItemRenderer.get();
            }
        });
    }
}
