package faygolover.rpmedicine.client.screen;

import faygolover.rpmedicine.client.ClientState;
import faygolover.rpmedicine.core.Nutrition;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Питание: запасы белков, жиров, углеводов и витаминов с полосой нормы и калории за последние часы.
 * Иконки — Health & Disease (JEDIGD, MIT).
 */
public class NutritionScreen extends Screen {
    private static final int W = 240;
    private static final int H = 168;
    private static final String[] ICONS = {"protein", "fats_and_oil", "dietary_fiber", "vitamin"};

    public NutritionScreen() {
        super(Component.translatable("rpmedicine.nutrition.title"));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int l = (width - W) / 2;
        int t = (height - H) / 2;
        g.fill(l, t, l + W, t + H, 0xF0201C18);
        g.renderOutline(l, t, W, H, 0xFF7A6A50);
        g.drawCenteredString(font, title.copy().withStyle(ChatFormatting.GOLD), width / 2, t + 8, 0xFFFFFF);
        var v = ClientState.self;
        int low = v.nutrientLow;
        int bmin = v.balancedMin;
        int bmax = v.balancedMax;
        for (int i = 0; i < Nutrition.COUNT; i++) {
            int y = t + 26 + i * 30;
            ResourceLocation icon = new ResourceLocation(faygolover.rpmedicine.RpMedicine.MODID, "textures/gui/nutrition/" + ICONS[i] + ".png");
            g.blit(icon, l + 10, y, 0, 0, 20, 20, 20, 20);
            int value = v.nutrients[i] & 0xFF;
            g.drawString(font, Component.translatable("rpmedicine.nutrition." + Nutrition.IDS[i]), l + 36, y, 0xFFFFFF, false);
            int bx = l + 36;
            int bw = W - 50;
            int by = y + 11;
            g.fill(bx, by, bx + bw, by + 6, 0xFF3A3530);
            // Полоса нормы.
            g.fill(bx + bw * bmin / 120, by, bx + bw * Math.min(120, bmax) / 120, by + 6, 0x502E8B57);
            int col = value < low ? 0xFFE04040 : value > 100 ? 0xFFE0A040 : value >= bmin && value <= bmax ? 0xFF58B35A : 0xFFE0D060;
            g.fill(bx, by, bx + bw * Math.min(120, value) / 120, by + 6, col);
            String state = value < low ? "low" : value > 100 ? "high" : value >= bmin && value <= bmax ? "ok" : "mid";
            Component st = Component.translatable("rpmedicine.nutrition.state_" + state);
            g.drawString(font, st, l + W - 10 - font.width(st), y, col & 0xFFFFFF, false);
        }
        g.drawString(font, Component.translatable("rpmedicine.nutrition.kcal_recent", v.kcalRecent).withStyle(ChatFormatting.GRAY),
                l + 10, t + H - 14, 0xFFFFFF, false);
        if (v.fedUpMask != 0) {
            net.minecraft.network.chat.MutableComponent list = Component.empty();
            boolean first = true;
            for (int i = 0; i < Nutrition.CATEGORIES.length; i++) {
                if ((v.fedUpMask & (1 << i)) == 0) continue;
                if (!first) list.append(", ");
                list.append(Component.translatable("rpmedicine.food_category." + Nutrition.CATEGORIES[i]));
                first = false;
            }
            g.drawString(font, Component.translatable("rpmedicine.nutrition.fed_up", list).withStyle(ChatFormatting.GOLD), l + 10, t + H - 26, 0xFFFFFF, false);
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
