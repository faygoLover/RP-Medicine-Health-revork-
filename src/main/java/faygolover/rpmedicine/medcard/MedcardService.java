package faygolover.rpmedicine.medcard;

import faygolover.rpmedicine.core.BloodType;
import faygolover.rpmedicine.core.MedicalState;
import faygolover.rpmedicine.entity.BodyStubEntity;
import faygolover.rpmedicine.item.MedcardItem;
import faygolover.rpmedicine.network.MedcardActionPacket;
import faygolover.rpmedicine.network.MedcardDataPacket;
import faygolover.rpmedicine.network.Network;
import faygolover.rpmedicine.server.Medical;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Медкарта (ТЗ второго этапа, п. 10): синхронизация роста, веса и группы с состоянием, предложения
 * записей после событий, правка с экрана, команда ГМа.
 */
public final class MedcardService {
    private MedcardService() {}

    /** Одинаковые предложения не чаще (очередь выстрелов не должна заваливать карту). */
    private static final long DEDUPE_MS = 10 * 60 * 1000L;
    public static final int MAX_TEXT = 300;

    /** UUID персонажа: у заглушки — её владельца. */
    @Nullable
    public static UUID ownerOf(@Nullable Entity e) {
        if (e instanceof Player p) return p.getUUID();
        if (e instanceof BodyStubEntity stub) return stub.ownerId();
        return null;
    }

    /** Вход: имя; рост, вес и группа — из карты, если ГМ их задал, иначе карта берёт их у персонажа. */
    public static void onLogin(ServerPlayer sp) {
        Medcard c = MedcardStore.get(sp.server, sp.getUUID());
        MedicalState m = Medical.state(sp);
        if (m == null) return;
        c.name = sp.getGameProfile().getName();
        if (c.height > 0) m.heightCm = c.height;
        else c.height = m.heightCm;
        if (c.weight > 0) {
            double f = m.bloodFraction(faygolover.rpmedicine.core.MedicalSettings.get());
            m.weightKg = c.weight;
            m.bloodVolume = m.normalBlood(faygolover.rpmedicine.core.MedicalSettings.get()) * f;
        } else {
            c.weight = m.weightKg;
        }
        if (!c.bloodType.isEmpty()) BloodType.byId(c.bloodType).ifPresent(t -> m.bloodType = t);
        else if (m.bloodType != null) c.bloodType = m.bloodType.id;
        Medical.changed(sp);
        MedcardStore.save(sp.server, c);
    }

    /**
     * Мод предлагает запись: она появляется в карте как «предложение» (принять, отклонить, изменить).
     * Аргументы, начинающиеся с {@code #}, — ключи перевода (часть тела и т.п.).
     */
    public static void propose(MinecraftServer server, @Nullable Entity patient, String key, List<String> args, String author) {
        UUID uuid = ownerOf(patient);
        if (uuid == null) return;
        Medcard c = MedcardStore.get(server, uuid);
        long now = System.currentTimeMillis();
        for (Medcard.Entry e : c.entries) {
            if (e.proposed && e.key.equals(key) && e.args.equals(args) && now - e.time < DEDUPE_MS) return;
        }
        c.add(key, args, "", author, true);
        if (c.entries.size() > 500) c.entries.remove(0);
        MedcardStore.save(server, c);
    }

    /** Высота голоса по полу из медкарты: женский выше. */
    public static float voicePitch(@Nullable Entity e) {
        UUID uuid = ownerOf(e);
        if (uuid == null || e.getServer() == null) return 1f;
        String g = MedcardStore.get(e.getServer(), uuid).gender;
        return g.equals("f") ? (float) faygolover.rpmedicine.core.MedicalSettings.get().femaleVoicePitch : 1f;
    }

    /** Открыть карту: проверяется, что у игрока карта этого персонажа (или он оператор). */
    public static void open(ServerPlayer viewer, UUID uuid) {
        if (!canAccess(viewer, uuid)) return;
        Network.send(viewer, MedcardDataPacket.of(MedcardStore.get(viewer.server, uuid)));
    }

    private static boolean canAccess(ServerPlayer viewer, UUID uuid) {
        if (viewer.hasPermissions(2)) return true;
        for (ItemStack st : new ItemStack[]{viewer.getMainHandItem(), viewer.getOffhandItem()}) {
            if (st.getItem() instanceof MedcardItem && uuid.equals(MedcardItem.owner(st))) return true;
        }
        return false;
    }

    /** Правка с экрана карты. Открыть и дописать может любой с картой (известная проблема 1.10). */
    public static void onAction(ServerPlayer sp, MedcardActionPacket p) {
        if (!canAccess(sp, p.uuid())) return;
        Medcard c = MedcardStore.get(sp.server, p.uuid());
        String text = p.text().length() > MAX_TEXT ? p.text().substring(0, MAX_TEXT) : p.text();
        switch (p.op()) {
            case ADD -> {
                if (!text.isBlank()) c.add("", List.of(), text, sp.getGameProfile().getName(), false);
            }
            case EDIT -> {
                Medcard.Entry e = c.entry(p.entryId());
                if (e != null && !text.isBlank()) {
                    e.text = text;
                    e.proposed = false;
                    if (!e.author.contains(sp.getGameProfile().getName()))
                        e.author = e.author.isEmpty() ? sp.getGameProfile().getName() : e.author + ", " + sp.getGameProfile().getName();
                }
            }
            case ACCEPT -> {
                Medcard.Entry e = c.entry(p.entryId());
                if (e != null) e.proposed = false;
            }
            case DECLINE -> {
                Medcard.Entry e = c.entry(p.entryId());
                if (e != null && e.proposed) c.entries.remove(e);
            }
            case ALLERGIES -> c.allergies = text;
            case CHRONIC -> c.chronic = text;
            case FULL_NAME -> c.fullName = text.length() > 64 ? text.substring(0, 64) : text;
            case AGE -> {
                try {
                    int v = Integer.parseInt(text.trim());
                    if (v >= 0 && v <= 150) c.age = v;
                } catch (NumberFormatException ignored) {
                }
            }
            case GENDER -> c.gender = text.equals("m") || text.equals("f") ? text : "";
        }
        MedcardStore.save(sp.server, c);
        Network.send(sp, MedcardDataPacket.of(c));
    }

    /** Команда ГМа: рост, вес, группа, аллергии, хронические состояния. */
    public static Component gmSet(MinecraftServer server, UUID uuid, String name, String field, String value) {
        Medcard c = MedcardStore.get(server, uuid);
        if (c.name.isEmpty()) c.name = name;
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        MedicalState m = online != null ? Medical.state(online) : null;
        var s = faygolover.rpmedicine.core.MedicalSettings.get();
        switch (field) {
            case "height" -> {
                double v = Double.parseDouble(value);
                if (v < 100 || v > 250) throw new IllegalArgumentException("100–250");
                c.height = v;
                if (m != null) m.heightCm = v;
            }
            case "weight" -> {
                double v = Double.parseDouble(value);
                if (v < 20 || v > 300) throw new IllegalArgumentException("20–300");
                c.weight = v;
                if (m != null) {
                    double f = m.bloodFraction(s);
                    m.weightKg = v;
                    m.bloodVolume = m.normalBlood(s) * f;
                }
            }
            case "blood_type" -> {
                BloodType t = value.equalsIgnoreCase("random") ? BloodType.random(s.bloodTypeWeights, new java.util.SplittableRandom())
                        : BloodType.byId(value).orElseThrow(() -> new IllegalArgumentException("o-, o+, a-, a+, b-, b+, ab-, ab+, random"));
                c.bloodType = t.id;
                if (m != null) m.bloodType = t;
            }
            case "allergies" -> c.allergies = value;
            case "chronic" -> c.chronic = value;
            case "full_name" -> c.fullName = value;
            case "age" -> {
                int v = Integer.parseInt(value);
                if (v < 0 || v > 150) throw new IllegalArgumentException("0–150");
                c.age = v;
            }
            case "gender" -> {
                if (!value.equals("m") && !value.equals("f") && !value.equals("-")) throw new IllegalArgumentException("m, f, -");
                c.gender = value.equals("-") ? "" : value;
            }
            default -> throw new IllegalArgumentException("height, weight, blood_type, allergies, chronic, full_name, age, gender");
        }
        if (online != null) Medical.changed(online);
        MedcardStore.save(server, c);
        return Component.translatable("rpmedicine.cmd.card_set", c.name, field, value).withStyle(ChatFormatting.GREEN);
    }
}
