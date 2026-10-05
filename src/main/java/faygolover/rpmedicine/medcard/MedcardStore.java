package faygolover.rpmedicine.medcard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import faygolover.rpmedicine.RpMedicine;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Файлы медкарт: {@code world/rpmedicine/cards/<uuid>.json}. Загрузка — по требованию, запись — в
 * отдельном потоке (п. 14 ТЗ второго этапа), атомарно через временный файл.
 */
public final class MedcardStore {
    private MedcardStore() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Map<UUID, Medcard> CACHE = new HashMap<>();
    private static ExecutorService writer;

    private static Path dir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("rpmedicine").resolve("cards");
    }

    /** Карта персонажа (создаётся пустой, если её ещё нет). */
    public static synchronized Medcard get(MinecraftServer server, UUID uuid) {
        Medcard c = CACHE.get(uuid);
        if (c != null) return c;
        c = load(server, uuid);
        if (c == null) {
            c = new Medcard(uuid);
            c.created = System.currentTimeMillis();
        }
        CACHE.put(uuid, c);
        return c;
    }

    @Nullable
    private static Medcard load(MinecraftServer server, UUID uuid) {
        Path f = dir(server).resolve(uuid + ".json");
        if (!Files.exists(f)) return null;
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            Medcard c = GSON.fromJson(r, Medcard.class);
            if (c != null) c.uuid = uuid;
            return c;
        } catch (Exception e) {
            RpMedicine.LOGGER.error("RP Medicine: не прочитать медкарту {}: {}", f, e.getMessage());
            return null;
        }
    }

    /** Сохранить карту в отдельном потоке. */
    public static synchronized void save(MinecraftServer server, Medcard c) {
        String json = GSON.toJson(c);
        Path d = dir(server);
        if (writer == null) writer = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "RP Medicine medcards");
            t.setDaemon(true);
            return t;
        });
        writer.submit(() -> {
            try {
                Files.createDirectories(d);
                Path tmp = d.resolve(c.uuid + ".json.tmp");
                try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                    w.write(json);
                }
                Files.move(tmp, d.resolve(c.uuid + ".json"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                RpMedicine.LOGGER.error("RP Medicine: не записать медкарту {}: {}", c.uuid, e.getMessage());
            }
        });
    }

    /** Сервер остановлен: дописать очередь и забыть кэш. */
    public static synchronized void shutdown() {
        if (writer != null) {
            writer.shutdown();
            try {
                writer.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            writer = null;
        }
        CACHE.clear();
    }
}
