package rngambling.outcome;

import net.minecraft.ChatFormatting;

/**
 * The effect catalog's tiers (docs/effect-catalog.csv). Each has a home luck, the middle of its
 * effects' luck bands: the closer a block's luck is to a tier's home, the likelier that tier
 * (see {@link Outcomes#tierWeight}). Wildcards are drawn separately.
 */
public enum Tier {
    CATASTROPHIC(-85, -1, ChatFormatting.DARK_RED),
    VERY_BAD(-52, -1, ChatFormatting.RED),
    BAD(-22, -1, ChatFormatting.GOLD),
    NEUTRAL(0, 0, ChatFormatting.YELLOW),
    GOOD(25, 1, ChatFormatting.GREEN),
    VERY_GOOD(60, 1, ChatFormatting.AQUA),
    LEGENDARY(88, 1, ChatFormatting.LIGHT_PURPLE),
    WILDCARD(0, 0, ChatFormatting.WHITE);

    /** The tier's home luck. */
    public final int home;
    /** -1 bad, 0 neither, +1 good. */
    public final int sign;
    public final ChatFormatting colour;

    Tier(int home, int sign, ChatFormatting colour) {
        this.home = home;
        this.sign = sign;
        this.colour = colour;
    }
}
