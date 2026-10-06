package faygolover.rpmedicine.hospital;

import faygolover.rpmedicine.core.BodyPart;
import faygolover.rpmedicine.core.Injuries;
import faygolover.rpmedicine.core.MedicalSettings;
import faygolover.rpmedicine.core.MedicalState;
import faygolover.rpmedicine.core.WoundType;
import faygolover.rpmedicine.item.BloodBagItem;
import faygolover.rpmedicine.registry.ModBlocks;
import faygolover.rpmedicine.registry.ModItems;
import faygolover.rpmedicine.server.Medical;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Стойка капельницы: пакеты на крючках и шланг к пациенту. Пока шланг подключён к катетеру,
 * из первого непустого пакета капает пациенту (обычная капельница ядра); отошёл дальше длины шланга —
 * катетер вырван, рана на руке.
 */
public class IvStandBlockEntity extends BlockEntity {
    public static final int HOOKS = 3;
    /** Вид пакета на крючке (и для отрисовки). */
    public static final byte NONE = 0, SALINE = 1, BLOOD = 2, EMPTY_SALINE = 3, EMPTY_BLOOD = 4;

    private final ItemStack[] bags = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    private final double[] volume = new double[HOOKS];
    @Nullable
    private UUID patient;
    private int arm = -1;
    private int active = -1;
    private int ticks;
    /** Клиент: id сущности пациента для шланга. */
    public int clientPatientId = -1;
    public final byte[] clientKinds = new byte[HOOKS];
    public int clientArm = -1;

    public IvStandBlockEntity(BlockPos pos, BlockState st) {
        super(ModBlocks.IV_STAND_BE.get(), pos, st);
    }

    public static boolean isBag(ItemStack st) {
        return st.is(ModItems.SALINE.get()) || st.is(ModItems.BLOOD_BAG.get());
    }

    public byte kind(int i) {
        if (bags[i].isEmpty()) return NONE;
        boolean blood = bags[i].is(ModItems.BLOOD_BAG.get());
        if (volume[i] <= 0.5) return blood ? EMPTY_BLOOD : EMPTY_SALINE;
        return blood ? BLOOD : SALINE;
    }

    public boolean linked() {
        return patient != null;
    }

    @Nullable
    public UUID patient() {
        return patient;
    }

    /** Повесить пакет на свободный крючок. */
    public boolean hang(ItemStack bag) {
        MedicalSettings s = MedicalSettings.get();
        for (int i = 0; i < HOOKS; i++) {
            if (!bags[i].isEmpty()) continue;
            bags[i] = bag.copyWithCount(1);
            volume[i] = bag.is(ModItems.BLOOD_BAG.get()) ? s.bloodBagVolume : s.salineVolume;
            changed();
            return true;
        }
        return false;
    }

    /** Снять последний пакет (не тот, что сейчас капает). Полный — вернуть предметом, начатый и пустой — выбросить. */
    public ItemStack takeLast(boolean[] thrown) {
        MedicalSettings s = MedicalSettings.get();
        for (int i = HOOKS - 1; i >= 0; i--) {
            if (bags[i].isEmpty() || i == active) continue;
            ItemStack out = bags[i];
            double full = out.is(ModItems.BLOOD_BAG.get()) ? s.bloodBagVolume : s.salineVolume;
            thrown[0] = volume[i] < full - 1;
            bags[i] = ItemStack.EMPTY;
            volume[i] = 0;
            changed();
            return thrown[0] ? ItemStack.EMPTY : out;
        }
        thrown[0] = false;
        return null;
    }

    public void link(ServerPlayer target, int armPart) {
        patient = target.getUUID();
        arm = armPart;
        active = -1;
        changed();
    }

    /** Отсоединить шланг: недокапавшее вернуть в пакет, капельницу пациента остановить. */
    public void detach() {
        if (patient != null && level != null && level.getServer() != null) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(patient);
            if (p != null && active >= 0) {
                MedicalState m = Medical.state(p);
                if (m != null) {
                    boolean blood = bags[active].is(ModItems.BLOOD_BAG.get());
                    volume[active] = Math.max(0, blood ? m.bloodDripRemaining : m.salineDripRemaining);
                    stopDrip(m, blood);
                    Medical.changed(p);
                }
            }
        }
        patient = null;
        arm = -1;
        active = -1;
        changed();
    }

    private static void stopDrip(MedicalState m, boolean blood) {
        if (blood) {
            m.bloodDripRemaining = 0;
            m.bloodDripRate = 0;
            m.bloodDripType = null;
            m.bloodDripSpoiled = false;
        } else {
            m.salineDripRemaining = 0;
            m.salineDripRate = 0;
        }
    }

    public Vec3 hook() {
        return Vec3.atBottomCenterOf(worldPosition).add(0, 1.3, 0);
    }

    public void serverTick() {
        if (patient == null || level == null || level.getServer() == null || ++ticks % 10 != 0) return;
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(patient);
        MedicalState m = p != null && p.level() == level ? Medical.state(p) : null;
        if (m == null || m.catheterPart < 0 || m.catheterPart != arm) {
            detach();
            return;
        }
        MedicalSettings s = MedicalSettings.get();
        if (p.position().distanceTo(Vec3.atBottomCenterOf(worldPosition)) > s.ivHoseLength) {
            // Отошёл дальше шланга, не вынув катетер: катетер вырван.
            detach();
            m.catheterPart = -1;
            BodyPart part = BodyPart.values()[arm < 0 ? BodyPart.RIGHT_ARM.ordinal() : arm];
            Injuries.mergeWound(m.part(part), WoundType.CUT, s.ivTearWoundSeverity, s);
            Medical.markHurt(p);
            Medical.changed(p);
            p.displayClientMessage(Component.translatable("rpmedicine.iv.torn").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (active >= 0) {
            boolean blood = bags[active].is(ModItems.BLOOD_BAG.get());
            double left = blood ? m.bloodDripRemaining : m.salineDripRemaining;
            if (Math.abs(left - volume[active]) > 0.01) {
                boolean wasFull = volume[active] > 0.5;
                volume[active] = Math.max(0, left);
                if (wasFull != volume[active] > 0.5) changed();
                else setChanged();
            }
            if (left <= 0) {
                active = -1;
                changed();
            }
            return;
        }
        // Своя капельница у пациента уже идёт (поставлена пакетом в руке) — ждём.
        if (m.salineDripRemaining > 0 || m.bloodDripRemaining > 0) return;
        for (int i = 0; i < HOOKS; i++) {
            if (bags[i].isEmpty() || volume[i] <= 0.5) continue;
            if (bags[i].is(ModItems.BLOOD_BAG.get())) {
                var bag = BloodBagItem.bag(bags[i], level.getGameTime());
                m.bloodDripRemaining = volume[i];
                m.bloodDripRate = s.bloodBagVolume / Math.max(1, s.transfusionSeconds);
                m.bloodDripType = bag.type();
                m.bloodDripSpoiled = bag.spoiled();
            } else {
                m.salineDripRemaining = volume[i];
                m.salineDripRate = s.salineVolume / Math.max(1, s.salineDripSeconds);
            }
            active = i;
            Medical.changed(p);
            changed();
            return;
        }
    }

    public void dropAll() {
        if (level == null) return;
        if (patient != null) detach();
        for (int i = 0; i < HOOKS; i++) {
            if (!bags[i].isEmpty() && kind(i) != EMPTY_BLOOD && kind(i) != EMPTY_SALINE && volume[i] > 0)
                Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5, bags[i]);
            bags[i] = ItemStack.EMPTY;
        }
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    // ------------------------------------------------------------------ сохранение и синхронизация

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        ListTag list = new ListTag();
        for (int i = 0; i < HOOKS; i++) {
            CompoundTag b = new CompoundTag();
            if (!bags[i].isEmpty()) {
                b.put("Item", bags[i].save(new CompoundTag()));
                b.putFloat("Vol", (float) volume[i]);
            }
            list.add(b);
        }
        t.put("Bags", list);
        if (patient != null) {
            t.putUUID("Patient", patient);
            t.putByte("Arm", (byte) arm);
            t.putByte("Active", (byte) active);
        }
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        ListTag list = t.getList("Bags", Tag.TAG_COMPOUND);
        for (int i = 0; i < HOOKS; i++) {
            CompoundTag b = i < list.size() ? list.getCompound(i) : new CompoundTag();
            bags[i] = b.contains("Item") ? ItemStack.of(b.getCompound("Item")) : ItemStack.EMPTY;
            volume[i] = b.getFloat("Vol");
        }
        patient = t.hasUUID("Patient") ? t.getUUID("Patient") : null;
        arm = patient != null ? t.getByte("Arm") : -1;
        active = patient != null ? t.getByte("Active") : -1;
        // Клиент
        if (t.contains("Kinds")) {
            byte[] k = t.getByteArray("Kinds");
            System.arraycopy(k, 0, clientKinds, 0, Math.min(k.length, HOOKS));
            clientPatientId = t.getInt("PatientId");
            clientArm = t.getByte("ClientArm");
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag t = new CompoundTag();
        byte[] k = new byte[HOOKS];
        for (int i = 0; i < HOOKS; i++) k[i] = kind(i);
        t.putByteArray("Kinds", k);
        int id = -1;
        if (patient != null && level != null && level.getServer() != null) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(patient);
            if (p != null) id = p.getId();
        }
        t.putInt("PatientId", id);
        t.putByte("ClientArm", (byte) arm);
        return t;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public AABB getRenderBoundingBox() {
        // Шланг тянется до пациента.
        return new AABB(worldPosition).inflate(6, 3, 6);
    }
}
