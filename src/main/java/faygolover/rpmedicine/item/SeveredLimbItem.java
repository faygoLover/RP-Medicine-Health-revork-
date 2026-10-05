package faygolover.rpmedicine.item;

import faygolover.rpmedicine.core.BodyPart;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Отнятая конечность (ТЗ третьего этапа, п. 6.1): часть тела, чья, когда отнята. Пришить обратно можно
 * в течение нескольких часов в холодильнике (шаг пересадки).
 */
public class SeveredLimbItem extends MedicalItem {
    public SeveredLimbItem(Properties props) {
        super(props);
    }

    public static ItemStack create(net.minecraft.world.item.Item item, BodyPart part, UUID owner, String ownerName, long gameTime) {
        ItemStack st = new ItemStack(item);
        var t = st.getOrCreateTag();
        t.putString("Part", part.id);
        t.putUUID("Owner", owner);
        t.putString("OwnerName", ownerName);
        t.putLong("Taken", gameTime);
        return st;
    }

    @Nullable
    public static BodyPart part(ItemStack st) {
        return st.hasTag() ? BodyPart.byId(st.getTag().getString("Part")).orElse(null) : null;
    }

    @Override
    public Component getName(ItemStack st) {
        BodyPart p = part(st);
        return p == null ? super.getName(st) : Component.translatable("item.rpmedicine.severed_limb.of", Component.translatable(p.translationKey()));
    }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(st, level, tooltip, flag);
        if (st.hasTag() && st.getTag().contains("OwnerName"))
            tooltip.add(Component.translatable("rpmedicine.tooltip.limb_owner", st.getTag().getString("OwnerName")).withStyle(ChatFormatting.GRAY));
    }
}
