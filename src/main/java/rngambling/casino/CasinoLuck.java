package rngambling.casino;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import net.minecraft.util.RandomSource;

/**
 * How a player's luck (Luck minus Bad Luck, -2 to +2, see PlayerLuck) tilts the casino. Results
 * stay fair draws; luck only sometimes gives a second draw:
 * <ul>
 * <li><b>Slots and roulette:</b> with Luck, a losing spin is spun again with chance
 * {@link #RESPIN} (15% at I, 33% at II); with Bad Luck, a winning spin is. The second spin stands.
 * <li><b>Blackjack:</b> each card has a chance ({@link #CARD}: 7% at I, 20% at II) of being drawn
 * twice, keeping the one better for the player (worse, with Bad Luck): for the player's hand, the
 * higher total that doesn't bust; for the dealer's, a bust, or a total that must draw again.
 * </ul>
 * What comes back, of what is bet (normally 92% slots, 97% roulette, 99% blackjack played well):
 * Luck I about 102% slots, 105% red/black, 104% blackjack (a slight edge for the player); Luck II
 * about 115%, 114%, 113% (a good edge). Bad Luck the other way: 81% / 90% / 92% at I, 68% / 81% /
 * 84% at II. Long-shot roulette bets gain (and lose) more. Checked by the casino_luck game test.
 */
public final class CasinoLuck {
    /** By luck level 0, 1, 2: the chance a losing (or, with Bad Luck, winning) spin is spun again. */
    public static final double[] RESPIN = {0, 0.15, 0.33};
    /** By luck level 0, 1, 2: the chance a blackjack card is drawn twice and the better (or worse) kept. */
    public static final double[] CARD = {0, 0.07, 0.20};

    private CasinoLuck() {
    }

    private static int strength(int level) {
        return Math.min(Math.abs(level), RESPIN.length - 1);
    }

    /** A spin: drawn once, and with luck maybe again (see the class notes). */
    public static <T> T spin(RandomSource random, int level, Supplier<T> roll, Predicate<T> wins) {
        T result = roll.get();
        if (level != 0 && wins.test(result) == (level < 0) && random.nextDouble() < RESPIN[strength(level)]) {
            result = roll.get();
        }
        return result;
    }

    /** A card, with luck maybe drawn twice; {@code forPlayer} scores a card, higher being better for the player. */
    public static int card(RandomSource random, int level, ToIntFunction<Integer> forPlayer) {
        int card = Blackjack.draw(random);
        if (level != 0 && random.nextDouble() < CARD[strength(level)]) {
            int other = Blackjack.draw(random);
            int mine = forPlayer.applyAsInt(card), theirs = forPlayer.applyAsInt(other);
            if (level > 0 ? theirs > mine : theirs < mine) {
                card = other;
            }
        }
        return card;
    }

    /** A card for the player's hand: higher totals are better, a bust worst. */
    public static int playerCard(RandomSource random, int level, List<Integer> hand) {
        return card(random, level, c -> score(hand, c));
    }

    /** A card for the dealer's hand: a bust is best for the player, then a total the dealer must draw on. */
    public static int dealerCard(RandomSource random, int level, List<Integer> hand) {
        return card(random, level, c -> {
            List<Integer> with = new java.util.ArrayList<>(hand);
            with.add(c);
            int total = Blackjack.total(with);
            return total > 21 ? 100 : Blackjack.dealerHits(with) ? 50 : 30 - total;
        });
    }

    private static int score(List<Integer> hand, int card) {
        List<Integer> with = new java.util.ArrayList<>(hand);
        with.add(card);
        int total = Blackjack.total(with);
        return total > 21 ? -1 : total;
    }
}
