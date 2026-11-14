package rngambling.casino;

import net.minecraft.util.RandomSource;

/**
 * European roulette: one zero and the numbers 1 to 36. Every bet pays at the usual casino odds,
 * which are set as if there were no zero, so the zero is the house's edge: every bet returns
 * 36/37 (97.3%) of what is bet over time, a 2.7% house edge, as in a real casino.
 */
public final class Roulette {
    /** The numbers in the order they sit around a European wheel. */
    public static final int[] WHEEL = {0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24, 16, 33, 1,
            20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26};
    private static final java.util.Set<Integer> RED = java.util.Set.of(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);

    /** What a bet can be on, and what a winning bet returns, times the bet (the bet itself included). */
    public enum Bet {
        RED(2), BLACK(2), GREEN(36), ODD(2), EVEN(2), LOW(2), HIGH(2), DOZEN_1(3), DOZEN_2(3), DOZEN_3(3), NUMBER(36);

        public final int returns;

        Bet(int returns) {
            this.returns = returns;
        }
    }

    private Roulette() {
    }

    public static boolean isRed(int number) {
        return RED.contains(number);
    }

    public static boolean isBlack(int number) {
        return number != 0 && !RED.contains(number);
    }

    public static int spin(RandomSource random) {
        return random.nextInt(37);
    }

    /** Whether a bet wins on this number. {@code chosen} is the number bet on, for {@link Bet#NUMBER}. */
    public static boolean wins(Bet bet, int chosen, int number) {
        return switch (bet) {
            case RED -> isRed(number);
            case BLACK -> isBlack(number);
            case GREEN -> number == 0;
            case ODD -> number != 0 && number % 2 == 1;
            case EVEN -> number != 0 && number % 2 == 0;
            case LOW -> number >= 1 && number <= 18;
            case HIGH -> number >= 19;
            case DOZEN_1 -> number >= 1 && number <= 12;
            case DOZEN_2 -> number >= 13 && number <= 24;
            case DOZEN_3 -> number >= 25;
            case NUMBER -> number == chosen;
        };
    }

    /** What the bet returns on this number, times the bet (0 if it loses). */
    public static int returns(Bet bet, int chosen, int number) {
        return wins(bet, chosen, number) ? bet.returns : 0;
    }

    /** A bet's share of tokens returned over many spins, worked out exactly. */
    public static double returnToPlayer(Bet bet, int chosen) {
        double total = 0;
        for (int number = 0; number <= 36; number++) {
            total += returns(bet, chosen, number);
        }
        return total / 37.0;
    }
}
