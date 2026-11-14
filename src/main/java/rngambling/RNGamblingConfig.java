package rngambling;

import net.neoforged.neoforge.common.ModConfigSpec;

/** config/rngambling-common.toml: settings that apply to every world. */
public final class RNGamblingConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue SURFACE_RARITY;
    public static final ModConfigSpec.IntValue CAVE_RARITY;

    static {
        BUILDER.comment("Disguised lucky blocks generated in new chunks of the Overworld.").push("world_generation");
        SURFACE_RARITY = BUILDER
                .comment("One disguised lucky block on the surface in every this many chunks, on average. 0 turns them off.")
                .defineInRange("surface_one_in_chunks", 8, 0, 10_000);
        CAVE_RARITY = BUILDER
                .comment("One disguised lucky block in a cave (floor, ceiling or wall) in every this many chunks, on average. 0 turns them off.")
                .defineInRange("cave_one_in_chunks", 8, 0, 10_000);
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private RNGamblingConfig() {
    }
}
