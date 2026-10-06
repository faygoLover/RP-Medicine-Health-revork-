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
            if (faygolover.rpmedicine.registry.ModItems.isPen(stack) || faygolover.rpmedicine.registry.ModItems.isVial(stack)) {
                // 1 мл = 1 доза; полоса прочности — остаток (решения, п. 1.16).
                String ml = String.format(java.util.Locale.ROOT, "%.1f", left / 10.0).replace('.', ',');
                tooltip.add(Component.translatable(faygolover.rpmedicine.registry.ModItems.isPen(stack) ? "rpmedicine.tooltip.pen_doses"
                        : "rpmedicine.tooltip.vial_doses", ml, stack.getMaxDamage() / 10).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
                String label = getDescriptionId() + ".label";
                if (net.minecraft.locale.Language.getInstance().has(label))
                    tooltip.add(Component.translatable(label).withStyle(net.minecraft.ChatFormatting.DARK_AQUA));
            } else {
                tooltip.add(Component.translatable("rpmedicine.tooltip.charges", left).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            }
        }
    }

    /** 3D-модель в руке (если для предмета она есть в rpgeo/items.json) — {@code GeoItemRenderer}. */
    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        // 3D-модели и хват двумя руками — клиентский класс.
        consumer.accept(faygolover.rpmedicine.client.ItemPoses.EXTENSIONS);
    }
}
