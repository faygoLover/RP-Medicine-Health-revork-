package faygolover.rpmedicine.server;

import faygolover.rpmedicine.RpMedicine;
import faygolover.rpmedicine.config.ServerConfig;
import faygolover.rpmedicine.core.MedicalState;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

/**
 * Добивание лежачего (п. 5.2 ТЗ): удержание клавиши с оружием в руке. Защиты от своих нет.
 * В режиме «без смерти» добитый попадает в клиническую смерть; клиническую смерть добить нельзя.
 */
public final class FinishService {
    private FinishService() {}

    /** Дополнительные предметы, которыми можно добить (оружие TaCZ и других модов). */
    public static final TagKey<Item> FINISHING_WEAPONS = TagKey.create(Registries.ITEM, new ResourceLocation(RpMedicine.MODID, "finishing_weapons"));

    public static boolean isWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(FINISHING_WEAPONS)) return true;
        Item it = stack.getItem();
        if (it instanceof SwordItem || it instanceof AxeItem || it instanceof TridentItem || it instanceof ProjectileWeaponItem) return true;
        return stack.getAttributeModifiers(EquipmentSlot.MAINHAND).containsKey(Attributes.ATTACK_DAMAGE);
    }

    public static void start(ServerPlayer sp, int targetId) {
        Entity e = sp.level().getEntity(targetId);
        if (!(e instanceof LivingEntity target) || target == sp) return;
        MedicalState m = Medical.state(target);
        if (m == null || !m.isDown()) return;
        if (m.down == MedicalState.Down.CLINICAL) {
            sp.displayClientMessage(Component.translatable("rpmedicine.refuse.clinical_no_finish"), true);
            return;
        }
        if (Medical.isDown(sp) || sp.distanceTo(target) > ServerConfig.INTERACT_DISTANCE.get() + 0.5) return;
        if (!isWeapon(sp.getMainHandItem())) {
            sp.displayClientMessage(Component.translatable("rpmedicine.refuse.no_weapon"), true);
            return;
        }
        ActionManager.TimedAction cur = ActionManager.current(sp);
        if (cur instanceof FinishAction fa && fa.target == target) return;
        ActionManager.start(new FinishAction(sp, target, (int) (ServerConfig.FINISH_SECONDS.get() * 20)));
    }

    public static void stop(ServerPlayer sp) {
        if (ActionManager.current(sp) instanceof FinishAction) ActionManager.cancel(sp, null);
    }

    static final class FinishAction extends ActionManager.TimedAction {
        final LivingEntity target;
        private final ItemStack weapon;

        FinishAction(ServerPlayer actor, LivingEntity target, int ticks) {
            super(actor, ticks);
            this.target = target;
            this.weapon = actor.getMainHandItem().copy();
        }

        @Override
        public String label() {
            return "rpmedicine.action.finish";
        }

        @Override
        public String checkContinue() {
            MedicalState m = Medical.state(target);
            if (target.isRemoved() || m == null || !m.isDown() || m.down == MedicalState.Down.CLINICAL) return "rpmedicine.action.target_lost";
            if (actor.distanceTo(target) > ServerConfig.INTERACT_DISTANCE.get() + 1.0) return "rpmedicine.action.target_lost";
            if (!ItemStack.isSameItem(actor.getMainHandItem(), weapon)) return "rpmedicine.action.item_changed";
            if (Medical.isDown(actor)) return "rpmedicine.action.interrupted";
            return null;
        }

        @Override
        public void complete() {
            DownedService.lethal(target, DownedService.FINISHED, actor);
        }

        @Override
        public boolean slowsActor() {
            return false;
        }
    }
}
