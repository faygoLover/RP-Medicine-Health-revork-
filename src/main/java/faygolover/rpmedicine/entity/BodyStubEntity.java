package faygolover.rpmedicine.entity;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import faygolover.rpmedicine.capability.MedicalNbt;
import faygolover.rpmedicine.core.MedicalSettings;
import faygolover.rpmedicine.core.MedicalState;
import faygolover.rpmedicine.core.PatientTraits;
import faygolover.rpmedicine.registry.ModEntities;
import faygolover.rpmedicine.server.PatientTicker;
import faygolover.rpmedicine.server.StubService;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Заглушка: тело игрока, который вышел из игры лежачим (п. 5.5 ТЗ). Хранит копию медицинского
 * состояния и весь инвентарь. С ней можно делать всё то же, что с лежачим игроком: лечить, нести,
 * обыскивать, ранить. Заживление у заглушки не идёт.
 */
public class BodyStubEntity extends LivingEntity implements IEntityAdditionalSpawnData {
    /** Индексы как у {@link Inventory}: 0–35 основной, 36–39 броня (ступни…голова), 40 вторая рука. */
    public static final int INV_SIZE = 41;

    private final MedicalState state = new MedicalState();
    private final SimpleContainer inventory = new SimpleContainer(INV_SIZE);
    /** Слоты Curios: идентификатор слота, индекс, предмет. */
    public final List<CurioEntry> curios = new ArrayList<>();

    private UUID ownerId = new UUID(0, 0);
    private String ownerName = "";
    @Nullable
    private String skinValue;
    @Nullable
    private String skinSignature;
    private int selectedSlot;
    private byte traitFlags;
    private boolean changed;
    /** Когда тело последний раз ранили (правило «в бою — прогресс-бар»). */
    public long lastHurtTick = Long.MIN_VALUE / 2;
    /** Койка, на которой лежит тело (второй этап: выход на койке оставляет тело на ней). */
    @Nullable
    private net.minecraft.core.BlockPos bedPos;

    public record CurioEntry(String identifier, int index, ItemStack stack) {}

    public BodyStubEntity(EntityType<? extends BodyStubEntity> type, Level level) {
        super(type, level);
        this.inventory.addListener(c -> markChanged());
    }

    public static BodyStubEntity create(ServerLevel level) {
        return new BodyStubEntity(ModEntities.BODY_STUB.get(), level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    // ------------------------------------------------------------------ данные

    public MedicalState state() {
        return state;
    }

    public SimpleContainer inventory() {
        return inventory;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public String ownerName() {
        return ownerName;
    }

    @Nullable
    public String skinValue() {
        return skinValue;
    }

    @Nullable
    public String skinSignature() {
        return skinSignature;
    }

    public PatientTraits traits() {
        return new PatientTraits((traitFlags & 1) != 0, (traitFlags & 2) != 0, (traitFlags & 4) != 0, (traitFlags & 8) != 0, (traitFlags & 16) != 0,
                (traitFlags & 32) != 0, (traitFlags & 64) != 0, (traitFlags & 128) != 0);
    }

    public void markChanged() {
        changed = true;
    }

    @Nullable
    public net.minecraft.core.BlockPos bedPos() {
        return bedPos;
    }

    public void setBedPos(@Nullable net.minecraft.core.BlockPos pos) {
        if (!java.util.Objects.equals(bedPos, pos)) {
            bedPos = pos;
            markChanged();
        }
    }

    /** Заполняет заглушку из игрока: профиль, перки, состояние. Инвентарь переносит {@link StubService}. */
    public void initFrom(Player player, PatientTraits traits) {
        GameProfile profile = player.getGameProfile();
        ownerId = player.getUUID();
        ownerName = profile.getName();
        for (Property p : profile.getProperties().get("textures")) {
            skinValue = p.getValue();
            skinSignature = p.getSignature();
        }
        selectedSlot = player.getInventory().selected;
        traitFlags = (byte) ((traits.tough ? 1 : 0) | (traits.fragile ? 2 : 0) | (traits.brave ? 4 : 0)
                | (traits.coward ? 8 : 0) | (traits.leftHanded ? 16 : 0) | (traits.diabetic ? 32 : 0) | (traits.smoker ? 64 : 0)
                | (traits.alcoholic ? 128 : 0));
        setCustomName(Component.literal(ownerName));
        setCustomNameVisible(false);
        moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0);
        yBodyRot = player.yBodyRot;
        yHeadRot = player.getYHeadRot();
    }

    public int selectedSlot() {
        return selectedSlot;
    }

    // ------------------------------------------------------------------ поведение

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            // Ванильное здоровье всегда полное.
            if (getHealth() < getMaxHealth() && isAlive()) setHealth(getMaxHealth());
            PatientTicker.tickStub(this);
            faygolover.rpmedicine.hospital.HospitalService.tickStub(this);
            if (changed) {
                changed = false;
                StubService.snapshot(this);
            }
        }
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide) StubService.onStubKilled(this, source);
        super.die(source);
    }

    @Override
    protected void dropAllDeathLoot(DamageSource source) {
        // Инвентарь не выпадает: он вернётся игроку при входе (и выпадет трупом Corpse).
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean shouldShowName() {
        return false;
    }

    @Override
    public boolean isAffectedByPotions() {
        return false;
    }

    @Override
    public HumanoidArm getMainArm() {
        return (traitFlags & 16) != 0 ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        NonNullList<ItemStack> list = NonNullList.withSize(4, ItemStack.EMPTY);
        for (int i = 0; i < 4; i++) list.set(i, inventory.getItem(36 + i));
        return list;
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return switch (slot) {
            case MAINHAND -> inventory.getItem(Math.max(0, Math.min(8, selectedSlot)));
            case OFFHAND -> inventory.getItem(40);
            default -> inventory.getItem(36 + slot.getIndex());
        };
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        switch (slot) {
            case MAINHAND -> inventory.setItem(Math.max(0, Math.min(8, selectedSlot)), stack);
            case OFFHAND -> inventory.setItem(40, stack);
            default -> inventory.setItem(36 + slot.getIndex(), stack);
        }
    }

    // ------------------------------------------------------------------ сохранение

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Medical", MedicalNbt.write(state));
        tag.put("Inv", saveInventory());
        tag.put("Curios", saveCurios());
        tag.putUUID("Owner", ownerId);
        tag.putString("OwnerName", ownerName);
        if (skinValue != null) tag.putString("Skin", skinValue);
        if (skinSignature != null) tag.putString("SkinSig", skinSignature);
        tag.putInt("Selected", selectedSlot);
        tag.putByte("Traits", traitFlags);
        if (bedPos != null) tag.putLong("Bed", bedPos.asLong());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        MedicalNbt.read(state, tag.getCompound("Medical"), MedicalSettings.get());
        loadInventory(tag.getList("Inv", Tag.TAG_COMPOUND));
        loadCurios(tag.getList("Curios", Tag.TAG_COMPOUND));
        if (tag.hasUUID("Owner")) ownerId = tag.getUUID("Owner");
        ownerName = tag.getString("OwnerName");
        skinValue = tag.contains("Skin") ? tag.getString("Skin") : null;
        skinSignature = tag.contains("SkinSig") ? tag.getString("SkinSig") : null;
        selectedSlot = tag.getInt("Selected");
        traitFlags = tag.getByte("Traits");
        bedPos = tag.contains("Bed") ? net.minecraft.core.BlockPos.of(tag.getLong("Bed")) : null;
    }

    public ListTag saveInventory() {
        ListTag list = new ListTag();
        for (int i = 0; i < INV_SIZE; i++) {
            ItemStack s = inventory.getItem(i);
            if (s.isEmpty()) continue;
            CompoundTag t = new CompoundTag();
            t.putByte("Slot", (byte) i);
            s.save(t);
            list.add(t);
        }
        return list;
    }

    public void loadInventory(ListTag list) {
        inventory.clearContent();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            int slot = t.getByte("Slot") & 0xFF;
            if (slot < INV_SIZE) inventory.setItem(slot, ItemStack.of(t));
        }
    }

    public ListTag saveCurios() {
        ListTag list = new ListTag();
        for (CurioEntry c : curios) {
            CompoundTag t = new CompoundTag();
            t.putString("Id", c.identifier());
            t.putInt("Index", c.index());
            c.stack().save(t);
            list.add(t);
        }
        return list;
    }

    public void loadCurios(ListTag list) {
        curios.clear();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            curios.add(new CurioEntry(t.getString("Id"), t.getInt("Index"), ItemStack.of(t)));
        }
    }

    // ------------------------------------------------------------------ сеть

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void writeSpawnData(FriendlyByteBuf buf) {
        buf.writeUUID(ownerId);
        buf.writeUtf(ownerName, 64);
        buf.writeBoolean(skinValue != null);
        if (skinValue != null) {
            buf.writeUtf(skinValue, 32767);
            buf.writeBoolean(skinSignature != null);
            if (skinSignature != null) buf.writeUtf(skinSignature, 32767);
        }
        buf.writeByte(traitFlags);
    }

    @Override
    public void readSpawnData(FriendlyByteBuf buf) {
        ownerId = buf.readUUID();
        ownerName = buf.readUtf(64);
        if (buf.readBoolean()) {
            skinValue = buf.readUtf(32767);
            skinSignature = buf.readBoolean() ? buf.readUtf(32767) : null;
        }
        traitFlags = buf.readByte();
    }
}
