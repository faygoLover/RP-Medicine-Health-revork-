package faygolover.rpmedicine.stats;

import faygolover.rpmedicine.RpMedicine;
import faygolover.rpmedicine.core.MedicalSettings;
import faygolover.rpmedicine.core.MedicalState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * История состояния пациента за последние 30 минут (ТЗ второго этапа, п. 11.1): снимок раз в 10 секунд —
 * кровь, давление, пульс, SpO2, сознание, мозг, температура, лежачий. Файлы {@code world/rpmedicine/history/<uuid>.bin}.
 */
public final class History {
    private History() {}

    /** 30 минут по 10 секунд. */
    public static final int SIZE = 180;
    public static final int PERIOD_TICKS = 200;
    private static final int FIELDS = 8;
    private static final Map<UUID, ArrayDeque<float[]>> BUFFERS = new HashMap<>();

    private static Path file(MinecraftServer server, UUID uuid) {
        return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("rpmedicine").resolve("history").resolve(uuid + ".bin");
    }

    public static synchronized void record(UUID uuid, MedicalState m) {
        MedicalSettings s = MedicalSettings.get();
        ArrayDeque<float[]> q = BUFFERS.computeIfAbsent(uuid, k -> new ArrayDeque<>());
        q.addLast(new float[]{(float) (m.bloodFraction(s) * 100), (float) m.pressure, (float) m.heartRate, (float) m.spo2,
                (float) m.consciousness, (float) m.brain, (float) m.bodyTemp, m.down.ordinal()});
        while (q.size() > SIZE) q.removeFirst();
    }

    public static synchronized List<float[]> get(UUID uuid) {
        ArrayDeque<float[]> q = BUFFERS.get(uuid);
        return q == null ? List.of() : new ArrayList<>(q);
    }

    public static synchronized void load(MinecraftServer server, UUID uuid) {
        Path f = file(server, uuid);
        if (BUFFERS.containsKey(uuid) || !Files.exists(f)) return;
        try (DataInputStream in = new DataInputStream(Files.newInputStream(f))) {
            int n = Math.min(SIZE, in.readInt());
            ArrayDeque<float[]> q = new ArrayDeque<>();
            for (int i = 0; i < n; i++) {
                float[] row = new float[FIELDS];
                for (int j = 0; j < FIELDS; j++) row[j] = in.readFloat();
                q.addLast(row);
            }
            BUFFERS.put(uuid, q);
        } catch (IOException e) {
            RpMedicine.LOGGER.error("RP Medicine: не прочитать историю {}: {}", f, e.getMessage());
        }
    }

    public static synchronized void save(MinecraftServer server, UUID uuid) {
        ArrayDeque<float[]> q = BUFFERS.get(uuid);
        if (q == null) return;
        Path f = file(server, uuid);
        try {
            Files.createDirectories(f.getParent());
            try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(f))) {
                out.writeInt(q.size());
                for (float[] row : q) for (float v : row) out.writeFloat(v);
            }
        } catch (IOException e) {
            RpMedicine.LOGGER.error("RP Medicine: не записать историю {}: {}", f, e.getMessage());
        }
    }

    public static synchronized void saveAll(MinecraftServer server) {
        for (UUID u : BUFFERS.keySet()) save(server, u);
        BUFFERS.clear();
    }

    private static final char[] BARS = "▁▂▃▄▅▆▇█".toCharArray();

    /** График текстом: по строке на показатель, столбец — минута. */
    public static List<Component> graph(UUID uuid, String name) {
        List<float[]> rows = get(uuid);
        List<Component> out = new ArrayList<>();
        out.add(Component.translatable("rpmedicine.stats.history_title", name, rows.size() * 10 / 60).withStyle(ChatFormatting.GOLD));
        if (rows.isEmpty()) {
            out.add(Component.translatable("rpmedicine.stats.no_history").withStyle(ChatFormatting.GRAY));
            return out;
        }
        String[] keys = {"blood", "bp", "hr", "spo2", "consciousness", "brain", "temp"};
        float[][] ranges = {{0, 100}, {0, 160}, {0, 180}, {50, 100}, {0, 100}, {0, 100}, {34, 41}};
        for (int f = 0; f < keys.length; f++) {
            StringBuilder sb = new StringBuilder();
            float min = Float.MAX_VALUE;
            float max = -Float.MAX_VALUE;
            for (int c = 0; c < rows.size(); c += 6) {
                float sum = 0;
                int n = 0;
                for (int k = c; k < Math.min(rows.size(), c + 6); k++) {
                    sum += rows.get(k)[f];
                    n++;
                }
                float v = sum / n;
                min = Math.min(min, v);
                max = Math.max(max, v);
                float x = (v - ranges[f][0]) / (ranges[f][1] - ranges[f][0]);
                sb.append(BARS[Math.max(0, Math.min(BARS.length - 1, Math.round(x * (BARS.length - 1))))]);
            }
            String last = String.format(java.util.Locale.ROOT, "%.0f", rows.get(rows.size() - 1)[f]);
            if (f == 6) last = String.format(java.util.Locale.ROOT, "%.1f", rows.get(rows.size() - 1)[f]);
            out.add(Component.translatable("rpmedicine.stats.metric_" + keys[f]).withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(" " + sb + " ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(String.format(java.util.Locale.ROOT, "%.0f–%.0f, сейчас %s", min, max, last)).withStyle(ChatFormatting.GRAY)));
        }
        return out;
    }
}
