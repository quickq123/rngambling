package rngambling.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.PushReaction;
import org.jspecify.annotations.Nullable;
import rngambling.outcome.Outcomes;

/**
 * A lucky block. Its outcome happens only when a real survival player breaks it: not in
 * creative, and not when a machine (a quarry, a mining turtle, another mod's block breaker,
 * all "fake players") or an explosion removes it. Those just remove it, and it drops nothing.
 */
public class LuckyBlock extends Block implements EntityBlock {
    public LuckyBlock(BlockBehaviour.Properties properties) {
        super(properties.pushReaction(PushReaction.BLOCK));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LuckyBlockEntity(pos, state);
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, ItemStack tool,
            boolean willHarvest, FluidState fluid) {
        int luck = level.getBlockEntity(pos) instanceof LuckyBlockEntity lucky ? lucky.luck() : 0;
        boolean removed = super.onDestroyedByPlayer(state, level, pos, player, tool, willHarvest, fluid);
        if (removed && level instanceof ServerLevel serverLevel && countsAsPlayerBreak(player)) {
            Outcomes.roll(serverLevel, pos, (ServerPlayer) player, luck);
        }
        return removed;
    }

    /** Only a real player in survival or adventure sets off an outcome. */
    public static boolean countsAsPlayerBreak(Player player) {
        return player instanceof ServerPlayer && !player.isFakePlayer() && !player.isCreative() && !player.isSpectator();
    }
}
