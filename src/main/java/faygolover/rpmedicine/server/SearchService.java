package faygolover.rpmedicine.server;

import faygolover.rpmedicine.config.ServerConfig;
import faygolover.rpmedicine.entity.BodyStubEntity;
import faygolover.rpmedicine.integration.CuriosCompat;
import faygolover.rpmedicine.integration.Integrations;
import faygolover.rpmedicine.menu.SearchMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.network.NetworkHooks;

import java.util.function.Predicate;

/**
 * Обыск лежачего (п. 5.2 ТЗ): прогресс-бар (по умолчанию 10 с), прерывается; затем открывается
 * весь инвентарь, броня и Curios. Жертве не сообщается, в лог не пишется.
 */
public final class SearchService {
    private SearchService() {}

    public static void start(ServerPlayer actor, LivingEntity target) {
        if (actor == target || !Medical.isDown(target) || Medical.isDown(actor)) return;
        if (actor.distanceTo(target) > ServerConfig.INTERACT_DISTANCE.get() + 0.5) return;
        ActionManager.start(new SearchAction(actor, target, (int) (ServerConfig.SEARCH_SECONDS.get() * 20)));
    }

    static boolean valid(Player actor, LivingEntity target) {
        return !target.isRemoved() && Medical.isDown(target) && !Medical.isDown(actor)
                && actor.distanceTo(target) <= ServerConfig.INTERACT_DISTANCE.get() + 1.5;
    }

    static final class SearchAction extends ActionManager.TimedAction {
        private final LivingEntity target;

        SearchAction(ServerPlayer actor, LivingEntity target, int ticks) {
            super(actor, ticks);
            this.target = target;
        }

        @Override
        public String label() {
            return "rpmedicine.action.search";
        }

        @Override
        public String checkContinue() {
            return valid(actor, target) ? null : "rpmedicine.action.target_lost";
        }

        @Override
        public void complete() {
            open(actor, target);
        }
    }

    static void open(ServerPlayer actor, LivingEntity target) {
        IItemHandlerModifiable curios = null;
        net.minecraft.world.Container container;
        if (target instanceof ServerPlayer tp) {
            container = tp.getInventory();
            if (Integrations.curios()) curios = CuriosCompat.equipped(tp);
        } else if (target instanceof BodyStubEntity stub) {
            container = stub.inventory();
            curios = stubCurios(stub);
        } else {
            return;
        }
        IItemHandlerModifiable c = curios;
        Predicate<Player> validator = p -> valid(p, target);
        int curiosCount = c != null ? c.getSlots() : 0;
        Component title = Component.translatable("rpmedicine.search.title", target.getDisplayName());
        NetworkHooks.openScreen(actor, new SimpleMenuProvider((id, inv, p) -> new SearchMenu(id, inv, container, c, target, validator), title),
                buf -> buf.writeVarInt(curiosCount));
    }

    /** Слоты Curios заглушки как обработчик предметов с записью обратно в заглушку. */
    private static IItemHandlerModifiable stubCurios(BodyStubEntity stub) {
        ItemStackHandler h = new ItemStackHandler(stub.curios.size()) {
            @Override
            protected void onContentsChanged(int slot) {
                var old = stub.curios.get(slot);
                stub.curios.set(slot, new BodyStubEntity.CurioEntry(old.identifier(), old.index(), getStackInSlot(slot).copy()));
                stub.markChanged();
            }
        };
        for (int i = 0; i < stub.curios.size(); i++) h.setStackInSlot(i, stub.curios.get(i).stack().copy());
        return h;
    }

    public static boolean hasItems(ItemStack s) {
        return !s.isEmpty();
    }
}
