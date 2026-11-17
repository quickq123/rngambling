package rngambling.luck;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import rngambling.RNGambling;
import rngambling.RNGamblingConfig;

/**
 * A luck zone: how generated lucky blocks behave in one part of the world. Zones are datapack
 * files, {@code data/<namespace>/rngambling/luck_zone/<name>.json}:
 * <pre>
 * {
 *   "dimension": "minecraft:the_nether",       optional: only in this dimension
 *   "biomes": "#minecraft:is_nether",          optional: only in these biomes (a tag, one id or a list)
 *   "structures": "minecraft:ancient_city",    optional: only inside these structures' pieces
 *   "luck": {"type": "extremes", "min": 50, "max": 100, "good_chance": 0.5},
 *   "surface_one_in_chunks": 0,                optional (default: the config's); 0 = none
 *   "cave_one_in_chunks": 6,                   optional (default: the config's); 0 = none
 *   "structure_one_in_chunks": 3,              optional (default 0): extra blocks hidden inside the structures
 *   "priority": 0                              optional: breaks ties between equally specific zones
 * }
 * </pre>
 * Where several zones match a spot, the most specific wins: a structure zone over a biome zone over
 * a dimension zone over a zone with no conditions; then the higher priority; then the name.
 */
public record LuckZone(
        Optional<ResourceKey<Level>> dimension,
        Optional<HolderSet<Biome>> biomes,
        Optional<HolderSet<Structure>> structures,
        LuckRoll luck,
        Optional<Integer> surfaceOneInChunks,
        Optional<Integer> caveOneInChunks,
        int structureOneInChunks,
        int priority) {

    public static final ResourceKey<Registry<LuckZone>> REGISTRY = ResourceKey.createRegistryKey(RNGambling.id("luck_zone"));

    public static final Codec<LuckZone> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceKey.codec(Registries.DIMENSION).optionalFieldOf("dimension").forGetter(LuckZone::dimension),
            RegistryCodecs.homogeneousList(Registries.BIOME).optionalFieldOf("biomes").forGetter(LuckZone::biomes),
            RegistryCodecs.homogeneousList(Registries.STRUCTURE).optionalFieldOf("structures").forGetter(LuckZone::structures),
            LuckRoll.CODEC.fieldOf("luck").forGetter(LuckZone::luck),
            Codec.intRange(0, 10_000).optionalFieldOf("surface_one_in_chunks").forGetter(LuckZone::surfaceOneInChunks),
            Codec.intRange(0, 10_000).optionalFieldOf("cave_one_in_chunks").forGetter(LuckZone::caveOneInChunks),
            Codec.intRange(0, 10_000).optionalFieldOf("structure_one_in_chunks", 0).forGetter(LuckZone::structureOneInChunks),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(LuckZone::priority)
    ).apply(i, LuckZone::new));

    /** Used where no zone matches: the original Overworld behaviour. */
    public static final LuckZone FALLBACK = new LuckZone(Optional.empty(), Optional.empty(), Optional.empty(),
            new LuckRoll.Spread(0, 15), Optional.empty(), Optional.empty(), 0, 0);

    public int surfaceRarity() {
        return surfaceOneInChunks.orElseGet(RNGamblingConfig.SURFACE_RARITY::getAsInt);
    }

    public int caveRarity() {
        return caveOneInChunks.orElseGet(RNGamblingConfig.CAVE_RARITY::getAsInt);
    }

    /** How specific the zone is: 3 structure, 2 biome, 1 dimension, 0 none. */
    public int specificity() {
        return structures.isPresent() ? 3 : biomes.isPresent() ? 2 : dimension.isPresent() ? 1 : 0;
    }

    /** The zone that applies at a spot being generated. */
    public static LuckZone at(WorldGenLevel level, BlockPos pos) {
        Registry<LuckZone> zones = level.registryAccess().lookupOrThrow(REGISTRY);
        ResourceKey<Level> dimension = level.getLevel().dimension();
        Holder<Biome> biome = level.getBiome(pos);
        StructureManager structureManager = null;
        LuckZone best = null;
        Comparator<Map.Entry<ResourceKey<LuckZone>, LuckZone>> order = Comparator
                .comparingInt((Map.Entry<ResourceKey<LuckZone>, LuckZone> e) -> e.getValue().specificity())
                .thenComparingInt(e -> e.getValue().priority())
                .thenComparing(e -> e.getKey().identifier().toString(), Comparator.reverseOrder());
        Map.Entry<ResourceKey<LuckZone>, LuckZone> winner = null;
        for (Map.Entry<ResourceKey<LuckZone>, LuckZone> entry : zones.entrySet()) {
            LuckZone zone = entry.getValue();
            if (zone.dimension.isPresent() && zone.dimension.get() != dimension) {
                continue;
            }
            if (zone.biomes.isPresent() && !zone.biomes.get().contains(biome)) {
                continue;
            }
            if (zone.structures.isPresent()) {
                if (structureManager == null) {
                    structureManager = structures(level);
                }
                HolderSet<Structure> wanted = zone.structures.get();
                Registry<Structure> structureRegistry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
                StructureManager manager = structureManager;
                boolean inside = startsReaching(level, ChunkPos.containing(pos),
                        s -> wanted.contains(structureRegistry.wrapAsHolder(s)))
                        .stream().anyMatch(start -> manager.structureHasPieceAt(pos, start));
                if (!inside) {
                    continue;
                }
            }
            if (winner == null || order.compare(entry, winner) > 0) {
                winner = entry;
            }
        }
        best = winner == null ? FALLBACK : winner.getValue();
        return best;
    }

    /** A zone's name (its file name), or "none" for the fallback. */
    public static String name(WorldGenLevel level, LuckZone zone) {
        net.minecraft.resources.Identifier id = level.registryAccess().lookupOrThrow(REGISTRY).getKey(zone);
        return id == null ? "none" : id.getNamespace().equals(RNGambling.MOD_ID) ? id.getPath() : id.toString();
    }

    /**
     * The starts of the structures reaching into a chunk, as {@link StructureManager#startsForStructure}
     * finds them, but safe while the world is being generated: a start is read from the chunk it
     * began in, and a big structure can begin further away than generation may read (a server
     * crashed on one 9 chunks off). Starts beyond that reach are left out.
     */
    public static List<StructureStart> startsReaching(WorldGenLevel level, ChunkPos chunk, Predicate<Structure> which) {
        if (!(level instanceof WorldGenRegion)) {
            return structures(level).startsForStructure(chunk, which);
        }
        List<StructureStart> starts = new ArrayList<>();
        for (Map.Entry<Structure, LongSet> entry : level.getChunk(chunk.x(), chunk.z()).getAllReferences().entrySet()) {
            Structure structure = entry.getKey();
            if (!which.test(structure)) {
                continue;
            }
            for (long reference : entry.getValue()) {
                int x = ChunkPos.getX(reference);
                int z = ChunkPos.getZ(reference);
                if (!level.hasChunk(x, z)) {
                    continue;
                }
                StructureStart start = level.getChunk(x, z, ChunkStatus.STRUCTURE_STARTS).getStartForStructure(structure);
                if (start != null && start.isValid()) {
                    starts.add(start);
                }
            }
        }
        return starts;
    }

    /** The structure look-up for a level being generated (or, in tests, a finished level). */
    public static StructureManager structures(WorldGenLevel level) {
        ServerLevel server = level.getLevel();
        return level instanceof WorldGenRegion region ? server.structureManager().forWorldGenRegion(region) : server.structureManager();
    }
}
