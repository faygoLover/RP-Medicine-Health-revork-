package faygolover.rpmedicine.client;

import faygolover.rpmedicine.config.ClientConfig;
import faygolover.rpmedicine.config.ClientConfig.HudElement;
import faygolover.rpmedicine.core.BodyPart;
import faygolover.rpmedicine.core.Examination;
import faygolover.rpmedicine.network.MonitorPacket;
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
            case STATUS -> 100;
            case PROGRESS -> 100;
            case KNOCKDOWN_TIMER -> 160;
            case HOVER -> 130;
            case MONITOR -> 110;
        };
    }

    public static int baseH(HudElement e) {
        return switch (e) {
            case SILHOUETTE -> 60;
            case STATUS -> 40;
            case PROGRESS -> 24;
            case KNOCKDOWN_TIMER -> 26;
            case HOVER -> 50;
            case MONITOR -> 62;
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
        if (mc.player == null || mc.options.hideGui || mc.player.isDeadOrDying()) return;
        SelfView v = ClientState.self;
        for (HudElement e : HudElement.values()) {
            if (!ClientConfig.hud(e).visible.get()) continue;
            int[] l = layout(e, sw, sh);
            g.pose().pushPose();
            // Выше чата по глубине: иначе фон сообщений чата закрывает элементы мода.
            g.pose().translate(l[0], l[1], 300);
            float scale = ClientConfig.hud(e).scale.get().floatValue();
            g.pose().scale(scale, scale, 1);
            drawElement(g, mc.font, e, v, false);
            g.pose().popPose();
        }
        if (ClientConfig.SENSATION_MESSAGES.get() && !v.isDown() && !v.sensations.isEmpty()) {
            int idx = (int) ((System.currentTimeMillis() / 4000) % v.sensations.size());
            Component t = Component.translatable("rpmedicine.exam.complaint_" + v.sensations.get(idx)).withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY);
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.drawCenteredString(mc.font, t, sw / 2, sh - 62, 0xFFFFFF);
            g.pose().popPose();
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
            case MONITOR -> drawMonitor(g, font, preview);
        }
    }

    private static boolean anyColor(SelfView v) {
        for (byte b : v.partColors) if (b > 0) return true;
        return false;
    }

    /** Значки состояний 18×18 (текстуры textures/gui/status) — в ряд по {@link #ICONS_PER_ROW}. */
    public static final int ICONS_PER_ROW = 5;

    private static net.minecraft.resources.ResourceLocation icon(String name) {
        return new net.minecraft.resources.ResourceLocation(faygolover.rpmedicine.RpMedicine.MODID, "textures/gui/status/" + name + ".png");
    }

    /** Значок и подпись поверх (минуты жгута и т.п.). */
    private record Icon(String name, String label, int labelColor, boolean blink) {}

    private static List<Icon> statusIcons(SelfView v, boolean preview) {
        List<Icon> out = new ArrayList<>();
        if (v.bleed > 0 || preview) out.add(new Icon("bleed_" + Math.max(1, Math.min(4, (int) v.bleed)), "", 0, v.bleed >= 4));
        if (v.pain > 0 || preview) out.add(new Icon("pain", String.valueOf(Math.max(1, (int) v.pain)), v.pain >= 3 ? 0xFF5555 : 0xFFAA00, v.pain >= 3));
        if (v.fracture || preview) out.add(new Icon("fracture", "", 0, false));
        if (v.splint) out.add(new Icon("splint", "", 0, false));
        for (int[] t : v.tourniquets) out.add(new Icon("tourniquet", t[1] + "'", t[1] >= 15 ? 0xFF5555 : 0xFFFFFF, t[1] >= 15));
        if (preview && v.tourniquets.isEmpty()) out.add(new Icon("tourniquet", "3'", 0xFFFFFF, false));
        if (v.analgesia || preview) out.add(new Icon("analgesia", "", 0, false));
        if (v.dyspnea || preview) out.add(new Icon("dyspnea", "", 0, false));
        if (v.concussion) out.add(new Icon("concussion", "", 0, false));
        if (v.nausea) out.add(new Icon("nausea", "", 0, false));
        if (v.fever) out.add(new Icon("fever", "", 0, false));
        if (v.cold) out.add(new Icon("cold", "", 0, false));
        if (v.drip) out.add(new Icon("drip", "", 0, false));
        if (v.stabilized) out.add(new Icon("stabilized", "", 0, false));
        if (v.sedated) out.add(new Icon("sedated", "", 0, false));
        return out;
    }

    private static void drawStatus(GuiGraphics g, Font font, SelfView v, boolean preview) {
        List<Icon> icons = statusIcons(v, preview);
        boolean blinkOff = (System.currentTimeMillis() / 400) % 2 == 0;
        for (int i = 0; i < icons.size(); i++) {
            Icon ic = icons.get(i);
            int x = (i % ICONS_PER_ROW) * 20;
            int y = (i / ICONS_PER_ROW) * 20;
            g.fill(x - 1, y - 1, x + 19, y + 19, 0x50000000);
            if (ic.blink() && blinkOff) com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 0.6f, 0.6f, 1f);
            g.blit(icon(ic.name()), x, y, 0, 0, 18, 18, 18, 18);
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            if (!ic.label().isEmpty()) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 10);
                g.drawString(font, ic.label(), x + 19 - font.width(ic.label()), y + 11, ic.labelColor(), true);
                g.pose().popPose();
            }
        }
    }

    private static void drawProgress(GuiGraphics g, Font font, boolean preview) {
        boolean own = ClientState.progressActive();
        boolean incoming = !own && ClientState.incomingActive();
        if (!preview && !own && !incoming) return;
        float frac;
        Component label;
        Component sub;
        net.minecraft.world.item.ItemStack icon;
        if (preview) {
            frac = 0.6f;
            label = Component.translatable("rpmedicine.hud.progress_preview");
            sub = Component.translatable("rpmedicine.progress.self");
            icon = new net.minecraft.world.item.ItemStack(faygolover.rpmedicine.registry.ModItems.BANDAGE.get());
        } else if (own) {
            label = Component.translatable(ClientState.progressLabel);
            sub = ClientState.progressSubtitle;
            icon = ClientState.progressIcon;
            if (ClientState.progressTotal < 0) {
                frac = (float) ((Math.sin(System.currentTimeMillis() / 300.0) + 1) / 2);
            } else {
                float ticks = ClientState.progressDone + (System.currentTimeMillis() - ClientState.progressStart) / 50f;
                frac = Math.min(1f, ticks / Math.max(1, ClientState.progressTotal));
            }
        } else {
            label = Component.translatable(ClientState.incomingLabel);
            sub = ClientState.incomingSubtitle;
            icon = ClientState.incomingIcon;
            float ticks = (System.currentTimeMillis() - ClientState.incomingStart) / 50f;
            frac = Math.min(1f, ticks / Math.max(1, ClientState.incomingTotal));
        }
        // Иконка предмета, название, на кого; полоса снизу.
        int textX = icon.isEmpty() ? 0 : 18;
        g.fill(-3, -3, 103, 24, 0x90000000);
        if (!icon.isEmpty()) g.renderItem(icon, -1, -1);
        g.drawString(font, label, textX, 0, incoming ? 0x9FE0FF : 0xFFFFFF, true);
        if (sub != null && !sub.getString().isEmpty())
            g.drawString(font, sub.copy().withStyle(ChatFormatting.GRAY), textX, 9, 0xAAAAAA, false);
        g.fill(0, 19, 100, 22, 0xA0000000);
        g.fill(0, 19, (int) (100 * frac), 22, incoming ? 0xFF5AA0D0 : 0xFF58B35A);
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

    /** Цифры монитора, на который смотрит игрок (второй этап, п. 2.3). */
    private static void drawMonitor(GuiGraphics g, Font font, boolean preview) {
        MonitorPacket p = preview ? new MonitorPacket(net.minecraft.core.BlockPos.ZERO, (byte) 0, (short) 88, (short) 118, (short) 77,
                (short) 97, (short) 16, (byte) 0) : ClientState.monitor;
        if (p == null || (!preview && !ClientState.monitorActive())) return;
        g.fill(-3, -3, 108, p.state() == 0 ? 60 : 22, 0xB0000000);
        g.drawString(font, Component.translatable("rpmedicine.monitor.title").withStyle(ChatFormatting.DARK_GREEN), 0, 0, 0xFFFFFF, false);
        if (p.state() != 0) {
            String key = p.state() == 1 ? "rpmedicine.monitor.empty" : "rpmedicine.monitor.locked";
            g.drawString(font, Component.translatable(key).withStyle(ChatFormatting.GRAY), 0, 10, 0xFFFFFF, false);
            return;
        }
        boolean alarm = p.rhythm() != 0;
        g.drawString(font, Component.translatable("rpmedicine.monitor.hr", dash(p.hr(), alarm)), 0, 10, alarm ? 0xFF5555 : 0x55FF55, false);
        g.drawString(font, Component.translatable("rpmedicine.monitor.bp", dash(p.sys(), false), dash(p.dia(), false)), 0, 20, 0xFF7777, false);
        boolean low = p.spo2() >= 0 && p.spo2() < 90;
        g.drawString(font, Component.translatable("rpmedicine.monitor.spo2", p.spo2() < 0 ? "--" : String.valueOf(p.spo2())), 0, 30,
                p.spo2() < 0 || low ? 0xFF5555 : 0x55FFFF, false);
        g.drawString(font, Component.translatable("rpmedicine.monitor.rr", dash(p.rr(), false)), 0, 40, 0xFFFF55, false);
        g.drawString(font, Component.translatable("rpmedicine.monitor.rhythm_" + p.rhythm()), 0, 50, alarm ? 0xFF5555 : 0xAAAAAA, false);
    }

    private static String dash(int v, boolean force) {
        return force || v <= 0 ? "--" : String.valueOf(v);
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
