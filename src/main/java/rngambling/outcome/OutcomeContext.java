package rngambling.outcome;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/** Where and for whom an outcome happens. {@code pos} is where the lucky block was (now empty). */
public record OutcomeContext(ServerLevel level, BlockPos pos, ServerPlayer player, RandomSource random, int luck) {
    /** A whole number from min to max, both included. */
    public int between(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    /** Drops items where the block was, splitting counts over a full stack. */
    public void drop(ItemStack stack) {
        int left = stack.getCount();
        while (left > 0) {
            int count = Math.min(left, stack.getMaxStackSize());
            Block.popResource(level, pos, stack.copyWithCount(count));
            left -= count;
        }
    }

    /**
     * A free spot near {@code centre} for a mob to stand: an empty block with an empty block above.
     * Falls back to the centre itself.
     */
    public BlockPos freeSpotNear(BlockPos centre, int radius) {
        for (int tries = 0; tries < 16; tries++) {
            BlockPos spot = centre.offset(between(-radius, radius), between(0, 1), between(-radius, radius));
            if (level.isEmptyBlock(spot) && level.isEmptyBlock(spot.above())) {
                return spot;
            }
        }
        return centre;
    }

    public void spawn(Entity entity, Vec3 at) {
        entity.snapTo(at);
        level.addFreshEntity(entity);
    }
}
