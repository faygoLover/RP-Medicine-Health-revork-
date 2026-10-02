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

    // Ограничения, которые клиент применяет сам (предсказание движения).
    public boolean noSprint;
    public boolean noJump;
    public boolean crawl;
    public boolean armsDisabled;
    public boolean carrying;
    public boolean carried;

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
        buf.writeVarInt(flags);
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
                && heavyBreathing == v.heavyBreathing && sway == v.sway && noSprint == v.noSprint && noJump == v.noJump
                && crawl == v.crawl && armsDisabled == v.armsDisabled && carrying == v.carrying && carried == v.carried
                && sensations.equals(v.sensations);
    }

    @Override
    public int hashCode() {
        int h = Objects.hash(down, knockdownSeconds, bleed, pain, fracture, analgesia, dyspnea, vignette, blur, darken,
                tunnel, gray, ringing, deaf, heartbeat, heavyBreathing, sway, noSprint, noJump, crawl, armsDisabled, carrying,
                carried, sensations);
        h = h * 31 + Arrays.hashCode(partColors);
        for (int[] t : tourniquets) h = h * 31 + Arrays.hashCode(t);
        return h;
    }
}
