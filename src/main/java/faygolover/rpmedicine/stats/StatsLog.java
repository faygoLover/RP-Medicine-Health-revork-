package faygolover.rpmedicine.stats;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import faygolover.rpmedicine.RpMedicine;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Журнал ГМа (ТЗ второго этапа, п. 11.1): каждое применение медпредмета, травмы, клинические смерти,
 * реанимации, смерти. Строки JSON в {@code world/rpmedicine/stats/<дата>.jsonl}, запись в отдельном потоке.
 */
public final class StatsLog {
    private StatsLog() {}

    private static final Gson GSON = new Gson();
    private static ExecutorService writer;

    static Path dir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("rpmedicine").resolve("stats");
    }

    /** Записать событие: {@code type} — treat, injury, clinical, rescue, death. */
    public static synchronized void log(MinecraftServer server, String type, JsonObject data) {
        data.addProperty("type", type);
        data.addProperty("t", System.currentTimeMillis());
        String line = GSON.toJson(data);
        Path file = dir(server).resolve(LocalDate.now() + ".jsonl");
        if (writer == null) writer = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "RP Medicine stats");
            t.setDaemon(true);
            return t;
        });
        writer.submit(() -> {
            try {
                Files.createDirectories(file.getParent());
                try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                    w.write(line);
                    w.write('\n');
                }
            } catch (IOException e) {
                RpMedicine.LOGGER.error("RP Medicine: не записать статистику: {}", e.getMessage());
            }
        });
    }

    /** События за последние {@code hours} часов (читает файлы нужных дат). */
    public static List<JsonObject> read(MinecraftServer server, double hours) {
        flush();
        List<JsonObject> out = new ArrayList<>();
        long since = System.currentTimeMillis() - (long) (hours * 3600_000);
        int days = (int) Math.ceil(hours / 24.0) + 1;
        for (int i = days; i >= 0; i--) {
            Path f = dir(server).resolve(LocalDate.now().minusDays(i) + ".jsonl");
            if (!Files.exists(f)) continue;
            try {
                for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                    if (line.isBlank()) continue;
                    try {
                        JsonObject o = GSON.fromJson(line, JsonObject.class);
                        if (o.has("t") && o.get("t").getAsLong() >= since) out.add(o);
                    } catch (Exception ignored) {
                        // повреждённая строка — пропускаем
                    }
                }
            } catch (IOException e) {
                RpMedicine.LOGGER.error("RP Medicine: не прочитать статистику {}: {}", f, e.getMessage());
            }
        }
        return out;
    }

    /** Дождаться записи очереди (перед чтением и при остановке). */
    public static synchronized void flush() {
        if (writer == null) return;
        ExecutorService w = writer;
        writer = null;
        w.shutdown();
        try {
            w.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
