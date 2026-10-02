package faygolover.rpmedicine.registry;

import faygolover.rpmedicine.RpMedicine;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Звуки. Пока заглушки: в sounds.json указывают на ванильные файлы (п. 7.5 ТЗ). */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, RpMedicine.MODID);

    public static final RegistryObject<SoundEvent> HEARTBEAT = reg("heartbeat");
    public static final RegistryObject<SoundEvent> HEAVY_BREATHING = reg("heavy_breathing");
    public static final RegistryObject<SoundEvent> EAR_RINGING = reg("ear_ringing");
    public static final RegistryObject<SoundEvent> BANDAGE = reg("bandage");
    public static final RegistryObject<SoundEvent> INJECTION = reg("injection");
    public static final RegistryObject<SoundEvent> TOURNIQUET = reg("tourniquet");
    public static final RegistryObject<SoundEvent> DEFIB_SHOCK = reg("defib_shock");
    public static final RegistryObject<SoundEvent> BONE_BREAK = reg("bone_break");
    public static final RegistryObject<SoundEvent> PILLS = reg("pills");

    private static RegistryObject<SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(RpMedicine.MODID, name)));
    }

    private ModSounds() {}
}
