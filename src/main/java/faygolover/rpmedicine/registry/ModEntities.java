package faygolover.rpmedicine.registry;

import faygolover.rpmedicine.RpMedicine;
import faygolover.rpmedicine.entity.BodyStubEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Сущности: тело офлайн-игрока (заглушка, п. 5.5 ТЗ). */
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, RpMedicine.MODID);

    public static final RegistryObject<EntityType<BodyStubEntity>> BODY_STUB = ENTITIES.register("body_stub",
            () -> EntityType.Builder.<BodyStubEntity>of(BodyStubEntity::new, MobCategory.MISC)
                    .sized(0.6f, 0.6f)
                    .clientTrackingRange(10)
                    .fireImmune()
                    .build("rpmedicine:body_stub"));

    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(BODY_STUB.get(), BodyStubEntity.createAttributes().build());
    }

    private ModEntities() {}
}
