package faygolover.rpmedicine.stats;

import com.google.gson.JsonObject;
import faygolover.rpmedicine.core.BodyPart;
import faygolover.rpmedicine.entity.BodyStubEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Статистика ГМа (ТЗ второго этапа, п. 11.1): запись событий и сводка по игроку. */
public final class StatsService {
    private StatsService() {}

    @Nullable
    static UUID uuid(@Nullable Entity e) {
        if (e instanceof Player p) return p.getUUID();
        if (e instanceof BodyStubEntity stub) return stub.ownerId();
        return null;
    }

    static String name(@Nullable Entity e) {
        if (e instanceof BodyStubEntity stub) return stub.ownerName();
        return e != null ? e.getName().getString() : "";
    }

    private static JsonObject who(String role, @Nullable Entity e, JsonObject o) {
        UUID u = uuid(e);
        if (u != null) o.addProperty(role, u.toString());
        o.addProperty(role + "Name", name(e));
        return o;
    }

    public static void treatment(Entity medic, Entity patient, String item, String action, BodyPart part, String result, boolean error) {
        if (medic.getServer() == null) return;
        JsonObject o = new JsonObject();
        who("medic", medic, o);
        who("patient", patient, o);
        o.addProperty("item", item);
        o.addProperty("action", action);
        o.addProperty("part", part.id);
        o.addProperty("result", result);
        o.addProperty("error", error);
        StatsLog.log(medic.getServer(), "treat", o);
    }

    public static void injury(Entity patient, String profile, @Nullable BodyPart part, @Nullable Entity source, double severity) {
        if (patient.getServer() == null || uuid(patient) == null) return;
        JsonObject o = new JsonObject();
        who("patient", patient, o);
        o.addProperty("profile", profile);
        if (part != null) o.addProperty("part", part.id);
        if (source != null) o.addProperty("source", net.minecraft.world.entity.EntityType.getKey(source.getType()).toString());
        o.addProperty("severity", Math.round(severity * 10) / 10.0);
        StatsLog.log(patient.getServer(), "injury", o);
    }

    /** Клиническая смерть, реанимация (вытащили из клинической смерти), смерть. */
    public static void event(Entity patient, String type, String detail) {
        if (patient.getServer() == null) return;
        JsonObject o = new JsonObject();
        who("patient", patient, o);
        o.addProperty("detail", detail);
        StatsLog.log(patient.getServer(), type, o);
    }

    /** Сводка по игроку за {@code hours} часов. */
    public static List<Component> summary(MinecraftServer server, UUID uuid, String name, double hours) {
        List<JsonObject> events = StatsLog.read(server, hours);
        String id = uuid.toString();
        int given = 0;
        int givenErr = 0;
        int received = 0;
        int receivedErr = 0;
        int clinical = 0;
        int rescued = 0;
        int deaths = 0;
        Map<String, Integer> injuries = new TreeMap<>();
        Map<String, Integer> items = new TreeMap<>();
        for (JsonObject o : events) {
            String type = o.get("type").getAsString();
            boolean asPatient = o.has("patient") && o.get("patient").getAsString().equals(id);
            boolean asMedic = o.has("medic") && o.get("medic").getAsString().equals(id);
            switch (type) {
                case "treat" -> {
                    boolean err = o.has("error") && o.get("error").getAsBoolean();
                    if (asMedic) {
                        given++;
                        if (err) givenErr++;
                        items.merge(o.get("item").getAsString(), 1, Integer::sum);
                    }
                    if (asPatient) {
                        received++;
                        if (err) receivedErr++;
                    }
                }
                case "injury" -> {
                    if (asPatient) injuries.merge(o.get("profile").getAsString(), 1, Integer::sum);
                }
                case "clinical" -> {
                    if (asPatient) clinical++;
                }
                case "rescue" -> {
                    if (asPatient) rescued++;
                }
                case "death" -> {
                    if (asPatient) deaths++;
                }
                default -> { }
            }
        }
        List<Component> out = new java.util.ArrayList<>();
        out.add(Component.translatable("rpmedicine.stats.title", name, String.format(java.util.Locale.ROOT, "%.0f", hours)).withStyle(ChatFormatting.GOLD));
        out.add(Component.translatable("rpmedicine.stats.treat_given", given, givenErr));
        if (!items.isEmpty()) out.add(Component.literal("  " + items).withStyle(ChatFormatting.GRAY));
        out.add(Component.translatable("rpmedicine.stats.treat_received", received, receivedErr));
        int total = injuries.values().stream().mapToInt(Integer::intValue).sum();
        out.add(Component.translatable("rpmedicine.stats.injuries", total));
        if (!injuries.isEmpty()) out.add(Component.literal("  " + injuries).withStyle(ChatFormatting.GRAY));
        out.add(Component.translatable("rpmedicine.stats.outcomes", clinical, rescued, deaths));
        return out;
    }
}
