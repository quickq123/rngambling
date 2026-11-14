package rngambling.outcome;

import java.util.EnumSet;
import java.util.Set;

/**
 * One effect a lucky block can have when broken (see docs/effect-catalog.csv and {@link Catalog}).
 *
 * @param id        names the effect; its message is the translation key {@code outcome.rngambling.<id>}
 * @param catalogId the catalog's ID (CAT-04 and so on), or "" for themed effects
 * @param tier      its tier, which luck mostly picks (see {@link Outcomes#pick})
 * @param luckMin   its luck band: inside it the effect has its full weight within its tier
 * @param luckMax   see {@code luckMin}
 * @param weight    how often it comes up within its tier
 * @param action    what it does
 * @param flags     {@link Flag}s
 * @param hold      for a Sandbox effect, the least time (in ticks) the visit lasts, for effects
 *                  that play out over time
 */
public record Outcome(String id, String catalogId, Tier tier, int luckMin, int luckMax, int weight, Action action,
        Set<Flag> flags, int hold) {

    public enum Flag {
        /** Builds, places blocks or tears a lot up: played out in the Sandbox (see {@link Sandbox}). */
        SANDBOX,
        /** Changes the whole server (time, weather): off in multiplayer unless the config allows it. */
        GLOBAL,
        /** Places more lucky blocks: not offered by a block that is itself deep in a chain. */
        CHAIN
    }

    public Outcome {
        if (weight <= 0 || luckMin > luckMax) {
            throw new IllegalArgumentException("bad outcome " + id);
        }
        flags = flags.isEmpty() ? EnumSet.noneOf(Flag.class) : EnumSet.copyOf(flags);
    }

    public static Outcome of(String catalogId, String id, Tier tier, int luckMin, int luckMax, int weight, Action action) {
        return new Outcome(id, catalogId, tier, luckMin, luckMax, weight, action, Set.of(), 0);
    }

    public boolean has(Flag flag) {
        return flags.contains(flag);
    }

    public boolean sandbox() {
        return has(Flag.SANDBOX);
    }

    public boolean inBand(int luck) {
        return luck >= luckMin && luck <= luckMax;
    }

    private Outcome with(Flag flag, int hold) {
        Set<Flag> more = EnumSet.copyOf(flags.isEmpty() ? EnumSet.noneOf(Flag.class) : flags);
        more.add(flag);
        return new Outcome(id, catalogId, tier, luckMin, luckMax, weight, action, more, hold);
    }

    /** Played out in the Sandbox, the visit lasting at least {@code holdTicks}. */
    public Outcome inSandbox(int holdTicks) {
        return with(Flag.SANDBOX, holdTicks);
    }

    public Outcome inSandbox() {
        return inSandbox(0);
    }

    public Outcome global() {
        return with(Flag.GLOBAL, hold);
    }

    public Outcome chain() {
        return with(Flag.CHAIN, hold);
    }

    /** The same metadata (tier, band, weight, flags) with a different id and action: for themes. */
    public Outcome swapped(String newId, Action newAction) {
        return new Outcome(newId, "", tier, luckMin, luckMax, weight, newAction, flags, hold);
    }

    @FunctionalInterface
    public interface Action {
        void run(OutcomeContext context);
    }
}
