package rngambling.casino;

/**
 * Higher or Lower: a card is turned up; guess whether the next is higher or the same, or lower or
 * the same (aces low). Each right guess grows the pot; a wrong one loses it. Cash out any time.
 * Cards come from an endless deck, as at blackjack.
 *
 * <p>The pot is always a whole number of tokens: the bet times the fair odds of every guess so
 * far, less a 3% house cut, rounded down, and never less than it was. Rounding down only ever
 * favours the house, so however a player plays, about 97% comes back at most (less at small bets;
 * the {@code casino_games} game test simulates it). Pots stop at {@link #TABLE_LIMIT}.
 */
public final class HigherLower {
    public static final double HOUSE = 0.97;
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

    /** The pot after a right guess: from the bet and the fair odds so far (1 / the chance of every guess, multiplied). */
    public static int pot(int bet, double odds, int before) {
        int fair = (int) Math.min(TABLE_LIMIT, Math.floor(bet * odds * HOUSE + 1e-9));
        return Math.max(before, fair);
    }
}
