package rngambling.luck;

/**
 * Luck runs from {@link #MIN} to {@link #MAX}. Negative luck leans a lucky block towards bad
 * outcomes, positive towards good ones, and 0 is the plain mix. Bad outcomes never vanish
 * entirely, even at +100.
 */
public final class Luck {
    public static final int MIN = -100;
    public static final int MAX = 100;

    /**
     * How strongly luck tilts the odds. An outcome's weight is multiplied by
     * {@code exp(TILT * luck/100 * rating/10)}, so at +100 luck the best outcomes are about
     * five times as likely as at 0 and the worst about a fifth as likely.
     */
    public static final double TILT = 1.6;

    private Luck() {
    }

    public static int clamp(int luck) {
        return Math.max(MIN, Math.min(MAX, luck));
    }

    /** Luck as a colour: red at -100, white at 0, green at +100, opaque ARGB. */
    public static int colour(int luck) {
        float t = Math.abs(clamp(luck)) / 100f;
        int fade = Math.round(255 * (1 - t * 0.75f));
        return luck >= 0 ? 0xFF000000 | (fade << 16) | (0xFF << 8) | fade : 0xFF000000 | (0xFF << 16) | (fade << 8) | fade;
    }

    /** The factor an outcome's base weight is multiplied by at this luck. */
    public static double weightFactor(int luck, int rating) {
        return Math.exp(TILT * (clamp(luck) / 100.0) * (rating / 10.0));
    }
}
