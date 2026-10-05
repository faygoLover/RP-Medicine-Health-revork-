package faygolover.rpmedicine.client.screen;

import faygolover.rpmedicine.client.ClientState;
import faygolover.rpmedicine.core.Nutrition;
import faygolover.rpmedicine.network.SelfView;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Самочувствие от питания: только ощущения, без цифр. Точный состав крови — лабораторным анализом
 * (как в Health & Disease). Иконки — Health & Disease (JEDIGD, MIT).
 */
public class NutritionScreen extends Screen {
    private static final int W = 260;
    private static final int H = 150;

    private record Feel(String icon, String key, ChatFormatting color) {}

    public NutritionScreen() {
        super(Component.translatable("rpmedicine.nutrition.title"));
    }

    private List<Feel> feelings() {
        int f = ClientState.self.nutritionFeel;
        List<Feel> out = new ArrayList<>();
        if ((f & SelfView.FEEL_LOW_PROTEIN) != 0) out.add(new Feel("protein", "feel_low_protein", ChatFormatting.RED));
        if ((f & SelfView.FEEL_LOW_FAT) != 0) out.add(new Feel("fats_and_oil", "feel_low_fat", ChatFormatting.RED));
        if ((f & SelfView.FEEL_LOW_CARBS) != 0) out.add(new Feel("dietary_fiber", "feel_low_carbs", ChatFormatting.RED));
        if ((f & SelfView.FEEL_LOW_VITAMINS) != 0) out.add(new Feel("vitamin", "feel_low_vitamins", ChatFormatting.RED));
        if ((f & SelfView.FEEL_HEAVY) != 0) out.add(new Feel("fats_and_oil", "feel_heavy", ChatFormatting.GOLD));
        if ((f & SelfView.FEEL_BALANCED) != 0) out.add(new Feel("vitamin", "feel_balanced", ChatFormatting.GREEN));
        if (out.isEmpty()) out.add(new Feel(null, "feel_ok", ChatFormatting.GRAY));
        return out;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int l = (width - W) / 2;
        int t = (height - H) / 2;
        g.fill(l, t, l + W, t + H, 0xF0201C18);
        g.renderOutline(l, t, W, H, 0xFF7A6A50);
        g.drawCenteredString(font, title.copy().withStyle(ChatFormatting.GOLD), width / 2, t + 8, 0xFFFFFF);
        int y = t + 26;
        for (Feel f : feelings()) {
            if (f.icon() != null)
                g.blit(new ResourceLocation(faygolover.rpmedicine.RpMedicine.MODID, "textures/gui/nutrition/" + f.icon() + ".png"), l + 10, y - 5, 0, 0, 20, 20, 20, 20);
            for (var line : font.split(Component.translatable("rpmedicine.nutrition." + f.key()).withStyle(f.color()), W - 48)) {
                g.drawString(font, line, l + 36, y, 0xFFFFFF, false);
                y += 10;
            }
            y += 12;
        }
        var v = ClientState.self;
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
        g.drawString(font, Component.translatable("rpmedicine.nutrition.lab_hint").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
                l + 10, t + H - 13, 0xFFFFFF, false);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
