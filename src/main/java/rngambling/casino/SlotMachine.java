package rngambling.casino;

import net.minecraft.util.RandomSource;

/**
 * The slot machine's rules: three reels, each stopping on a symbol drawn by weight, and a pay
 * table. It's a fair game: over many spins it pays back what is bet ({@link #returnToPlayer()},
 * checked by a game test). About 35% of spins win, and another 7% hand the bet back (two bells),
 * so losing streaks stay short; three sevens is the big one.
 */
public final class SlotMachine {
    /**
     * Symbols, with how often each comes up on a reel (out of {@link #TOTAL_WEIGHT}), what three of
     * them pay and what exactly two of them pay (anywhere on the line), times the bet.
     */
    public enum Symbol {
        CHERRY(8, 3, 2),
        /** Two lemons are sour: they pay nothing. */
        LEMON(6, 5, 0),
        /** Two bells hand the bet back. */
        BELL(4, 10, 1),
        BAR(3, 25, 2),
        SEVEN(2, 80, 3),
        /** Three lucky blocks also win a Lucky Block. */
        LUCKY(1, 15, 3);

        public final int weight;
        public final int threePays;
        public final int twoPays;

        Symbol(int weight, int threePays, int twoPays) {
            this.weight = weight;
            this.threePays = threePays;
            this.twoPays = twoPays;
        }
    }

    public static final int TOTAL_WEIGHT;
    /** The slot machine's odds, for luck's re-spins (see {@link CasinoLuck}). */
    public static final CasinoLuck.Odds ODDS;

    static {
        int total = 0;
        for (Symbol symbol : Symbol.values()) {
            total += symbol.weight;
        }
        TOTAL_WEIGHT = total;
        ODDS = CasinoLuck.Odds.of(allSpins(), SlotMachine::chance, SlotMachine::pays);
    }

    private SlotMachine() {
    }

    /** One reel's stop. */
    public static Symbol spinReel(RandomSource random) {
        int roll = random.nextInt(TOTAL_WEIGHT);
        for (Symbol symbol : Symbol.values()) {
            roll -= symbol.weight;
            if (roll < 0) {
                return symbol;
            }
        }
        return Symbol.CHERRY;
    }

    public static Symbol[] spin(RandomSource random) {
        return new Symbol[] {spinReel(random), spinReel(random), spinReel(random)};
    }

    /** What a spin pays, times the bet (0 for nothing, 1 for the bet back). */
    public static int pays(Symbol[] reels) {
        if (reels[0] == reels[1] && reels[1] == reels[2]) {
            return reels[0].threePays;
        }
        Symbol pair = reels[0] == reels[1] || reels[0] == reels[2] ? reels[0] : reels[1] == reels[2] ? reels[1] : null;
        return pair == null ? 0 : pair.twoPays;
    }

    public static boolean winsLuckyBlock(Symbol[] reels) {
        return reels[0] == Symbol.LUCKY && reels[1] == Symbol.LUCKY && reels[2] == Symbol.LUCKY;
    }

    /** Every way the reels can stop. */
    public static java.util.List<Symbol[]> allSpins() {
        java.util.List<Symbol[]> all = new java.util.ArrayList<>();
        for (Symbol a : Symbol.values()) {
            for (Symbol b : Symbol.values()) {
                for (Symbol c : Symbol.values()) {
                    all.add(new Symbol[] {a, b, c});
                }
            }
        }
        return all;
    }

    /** How likely the reels are to stop just so. */
    public static double chance(Symbol[] reels) {
        return reels[0].weight * reels[1].weight * reels[2].weight / Math.pow(TOTAL_WEIGHT, 3);
    }

    /** The share of tokens bet that comes back over many spins, worked out exactly from the weights. */
    public static double returnToPlayer() {
        double total = 0;
        for (Symbol[] reels : allSpins()) {
            total += chance(reels) * pays(reels);
        }
        return total;
    }
}
