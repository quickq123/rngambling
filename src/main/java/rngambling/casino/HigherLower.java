package rngambling.casino;

/**
 * Higher or Lower: a card is turned up; guess whether the next is higher or the same, or lower or
 * the same (aces low). Each right guess grows the pot; a wrong one loses it. Cash out any time.
 * Cards come from an endless deck, as at blackjack.
 *
 * <p>The pot is always a whole number of tokens: the bet times the fair odds of every guess so
 * far, rounded to the nearest token, and never less than it was. It's a fair game: however a
 * player plays, what comes back is about what was bet (the {@code casino_games} game test
 * simulates it). Luck makes every pot bigger, Bad Luck smaller (see {@link CasinoLuck#potFactor}),
 * though not the pot of guesses that can't lose. Pots stop at {@link #TABLE_LIMIT}.
 */
public final class HigherLower {
    /** The most a pot can reach: it's paid out at once when a guess would pass it. */
    public static final int TABLE_LIMIT = 10_000;

    public enum Guess { HIGHER, LOWER }

    private HigherLower() {
    }

    /** The chance a guess is right on this card (ties count as right). */
    public static double chance(Guess guess, int card) {
        int rank = Blackjack.rank(card);
        return guess == Guess.HIGHER ? (13 - rank) / 13.0 : (rank + 1) / 13.0;
    }

    public static boolean wins(Guess guess, int card, int next) {
        int a = Blackjack.rank(card), b = Blackjack.rank(next);
        return guess == Guess.HIGHER ? b >= a : b <= a;
    }

    /**
     * The pot after a right guess: from the bet, the fair odds so far (1 / the chance of every
     * guess, multiplied) and the player's luck level.
     */
    public static int pot(int bet, double odds, int before, int luck) {
        double factor = odds > 1 + 1e-9 ? CasinoLuck.potFactor(luck) : 1;
        int pot = (int) Math.min(TABLE_LIMIT, Math.round(bet * odds * factor));
        return Math.max(before, pot);
    }
}
