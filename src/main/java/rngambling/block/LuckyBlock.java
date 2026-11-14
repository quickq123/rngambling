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
import net.minecraft.world.entity.LivingEntity;
import rngambling.luck.LuckRoll;
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
        int chain = level.getBlockEntity(pos) instanceof LuckyBlockEntity lucky ? lucky.chain() : 0;
        boolean removed = super.onDestroyedByPlayer(state, level, pos, player, tool, willHarvest, fluid);
        if (removed && level instanceof ServerLevel serverLevel && countsAsPlayerBreak(player)) {
            Outcomes.roll(serverLevel, pos, (ServerPlayer) player, luck, chain);
        }
        return removed;
    }

    /**
     * A placed lucky block rolls its luck from the item's range. Only a creative player keeps a
     * fixed test luck. Blocks moved by other mods' transport tools keep their saved luck (they
     * aren't "placed" from an item).
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof LuckyBlockEntity lucky)) {
            return;
        }
        boolean keepFixed = stack.has(rngambling.RNGambling.LUCK.get()) && placer instanceof Player player && player.isCreative();
        if (!keepFixed) {
            LuckRoll roll = stack.getItem() instanceof LuckyBlockItem item ? item.roll() : LuckyBlock.RANDOM;
            lucky.setLuck(roll.roll(level.getRandom()));
        }
    }

    /** The plain Lucky Block's roll: any luck from -100 to +100, each equally likely. */
    public static final LuckRoll RANDOM = new LuckRoll.Even(rngambling.luck.Luck.MIN, rngambling.luck.Luck.MAX);

    /** Only a real player in survival or adventure sets off an outcome. */
    public static boolean countsAsPlayerBreak(Player player) {
        return player instanceof ServerPlayer && !player.isFakePlayer() && !player.isCreative() && !player.isSpectator();
    }
}
