package rngambling.outcome;

/**
 * One thing a lucky block can do when broken.
 *
 * @param id     names the outcome; its message is the translation key {@code outcome.rngambling.<id>}
 * @param weight how often it happens at 0 luck, relative to the others
 * @param rating how good it is, from -10 (awful) to +10 (wonderful); luck tilts the odds by this
 * @param action what it does
 */
public record Outcome(String id, int weight, int rating, Action action) {
    public Outcome {
        if (weight <= 0 || rating < -10 || rating > 10) {
            throw new IllegalArgumentException("bad outcome " + id);
        }
    }

    @FunctionalInterface
    public interface Action {
        void run(OutcomeContext context);
    }
}
