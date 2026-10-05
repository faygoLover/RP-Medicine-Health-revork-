package faygolover.rpmedicine.medcard;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Медкарта персонажа (ТЗ второго этапа, п. 10): привязана к UUID игрока, лежит файлом на сервере.
 * Рост, вес, группу меняет ГМ; аллергии, хронические состояния и записи может дописать любой.
 */
public final class Medcard {
    /** Запись: автоматическая (ключ перевода и аргументы) или текстом; «предложение» ждёт решения. */
    public static final class Entry {
        public int id;
        public long time;
        /** Ключ перевода {@code rpmedicine.medcard.entry.<key>} для записей мода; пусто — запись текстом. */
        public String key = "";
        public List<String> args = new ArrayList<>();
        /** Текст записи (после правки или запись вручную). */
        public String text = "";
        public String author = "";
        public boolean proposed;
    }

    public UUID uuid;
    public String name = "";
    /** Полное имя персонажа (ролевое), возраст, пол: {@code m}, {@code f} или пусто. Пол задаёт голос стонов. */
    public String fullName = "";
    public int age;
    public String gender = "";
    /** Когда карта заведена (мс); 0 — карта старше этого поля. */
    public long created;
    public double height;
    public double weight;
    /** Группа крови: id ({@code a+}) или пусто. */
    public String bloodType = "";
    public String allergies = "";
    public String chronic = "";
    public int nextId = 1;
    public List<Entry> entries = new ArrayList<>();

    public Medcard() {}

    public Medcard(UUID uuid) {
        this.uuid = uuid;
    }

    public Entry add(String key, List<String> args, String text, String author, boolean proposed) {
        Entry e = new Entry();
        e.id = nextId++;
        e.time = System.currentTimeMillis();
        e.key = key == null ? "" : key;
        e.args = new ArrayList<>(args);
        e.text = text == null ? "" : text;
        e.author = author == null ? "" : author;
        e.proposed = proposed;
        entries.add(e);
        return e;
    }

    public Entry entry(int id) {
        for (Entry e : entries) if (e.id == id) return e;
        return null;
    }
}
