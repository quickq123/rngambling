package rngambling.worldgen;

import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import rngambling.RNGambling;
import rngambling.block.LuckyBlockEntity;
import rngambling.block.MimicLuckyBlock;
import rngambling.luck.LuckZone;

/**
 * Places one disguised lucky block in a chunk, rarely. How rarely, and how its luck is rolled,
 * come from the {@link LuckZone} at that spot. It copies the block it replaces and always has at
 * least one side open to air, so every one can be found by a sharp-eyed player.
 * <ul>
 * <li>{@link Kind#SURFACE}: the top ground block (placed by the heightmap), under open air or plants.
 * <li>{@link Kind#CAVE}: a cave's floor, ceiling or wall, found by scanning a few columns near the
 * placement point for a block next to roofed air.
 * <li>{@link Kind#STRUCTURE}: inside a structure that a zone names (a wall, floor or pillar of the
 * structure next to its rooms), in chunks the structure reaches into.
 * </ul>
 */
public final class LuckyBlockFeature extends Feature<NoneFeatureConfiguration> {
    public enum Kind { SURFACE, CAVE, STRUCTURE }

    /** How far up and down a cave scan looks from the placement point, and how many columns it tries. */
    private static final int CAVE_SCAN = 24;
    private static final int CAVE_COLUMNS = 12;
    private static final int STRUCTURE_COLUMNS = 24;

    private final Kind kind;

    public LuckyBlockFeature(Kind kind) {
        super(NoneFeatureConfiguration.CODEC);
        this.kind = kind;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        if (kind == Kind.STRUCTURE) {
            return placeInStructure(level, random, origin);
        }
        LuckZone zone = LuckZone.at(level, origin);
        int oneIn = kind == Kind.SURFACE ? zone.surfaceRarity() : zone.caveRarity();
        if (oneIn <= 0 || random.nextInt(oneIn) != 0) {
            return false;
        }
        return tryPlace(kind, level, random, origin);
    }

    /** Places a disguised lucky block near {@code origin} if a good spot is found (no rarity roll). */
    public static boolean tryPlace(Kind kind, WorldGenLevel level, RandomSource random, BlockPos origin) {
        BlockPos spot = kind == Kind.SURFACE ? surfaceSpot(level, origin) : caveSpot(level, random, origin);
        return spot != null && disguise(level, random, spot);
    }

    /** Turns the block at {@code spot} into a disguised lucky block, with luck rolled for its zone. */
    private static boolean disguise(WorldGenLevel level, RandomSource random, BlockPos spot) {
        BlockState copied = level.getBlockState(spot);
        LuckZone zone = LuckZone.at(level, spot);
        level.setBlock(spot, RNGambling.MIMIC_LUCKY_BLOCK.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        if (level.getBlockEntity(spot) instanceof LuckyBlockEntity lucky) {
            lucky.initialise(copied, zone.luck().roll(random));
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
                if (MimicLuckyBlock.canMimic(level.getBlockState(pos), level, pos) && opensOntoAir(level, pos, true)) {
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
     * In a chunk that a zone's structures reach into, hides one disguised lucky block in the
     * structure (1 in the zone's {@code structure_one_in_chunks} such chunks).
     */
    private static boolean placeInStructure(WorldGenLevel level, RandomSource random, BlockPos origin) {
        ChunkPos chunk = ChunkPos.containing(origin);
        Map<Structure, LongSet> references = level.getChunk(chunk.x(), chunk.z()).getAllReferences();
        if (references.isEmpty()) {
            return false;
        }
        Registry<Structure> structureRegistry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        for (LuckZone zone : level.registryAccess().lookupOrThrow(LuckZone.REGISTRY)) {
            if (zone.structures().isEmpty() || zone.structureOneInChunks() <= 0) {
                continue;
            }
            Set<Structure> wanted = references.keySet().stream()
                    .filter(s -> zone.structures().get().contains(structureRegistry.wrapAsHolder(s)))
                    .collect(Collectors.toSet());
            if (wanted.isEmpty() || random.nextInt(zone.structureOneInChunks()) != 0) {
                continue;
            }
            StructureManager structures = LuckZone.structures(level);
            List<StructureStart> starts = LuckZone.startsReaching(level, chunk, wanted::contains);
            BlockPos spot = structureSpot(level, random, chunk, structures, starts);
            if (spot != null) {
                return disguise(level, random, spot);
            }
        }
        return false;
    }

    /** A block of one of the structures, inside this chunk, next to air (a room or corridor). */
    private static BlockPos structureSpot(WorldGenLevel level, RandomSource random, ChunkPos chunk,
            StructureManager structures, List<StructureStart> starts) {
        if (starts.isEmpty()) {
            return null;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int column = 0; column < STRUCTURE_COLUMNS; column++) {
            StructureStart start = starts.get(random.nextInt(starts.size()));
            BoundingBox box = start.getBoundingBox();
            int x = chunk.getMinBlockX() + random.nextInt(16);
            int z = chunk.getMinBlockZ() + random.nextInt(16);
            if (x < box.minX() || x > box.maxX() || z < box.minZ() || z > box.maxZ()) {
                continue;
            }
            List<BlockPos> found = new ArrayList<>();
            for (int y = box.maxY(); y >= box.minY(); y--) {
                pos.set(x, y, z);
                if (MimicLuckyBlock.canMimic(level.getBlockState(pos), level, pos) && opensOntoAir(level, pos, false)
                        && structures.structureHasPieceAt(pos, start)) {
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
     * Next to air on some side. For caves the air must also have a roof somewhere above it (a
     * cave, not open sky, which is the surface placement's job), checked by looking up the
     * column, since light, which the game's own sky check uses, isn't worked out yet while a
     * chunk is being made.
     */
    private static boolean opensOntoAir(WorldGenLevel level, BlockPos pos, boolean roofed) {
        for (Direction side : Direction.values()) {
            BlockPos next = pos.relative(side);
            if (level.getBlockState(next).isAir() && (!roofed || roofed(level, next))) {
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
