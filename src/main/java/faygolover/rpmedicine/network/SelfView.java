package faygolover.rpmedicine.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Что игрок знает о себе: ощущения, значки HUD, сила экранных эффектов. Без цифр физиологии —
 * всё квантовано, чтобы пакет уходил только при заметном изменении (п. 13 ТЗ).
 */
public final class SelfView {
    /** 0 — на ногах, 1 — обморок, 2 — нокдаун, 3 — клиническая смерть. */
    public byte down;
    /** Секунд до конца нокдауна (−1 — таймера нет). */
    public short knockdownSeconds = -1;
    /** Цвета частей тела 0–4, как видит их сам игрок. */
    public final byte[] partColors = new byte[9];
    /** Сильнейшее кровотечение 0–4. */
    public byte bleed;
    /** Боль 0–3: нет, от 30, от 60, от 85. */
    public byte pain;
    public boolean fracture;
    /** Жгуты: часть и минуты. */
    public final List<int[]> tourniquets = new ArrayList<>();
    public boolean analgesia;
    public boolean dyspnea;
    // Значки HUD: то, что человек чувствует или видит на себе сам.
    public boolean fever;
    public boolean cold;
    public boolean nausea;
    public boolean concussion;
    public boolean stabilized;
    public boolean drip;
    public boolean splint;
    public boolean sedated;

    // Эффекты экрана и звука, 0–20.
    public byte vignette;
    public byte blur;
    public byte darken;
    public byte tunnel;
    public byte gray;
    public byte ringing;
    /** Глухота после взрыва, секунд осталось. */
    public byte deaf;
    /** Стук сердца: 0 — не слышно, 1–3 — учащённый … очень частый. */
    public short heartbeat;
    public boolean heavyBreathing;
    public byte sway;
    /** Опиаты и диссоциация: сила «плывущих» цветов, 0–20. */
    public byte high;

    // Ограничения, которые клиент применяет сам (предсказание движения).
    public boolean noSprint;
    public boolean noJump;
    public boolean crawl;
    public boolean armsDisabled;
    public boolean carrying;
    public boolean carried;
    /** Скорость ломания блоков, % (100 — обычная). */
    public byte breakSpeedPct = 100;
    /** Время использования предметов, % (100 — обычное): еда, питьё, лук — дольше при плохой руке. */
    public short useTimePct = 100;
    /** Свой уровень медицины (подсказки предметов). */
    public byte medLevel;
    /** Питание: запасы 0–120 и пороги, калории за последние часы. */
    public final byte[] nutrients = new byte[4];
    public byte nutrientLow = 20;
    public byte balancedMin = 35;
    public byte balancedMax = 85;
    public short kcalRecent;

    /** Ощущения: ключи {@code rpmedicine.exam.complaint_<k>}. */
    public final List<String> sensations = new ArrayList<>();

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(down);
        buf.writeShort(knockdownSeconds);
        buf.writeBytes(partColors);
        buf.writeByte(bleed);
        buf.writeByte(pain);
        buf.writeVarInt(tourniquets.size());
        for (int[] t : tourniquets) {
            buf.writeByte(t[0]);
            buf.writeVarInt(t[1]);
        }
        buf.writeByte(vignette);
        buf.writeByte(blur);
        buf.writeByte(darken);
        buf.writeByte(tunnel);
        buf.writeByte(gray);
        buf.writeByte(ringing);
        buf.writeByte(deaf);
        buf.writeShort(heartbeat);
        buf.writeByte(sway);
        buf.writeByte(high);
        int flags = 0;
        if (fracture) flags |= 1;
        if (analgesia) flags |= 2;
        if (dyspnea) flags |= 4;
        if (heavyBreathing) flags |= 8;
        if (noSprint) flags |= 16;
        if (noJump) flags |= 32;
        if (crawl) flags |= 64;
        if (armsDisabled) flags |= 128;
        if (carrying) flags |= 256;
        if (carried) flags |= 512;
        if (fever) flags |= 1 << 10;
        if (cold) flags |= 1 << 11;
        if (nausea) flags |= 1 << 12;
        if (concussion) flags |= 1 << 13;
        if (stabilized) flags |= 1 << 14;
        if (drip) flags |= 1 << 15;
        if (splint) flags |= 1 << 16;
        if (sedated) flags |= 1 << 17;
        buf.writeVarInt(flags);
        buf.writeByte(breakSpeedPct);
        buf.writeShort(useTimePct);
        buf.writeByte(medLevel);
        buf.writeBytes(nutrients);
        buf.writeByte(nutrientLow);
        buf.writeByte(balancedMin);
        buf.writeByte(balancedMax);
        buf.writeShort(kcalRecent);
        buf.writeVarInt(sensations.size());
        for (String s : sensations) buf.writeUtf(s, 64);
    }

    public static SelfView decode(FriendlyByteBuf buf) {
        SelfView v = new SelfView();
        v.down = buf.readByte();
        v.knockdownSeconds = buf.readShort();
        buf.readBytes(v.partColors);
        v.bleed = buf.readByte();
        v.pain = buf.readByte();
        int n = Math.min(9, buf.readVarInt());
        for (int i = 0; i < n; i++) v.tourniquets.add(new int[]{buf.readByte(), buf.readVarInt()});
        v.vignette = buf.readByte();
        v.blur = buf.readByte();
        v.darken = buf.readByte();
        v.tunnel = buf.readByte();
        v.gray = buf.readByte();
        v.ringing = buf.readByte();
        v.deaf = buf.readByte();
        v.heartbeat = buf.readShort();
        v.sway = buf.readByte();
        v.high = buf.readByte();
        int flags = buf.readVarInt();
        v.fracture = (flags & 1) != 0;
        v.analgesia = (flags & 2) != 0;
        v.dyspnea = (flags & 4) != 0;
        v.heavyBreathing = (flags & 8) != 0;
        v.noSprint = (flags & 16) != 0;
        v.noJump = (flags & 32) != 0;
        v.crawl = (flags & 64) != 0;
        v.armsDisabled = (flags & 128) != 0;
        v.carrying = (flags & 256) != 0;
        v.carried = (flags & 512) != 0;
        v.fever = (flags & (1 << 10)) != 0;
        v.cold = (flags & (1 << 11)) != 0;
        v.nausea = (flags & (1 << 12)) != 0;
        v.concussion = (flags & (1 << 13)) != 0;
        v.stabilized = (flags & (1 << 14)) != 0;
        v.drip = (flags & (1 << 15)) != 0;
        v.splint = (flags & (1 << 16)) != 0;
        v.sedated = (flags & (1 << 17)) != 0;
        v.breakSpeedPct = buf.readByte();
        v.useTimePct = buf.readShort();
        v.medLevel = buf.readByte();
        buf.readBytes(v.nutrients);
        v.nutrientLow = buf.readByte();
        v.balancedMin = buf.readByte();
        v.balancedMax = buf.readByte();
        v.kcalRecent = buf.readShort();
        int s = Math.min(32, buf.readVarInt());
        for (int i = 0; i < s; i++) v.sensations.add(buf.readUtf(64));
        return v;
    }

    public boolean isDown() {
        return down != 0;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SelfView v)) return false;
        if (tourniquets.size() != v.tourniquets.size()) return false;
        for (int i = 0; i < tourniquets.size(); i++) if (!Arrays.equals(tourniquets.get(i), v.tourniquets.get(i))) return false;
        return down == v.down && knockdownSeconds == v.knockdownSeconds && Arrays.equals(partColors, v.partColors)
                && bleed == v.bleed && pain == v.pain && fracture == v.fracture && analgesia == v.analgesia
                && dyspnea == v.dyspnea && vignette == v.vignette && blur == v.blur && darken == v.darken
                && tunnel == v.tunnel && gray == v.gray && ringing == v.ringing && deaf == v.deaf && heartbeat == v.heartbeat
                && heavyBreathing == v.heavyBreathing && sway == v.sway && high == v.high && noSprint == v.noSprint && noJump == v.noJump
                && crawl == v.crawl && armsDisabled == v.armsDisabled && carrying == v.carrying && carried == v.carried
                && breakSpeedPct == v.breakSpeedPct && useTimePct == v.useTimePct && medLevel == v.medLevel
                && fever == v.fever && cold == v.cold && nausea == v.nausea && concussion == v.concussion
                && stabilized == v.stabilized && drip == v.drip && splint == v.splint && sedated == v.sedated
                && java.util.Arrays.equals(nutrients, v.nutrients) && kcalRecent == v.kcalRecent
                && sensations.equals(v.sensations);
    }

    @Override
    public int hashCode() {
        int h = Objects.hash(down, knockdownSeconds, bleed, pain, fracture, analgesia, dyspnea, vignette, blur, darken,
                tunnel, gray, ringing, deaf, heartbeat, heavyBreathing, sway, noSprint, noJump, crawl, armsDisabled, carrying,
                carried, breakSpeedPct, useTimePct, fever, cold, nausea, concussion, stabilized, drip, splint, sedated, sensations);
        h = h * 31 + Arrays.hashCode(partColors);
        for (int[] t : tourniquets) h = h * 31 + Arrays.hashCode(t);
        return h;
    }
}
