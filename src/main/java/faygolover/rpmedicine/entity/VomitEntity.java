package faygolover.rpmedicine.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/** Лужа рвоты на земле: лежит пару минут и исчезает. Ничего не делает. */
public class VomitEntity extends Entity {
    /** Сколько тиков лежит. */
    public static final int LIFETIME = 2400;

    public VomitEntity(EntityType<? extends VomitEntity> type, Level level) {
        super(type, level);
        this.noCulling = false;
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    public void tick() {
        super.tick();
        // Падает на землю, дальше лежит.
        if (!onGround()) {
            setDeltaMovement(getDeltaMovement().add(0, -0.04, 0).scale(0.9));
            move(MoverType.SELF, getDeltaMovement());
        } else {
            setDeltaMovement(0, 0, 0);
        }
        if (!level().isClientSide && tickCount > LIFETIME) discard();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        tickCount = tag.getInt("age");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("age", tickCount);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
