package faygolover.rpmedicine.medcard;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Медкарта персонажа (ТЗ второго этапа, п. 10; форма — «Медицинская карта пациента» из каталога квент автора,
 * решения, п. 1.14): привязана к UUID игрока, лежит файлом на сервере. Рост, вес, группу меняет ГМ;
 * остальное может дописать любой, у кого карта в руках.
 */
public final class Medcard {
    /** Запись анамнеза: автоматическая (ключ перевода и аргументы) или текстом; «предложение» ждёт решения. */
    public static final class Entry {
        public int id;
        public long time;
        /** Ключ перевода {@code rpmedicine.medcard.entry.<key>} для записей мода; пусто — запись текстом. */
        public String key = "";
        public List<String> args = new ArrayList<>();
        /** Характер травмы или диагноз текстом (после правки или запись вручную). */
        public String text = "";
        /** Обстоятельства, механизм травмы; {@code #ключ} — перевод (кто или что ранило). */
        public String circumstances = "";
        /** Отдалённые последствия, остаточные явления. */
        public String consequences = "";
        public String author = "";
        public boolean proposed;
    }

    /** Особые отметки (битами). */
    public static final int MARK_PSYCH = 1;
    public static final int MARK_INCAPACITY = 2;
    public static final int MARK_HIGH_RISK = 4;

    /** Отделы Департамента (ключи перевода {@code rpmedicine.medcard.dept.<id>}). */
    public static final String[] DEPARTMENTS = {"", "health", "security", "research", "engineering", "admin"};

    public UUID uuid;
    /** Ник игрока. */
    public String name = "";
    // Регистрационные данные.
    public String cardNumber = "";
    public long created;
    public boolean closed;
    // Сведения о пациенте.
    public String fullName = "";
    public String callsign = "";
    /** Старое поле «вступил на службу» (убрано с формы — замечание 53), хранится для старых карт. */
    public String serviceDate = "";
    public String birthDate = "";
    /** Пол: {@code m}, {@code f} или пусто. Задаёт голос стонов. */
    public String gender = "";
    /** Группа крови: id ({@code a+}) или пусто. */
    public String bloodType = "";
    public String department = "";
    public double height;
    public double weight;
    /** Возраст (старое поле, до даты рождения). */
    public int age;
    // Медицинские сведения.
    public String allergies = "";
    public String chronic = "";
    public String medications = "";
    public String implants = "";
    public String disability = "";
    public int marks;
    public String comment = "";
    /**
     * Фото: свойство {@code textures} профиля (base64) на момент заведения карты — снимок анфас по скину.
     * Пусто — скин по умолчанию для uuid (офлайн-сервер).
     */
    public String photo = "";
    public boolean photoTaken;
    /** Снимок скина с клиента (PNG в base64, замечание 09.10, М2): делается один раз; есть — важнее {@link #photo}. */
    public String photoPng = "";

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

    /** Номер карты: четыре цифры, постоянные для персонажа. */
    public void ensureNumber() {
        if (cardNumber == null || cardNumber.isEmpty())
            cardNumber = String.format(java.util.Locale.ROOT, "%04d", Math.floorMod(uuid.hashCode(), 10000));
    }

    /** Строки карты не бывают null (старые файлы без новых полей). */
    public void fixNulls() {
        if (cardNumber == null) cardNumber = "";
        if (fullName == null) fullName = "";
        if (callsign == null) callsign = "";
        if (serviceDate == null) serviceDate = "";
        if (birthDate == null) birthDate = "";
        if (gender == null) gender = "";
        if (bloodType == null) bloodType = "";
        if (department == null) department = "";
        if (allergies == null) allergies = "";
        if (chronic == null) chronic = "";
        if (medications == null) medications = "";
        if (implants == null) implants = "";
        if (disability == null) disability = "";
        if (comment == null) comment = "";
        if (photo == null) photo = "";
        if (photoPng == null) photoPng = "";
        if (entries == null) entries = new ArrayList<>();
        for (Entry e : entries) {
            if (e.circumstances == null) e.circumstances = "";
            if (e.consequences == null) e.consequences = "";
            if (e.text == null) e.text = "";
            if (e.key == null) e.key = "";
            if (e.author == null) e.author = "";
            if (e.args == null) e.args = new ArrayList<>();
        }
    }
}
