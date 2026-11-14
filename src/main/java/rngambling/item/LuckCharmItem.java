package rngambling.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import rngambling.block.LuckyBlockEntity;
import rngambling.luck.Luck;

/**
 * A one-use luck charm, sold by the Casino Dealer: use it on a lucky block (bought or disguised)
 * to raise its luck by {@code amount}, up to the most there is. Survival players aren't told the
 * number, only that the block feels luckier.
 */
public class LuckCharmItem extends Item {
    private final int amount;

    public LuckCharmItem(int amount, Item.Properties properties) {
        super(properties);
        this.amount = amount;
    }

    public int amount() {
        return amount;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof LuckyBlockEntity lucky)) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = context.getClickedPos();
        if (lucky.luck() >= Luck.MAX) {
            player.sendOverlayMessage(Component.translatable("message.rngambling.charm.full").withStyle(ChatFormatting.YELLOW));
            return InteractionResult.FAIL;
        }
        int before = lucky.luck();
        lucky.setLuck(before + amount);
        if (!player.isCreative()) {
            context.getItemInHand().shrink(1);
        }
        player.sendOverlayMessage((player.isCreative()
                ? Component.translatable("message.rngambling.charm.used_creative", before, lucky.luck())
                : Component.translatable("message.rngambling.charm.used")).withStyle(ChatFormatting.GREEN));
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 20, 0.4, 0.4, 0.4, 0.1);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 1.2f);
        return InteractionResult.SUCCESS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
            Consumer<Component> lines, TooltipFlag flag) {
        lines.accept(Component.translatable("tooltip.rngambling.charm").withStyle(ChatFormatting.GRAY));
    }
}
