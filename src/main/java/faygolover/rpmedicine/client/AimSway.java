package faygolover.rpmedicine.client;

import faygolover.rpmedicine.config.ClientConfig;
import faygolover.rpmedicine.network.SelfView;
import net.minecraft.client.player.LocalPlayer;

/** Раскачка и дрожь прицела от боли, плохой руки, оглушения (п. 2.4, 4.4, 7.5 ТЗ). */
public final class AimSway {
    private AimSway() {}

    private static double t;
    private static float lastDx;
    private static float lastDy;

    public static void tick(LocalPlayer p, SelfView v) {
        if (!ClientConfig.AIM_SWAY.get() || v.isDown() || v.sway <= 0) {
            lastDx = lastDy = 0;
            return;
        }
        float amp = v.sway / 20f * 0.6f;
        t += 0.08;
        // Плавная раскачка + мелкая дрожь; смещение — разность с прошлым тиком, чтобы не уводить прицел.
        float dx = (float) (Math.sin(t * 1.3) * amp + Math.sin(t * 7.1) * amp * 0.15);
        float dy = (float) (Math.cos(t * 0.9) * amp * 0.7 + Math.sin(t * 6.3) * amp * 0.12);
        p.setYRot(p.getYRot() + dx - lastDx);
        p.setXRot(Math.max(-90, Math.min(90, p.getXRot() + dy - lastDy)));
        lastDx = dx;
        lastDy = dy;
    }
}
