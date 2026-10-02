package faygolover.rpmedicine.capability;

import faygolover.rpmedicine.RpMedicine;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Привязка {@link MedicalData} к игроку и сохранение вместе с его данными. */
public final class MedicalCapability implements ICapabilitySerializable<CompoundTag> {
    public static final Capability<MedicalData> MEDICAL = CapabilityManager.get(new CapabilityToken<>() {});
    private static final ResourceLocation KEY = new ResourceLocation(RpMedicine.MODID, "medical");

    private final MedicalData data = new MedicalData();
    private final LazyOptional<MedicalData> optional = LazyOptional.of(() -> data);

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return MEDICAL.orEmpty(cap, optional);
    }

    @Override
    public CompoundTag serializeNBT() {
        return data.save();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        data.load(nbt);
    }

    public static void register(RegisterCapabilitiesEvent event) {
        event.register(MedicalData.class);
    }

    public static void attach(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) event.addCapability(KEY, new MedicalCapability());
    }

    /** Данные игрока или null (на клиенте у чужих игроков данных нет). */
    @Nullable
    public static MedicalData get(Player player) {
        return player.getCapability(MEDICAL).resolve().orElse(null);
    }
}
