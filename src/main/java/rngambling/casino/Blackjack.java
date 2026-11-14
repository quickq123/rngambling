package rngambling.casino;

import java.util.List;
import net.minecraft.util.RandomSource;

/**
 * Blackjack against the dealer. Cards are drawn from an endlessly shuffled deck (each card is
 * any of the 52, every time). The rules lean slightly to the house, as in a real casino: the
 * dealer hits a soft 17, there's no splitting, and doubling is only on the first two cards. A
 * blackjack pays 3 to 2. Played perfectly ({@link #basicStrategy}) the house keeps about 1% of
 * what is bet (the {@code blackjack} game test simulates it); played carelessly, more.
 *
 * <p>A card is a number 0-51: its rank is {@code card % 13} (0 ace, 1-9 two to ten, 10-12 jack,
 * queen, king) and its suit {@code card / 13} (hearts, diamonds, clubs, spades).
 */
public final class Blackjack {
    /** Bets a player can choose, in tokens: even, so a 3 to 2 blackjack pays whole tokens. */

    private Blackjack() {
    }

    public static int draw(RandomSource random) {
        return random.nextInt(52);
    }

    public static int rank(int card) {
        return card % 13;
    }

    public static int suit(int card) {
        return card / 13;
    }

    /** A card's value, counting an ace as 1. */
    public static int value(int card) {
        return Math.min(rank(card) + 1, 10);
    }

    /** The best total of a hand: aces count 11 where that doesn't bust it. */
    public static int total(List<Integer> hand) {
        int total = 0;
        boolean ace = false;
        for (int card : hand) {
            total += value(card);
            ace |= rank(card) == 0;
        }
        return ace && total + 10 <= 21 ? total + 10 : total;
    }

    /** Whether the hand's total counts an ace as 11. */
    public static boolean soft(List<Integer> hand) {
        int hard = 0;
        boolean ace = false;
        for (int card : hand) {
            hard += value(card);
            ace |= rank(card) == 0;
        }
        return ace && hard + 10 <= 21;
    }

    public static boolean blackjack(List<Integer> hand) {
        return hand.size() == 2 && total(hand) == 21;
    }

    /** Whether the dealer takes another card: below 17, or on a soft 17. */
    public static boolean dealerHits(List<Integer> hand) {
        int total = total(hand);
        return total < 17 || (total == 17 && soft(hand));
    }

    public enum Outcome { NONE, WIN, LOSE, PUSH, BLACKJACK, BUST, DEALER_BUST }

    /** How a finished hand ends (neither side has a blackjack; the player hasn't bust). */
    public static Outcome compare(List<Integer> player, List<Integer> dealer) {
        int p = total(player);
        int d = total(dealer);
        if (d > 21) {
            return Outcome.DEALER_BUST;
        }
        return p > d ? Outcome.WIN : p < d ? Outcome.LOSE : Outcome.PUSH;
    }

    /** What an outcome returns, from the stake (the bet, doubled if the player doubled), the stake itself included. */
    public static int returns(Outcome outcome, int stake) {
        return switch (outcome) {
            case WIN, DEALER_BUST -> stake * 2;
            case BLACKJACK -> stake + stake * 3 / 2;
            case PUSH -> stake;
            default -> 0;
        };
    }

    public enum Move { HIT, STAND, DOUBLE }

    /** The usual best play for these rules (no splitting), from the player's hand and the dealer's face-up card. */
    public static Move basicStrategy(List<Integer> hand, int dealerUp) {
        int total = total(hand);
        int up = rank(dealerUp) == 0 ? 11 : value(dealerUp);
        boolean canDouble = hand.size() == 2;
        if (soft(hand)) {
            if (total >= 19) {
                return Move.STAND;
            }
            if (total == 18) {
                if (up >= 2 && up <= 6) {
                    return canDouble ? Move.DOUBLE : Move.STAND;
                }
                return up <= 8 ? Move.STAND : Move.HIT;
            }
            int lowestDouble = total == 17 ? 3 : total >= 15 ? 4 : 5;
            return canDouble && up >= lowestDouble && up <= 6 ? Move.DOUBLE : Move.HIT;
        }
        if (total >= 17) {
            return Move.STAND;
        }
        if (total >= 13) {
            return up <= 6 ? Move.STAND : Move.HIT;
        }
        if (total == 12) {
            return up >= 4 && up <= 6 ? Move.STAND : Move.HIT;
        }
        if (total == 11) {
            return canDouble ? Move.DOUBLE : Move.HIT;
        }
        if (total == 10) {
            return canDouble && up <= 9 ? Move.DOUBLE : Move.HIT;
        }
        if (total == 9) {
            return canDouble && up >= 3 && up <= 6 ? Move.DOUBLE : Move.HIT;
        }
        return Move.HIT;
    }

    /**
     * Plays one whole hand by {@link #basicStrategy} with a bet of 1 and returns the player's net
     * result: -2 to +2 (a double risks a second bet), +1.5 for a blackjack. The house edge is how
     * much this loses on average, per starting bet.
     */
    public static double playOneHand(RandomSource random) {
        List<Integer> player = new java.util.ArrayList<>(List.of(draw(random), draw(random)));
        List<Integer> dealer = new java.util.ArrayList<>(List.of(draw(random), draw(random)));
        if (blackjack(player) || blackjack(dealer)) {
            return blackjack(player) && blackjack(dealer) ? 0 : blackjack(player) ? 1.5 : -1;
        }
        double stake = 1;
        while (true) {
            Move move = basicStrategy(player, dealer.getFirst());
            if (move == Move.STAND) {
                break;
            }
            player.add(draw(random));
            if (move == Move.DOUBLE) {
                stake = 2;
                break;
            }
            if (total(player) > 21) {
                return -stake;
            }
        }
        if (total(player) > 21) {
            return -stake;
        }
        while (dealerHits(dealer)) {
            dealer.add(draw(random));
        }
        return switch (compare(player, dealer)) {
            case WIN, DEALER_BUST -> stake;
            case PUSH -> 0;
            default -> -stake;
        };
    }
}
