package faygolover.rpmedicine.server;

import faygolover.rpmedicine.capability.MedicalData;
import faygolover.rpmedicine.config.ServerConfig;
import faygolover.rpmedicine.core.TreatmentAction;
import faygolover.rpmedicine.data.ItemRules;
import faygolover.rpmedicine.hospital.HospitalBlocks;
import faygolover.rpmedicine.hospital.HospitalService;
import faygolover.rpmedicine.integration.Integrations;
import faygolover.rpmedicine.item.GmScannerItem;
import faygolover.rpmedicine.item.MedicalContainerItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/**
 * Взаимодействия: лечение предметом по себе и другому (быстрый способ, п. 6.1 ТЗ), СЛР и переноска
 * пустой рукой, запреты для лежачего (п. 5.3) и несущего тело (п. 5.2).
 */
public final class InteractionHandler {
    private InteractionHandler() {}

    public static boolean canUseWeapons(ServerPlayer sp) {
        if (Medical.isDown(sp)) return false;
        MedicalData d = Medical.data(sp);
        return d == null || (!d.carrying && !d.lastMods.armsDisabled);
    }

    /** ПКМ по сущности. */
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player p = event.getEntity();
        if (Medical.isDown(p)) {
            cancel(event);
            return;
        }
        if (!(event.getTarget() instanceof LivingEntity target) || !Medical.isPatient(target)) return;
        ItemStack main = p.getMainHandItem();
        boolean carryOnGesture = p.isShiftKeyDown() && main.isEmpty();
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            // Вторая рука: не мешаем, но жест Carry On по людям запрещён.
            if (carryOnGesture && Integrations.carryOn() && ServerConfig.BLOCK_CARRY_ON.get()) cancel(event);
            return;
        }
        if (main.getItem() instanceof GmScannerItem) return;
        if (event.getLevel().isClientSide) {
            // Клиент: жест переноски и медпредметы поглощаем, чтобы не сработали чужие действия.
            if (carryOnGesture || main.is(faygolover.rpmedicine.menu.MedicalContainerMenu.MEDICAL_ITEMS)) cancel(event);
            return;
        }
        ServerPlayer sp = (ServerPlayer) p;
        ItemRules.Spec spec = ItemRules.specFor(main);
        if (spec != null) {
            if (TreatmentService.startWithItem(sp, target, sp.getInventory().selected, null)) cancel(event);
            return;
        }
        if (main.isEmpty() && Medical.isDown(target)) {
            if (sp.isShiftKeyDown()) CarryService.pickUp(sp, target);
            else TreatmentService.hold(sp, target, TreatmentAction.CPR, 0);
            cancel(event);
            return;
        }
        // Carry On не поднимает игроков и тела: у мода своя переноска.
        if (carryOnGesture && Integrations.carryOn() && ServerConfig.BLOCK_CARRY_ON.get()) cancel(event);
    }

    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        Player p = event.getEntity();
        if (Medical.isDown(p)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        if (event.getTarget() instanceof LivingEntity t && Medical.isPatient(t) && p.isShiftKeyDown() && p.getMainHandItem().isEmpty()
                && Integrations.carryOn() && ServerConfig.BLOCK_CARRY_ON.get()) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    private static void cancel(PlayerInteractEvent.EntityInteract event) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /** ПКМ предметом в воздух: лечение себя. */
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player p = event.getEntity();
        if (Medical.isDown(p)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        ItemStack stack = event.getItemStack();
        if (stack.getItem() instanceof GmScannerItem || stack.getItem() instanceof MedicalContainerItem) return;
        if (event.getLevel().isClientSide) {
            if (stack.is(faygolover.rpmedicine.menu.MedicalContainerMenu.MEDICAL_ITEMS)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
            return;
        }
        ServerPlayer sp = (ServerPlayer) p;
        if (!canUseWeapons(sp) && stack.getItem() instanceof ProjectileWeaponItem) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        ItemRules.Spec spec = ItemRules.specFor(stack);
        if (spec == null) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (spec.action().target == TreatmentAction.Target.HOLD) return;
        int slot = event.getHand() == InteractionHand.MAIN_HAND ? sp.getInventory().selected : 40;
        TreatmentService.startWithItem(sp, sp, slot, null);
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player p = event.getEntity();
        if (Medical.isDown(p)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        // Лабораторный стол: пробирка в руке (второй этап, п. 3).
        if (p.getMainHandItem().is(faygolover.rpmedicine.registry.ModItems.BLOOD_SAMPLE.get())
                && HospitalBlocks.is(event.getLevel().getBlockState(event.getPos()), faygolover.rpmedicine.hospital.HospitalFunction.LAB)) {
            if (!event.getLevel().isClientSide) LabService.onUseBlock((ServerPlayer) p, event.getPos());
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        // Стерилизатор: инструмент в руке снова стерилен (второй этап, п. 2.1).
        if (p.getMainHandItem().getItem() instanceof faygolover.rpmedicine.item.SurgicalInstrumentItem
                && HospitalBlocks.is(event.getLevel().getBlockState(event.getPos()), faygolover.rpmedicine.hospital.HospitalFunction.STERILIZER)) {
            if (!event.getLevel().isClientSide) {
                faygolover.rpmedicine.item.SurgicalInstrumentItem.setSterile(p.getMainHandItem(), true);
                p.displayClientMessage(net.minecraft.network.chat.Component.translatable("rpmedicine.msg.sterilized"), true);
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        // Больничная койка: лечь пустой рукой или положить того, кого несёшь (второй этап, п. 2.2).
        boolean carrying = !p.getPassengers().isEmpty();
        if (!carrying && (p.isShiftKeyDown() || !p.getMainHandItem().isEmpty())) return;
        if (!HospitalBlocks.isBed(event.getLevel().getBlockState(event.getPos()))) return;
        boolean handled = !event.getLevel().isClientSide
                ? HospitalService.onUseBlock((ServerPlayer) p, event.getPos())
                : true;
        if (handled) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (Medical.isDown(event.getEntity())) event.setCanceled(true);
    }

    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (Medical.isDown(event.getEntity())) event.setNewSpeed(0);
    }

    public static void onAttack(AttackEntityEvent event) {
        if (Medical.isDown(event.getEntity())) event.setCanceled(true);
    }

    public static void onToss(ItemTossEvent event) {
        Player p = event.getPlayer();
        if (!p.level().isClientSide && Medical.isDown(p)) {
            // Лежачий не бросает вещи: возвращаем в инвентарь.
            ItemStack s = event.getEntity().getItem().copy();
            event.setCanceled(true);
            if (!p.getInventory().add(s)) p.drop(s, true, false);
        }
    }

    public static void onPickup(EntityItemPickupEvent event) {
        if (Medical.isDown(event.getEntity())) event.setCanceled(true);
    }

    /** Использование предметов: дольше при плохой руке; лук и арбалет — нельзя лежачему, несущему, без рук. */
    public static void onUseStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (event.getItem().getItem() instanceof ProjectileWeaponItem && !canUseWeapons(sp)) {
            event.setCanceled(true);
            return;
        }
        if (Medical.isDown(sp)) {
            event.setCanceled(true);
            return;
        }
        MedicalData d = Medical.data(sp);
        if (d != null && d.lastMods.useTimeFactor > 1.0)
            event.setDuration((int) Math.ceil(event.getDuration() * d.lastMods.useTimeFactor));
    }

    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            MedicalData d = Medical.data(sp);
            if (d != null) d.jumps++;
        }
    }
}
