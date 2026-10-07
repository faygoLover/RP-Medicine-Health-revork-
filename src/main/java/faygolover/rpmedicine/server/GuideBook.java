package faygolover.rpmedicine.server;

import faygolover.rpmedicine.RpMedicine;
import faygolover.rpmedicine.core.MedicalSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Справочник медика — книга Patchouli (необязательна: без Patchouli книги нет). Разделы открываются скрытыми
 * достижениями: {@code book/gm} — только операторам; {@code book/medicine_N} — по уровню медицины, если включено
 * в конфиге ({@code bookSkillGating}), иначе всем.
 */
public final class GuideBook {
    private GuideBook() {}

    public static final ResourceLocation BOOK = new ResourceLocation(RpMedicine.MODID, "guide");
    private static final String GIVEN = "rpmedicine_book_given";

    public static boolean available() {
        return ModList.get().isLoaded("patchouli");
    }

    /** Книга-предмет Patchouli или пусто. */
    public static ItemStack stack() {
        if (!available()) return ItemStack.EMPTY;
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("patchouli", "guide_book"));
        if (item == null) return ItemStack.EMPTY;
        ItemStack st = new ItemStack(item);
        st.getOrCreateTag().putString("patchouli:book", BOOK.toString());
        return st;
    }

    /** Вход: при первом входе в мир — книга и подсказка в чат; доступ к разделам. */
    public static void onLogin(ServerPlayer sp) {
        CompoundTag pd = sp.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!pd.getBoolean(GIVEN) && available()) {
            pd.putBoolean(GIVEN, true);
            sp.getPersistentData().put(Player.PERSISTED_NBT_TAG, pd);
            ItemStack book = stack();
            if (!book.isEmpty() && !sp.getInventory().add(book)) sp.drop(book, false);
            sp.sendSystemMessage(Component.translatable("rpmedicine.book.welcome").withStyle(ChatFormatting.GOLD));
        }
        updateAccess(sp);
    }

    /** Раз в несколько секунд: оператором могли сделать, уровень мог поменяться. */
    public static void tick(ServerPlayer sp) {
        if (sp.tickCount % 100 == 0) updateAccess(sp);
    }

    public static void updateAccess(ServerPlayer sp) {
        if (!available()) return;
        set(sp, "book/gm", sp.hasPermissions(2));
        boolean gating = MedicalSettings.get().bookSkillGating;
        int level = Medical.medicineLevel(sp);
        for (int n = 1; n <= 10; n++) set(sp, "book/medicine_" + n, !gating || level >= n);
    }

    private static void set(ServerPlayer sp, String path, boolean on) {
        Advancement adv = sp.server.getAdvancements().getAdvancement(new ResourceLocation(RpMedicine.MODID, path));
        if (adv == null) return;
        AdvancementProgress pr = sp.getAdvancements().getOrStartProgress(adv);
        if (on && !pr.isDone()) {
            for (String c : pr.getRemainingCriteria()) sp.getAdvancements().award(adv, c);
        } else if (!on && pr.hasProgress()) {
            for (String c : pr.getCompletedCriteria()) sp.getAdvancements().revoke(adv, c);
        }
    }
}
