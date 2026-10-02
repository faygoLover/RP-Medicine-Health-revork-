package faygolover.rpmedicine.client;

import faygolover.rpmedicine.config.ClientConfig;
import faygolover.rpmedicine.config.ClientConfig.HudElement;
import faygolover.rpmedicine.core.BodyPart;
import faygolover.rpmedicine.core.Examination;
import faygolover.rpmedicine.network.SelfView;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD (п. 7.4 ТЗ): мини-силуэт, значки состояний, прогресс-бар действия, таймер нокдауна, сводка при
 * наведении, ощущения текстом. Положение элементов — клиентский конфиг и редактор {@code /rpmedicine hud}.
 */
public final class MedicalHud {
    private MedicalHud() {}

    /** Базовые размеры элементов (до масштаба). */
    public static int baseW(HudElement e) {
        return switch (e) {
            case SILHOUETTE -> 32;
            case STATUS -> 90;
            case PROGRESS -> 100;
            case KNOCKDOWN_TIMER -> 160;
            case HOVER -> 130;
        };
    }

    public static int baseH(HudElement e) {
        return switch (e) {
            case SILHOUETTE -> 60;
            case STATUS -> 60;
            case PROGRESS -> 16;
            case KNOCKDOWN_TIMER -> 26;
            case HOVER -> 50;
        };
    }

    /** Прямоугольник элемента на экране: {x, y, w, h}. */
    public static int[] layout(HudElement e, int sw, int sh) {
        ClientConfig.ElementConfig c = ClientConfig.hud(e);
        double scale = c.scale.get();
        int w = (int) Math.round(baseW(e) * scale);
        int h = (int) Math.round(baseH(e) * scale);
        ClientConfig.Anchor a = c.anchor.get();
        int x = Math.round(sw * a.fx - w * a.fx) + c.offsetX.get();
        int y = Math.round(sh * a.fy - h * a.fy) + c.offsetY.get();
        return new int[]{x, y, w, h};
    }

    /** Точка входа слоя HUD (сигнатура IGuiOverlay). */
    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int sw, int sh) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        SelfView v = ClientState.self;
        for (HudElement e : HudElement.values()) {
            if (!ClientConfig.hud(e).visible.get()) continue;
            int[] l = layout(e, sw, sh);
            g.pose().pushPose();
            g.pose().translate(l[0], l[1], 0);
            float scale = ClientConfig.hud(e).scale.get().floatValue();
            g.pose().scale(scale, scale, 1);
            drawElement(g, mc.font, e, v, false);
            g.pose().popPose();
        }
        if (ClientConfig.SENSATION_MESSAGES.get() && !v.isDown() && !v.sensations.isEmpty()) {
            int idx = (int) ((System.currentTimeMillis() / 4000) % v.sensations.size());
            Component t = Component.translatable("rpmedicine.exam.complaint_" + v.sensations.get(idx)).withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY);
            g.drawCenteredString(mc.font, t, sw / 2, sh - 62, 0xFFFFFF);
        }
    }

    /** Рисует элемент в локальных координатах (0,0 — левый верхний угол). {@code preview} — для редактора. */
    public static void drawElement(GuiGraphics g, Font font, HudElement e, SelfView v, boolean preview) {
        switch (e) {
            case SILHOUETTE -> {
                if (!preview && !anyColor(v) && !v.isDown()) return;
                g.fill(-2, -2, 34, 62, 0x60000000);
                Silhouette.draw(g, 0, 0, 2, v.partColors, null, null);
            }
            case STATUS -> drawStatus(g, font, v, preview);
            case PROGRESS -> drawProgress(g, font, preview);
            case KNOCKDOWN_TIMER -> drawKnockdown(g, font, v, preview);
            case HOVER -> drawHover(g, font, preview);
        }
    }

    private static boolean anyColor(SelfView v) {
        for (byte b : v.partColors) if (b > 0) return true;
        return false;
    }

    private static void drawStatus(GuiGraphics g, Font font, SelfView v, boolean preview) {
        List<Component> lines = new ArrayList<>();
        if (v.bleed > 0 || preview) lines.add(Component.translatable("rpmedicine.hud.bleed_" + Math.max(1, (int) v.bleed)).withStyle(ChatFormatting.RED));
        if (v.pain > 0 || preview) lines.add(Component.translatable("rpmedicine.hud.pain_" + Math.max(1, (int) v.pain)).withStyle(ChatFormatting.GOLD));
        if (v.fracture || preview) lines.add(Component.translatable("rpmedicine.hud.fracture").withStyle(ChatFormatting.YELLOW));
        for (int[] t : v.tourniquets)
            lines.add(Component.translatable("rpmedicine.hud.tourniquet", Component.translatable(BodyPart.byOrdinal(t[0]).translationKey()), t[1])
                    .withStyle(t[1] >= 15 ? ChatFormatting.RED : ChatFormatting.AQUA));
        if (preview && v.tourniquets.isEmpty()) lines.add(Component.translatable("rpmedicine.hud.tourniquet", "—", 3).withStyle(ChatFormatting.AQUA));
        if (v.analgesia || preview) lines.add(Component.translatable("rpmedicine.hud.analgesia").withStyle(ChatFormatting.GREEN));
        if (v.dyspnea || preview) lines.add(Component.translatable("rpmedicine.hud.dyspnea").withStyle(ChatFormatting.BLUE));
        int y = 0;
        for (Component c : lines) {
            g.drawString(font, c, 0, y, 0xFFFFFF, true);
            y += 10;
        }
    }

    private static void drawProgress(GuiGraphics g, Font font, boolean preview) {
        if (!preview && !ClientState.progressActive()) return;
        float frac;
        String label;
        if (preview) {
            frac = 0.6f;
            label = "rpmedicine.hud.progress_preview";
        } else {
            label = ClientState.progressLabel;
            if (ClientState.progressTotal < 0) {
                frac = (float) ((Math.sin(System.currentTimeMillis() / 300.0) + 1) / 2);
            } else {
                float ticks = ClientState.progressDone + (System.currentTimeMillis() - ClientState.progressStart) / 50f;
                frac = Math.min(1f, ticks / Math.max(1, ClientState.progressTotal));
            }
        }
        g.drawCenteredString(font, Component.translatable(label), 50, 0, 0xFFFFFF);
        g.fill(0, 11, 100, 15, 0xA0000000);
        g.fill(0, 11, (int) (100 * frac), 15, 0xFF58B35A);
    }

    private static void drawKnockdown(GuiGraphics g, Font font, SelfView v, boolean preview) {
        if (!preview && v.down != 1 && v.down != 2) return;
        Component title;
        if (preview || v.down == 2) {
            int sec = preview ? 215 : v.knockdownSeconds;
            title = sec >= 0
                    ? Component.translatable("rpmedicine.hud.knockdown", ExamText.time(sec)).withStyle(ChatFormatting.RED)
                    : Component.translatable("rpmedicine.hud.unconscious").withStyle(ChatFormatting.GRAY);
        } else {
            title = Component.translatable("rpmedicine.hud.unconscious").withStyle(ChatFormatting.GRAY);
        }
        g.drawCenteredString(font, title, 80, 0, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("rpmedicine.hud.downed_hint", ClientSetup.PANEL.getTranslatedKeyMessage())
                .withStyle(ChatFormatting.DARK_GRAY), 80, 12, 0xFFFFFF);
    }

    private static void drawHover(GuiGraphics g, Font font, boolean preview) {
        List<Examination.Line> lines = ClientState.hoverLines;
        if (preview) {
            lines = List.of(new Examination.Line("hover_bleeding"), new Examination.Line("hover_unconscious"));
        } else if (ClientState.hoverTarget < 0 || lines.isEmpty() || ClientState.self.isDown()) {
            return;
        }
        int y = 0;
        for (Examination.Line l : lines) {
            g.drawString(font, ExamText.format(l).withStyle(ExamText.color(l)), 0, y, 0xFFFFFF, true);
            y += 10;
        }
    }
}
