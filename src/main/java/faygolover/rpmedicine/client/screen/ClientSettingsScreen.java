package faygolover.rpmedicine.client.screen;

import faygolover.rpmedicine.config.ClientConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Настройки мода в игре (кнопка «Настройки» в списке модов): эффекты экрана и звука, громкость звуков
 * состояния, сила эффектов, редактор HUD. Изменения сохраняются в клиентский конфиг сразу.
 */
public class ClientSettingsScreen extends Screen {
    private final Screen parent;

    public ClientSettingsScreen(Screen parent) {
        super(Component.translatable("rpmedicine.settings.title"));
        this.parent = parent;
    }

    private record Toggle(String key, ForgeConfigSpec.BooleanValue value) {}

    private record Volume(String key, ForgeConfigSpec.DoubleValue value, double max) {}

    @Override
    protected void init() {
        Toggle[] toggles = {
                new Toggle("hide_vanilla_health", ClientConfig.HIDE_VANILLA_HEALTH),
                new Toggle("pain_vignette", ClientConfig.PAIN_VIGNETTE),
                new Toggle("pain_blur", ClientConfig.PAIN_BLUR),
                new Toggle("low_pressure_darken", ClientConfig.LOW_PRESSURE_DARKEN),
                new Toggle("blood_loss_tunnel", ClientConfig.BLOOD_LOSS_TUNNEL),
                new Toggle("dazed_gray", ClientConfig.DAZED_GRAY),
                new Toggle("muffled_sound", ClientConfig.MUFFLED_SOUND),
                new Toggle("concussion_ringing", ClientConfig.CONCUSSION_RINGING),
                new Toggle("heartbeat", ClientConfig.HEARTBEAT),
                new Toggle("heavy_breathing", ClientConfig.HEAVY_BREATHING),
                new Toggle("aim_sway", ClientConfig.AIM_SWAY),
                new Toggle("look_up_when_downed", ClientConfig.LOOK_UP_WHEN_DOWNED),
                new Toggle("sensation_messages", ClientConfig.SENSATION_MESSAGES),
                new Toggle("leave_body", ClientConfig.LEAVE_BODY),
                new Toggle("no_minigames", ClientConfig.NO_MINIGAMES),
                new Toggle("show_missing_limbs", ClientConfig.SHOW_MISSING_LIMBS),
        };
        Volume[] volumes = {
                new Volume("effect_strength", ClientConfig.EFFECT_STRENGTH, 1.5),
                new Volume("heartbeat_volume", ClientConfig.HEARTBEAT_VOLUME, 1.0),
                new Volume("breathing_volume", ClientConfig.BREATHING_VOLUME, 1.0),
                new Volume("ringing_volume", ClientConfig.RINGING_VOLUME, 1.0),
        };
        int colW = 180;
        int x0 = width / 2 - colW - 5;
        int x1 = width / 2 + 5;
        int y = 34;
        for (int i = 0; i < toggles.length; i++) {
            Toggle tg = toggles[i];
            int x = i % 2 == 0 ? x0 : x1;
            int yy = y + (i / 2) * 22;
            addRenderableWidget(CycleButton.onOffBuilder(tg.value().get())
                    .create(x, yy, colW, 20, Component.translatable("rpmedicine.settings." + tg.key()), (b, on) -> {
                        tg.value().set(on);
                        ClientConfig.SPEC.save();
                        if (tg.value() == ClientConfig.LEAVE_BODY || tg.value() == ClientConfig.NO_MINIGAMES)
                            faygolover.rpmedicine.client.ClientEvents.sendPrefs();
                    }));
        }
        int vy = y + ((toggles.length + 1) / 2) * 22 + 6;
        for (int i = 0; i < volumes.length; i++) {
            Volume vol = volumes[i];
            int x = i % 2 == 0 ? x0 : x1;
            int yy = vy + (i / 2) * 22;
            addRenderableWidget(new AbstractSliderButton(x, yy, colW, 20, Component.empty(), vol.value().get() / vol.max()) {
                {
                    updateMessage();
                }

                @Override
                protected void updateMessage() {
                    setMessage(Component.translatable("rpmedicine.settings." + vol.key(), Math.round(value * vol.max() * 100) + "%"));
                }

                @Override
                protected void applyValue() {
                    vol.value().set(Math.round(value * vol.max() * 100) / 100.0);
                    ClientConfig.SPEC.save();
                }
            });
        }
        int by = vy + ((volumes.length + 1) / 2) * 22 + 10;
        addRenderableWidget(Button.builder(Component.translatable("rpmedicine.settings.hud_editor"), b -> minecraft.setScreen(new HudEditorScreen()))
                .bounds(x0, by, colW, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(x1, by, colW, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        super.render(g, mx, my, pt);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
