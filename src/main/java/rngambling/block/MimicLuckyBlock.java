package rngambling.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A disguised lucky block: it looks, sounds and mines like the block it copies (drawn a few
 * percent darker, see {@code MimicModel}), and middle-click in creative gives the copied block,
 * so nothing but a careful eye gives it away. Breaking it works like any lucky block.
 */
public class MimicLuckyBlock extends LuckyBlock {
    public MimicLuckyBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    /** The copied block, or null if it isn't known (yet). */
    public static @Nullable BlockState mimicAt(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof LuckyBlockEntity lucky ? lucky.mimic() : null;
    }

    /**
     * Whether a block can be copied: plain, full, solid, opaque cubes that can be broken and
     * hold nothing (no chests or furnaces, whose contents would be lost).
     */
    public static boolean canMimic(BlockState state, BlockGetter level, BlockPos pos) {
        return !state.hasBlockEntity() && !(state.getBlock() instanceof LuckyBlock)
                && state.isSolidRender() && state.isCollisionShapeFullBlock(level, pos)
                && state.getDestroySpeed(level, pos) >= 0;
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        BlockState mimic = mimicAt(level, pos);
        return mimic != null ? mimic.getDestroyProgress(player, level, pos) : super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    public SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity) {
        BlockState mimic = mimicAt(level, pos);
        return mimic != null ? mimic.getSoundType(level, pos, entity) : super.getSoundType(state, level, pos, entity);
    }

    @Override
    public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        BlockState mimic = mimicAt(level, pos);
        return mimic != null ? mimic.getExplosionResistance(level, pos, explosion) : super.getExplosionResistance(state, level, pos, explosion);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData, Player player) {
        BlockState mimic = mimicAt(level, pos);
        return mimic != null ? new ItemStack(mimic.getBlock()) : ItemStack.EMPTY;
    }

    /** What other mods (connected textures, block-face lookups) should treat this block as. */
    @Override
    public BlockState getAppearance(BlockState state, BlockAndLightGetter level, BlockPos pos, Direction side,
            @Nullable BlockState queryState, @Nullable BlockPos queryPos) {
        BlockState mimic = mimicAt(level, pos);
        return mimic != null ? mimic : state;
    }
}
