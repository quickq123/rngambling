package rngambling.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import rngambling.RNGambling;
import rngambling.block.LuckyBlockEntity;
import rngambling.block.MimicLuckyBlock;
import rngambling.luck.Luck;

/**
 * Creative only. Right-click a block to turn it into a disguised lucky block that copies it,
 * with luck rolled the way world-generated ones will be. Right-click a disguised one to see what
 * it copies and its luck; sneak and right-click to turn it back into the block it copied.
 */
public class MimicWandItem extends Item {
    public MimicWandItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        if (!player.isCreative()) {
            tell(player, Component.translatable("message.rngambling.wand.creative_only"), ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);

        if (state.getBlock() instanceof MimicLuckyBlock) {
            LuckyBlockEntity lucky = level.getBlockEntity(pos) instanceof LuckyBlockEntity found ? found : null;
            BlockState mimic = lucky == null ? null : lucky.mimic();
            if (player.isShiftKeyDown()) {
                if (mimic != null) {
                    level.setBlock(pos, mimic, Block.UPDATE_ALL);
                    tell(player, Component.translatable("message.rngambling.wand.restored", mimic.getBlock().getName()), ChatFormatting.YELLOW);
                }
                return InteractionResult.SUCCESS;
            }
            int luck = lucky == null ? 0 : lucky.luck();
            tell(player, Component.translatable("message.rngambling.wand.info",
                    mimic == null ? Component.literal("?") : mimic.getBlock().getName(), signed(luck)), colour(luck));
            return InteractionResult.SUCCESS;
        }

        if (player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (!MimicLuckyBlock.canMimic(state, level, pos)) {
            tell(player, Component.translatable("message.rngambling.wand.cannot", state.getBlock().getName()), ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        level.setBlock(pos, RNGambling.MIMIC_LUCKY_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof LuckyBlockEntity lucky) {
            lucky.setMimic(state);
            lucky.setLuck(Luck.rollNatural(level.getRandom()));
            tell(player, Component.translatable("message.rngambling.wand.disguised", state.getBlock().getName(), signed(lucky.luck())),
                    colour(lucky.luck()));
        }
        level.playSound(null, pos, SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 0.5f, 1.4f);
        return InteractionResult.SUCCESS;
    }

    private static void tell(ServerPlayer player, Component message, ChatFormatting colour) {
        player.sendOverlayMessage(message.copy().withStyle(colour));
    }

    private static String signed(int luck) {
        return (luck > 0 ? "+" : "") + luck;
    }

    private static ChatFormatting colour(int luck) {
        return luck > 0 ? ChatFormatting.GREEN : luck < 0 ? ChatFormatting.RED : ChatFormatting.WHITE;
    }
}
