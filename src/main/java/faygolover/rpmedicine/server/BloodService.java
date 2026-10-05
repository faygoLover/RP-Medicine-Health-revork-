package faygolover.rpmedicine.server;

import faygolover.rpmedicine.config.ServerConfig;
import faygolover.rpmedicine.core.BloodType;
import faygolover.rpmedicine.core.MedicalSettings;
import faygolover.rpmedicine.core.MedicalState;
import faygolover.rpmedicine.hospital.HospitalBlocks;
import faygolover.rpmedicine.hospital.HospitalFunction;
import faygolover.rpmedicine.item.BloodBagItem;
import faygolover.rpmedicine.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Кровь (ТЗ второго этапа, п. 4): группа при первом входе, пакеты в холодильнике, полный пакет после
 * забора у донора.
 */
public final class BloodService {
    private BloodService() {}

    private static final SplittableRandom RANDOM = new SplittableRandom();

    /** Группа не задана — по конфигу случайная по распределению (спорный п. 16.2: предложено «да»). */
    public static void onLogin(ServerPlayer sp) {
        MedicalState m = Medical.state(sp);
        if (m == null || m.bloodType != null || !ServerConfig.RANDOM_BLOOD_TYPE.get()) return;
        m.bloodType = BloodType.random(MedicalSettings.get().bloodTypeWeights, RANDOM);
        Medical.changed(sp);
    }

    /**
     * Закрыли меню контейнера: пакеты в холодильнике (функция {@code fridge} датапака) «остывают»,
     * в любом другом блоке — нет.
     */
    public static void onContainerClose(PlayerContainerEvent.Close e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        long now = sp.level().getGameTime();
        Set<Container> seen = new HashSet<>();
        for (Slot slot : e.getContainer().slots) {
            Container c = slot.container;
            if (!(c instanceof BlockEntity be) || !seen.add(c)) continue;
            boolean fridge = HospitalBlocks.is(be.getBlockState(), HospitalFunction.FRIDGE);
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack st = c.getItem(i);
                if (st.getItem() instanceof BloodBagItem) BloodBagItem.setCold(st, fridge, now);
                if (st.getItem() instanceof faygolover.rpmedicine.item.Perishable.Item) faygolover.rpmedicine.item.Perishable.setCold(st, fridge, now);
            }
        }
    }

    /** Кровь взята: пустой пакет становится полным, подписан группой донора. */
    public static void giveFilledBag(ServerPlayer medic, LivingEntity donor) {
        MedicalState m = Medical.state(donor);
        ItemStack bag = new ItemStack(ModItems.BLOOD_BAG.get());
        BloodBagItem.fill(bag, m != null ? m.bloodType : null, medic.level().getGameTime(), donor.getName().getString());
        if (!medic.getInventory().add(bag)) medic.drop(bag, false);
        BloodType t = m != null ? m.bloodType : null;
        medic.displayClientMessage(Component.translatable("rpmedicine.msg.bag_filled", t != null ? t.label : "?")
                .withStyle(ChatFormatting.GREEN), true);
    }
}
