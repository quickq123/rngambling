package rngambling.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import rngambling.RNGambling;
import rngambling.block.LuckyBlockEntity;
import rngambling.block.MimicLuckyBlock;
import rngambling.luck.Luck;

/**
 * Places one disguised lucky block in a chunk, rarely (1 in {@code rarity} chunks, from the
 * config). It copies the block it replaces and always has at least one side open to air, so
 * every one can be found by a sharp-eyed player.
 * <ul>
 * <li>{@link Kind#SURFACE}: the top ground block (placed by the heightmap), under open air or plants.
 * <li>{@link Kind#CAVE}: a cave's floor, ceiling or wall, found by scanning a few columns near the
 * placement point for a block next to air.
 * </ul>
 */
public final class LuckyBlockFeature extends Feature<NoneFeatureConfiguration> {
    public enum Kind { SURFACE, CAVE }

    /** How far up and down a cave scan looks from the placement point, and how many columns it tries. */
    private static final int CAVE_SCAN = 24;
    private static final int CAVE_COLUMNS = 12;

    private final Kind kind;
    private final IntSupplier rarity;

    public LuckyBlockFeature(Kind kind, IntSupplier rarity) {
        super(NoneFeatureConfiguration.CODEC);
        this.kind = kind;
        this.rarity = rarity;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        int oneIn = rarity.getAsInt();
        if (oneIn <= 0 || context.random().nextInt(oneIn) != 0) {
            return false;
        }
        return tryPlace(kind, context.level(), context.random(), context.origin());
    }

    /** Places a disguised lucky block near {@code origin} if a good spot is found (no rarity roll). */
    public static boolean tryPlace(Kind kind, WorldGenLevel level, RandomSource random, BlockPos origin) {
        BlockPos spot = kind == Kind.SURFACE ? surfaceSpot(level, origin) : caveSpot(level, random, origin);
        if (spot == null) {
            return false;
        }
        BlockState copied = level.getBlockState(spot);
        level.setBlock(spot, RNGambling.MIMIC_LUCKY_BLOCK.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        if (level.getBlockEntity(spot) instanceof LuckyBlockEntity lucky) {
            lucky.initialise(copied, Luck.rollNatural(random));
            return true;
        }
        return false;
    }

    /** The heightmap gives the space just above the ground: the block below it, if it can be copied and the space isn't water. */
    private static BlockPos surfaceSpot(WorldGenLevel level, BlockPos origin) {
        BlockPos ground = origin.below();
        BlockState above = level.getBlockState(origin);
        boolean open = above.isAir() || (above.getFluidState().isEmpty() && above.getCollisionShape(level, origin).isEmpty());
        return open && MimicLuckyBlock.canMimic(level.getBlockState(ground), level, ground) ? ground : null;
    }

    private static BlockPos caveSpot(WorldGenLevel level, RandomSource random, BlockPos origin) {
        int bottom = level.getMinY() + 1;
        for (int column = 0; column < CAVE_COLUMNS; column++) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            int x = origin.getX() + random.nextInt(15) - 7;
            int z = origin.getZ() + random.nextInt(15) - 7;
            List<BlockPos> found = new ArrayList<>();
            for (int y = origin.getY() + CAVE_SCAN; y >= Math.max(bottom, origin.getY() - CAVE_SCAN); y--) {
                pos.set(x, y, z);
                if (MimicLuckyBlock.canMimic(level.getBlockState(pos), level, pos) && opensOntoCave(level, pos)) {
                    found.add(pos.immutable());
                }
            }
            if (!found.isEmpty()) {
                return found.get(random.nextInt(found.size()));
            }
        }
        return null;
    }

    /**
     * Next to air that has a roof somewhere above it: a cave, not open sky (the open surface is
     * the surface placement's job). Checked by looking up the column, since light, which the
     * game's own sky check uses, isn't worked out yet while a chunk is being made.
     */
    private static boolean opensOntoCave(WorldGenLevel level, BlockPos pos) {
        for (Direction side : Direction.values()) {
            BlockPos next = pos.relative(side);
            if (level.getBlockState(next).isAir() && roofed(level, next)) {
                return true;
            }
        }
        return false;
    }

    private static boolean roofed(WorldGenLevel level, BlockPos air) {
        BlockPos.MutableBlockPos pos = air.mutable();
        for (int y = air.getY() + 1; y < level.getMaxY(); y++) {
            pos.setY(y);
            if (level.getBlockState(pos).canOcclude()) {
                return true;
            }
        }
        return false;
    }
}
