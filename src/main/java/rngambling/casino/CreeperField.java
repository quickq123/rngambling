package rngambling.casino;

/**
 * Creeper Field: a 5 x 5 field of grass hides some creepers (the player picks how many, 1 to
 * {@link #MAX_CREEPERS}). Dig a block: safe, and the pot grows; a creeper, and it's lost. Cash out
 * any time. Where the creepers are is decided when the game starts.
 *
 * <p>The pot is always a whole number of tokens: the bet times the fair odds of digging that many
 * safe blocks, rounded to the nearest token, and never less than it was. More creepers, bigger
 * steps. As at Higher or Lower it's a fair game, and luck makes pots bigger or smaller (the
 * {@code casino_games} game test simulates it). Pots stop at {@link HigherLower#TABLE_LIMIT}.
 */
public final class CreeperField {
    public static final int SIZE = 5, BLOCKS = SIZE * SIZE;
    public static final int MIN_CREEPERS = 1, MAX_CREEPERS = 20, START_CREEPERS = 3;

    private CreeperField() {
    }

    /** The fair odds of digging {@code dug} safe blocks in a row with {@code creepers} hidden: 1 / the chance. */
    public static double odds(int creepers, int dug) {
        double odds = 1;
        for (int i = 0; i < dug; i++) {
            odds *= (BLOCKS - i) / (double) (BLOCKS - creepers - i);
        }
        return odds;
    }

    /** The pot after {@code dug} safe blocks. */
    public static int pot(int bet, int creepers, int dug, int before, int luck) {
        return HigherLower.pot(bet, odds(creepers, dug), before, luck);
    }
}
