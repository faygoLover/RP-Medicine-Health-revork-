package faygolover.rpmedicine.registry;

import faygolover.rpmedicine.RpMedicine;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Звуки: из BodyControl (VKM), Tactical Medicine, Health & Disease, остальное — ванильные заглушки (sounds.json). */
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
    public static final RegistryObject<SoundEvent> MONITOR_ALARM = reg("monitor_alarm");
    public static final RegistryObject<SoundEvent> VOMIT = reg("vomit");
    public static final RegistryObject<SoundEvent> HEARTBEAT_FAST = reg("heartbeat_fast");
    public static final RegistryObject<SoundEvent> GASP = reg("gasp");
    public static final RegistryObject<SoundEvent> COUGH = reg("cough");
    public static final RegistryObject<SoundEvent> PAIN_GROAN = reg("pain_groan");
    public static final RegistryObject<SoundEvent> PAIN_MOAN = reg("pain_moan");
    public static final RegistryObject<SoundEvent> SPLINT = reg("splint");
    public static final RegistryObject<SoundEvent> EARDRUM_BURST = reg("eardrum_burst");
    public static final RegistryObject<SoundEvent> FLATLINE = reg("flatline");
    public static final RegistryObject<SoundEvent> HEART_STOPPING = reg("heart_stopping");
    public static final RegistryObject<SoundEvent> SCANNER = reg("scanner");
    public static final RegistryObject<SoundEvent> AMMONIA = reg("ammonia");
    public static final RegistryObject<SoundEvent> WAKE_UP = reg("wake_up");
    public static final RegistryObject<SoundEvent> SURGERY_CUT = reg("surgery_cut");
    public static final RegistryObject<SoundEvent> SURGERY_STITCH = reg("surgery_stitch");
    public static final RegistryObject<SoundEvent> SURGERY_CLAMP = reg("surgery_clamp");
    public static final RegistryObject<SoundEvent> SURGERY_RETRACT = reg("surgery_retract");
    public static final RegistryObject<SoundEvent> SURGERY_SUCTION = reg("surgery_suction");
    public static final RegistryObject<SoundEvent> SURGERY_BLEED = reg("surgery_bleed");
    public static final RegistryObject<SoundEvent> SURGERY_BONE_SET = reg("surgery_bone_set");
    public static final RegistryObject<SoundEvent> SURGERY_VESSEL_CUT = reg("surgery_vessel_cut");
    public static final RegistryObject<SoundEvent> SURGERY_CAUTERY = reg("surgery_cautery");
    public static final RegistryObject<SoundEvent> SURGERY_ERROR = reg("surgery_error");
    public static final RegistryObject<SoundEvent> SURGERY_TRACHEA = reg("surgery_trachea");
    public static final RegistryObject<SoundEvent> BONE_SAW = reg("bone_saw");
    public static final RegistryObject<SoundEvent> BONE_DRILL = reg("bone_drill");
    public static final RegistryObject<SoundEvent> ORGAN_MOVE = reg("organ_move");
    public static final RegistryObject<SoundEvent> MINIGAME_OK = reg("minigame_ok");
    public static final RegistryObject<SoundEvent> MINIGAME_SLIP = reg("minigame_slip");
    public static final RegistryObject<SoundEvent> BODY_FALL = reg("body_fall");

    private static RegistryObject<SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(RpMedicine.MODID, name)));
    }

    private ModSounds() {}
}
