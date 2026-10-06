package faygolover.rpmedicine.hospital;

import faygolover.rpmedicine.core.BodyPart;
import faygolover.rpmedicine.core.Drug;
import faygolover.rpmedicine.core.DrugLevels;
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
 * Стойка капельницы: пакеты на крючках и шланг к пациенту. Пока шланг подключён к катетеру, из первого
 * непустого пакета капает пациенту (обычная капельница ядра); пакет опустел — шланг переходит на следующий.
 * В пакет физраствора можно ввести препарат шприцем (норадреналин, пропофол) — он капает дольше и вводит
 * препарат по мере того, как капает (решения, п. 1.16). Отошёл дальше шланга — катетер вырван, рана на руке.
 */
public class IvStandBlockEntity extends BlockEntity {
    public static final int HOOKS = 3;
    /** Вид пакета на крючке (и для отрисовки). */
    public static final byte NONE = 0, SALINE = 1, BLOOD = 2, EMPTY_SALINE = 3, EMPTY_BLOOD = 4, SALINE_YELLOW = 5, SALINE_MILKY = 6;
    /** NBT пакета: объём (если начат) и добавленный препарат. */
    public static final String ML = "Ml", ADD = "Add", ADD_DOSES = "AddDoses";

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
    public final float[] clientVolume = new float[HOOKS];
    public final float[] clientMax = new float[HOOKS];
    public final String[] clientAdd = {"", "", ""};
    public final float[] clientAddDoses = new float[HOOKS];
    public int clientArm = -1;
    public int clientActive = -1;

    public IvStandBlockEntity(BlockPos pos, BlockState st) {
        super(ModBlocks.IV_STAND_BE.get(), pos, st);
    }

    public static boolean isBag(ItemStack st) {
        return st.is(ModItems.SALINE.get()) || st.is(ModItems.BLOOD_BAG.get());
    }

    /** Полный объём пакета. */
    public static double fullVolume(ItemStack bag) {
        MedicalSettings s = MedicalSettings.get();
        return bag.is(ModItems.BLOOD_BAG.get()) ? s.bloodBagVolume : s.salineVolume;
    }

    /** Сколько в пакете (начатый — из NBT). */
    public static double volumeOf(ItemStack bag) {
        CompoundTag t = bag.getTag();
        return t != null && t.contains(ML) ? t.getFloat(ML) : fullVolume(bag);
    }

    @Nullable
    public static String additive(ItemStack bag) {
        CompoundTag t = bag.getTag();
        return t != null && t.contains(ADD) ? t.getString(ADD) : null;
    }

    public static double additiveDoses(ItemStack bag) {
        CompoundTag t = bag.getTag();
        return t != null ? t.getFloat(ADD_DOSES) : 0;
    }

    public byte kind(int i) {
        if (bags[i].isEmpty()) return NONE;
        boolean blood = bags[i].is(ModItems.BLOOD_BAG.get());
        if (volume[i] <= 0.5) return blood ? EMPTY_BLOOD : EMPTY_SALINE;
        if (blood) return BLOOD;
        String add = additive(bags[i]);
        if (add == null) return SALINE;
        return add.endsWith("propofol") ? SALINE_MILKY : SALINE_YELLOW;
    }

    public boolean linked() {
        return patient != null;
    }

    @Nullable
    public UUID patient() {
        return patient;
    }

    /** Повесить пакет на свободный крючок (начатый — со своим объёмом). Использованный не вешается. */
    public boolean hang(ItemStack bag) {
        for (int i = 0; i < HOOKS; i++) {
            if (!bags[i].isEmpty()) continue;
            bags[i] = bag.copyWithCount(1);
            volume[i] = volumeOf(bag);
            changed();
            return true;
        }
        return false;
    }

    /**
     * Снять последний пакет (не тот, что сейчас капает). Полный — тем же предметом, начатый — с остатком в мл,
     * пустой — использованным пакетом (мусор).
     */
    @Nullable
    public ItemStack takeLast() {
        for (int i = HOOKS - 1; i >= 0; i--) {
            if (bags[i].isEmpty() || i == active) continue;
            ItemStack out = bags[i].copy();
            double v = volume[i];
            bags[i] = ItemStack.EMPTY;
            volume[i] = 0;
            changed();
            if (v <= 0.5) return new ItemStack(ModItems.USED_IV_BAG.get());
            if (v < fullVolume(out) - 1) out.getOrCreateTag().putFloat(ML, (float) v);
            else if (out.getTag() != null) out.getTag().remove(ML);
            return out;
        }
        return null;
    }

    /**
     * Ввести препарат шприцем в пакет физраствора (первый подходящий): капельница с ним держит уровень.
     * Возвращает ключ отказа или null.
     */
    @Nullable
    public String inject(String drugId, double doses) {
        for (int i = 0; i < HOOKS; i++) {
            if (bags[i].isEmpty() || !bags[i].is(ModItems.SALINE.get()) || volume[i] <= 0.5) continue;
            String add = additive(bags[i]);
            if (add != null && !add.equals(drugId)) continue;
            CompoundTag t = bags[i].getOrCreateTag();
            t.putString(ADD, drugId);
            t.putFloat(ADD_DOSES, (float) (additiveDoses(bags[i]) + doses));
            // Уже капает — пересчитать скорость под добавленный препарат.
            if (i == active) restartActive();
            changed();
            return null;
        }
        return "rpmedicine.iv.no_saline_for_drug";
    }

    /** Пациент на шланге: игрок или тело-заглушка офлайн-игрока (решения, п. 1.16). */
    @Nullable
    private net.minecraft.world.entity.LivingEntity patientEntity() {
        if (patient == null || !(level instanceof net.minecraft.server.level.ServerLevel sl)) return null;
        return sl.getEntity(patient) instanceof net.minecraft.world.entity.LivingEntity le ? le : null;
    }

    public void link(net.minecraft.world.entity.LivingEntity target, int armPart) {
        patient = target.getUUID();
        arm = armPart;
        active = -1;
        changed();
    }

    /** Отсоединить шланг: недокапавшее вернуть в пакет, капельницу пациента остановить. */
    public void detach() {
        if (patient != null && level != null && level.getServer() != null) {
            net.minecraft.world.entity.LivingEntity p = patientEntity();
            if (p != null && active >= 0) {
                MedicalState m = Medical.state(p);
                if (m != null) {
                    boolean blood = bags[active].is(ModItems.BLOOD_BAG.get());
                    setVolume(active, Math.max(0, blood ? m.bloodDripRemaining : m.salineDripRemaining));
                    if (blood) BloodBagItem.setCold(bags[active], false, level.getGameTime());
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

    private void restartActive() {
        if (patient == null || level == null || level.getServer() == null || active < 0) return;
        net.minecraft.world.entity.LivingEntity p = patientEntity();
        MedicalState m = p != null ? Medical.state(p) : null;
        if (m == null) return;
        if (!bags[active].is(ModItems.BLOOD_BAG.get())) m.salineDripRate = rateFor(bags[active]);
    }

    private static double rateFor(ItemStack bag) {
        MedicalSettings s = MedicalSettings.get();
        double secs = additive(bag) != null ? s.ivAdditiveDripSeconds : s.salineDripSeconds;
        return s.salineVolume / Math.max(1, secs);
    }

    /** Новый объём пакета; с препаратом — препарат убывает вместе с раствором. */
    private void setVolume(int i, double v) {
        String add = additive(bags[i]);
        if (add != null && volume[i] > 0.01) {
            double frac = Math.max(0, v) / volume[i];
            bags[i].getOrCreateTag().putFloat(ADD_DOSES, (float) (additiveDoses(bags[i]) * Math.min(1, frac)));
        }
        volume[i] = Math.max(0, v);
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

    /** Кровь на стойке портится как обычно, но не пока течёт пациенту (решения, п. 1.16). */
    public boolean bloodFlowing(int i) {
        return i == active && patient != null;
    }

    public ItemStack bag(int i) {
        return bags[i];
    }

    public void serverTick() {
        if (patient == null || level == null || level.getServer() == null || ++ticks % 10 != 0) return;
        net.minecraft.world.entity.LivingEntity p = patientEntity();
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
            if (p instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable("rpmedicine.iv.torn").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (active >= 0) {
            boolean blood = bags[active].is(ModItems.BLOOD_BAG.get());
            double left = blood ? m.bloodDripRemaining : m.salineDripRemaining;
            double before = volume[active];
            if (Math.abs(left - before) > 0.01) {
                // Препарат в пакете вводится по мере того, как капает.
                String add = additive(bags[active]);
                if (add != null && before > 0.01 && left < before) {
                    Drug d = DrugLevels.resolver.apply(add);
                    double doses = additiveDoses(bags[active]) * (before - left) / before;
                    if (d != null && doses > 0) DrugLevels.give(m, d, doses, DrugLevels.Route.DRIP, s);
                }
                boolean wasFull = before > 0.5;
                setVolume(active, left);
                if (wasFull != volume[active] > 0.5) changed();
                else syncSoft();
            }
            if (left <= 0) {
                active = -1;
                changed();
            } else {
                return;
            }
        }
        // Своя капельница у пациента уже идёт (поставлена пакетом в руке) — ждём.
        if (m.salineDripRemaining > 0 || m.bloodDripRemaining > 0) return;
        for (int i = 0; i < HOOKS; i++) {
            if (bags[i].isEmpty() || volume[i] <= 0.5) continue;
            if (bags[i].is(ModItems.BLOOD_BAG.get())) {
                var bag = BloodBagItem.bag(bags[i], level.getGameTime());
                // Пока кровь течёт пациенту — не портится.
                BloodBagItem.setCold(bags[i], true, level.getGameTime());
                m.bloodDripRemaining = volume[i];
                m.bloodDripRate = s.bloodBagVolume / Math.max(1, s.transfusionSeconds);
                m.bloodDripType = bag.type();
                m.bloodDripSpoiled = bag.spoiled();
            } else {
                m.salineDripRemaining = volume[i];
                m.salineDripRate = rateFor(bags[i]);
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
        for (int i = HOOKS - 1; i >= 0; i--) {
            if (bags[i].isEmpty()) continue;
            ItemStack out = takeLast();
            if (out != null && !out.isEmpty())
                Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5, out);
        }
    }

    private long lastSoftSync;

    /** Объём на экране стойки — не чаще раза в 2 с. */
    private void syncSoft() {
        setChanged();
        if (level != null && level.getGameTime() - lastSoftSync >= 40) {
            lastSoftSync = level.getGameTime();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
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
        // Клиент: данные для отрисовки и экрана стойки.
        if (t.contains("Kinds")) {
            byte[] k = t.getByteArray("Kinds");
            System.arraycopy(k, 0, clientKinds, 0, Math.min(k.length, HOOKS));
            ListTag info = t.getList("Info", Tag.TAG_COMPOUND);
            for (int i = 0; i < HOOKS && i < info.size(); i++) {
                CompoundTag c = info.getCompound(i);
                clientVolume[i] = c.getFloat("v");
                clientMax[i] = c.getFloat("m");
                clientAdd[i] = c.getString("a");
                clientAddDoses[i] = c.getFloat("d");
            }
            clientPatientId = t.getInt("PatientId");
            clientArm = t.getByte("ClientArm");
            clientActive = t.getByte("ClientActive");
            return;
        }
        ListTag list = t.getList("Bags", Tag.TAG_COMPOUND);
        for (int i = 0; i < HOOKS; i++) {
            CompoundTag b = i < list.size() ? list.getCompound(i) : new CompoundTag();
            bags[i] = b.contains("Item") ? ItemStack.of(b.getCompound("Item")) : ItemStack.EMPTY;
            volume[i] = b.getFloat("Vol");
        }
        patient = t.hasUUID("Patient") ? t.getUUID("Patient") : null;
        arm = patient != null ? t.getByte("Arm") : -1;
        active = patient != null ? t.getByte("Active") : -1;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag t = new CompoundTag();
        byte[] k = new byte[HOOKS];
        ListTag info = new ListTag();
        for (int i = 0; i < HOOKS; i++) {
            k[i] = kind(i);
            CompoundTag c = new CompoundTag();
            if (!bags[i].isEmpty()) {
                c.putFloat("v", (float) volume[i]);
                c.putFloat("m", (float) fullVolume(bags[i]));
                String add = additive(bags[i]);
                if (add != null) {
                    c.putString("a", add);
                    c.putFloat("d", (float) additiveDoses(bags[i]));
                }
            }
            info.add(c);
        }
        t.putByteArray("Kinds", k);
        t.put("Info", info);
        int id = -1;
        if (patient != null && level != null && level.getServer() != null) {
            net.minecraft.world.entity.LivingEntity p = patientEntity();
            if (p != null) id = p.getId();
        }
        t.putInt("PatientId", id);
        t.putByte("ClientArm", (byte) arm);
        t.putByte("ClientActive", (byte) active);
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
