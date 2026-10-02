package faygolover.rpmedicine.server;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Где лежат заглушки офлайн-игроков. Хранит и снимок состояния: если при входе чанк с телом ещё не
 * загружен, игрок восстанавливается из снимка, а сама сущность при загрузке исчезнет.
 */
public final class StubRegistry extends SavedData {
    private static final String NAME = "rpmedicine_stubs";

    public static final class Record {
        public UUID entityId;
        public ResourceKey<Level> dimension;
        public Vec3 pos;
        public float yaw;
        /** Снимок: Medical, Inv, Curios — как в {@code BodyStubEntity}. */
        public CompoundTag snapshot = new CompoundTag();
        /** Заглушку убили (режим «без смерти» выключен): игрок войдёт мёртвым. */
        public boolean dead;
    }

    private final Map<UUID, Record> records = new HashMap<>();

    public static StubRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(StubRegistry::load, StubRegistry::new, NAME);
    }

    @Nullable
    public Record get(UUID owner) {
        return records.get(owner);
    }

    public void put(UUID owner, Record r) {
        records.put(owner, r);
        setDirty();
    }

    @Nullable
    public Record remove(UUID owner) {
        Record r = records.remove(owner);
        if (r != null) setDirty();
        return r;
    }

    public boolean isCurrent(UUID owner, UUID entityId) {
        Record r = records.get(owner);
        return r != null && r.entityId.equals(entityId);
    }

    public java.util.List<UUID> owners() {
        return java.util.List.copyOf(records.keySet());
    }

    public int size() {
        return records.size();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Record> e : records.entrySet()) {
            Record r = e.getValue();
            CompoundTag t = new CompoundTag();
            t.putUUID("Owner", e.getKey());
            t.putUUID("Entity", r.entityId);
            t.putString("Dim", r.dimension.location().toString());
            t.putDouble("X", r.pos.x);
            t.putDouble("Y", r.pos.y);
            t.putDouble("Z", r.pos.z);
            t.putFloat("Yaw", r.yaw);
            t.put("Snapshot", r.snapshot);
            t.putBoolean("Dead", r.dead);
            list.add(t);
        }
        tag.put("Stubs", list);
        return tag;
    }

    static StubRegistry load(CompoundTag tag) {
        StubRegistry reg = new StubRegistry();
        ListTag list = tag.getList("Stubs", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            Record r = new Record();
            r.entityId = t.getUUID("Entity");
            r.dimension = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(t.getString("Dim")));
            r.pos = new Vec3(t.getDouble("X"), t.getDouble("Y"), t.getDouble("Z"));
            r.yaw = t.getFloat("Yaw");
            r.snapshot = t.getCompound("Snapshot");
            r.dead = t.getBoolean("Dead");
            reg.records.put(t.getUUID("Owner"), r);
        }
        return reg;
    }
}
