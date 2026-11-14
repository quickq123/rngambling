package rngambling.casino;

/**
 * How much a player can bet at a game, chosen with four buttons: down 10, down a step, up a step,
 * up 10 (menu buttons {@link #DOWN_BIG} to {@link #UP_BIG}). Blackjack steps by 2, since it pays
 * 3:2 and a bet must stay even to be paid in whole tokens.
 */
public record Bets(int min, int max, int step, int start) {
    public static final Bets SLOTS = new Bets(1, 100, 1, 5);
    public static final Bets ROULETTE = new Bets(1, 100, 1, 5);
    public static final Bets BLACKJACK = new Bets(2, 100, 2, 10);

    public static final int DOWN_BIG = 0, DOWN = 1, UP = 2, UP_BIG = 3;
    public static final int BIG_STEP = 10;

    public static boolean isButton(int id) {
        return id >= DOWN_BIG && id <= UP_BIG;
    }

    /** A bet within the limits, on a step. */
    public int clamp(int bet) {
        int stepped = Math.round(bet / (float) step) * step;
        return Math.max(min, Math.min(max, stepped));
    }

    /** The bet after pressing button {@code id}. */
    public int adjust(int bet, int id) {
        int delta = switch (id) {
            case DOWN_BIG -> -BIG_STEP;
            case DOWN -> -step;
            case UP -> step;
            case UP_BIG -> BIG_STEP;
            default -> 0;
        };
        return clamp(clamp(bet) + delta);
    }
}
