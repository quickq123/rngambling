package rngambling.outcome;

/**
 * One thing a lucky block can do when broken.
 *
 * @param id      names the outcome; its message is the translation key {@code outcome.rngambling.<id>}
 * @param weight  how often it happens at 0 luck, relative to the others
 * @param rating  how good it is, from -10 (awful) to +10 (wonderful); luck tilts the odds by this
 * @param action  what it does
 * @param sandbox whether it changes or could grief the world (explosions, fire, placed or moved
 *                blocks), so it's played out in the Sandbox dimension instead (see {@link Sandbox})
 */
public record Outcome(String id, int weight, int rating, Action action, boolean sandbox) {
    public Outcome {
        if (weight <= 0 || rating < -10 || rating > 10) {
            throw new IllegalArgumentException("bad outcome " + id);
        }
    }

    public Outcome(String id, int weight, int rating, Action action) {
        this(id, weight, rating, action, false);
    }

    /** This outcome, played out in the Sandbox. */
    public Outcome inSandbox() {
        return new Outcome(id, weight, rating, action, true);
    }

    @FunctionalInterface
    public interface Action {
        void run(OutcomeContext context);
    }
}
