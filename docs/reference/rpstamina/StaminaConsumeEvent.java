package faygolover.rpstamina.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * Игровая шина Forge. Вызывается перед каждой тратой выносливости (после множителей атрибута и брони).
 * Значение можно изменить или отменить событие. Источники: rpstamina:sprint, jump, sprint_jump, swim,
 * swim_sprint, climb, mine_tick, mine_break, melee_hit, item_use, shield_hold, shield_block.
 */
@Cancelable
public class StaminaConsumeEvent extends PlayerEvent {
    private final ResourceLocation source;
    private float amount;

    public StaminaConsumeEvent(Player player, ResourceLocation source, float amount) {
        super(player);
        this.source = source;
        this.amount = amount;
    }

    public ResourceLocation getSource() {
        return source;
    }

    public float getAmount() {
        return amount;
    }

    public void setAmount(float amount) {
        this.amount = Math.max(0f, amount);
    }
}
