package faygolover.rpmedicine.server;

import com.mojang.brigadier.ParseResults;
import faygolover.rpmedicine.core.MedicalState;
import faygolover.rpmedicine.core.Speech;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.ServerChatEvent;

import java.util.Locale;
import java.util.Set;

/**
 * Чат лежачего (п. 5.3, 8 ТЗ): всё, что он пишет в общий чат или через /tell, заменяется на «...».
 * В клинической смерти чат недоступен. При одышке (второй этап) речь с обрывами.
 */
public final class ChatHandler {
    private ChatHandler() {}

    private static final Set<String> PRIVATE = Set.of("tell", "msg", "w", "teammsg", "tm", "me");
    private static final java.util.SplittableRandom RANDOM = new java.util.SplittableRandom();
    public static final String DOTS = "...";

    public static void onChat(ServerChatEvent event) {
        MedicalState m = Medical.state(event.getPlayer());
        if (m == null) return;
        Speech sp = Speech.of(m);
        if (sp == Speech.SILENCED) event.setCanceled(true);
        else if (!sp.canSpeak()) event.setMessage(Component.literal(DOTS));
        else {
            // Одышка текст чата не искажает (замечание 64) — только голос.
            String text = event.getRawText();
            text = faygolover.rpmedicine.core.Substances.slur(text, m.intoxication, RANDOM.split());
            if (!text.equals(event.getRawText())) event.setMessage(Component.literal(text));
        }
    }

    public static void onCommand(CommandEvent event) {
        ParseResults<CommandSourceStack> parse = event.getParseResults();
        if (!(parse.getContext().getSource().getEntity() instanceof ServerPlayer sp)) return;
        MedicalState m = Medical.state(sp);
        if (m == null) return;
        Speech speech = Speech.of(m);
        if (speech == Speech.NORMAL) return;
        String input = parse.getReader().getString();
        String cmd = input.startsWith("/") ? input.substring(1) : input;
        String[] parts = cmd.split(" ", 3);
        if (parts.length == 0 || !PRIVATE.contains(parts[0].toLowerCase(Locale.ROOT))) return;
        if (speech == Speech.BREATHLESS) return;
        if (Speech.of(m) == Speech.SILENCED) {
            event.setCanceled(true);
            return;
        }
        String root = parts[0].toLowerCase(Locale.ROOT);
        String rewritten;
        if (root.equals("me") || root.equals("teammsg") || root.equals("tm")) rewritten = root + " " + DOTS;
        else if (parts.length >= 2) rewritten = root + " " + parts[1] + " " + DOTS;
        else return;
        var dispatcher = sp.server.getCommands().getDispatcher();
        event.setParseResults(dispatcher.parse(rewritten, parse.getContext().getSource()));
    }
}
