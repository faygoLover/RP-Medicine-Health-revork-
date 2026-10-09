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
        c.fixNulls();
        c.ensureNumber();
        takePhoto(server, c);
        CACHE.put(uuid, c);
        return c;
    }

    /**
     * Снимок для карты — один раз, пока персонаж в сети (скин на момент заведения карты). Профиль сервера — запасной
     * вариант; главный — картинка скина с клиента: сервер просит её, пока её нет (за запуск — один раз на игрока).
     */
    public static void takePhoto(MinecraftServer server, Medcard c) {
        var sp = server.getPlayerList().getPlayer(c.uuid);
        if (sp == null) return;
        if (!c.photoTaken) {
            var tex = sp.getGameProfile().getProperties().get("textures");
            c.photo = tex.isEmpty() ? "" : tex.iterator().next().getValue();
            c.photoTaken = true;
            save(server, c);
        }
        if (c.photoPng.isEmpty() && PHOTO_ASKED.add(c.uuid))
            faygolover.rpmedicine.network.Network.send(sp, new faygolover.rpmedicine.network.MedcardPhotoPacket.Request());
    }

    /** Кого уже просили о снимке за этот запуск сервера. */
    private static final java.util.Set<UUID> PHOTO_ASKED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Снимок скина пришёл: только если его ещё нет (карта не переснимается) и это настоящая картинка скина. */
    public static synchronized void onPhoto(net.minecraft.server.level.ServerPlayer sp, byte[] png) {
        Medcard c = get(sp.server, sp.getUUID());
        if (!c.photoPng.isEmpty() || png.length == 0 || png.length > faygolover.rpmedicine.network.MedcardPhotoPacket.MAX_BYTES) return;
        try {
            var img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
            if (img == null || img.getWidth() < 64 || img.getWidth() > 512 || img.getHeight() < 32 || img.getHeight() > 512) return;
        } catch (Exception e) {
            return;
        }
        c.photoPng = java.util.Base64.getEncoder().encodeToString(png);
        save(sp.server, c);
        RpMedicine.LOGGER.info("RP Medicine: фото для медкарты {} снято ({} байт)", sp.getGameProfile().getName(), png.length);
    }

    /** Выход игрока: в следующий вход снова можно попросить снимок, если его так и нет. */
    public static void forgetPhotoRequest(UUID uuid) {
        PHOTO_ASKED.remove(uuid);
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
