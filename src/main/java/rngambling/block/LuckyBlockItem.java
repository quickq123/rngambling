package rngambling.block;

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

/** A lucky block as an item. Its luck is hidden from survival players: that's part of the gamble. */
public class LuckyBlockItem extends BlockItem {
    public LuckyBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    public static ItemStack withLuck(int luck) {
        ItemStack stack = new ItemStack(RNGambling.LUCKY_BLOCK_ITEM.get());
        if (luck != 0) {
            stack.set(RNGambling.LUCK.get(), Luck.clamp(luck));
        }
        return stack;
    }

    public static int luckOf(ItemStack stack) {
        return stack.getOrDefault(RNGambling.LUCK.get(), 0);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
            Consumer<Component> lines, TooltipFlag flag) {
        if (flag.isCreative()) {
            int luck = luckOf(stack);
            lines.accept(Component.translatable("tooltip.rngambling.luck", (luck > 0 ? "+" : "") + luck)
                    .withStyle(luck > 0 ? ChatFormatting.GREEN : luck < 0 ? ChatFormatting.RED : ChatFormatting.GRAY));
        }
    }
}
