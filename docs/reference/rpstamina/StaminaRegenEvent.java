package faygolover.rpstamina.api;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * Игровая шина Forge. Вызывается каждый тик естественного восстановления (не для предметов),
 * когда восстановление идёт. Значение (за этот тик) можно изменить или отменить событие.
 */
@Cancelable
public class StaminaRegenEvent extends PlayerEvent {
    private float amount;

    public StaminaRegenEvent(Player player, float amount) {
        super(player);
        this.amount = amount;
    }

    public float getAmount() {
        return amount;
    }

    public void setAmount(float amount) {
        this.amount = Math.max(0f, amount);
    }
}
