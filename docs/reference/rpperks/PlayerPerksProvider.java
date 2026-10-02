package faygolover.rpperks.capability;

import faygolover.rpperks.Rpperks;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Привязка {@link PlayerPerks} к игроку.
 * Регистрация самой capability — в {@link Rpperks} (событие MOD-шины).
 */
@Mod.EventBusSubscriber(modid = Rpperks.MODID)
public class PlayerPerksProvider implements ICapabilitySerializable<CompoundTag> {
    public static final Capability<PlayerPerks> PLAYER_PERKS = CapabilityManager.get(new CapabilityToken<>() {});
    private static final ResourceLocation KEY = ResourceLocation.fromNamespaceAndPath(Rpperks.MODID, "player_perks");

    private final PlayerPerks instance = new PlayerPerks();
    private final LazyOptional<PlayerPerks> optional = LazyOptional.of(() -> instance);

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return PLAYER_PERKS.orEmpty(cap, optional);
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        instance.saveNBT(nbt);
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        instance.loadNBT(nbt);
    }

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(KEY, new PlayerPerksProvider());
        }
    }
}
