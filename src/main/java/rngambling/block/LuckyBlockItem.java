package rngambling.block;

import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;
import rngambling.RNGambling;
import rngambling.luck.Luck;
import rngambling.luck.LuckRoll;

/**
 * A lucky block as an item. Its luck isn't fixed: it's rolled from the item's range when a
 * player places it (see {@link LuckyBlock#setPlacedBy}), so carrying blocks somewhere
 * favourable gains nothing. The variants differ only in range: the plain Lucky Block (-100 to
 * +100), Safe (-50 to +50), Extreme (50 to 100, good or bad) and Blessed (+80 to +100).
 *
 * <p>A fixed luck ({@link RNGambling#LUCK} on the item) is kept only when a creative player
 * places it: it's for testing.
 */
public class LuckyBlockItem extends BlockItem {
    private final LuckRoll roll;
    private final String rangeKey;
    private final boolean canonical;

    /**
     * @param canonical whether this is the item the block is known by (picked, dropped): only the
     *                  plain Lucky Block; the variants place the same block
     */
    public LuckyBlockItem(Block block, Item.Properties properties, LuckRoll roll, String rangeKey, boolean canonical) {
        super(block, properties);
        this.roll = roll;
        this.rangeKey = rangeKey;
        this.canonical = canonical;
    }

    public LuckRoll roll() {
        return roll;
    }

    @Override
    public void registerBlocks(Map<Block, Item> blockToItemMap, Item item) {
        if (canonical) {
            super.registerBlocks(blockToItemMap, item);
        }
    }

    /** A creative test block with a fixed luck. */
    public static ItemStack withLuck(int luck) {
        ItemStack stack = new ItemStack(RNGambling.LUCKY_BLOCK_ITEM.get());
        stack.set(RNGambling.LUCK.get(), Luck.clamp(luck));
        return stack;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
            Consumer<Component> lines, TooltipFlag flag) {
        Integer fixed = stack.get(RNGambling.LUCK.get());
        if (fixed != null && flag.isCreative()) {
            lines.accept(Component.translatable("tooltip.rngambling.luck_fixed", (fixed > 0 ? "+" : "") + fixed)
                    .withStyle(fixed > 0 ? ChatFormatting.GREEN : fixed < 0 ? ChatFormatting.RED : ChatFormatting.GRAY));
        } else {
            lines.accept(Component.translatable("tooltip.rngambling.luck_range." + rangeKey).withStyle(ChatFormatting.GRAY));
        }
    }
}
