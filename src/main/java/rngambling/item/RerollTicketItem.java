package rngambling.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * GOD-44 Re-roll Ticket: held in the off hand when breaking a lucky block, it's used up and the
 * block draws two effects to choose from (see rngambling.outcome.RerollMenu).
 */
public class RerollTicketItem extends Item {
    public RerollTicketItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
            Consumer<Component> lines, TooltipFlag flag) {
        lines.accept(Component.translatable("tooltip.rngambling.reroll_ticket").withStyle(ChatFormatting.GRAY));
    }
}
