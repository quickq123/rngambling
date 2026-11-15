package rngambling.casino;

import java.util.List;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import net.minecraft.util.RandomSource;

/**
 * How a player's luck (Luck minus Bad Luck, -2 to +2, see PlayerLuck) tilts the casino. Without
 * luck every game is fair: over many bets it pays back what is bet. Luck I makes it pay back about
 * 110%, Luck II about 125% ({@link #EDGE}); Bad Luck I about 90%, Bad Luck II about 75%. Every
 * game gets there in a way a player can see:
 * <ul>
 * <li><b>Slots and roulette:</b> with Luck, a losing spin is sometimes spun again; with Bad Luck,
 * a winning spin is. The second spin stands. How often is worked out exactly from the game's
 * odds ({@link Odds}), so it comes to the same edge on every bet.
 * <li><b>Blackjack:</b> each card has a chance ({@link #LUCKY_CARD}, {@link #UNLUCKY_CARD}) of
 * being drawn twice, keeping the one better for the player (worse, with Bad Luck).
 * <li><b>Higher or Lower and Creeper Field:</b> every pot is bigger (smaller) by the edge.
 * </ul>
 * The {@code casino_luck} game test checks each game comes out at these figures.
 */
public final class CasinoLuck {
    /** By luck level 0, 1, 2: how much more (Luck) or less (Bad Luck) a game pays back, of what is bet. */
    public static final double[] EDGE = {0, 0.10, 0.25};
    /** By luck level: the chance a blackjack card is drawn twice and the better kept (found by simulation). */
    public static final double[] LUCKY_CARD = {0, 0.135, 0.335};
    /** By Bad Luck level: the chance a blackjack card is drawn twice and the worse kept. */
    public static final double[] UNLUCKY_CARD = {0, 0.14, 0.35};

    private CasinoLuck() {
    }

    private static int strength(int level) {
        return Math.min(Math.abs(level), EDGE.length - 1);
    }

    /** What a pot is multiplied by at this luck level: 1.25 at Luck II, 0.75 at Bad Luck II. */
    public static double potFactor(int level) {
        return 1 + Math.signum(level) * EDGE[strength(level)];
    }

    /**
     * A fair game's odds, per token bet: the chance of losing everything, the chance of a result
     * that pays back more than the bet, and what those results pay back, weighted by their chance
     * (results that just hand the bet back are neither).
     */
    public record Odds(double lose, double win, double winReturns) {
        /** The odds of a game whose results come up with these chances and pay back these amounts, times the bet. */
        public static <T> Odds of(Iterable<T> results, ToDoubleFunction<T> chance, ToDoubleFunction<T> returns) {
            double lose = 0, win = 0, winReturns = 0;
            for (T result : results) {
                double c = chance.applyAsDouble(result), r = returns.applyAsDouble(result);
                if (r == 0) {
                    lose += c;
                } else if (r > 1) {
                    win += c;
                    winReturns += c * r;
                }
            }
            return new Odds(lose, win, winReturns);
        }

        /**
         * The chance of spinning again, at this luck level. For a game paying back 1: re-spinning a
         * loss with chance p adds p x lose; re-spinning a win with chance q takes q x (winReturns - win).
         */
        public double respin(int level) {
            double edge = EDGE[strength(level)];
            double scale = level > 0 ? lose : winReturns - win;
            return level == 0 || scale <= 0 ? 0 : Math.min(1, edge / scale);
        }
    }

    /** A spin: drawn once, and with luck maybe again (see the class notes). {@code returns} is what a result pays back, times the bet. */
    public static <T> T spin(RandomSource random, int level, Odds odds, Supplier<T> roll, ToDoubleFunction<T> returns) {
        T result = roll.get();
        double paid = returns.applyAsDouble(result);
        boolean again = level > 0 ? paid == 0 : level < 0 && paid > 1;
        if (again && random.nextDouble() < odds.respin(level)) {
            result = roll.get();
        }
        return result;
    }

    /** A card, with luck maybe drawn twice; {@code forPlayer} scores a card, higher being better for the player. */
    public static int card(RandomSource random, int level, ToIntFunction<Integer> forPlayer) {
        int card = Blackjack.draw(random);
        double chance = level > 0 ? LUCKY_CARD[strength(level)] : UNLUCKY_CARD[strength(level)];
        if (level != 0 && random.nextDouble() < chance) {
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
