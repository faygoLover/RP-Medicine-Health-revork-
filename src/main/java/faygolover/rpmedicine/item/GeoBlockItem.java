package faygolover.rpmedicine.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;

/** Предмет блока с 3D-моделью (Health & Disease): в руке и в инвентаре — GeoItemRenderer. */
public class GeoBlockItem extends BlockItem {
    public GeoBlockItem(Block block, Properties props) {
        super(block, props);
    }

    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(faygolover.rpmedicine.client.ItemPoses.EXTENSIONS);
    }
}
