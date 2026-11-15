package rngambling;

import net.neoforged.neoforge.common.ModConfigSpec;

/** config/rngambling-common.toml: settings that apply to every world. */
public final class RNGamblingConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue SURFACE_RARITY;
    public static final ModConfigSpec.IntValue CAVE_RARITY;
    public static final ModConfigSpec.BooleanValue SANDBOX_ENABLED;
    public static final ModConfigSpec.BooleanValue GLOBAL_EFFECTS_IN_MULTIPLAYER;
    public static final ModConfigSpec.DoubleValue MAX_EXPLOSION;
    public static final ModConfigSpec.IntValue MAX_BONUS_HEARTS;
    public static final ModConfigSpec.IntValue VILLAGE_CASINO_PERCENT;
    public static final ModConfigSpec.BooleanValue CASINOS_IN_MODDED_VILLAGES;

    static {
        BUILDER.comment("Lucky block outcomes.").push("outcomes");
        SANDBOX_ENABLED = BUILDER
                .comment("true: outcomes that would change or grief the world (explosions, fire, placed or moved blocks) are played out",
                        "in the Sandbox dimension (an endless bedrock plane under the End sky), and the player is brought back afterwards.",
                        "false: they happen where the lucky block was broken.")
                .define("sandbox_enabled", true);
        GLOBAL_EFFECTS_IN_MULTIPLAYER = BUILDER
                .comment("Effects that change the whole server (Nightfall, Weather Roulette, Time Skip) happen in single player.",
                        "true: in multiplayer too. false: they're left out of the draw in multiplayer.")
                .define("global_effects_in_multiplayer", false);
        MAX_EXPLOSION = BUILDER
                .comment("The strongest explosion an effect can make (TNT is 4, a charged creeper 6).")
                .defineInRange("max_explosion_power", 6.0, 0.0, 12.0);
        MAX_BONUS_HEARTS = BUILDER
                .comment("The most extra hearts Heart Container can give a player, in all.")
                .defineInRange("max_bonus_hearts", 10, 0, 100);
        BUILDER.pop();
        BUILDER.comment("Disguised lucky blocks generated in new chunks of the Overworld.").push("world_generation");
        SURFACE_RARITY = BUILDER
                .comment("One disguised lucky block on the surface in every this many chunks, on average. 0 turns them off.")
                .defineInRange("surface_one_in_chunks", 8, 0, 10_000);
        CAVE_RARITY = BUILDER
                .comment("One disguised lucky block in a cave (floor, ceiling or wall) in every this many chunks, on average. 0 turns them off.")
                .defineInRange("cave_one_in_chunks", 8, 0, 10_000);
        BUILDER.pop();
        BUILDER.comment("Casinos in villages (a house with a slot machine, roulette or blackjack table, where a villager becomes a Casino Dealer).",
                "Like all world generation, these only reach villages generated after a change.").push("villages");
        VILLAGE_CASINO_PERCENT = BUILDER
                .comment("How often a village's house is a casino, in percent of the houses it picks from. 6 puts a casino in about half",
                        "of vanilla villages; 15 in nearly all of them, often two. 0 turns them off.")
                .defineInRange("village_casino_percent", 6, 0, 50);
        CASINOS_IN_MODDED_VILLAGES = BUILDER
                .comment("true: villages from other mods (any <mod>:village/<type>/house or houses pool, such as ChoiceTheorem's Overhauled",
                        "Village) get casinos too, built in the nearest vanilla style. false: vanilla's five village types only.")
                .define("casinos_in_modded_villages", true);
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private RNGamblingConfig() {
    }
}
