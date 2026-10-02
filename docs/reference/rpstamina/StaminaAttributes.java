package faygolover.rpstamina.attribute;

import faygolover.rpstamina.RpStamina;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Атрибуты для интеграции с перками (rpperks и др.). Итоговый расход действия =
 * базовая цена × cost_multiplier × множитель категории × (вес брони, для движения).
 * <ul>
 *   <li>rpstamina:max_stamina — максимум (без rpperks база берётся из конфига)</li>
 *   <li>rpstamina:regen_multiplier — множитель восстановления (1.0 = обычное)</li>
 *   <li>rpstamina:cost_multiplier — общий множитель расхода</li>
 *   <li>rpstamina:move_cost_multiplier — бег, прыжки, лестницы</li>
 *   <li>rpstamina:swim_cost_multiplier — плавание</li>
 *   <li>rpstamina:mine_cost_multiplier — добыча блоков</li>
 *   <li>rpstamina:melee_cost_multiplier — удары в ближнем бою</li>
 *   <li>rpstamina:armor_burden — влияние веса брони на расход движения (0 = броня не мешает)</li>
 * </ul>
 */
public final class StaminaAttributes {
    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(ForgeRegistries.ATTRIBUTES, RpStamina.MODID);

    public static final RegistryObject<Attribute> MAX_STAMINA = ATTRIBUTES.register("max_stamina",
            () -> new RangedAttribute("attribute.name.rpstamina.max_stamina", 1000.0D, 1.0D, 100000.0D).setSyncable(true));

    public static final RegistryObject<Attribute> REGEN_MULTIPLIER = multiplier("regen_multiplier");
    public static final RegistryObject<Attribute> COST_MULTIPLIER = multiplier("cost_multiplier");
    public static final RegistryObject<Attribute> MOVE_COST_MULTIPLIER = multiplier("move_cost_multiplier");
    public static final RegistryObject<Attribute> SWIM_COST_MULTIPLIER = multiplier("swim_cost_multiplier");
    public static final RegistryObject<Attribute> MINE_COST_MULTIPLIER = multiplier("mine_cost_multiplier");
    public static final RegistryObject<Attribute> MELEE_COST_MULTIPLIER = multiplier("melee_cost_multiplier");
    public static final RegistryObject<Attribute> ARMOR_BURDEN = multiplier("armor_burden");

    private static RegistryObject<Attribute> multiplier(String name) {
        return ATTRIBUTES.register(name,
                () -> new RangedAttribute("attribute.name.rpstamina." + name, 1.0D, 0.0D, 10.0D).setSyncable(true));
    }

    public static void onAttributeModification(EntityAttributeModificationEvent event) {
        event.add(EntityType.PLAYER, MAX_STAMINA.get());
        event.add(EntityType.PLAYER, REGEN_MULTIPLIER.get());
        event.add(EntityType.PLAYER, COST_MULTIPLIER.get());
        event.add(EntityType.PLAYER, MOVE_COST_MULTIPLIER.get());
        event.add(EntityType.PLAYER, SWIM_COST_MULTIPLIER.get());
        event.add(EntityType.PLAYER, MINE_COST_MULTIPLIER.get());
        event.add(EntityType.PLAYER, MELEE_COST_MULTIPLIER.get());
        event.add(EntityType.PLAYER, ARMOR_BURDEN.get());
    }

    private StaminaAttributes() {}
}
