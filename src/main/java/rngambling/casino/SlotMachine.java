package rngambling.casino;

import net.minecraft.util.RandomSource;

/**
 * The slot machine's rules: three reels, each stopping on a symbol drawn by weight, and a pay
 * table. Like a real casino it is weighted slightly against the player: over many spins it pays
 * back about 92% of the tokens bet (see {@link #returnToPlayer()}, checked by a game test).
 */
public final class SlotMachine {
    /** Bets a player can choose, in tokens. */
    public static final int[] BETS = {1, 5, 10};

    /** Symbols, with how often each comes up on a reel (out of {@link #TOTAL_WEIGHT}) and what three of them pay, times the bet. */
    public enum Symbol {
        CHERRY(7, 8),
        LEMON(6, 10),
        BELL(4, 18),
        BAR(3, 30),
        SEVEN(2, 80),
        /** Three lucky blocks also win a lucky block, {@link #PRIZE_LUCK} luck. */
        LUCKY(2, 20);

        public final int weight;
        public final int threePays;

        Symbol(int weight, int threePays) {
            this.weight = weight;
            this.threePays = threePays;
        }
    }

    /** Any two cherries (and no three of a kind) pay this, times the bet. */
    public static final int TWO_CHERRIES_PAY = 2;
    /** The luck of the lucky block won with three lucky blocks. */
    public static final int PRIZE_LUCK = 25;

    public static final int TOTAL_WEIGHT;

    static {
        int total = 0;
        for (Symbol symbol : Symbol.values()) {
            total += symbol.weight;
        }
        TOTAL_WEIGHT = total;
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

    /** What a spin pays, times the bet (0 for nothing). */
    public static int pays(Symbol[] reels) {
        if (reels[0] == reels[1] && reels[1] == reels[2]) {
            return reels[0].threePays;
        }
        int cherries = 0;
        for (Symbol symbol : reels) {
            cherries += symbol == Symbol.CHERRY ? 1 : 0;
        }
        return cherries >= 2 ? TWO_CHERRIES_PAY : 0;
    }

    public static boolean winsLuckyBlock(Symbol[] reels) {
        return reels[0] == Symbol.LUCKY && reels[1] == Symbol.LUCKY && reels[2] == Symbol.LUCKY;
    }

    /** The share of tokens bet that comes back over many spins, worked out exactly from the weights. */
    public static double returnToPlayer() {
        double total = 0;
        double cube = Math.pow(TOTAL_WEIGHT, 3);
        for (Symbol a : Symbol.values()) {
            for (Symbol b : Symbol.values()) {
                for (Symbol c : Symbol.values()) {
                    total += a.weight * b.weight * c.weight / cube * pays(new Symbol[] {a, b, c});
                }
            }
        }
        return total;
    }
}
