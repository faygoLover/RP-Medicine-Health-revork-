package faygolover.rpmedicine.client;

import faygolover.rpmedicine.core.Examination;
import faygolover.rpmedicine.core.WoundType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Перевод строк осмотра в текст. Сервер присылает ключи и малые целые — без цифр физиологии. */
public final class ExamText {
    private ExamText() {}

    public static MutableComponent format(Examination.Line l) {
        String k = "rpmedicine.exam." + l.key();
        int[] a = l.args();
        return switch (l.key()) {
            case "wound_type" -> Component.translatable(k, wound(a, 0));
            case "wound_typed_severity" -> Component.translatable(k, wound(a, 0),
                    Component.translatable("rpmedicine.exam.severity_" + arg(a, 1)));
            case "consciousness", "skin", "breathing_rate" -> Component.translatable(k + "_" + arg(a, 0));
            case "pulse" -> Component.translatable(k + "_" + arg(a, 0) + (arg(a, 1) == 1 ? "_weak" : ""));
            case "hover_knockdown" -> Component.translatable(k, time(arg(a, 0)));
            case "bleeding_kind" -> Component.translatable(k, Component.translatable("rpmedicine.exam.bleed_strength_" + arg(a, 0)),
                    Component.translatable("rpmedicine.exam.bleed_kind_" + arg(a, 1)));
            case "dressing_seeping" -> Component.translatable(k, Component.translatable("rpmedicine.exam.bleed_strength_" + arg(a, 0)));
            case "dressing_bandage_q", "dressing_pressure_q", "dressing_hemostatic_q" -> Component.translatable(k,
                    Component.translatable("rpmedicine.exam.dressing_quality_" + arg(a, 0)));
            default -> {
                Object[] args = new Object[a.length];
                for (int i = 0; i < a.length; i++) args[i] = a[i];
                yield Component.translatable(k, args);
            }
        };
    }

    /** Цвет строки по смыслу: кровь и угроза — красным. */
    public static ChatFormatting color(Examination.Line l) {
        String k = l.key();
        if (k.startsWith("bleeding_class_4") || k.equals("bleeding_heavy") || k.equals("dying") || k.equals("pulse_none")
                || k.equals("breathing_none") || k.startsWith("hover_bleeding_heavy") || k.equals("hover_no_signs")) return ChatFormatting.RED;
        if (k.startsWith("bleeding") || k.startsWith("fracture") || k.equals("broken") || k.startsWith("suspect") || k.startsWith("hover_bleeding"))
            return ChatFormatting.GOLD;
        if (k.startsWith("dressing") || k.equals("splint") || k.equals("occlusive") || k.equals("tourniquet") || k.equals("esmarch"))
            return ChatFormatting.AQUA;
        if (k.startsWith("complaint")) return ChatFormatting.GRAY;
        return ChatFormatting.WHITE;
    }

    private static int arg(int[] a, int i) {
        return i < a.length ? a[i] : 0;
    }

    private static Component wound(int[] a, int i) {
        return Component.translatable(WoundType.byOrdinal(arg(a, i)).translationKey());
    }

    public static String time(int seconds) {
        int s = Math.max(0, seconds);
        return String.format("%d:%02d", s / 60, s % 60);
    }
}
