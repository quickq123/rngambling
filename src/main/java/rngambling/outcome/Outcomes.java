package rngambling.outcome;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import rngambling.RNGamblingConfig;
import rngambling.luck.Luck;
import rngambling.luck.LuckZone;

/**
 * Drawing and playing lucky block effects.
 *
 * <p>How luck picks an effect (a reconciliation of the catalog's luck bands with "nothing is ever
 * certain"):
 * <ol>
 * <li><b>Good, neutral or bad.</b> A smooth S-curve in luck splits good from bad, never below
 * {@link #GROUP_FLOOR} either way (so a +100 block can still go wrong and a -100 block can still go
 * right); neutral is likeliest near 0. Good outcomes get steadily likelier with luck: 7% at -100,
 * 34% at 0, 80% at +50, 90% at +100.
 * <li><b>Tier.</b> Within good or bad, by closeness to each tier's home luck ({@link Tier#home}):
 * {@code exp(-((luck - home) / WIDTH)^2) + 0.02}.
 * <li><b>Effect.</b> Within the tier, by the catalog's weights, at full weight where the block's luck
 * is inside the effect's own band and at {@link #OUTSIDE_BAND} of it where it isn't.
 * <li><b>Wildcards</b> are drawn instead, {@link #WILDCARD_SHARE} of the time, from those whose band
 * holds the luck.
 * </ol>
 */
public final class Outcomes {
    private Outcomes() {
    }

    public static final List<Outcome> ALL = Catalog.ALL;

    static final double WIDTH = 40, GROUP_FLOOR = 0.06, SLOPE = 22, OUTSIDE_BAND = 0.25, WILDCARD_SHARE = 0.08;
    /** A lucky block placed this many deep by other blocks' effects doesn't place more. */
    public static final int MAX_CHAIN = 2;

    /** The chance of a neutral, good or bad effect at a luck (by {@link Tier#sign}: 0, +1, -1). */
    public static double groupShare(int sign, int luck) {
        double l = Luck.clamp(luck);
        double neutral = 0.30 * Math.exp(-(l / 35) * (l / 35)) + 0.03;
        if (sign == 0) {
            return neutral;
        }
        double good = (1 - neutral) * (GROUP_FLOOR + (1 - 2 * GROUP_FLOOR) / (1 + Math.exp(-l / SLOPE)));
        return sign > 0 ? good : 1 - neutral - good;
    }

    /** A tier's closeness to a luck, for choosing between the tiers of one group. */
    static double closeness(Tier tier, int luck) {
        double d = (Luck.clamp(luck) - tier.home) / WIDTH;
        return Math.exp(-d * d) + 0.02;
    }

    /** Each present tier's chance at a luck: its group's share, split between the group's present tiers by closeness. */
    static Map<Tier, Double> tierChances(java.util.Collection<Tier> present, int luck) {
        Map<Tier, Double> chances = new EnumMap<>(Tier.class);
        double total = 0;
        for (Tier tier : present) {
            double group = 0;
            for (Tier other : present) {
                if (other.sign == tier.sign) {
                    group += closeness(other, luck);
                }
            }
            double chance = groupShare(tier.sign, luck) * closeness(tier, luck) / group;
            chances.put(tier, chance);
            total += chance;
        }
        for (Map.Entry<Tier, Double> entry : chances.entrySet()) {
            entry.setValue(entry.getValue() / total);
        }
        return chances;
    }

    /** The effects open to a block: without chain effects deep in a chain, and without server-wide effects where they're off. */
    public static List<Outcome> available(List<Outcome> outcomes, MinecraftServer server, int chain) {
        boolean global = server.isSingleplayer() || RNGamblingConfig.GLOBAL_EFFECTS_IN_MULTIPLAYER.getAsBoolean();
        boolean rooms = Sandbox.available(server);
        List<Outcome> open = new ArrayList<>();
        for (Outcome outcome : outcomes) {
            if ((chain < MAX_CHAIN || !outcome.has(Outcome.Flag.CHAIN)) && (global || !outcome.has(Outcome.Flag.GLOBAL))
                    && (rooms || !outcome.has(Outcome.Flag.ROOM))) {
                open.add(outcome);
            }
        }
        return open;
    }

    /**
     * Picks an effect for this luck, themed by the luck zone where the block is broken (see
     * {@link Themes}), and plays it. A foretold or jinxed tier ({@link Fate}) narrows the draw;
     * insurance cancels a bad effect. With a Re-roll Ticket in the player's off hand, two effects
     * are drawn and offered instead ({@link RerollMenu}), and this returns null: the chosen one
     * plays when they choose.
     */
    public static Outcome roll(ServerLevel level, BlockPos pos, ServerPlayer player, int luck, int chain) {
        String zone = LuckZone.name(level, LuckZone.at(level, pos));
        List<Outcome> open = Fate.applyOmen(player, available(Themes.outcomesFor(zone), level.getServer(), chain));
        if (Fate.holdsTicket(player)) {
            Fate.takeTicket(player);
            RerollMenu.offer(level, pos, player, luck, chain, open);
            return null;
        }
        Outcome outcome = pick(level.getRandom(), luck, open, variety(player));
        playDrawn(level, pos, player, luck, chain, outcome);
        return outcome;
    }

    /** Plays a drawn effect: counted towards variety, then cancelled if the player is insured and it's bad, else played. */
    static void playDrawn(ServerLevel level, BlockPos pos, ServerPlayer player, int luck, int chain, Outcome outcome) {
        drawn(player, outcome);
        if (!Fate.claim(player, outcome)) {
            play(level, pos, player, luck, chain, outcome);
        }
    }

    public static Outcome roll(ServerLevel level, BlockPos pos, ServerPlayer player, int luck) {
        return roll(level, pos, player, luck, 0);
    }

    /** A fresh draw at another luck (Coin Flip), leaving out {@code except}. */
    public static void playRandom(ServerLevel level, BlockPos pos, ServerPlayer player, int luck, int chain, String except) {
        String zone = LuckZone.name(level, LuckZone.at(level, pos));
        List<Outcome> open = new ArrayList<>(available(Themes.outcomesFor(zone), level.getServer(), chain));
        open.removeIf(o -> o.id().equals(except));
        Outcome outcome = pick(level.getRandom(), luck, open, variety(player));
        playDrawn(level, pos, player, luck, chain, outcome);
    }

    /** A draw from one tier only (The Last Gamble's Legendary reward). */
    public static void playTier(ServerLevel level, BlockPos pos, ServerPlayer player, int luck, int chain, Tier tier) {
        String zone = LuckZone.name(level, LuckZone.at(level, pos));
        List<Outcome> open = new ArrayList<>(available(Themes.outcomesFor(zone), level.getServer(), chain));
        open.removeIf(o -> o.tier() != tier);
        if (!open.isEmpty()) {
            Outcome outcome = pickWithin(level.getRandom(), luck, open, variety(player));
            playDrawn(level, pos, player, luck, chain, outcome);
        }
    }

    public static void play(ServerLevel level, BlockPos pos, ServerPlayer player, int luck, Outcome outcome) {
        play(level, pos, player, luck, 0, outcome);
    }

    /** Plays one effect for a player, in the Sandbox if it's one that builds or tears things up. */
    public static void play(ServerLevel level, BlockPos pos, ServerPlayer player, int luck, int chain, Outcome outcome) {
        if (!outcome.has(Outcome.Flag.SURPRISE)) {
            announce(player, outcome);
        }
        if (outcome.sandbox()) {
            Sandbox.Arena arena = Sandbox.start(player, outcome.hold());
            if (arena != null) {
                level = arena.level();
                pos = arena.centre();
            }
        }
        outcome.action().run(new OutcomeContext(level, pos.immutable(), player, level.getRandom(), Luck.clamp(luck), chain));
        if (outcome.has(Outcome.Flag.SURPRISE)) {
            // The scare first; who did it, after.
            ServerLevel where = level;
            BlockPos at = pos.immutable();
            Scheduler.later(outcome.hold(), () -> {
                if (!player.hasDisconnected()) {
                    announce(player, outcome);
                    reveal(where, at, player, outcome);
                }
            });
            return;
        }
        reveal(level, pos, player, outcome);
    }

    /** The effect's name over the hotbar and its tier's sound. */
    private static void reveal(ServerLevel level, BlockPos pos, ServerPlayer player, Outcome outcome) {
        player.sendOverlayMessage(Component.translatable("outcome.rngambling." + outcome.id()).withStyle(outcome.tier().colour));
        level.playSound(null, pos, outcome.tier().sign > 0 ? SoundEvents.PLAYER_LEVELUP
                        : outcome.tier().sign < 0 ? SoundEvents.WITCH_CELEBRATE : SoundEvents.NOTE_BLOCK_CHIME.value(),
                SoundSource.BLOCKS, 0.6f, 1.2f);
    }

    /**
     * Tells the player in chat what they got: "[Lucky Block] Name: a one-liner" (the one-liners,
     * some of them snarky, are the {@code outcome.rngambling.<id>.quip} lines in the language file).
     * Sent before the effect plays, so effects with a message of their own follow it.
     */
    static void announce(ServerPlayer player, Outcome outcome) {
        Journal.broadcast(player, outcome);
        player.sendSystemMessage(Component.translatable("outcome.rngambling.announce").withStyle(net.minecraft.ChatFormatting.GOLD)
                .append(Component.translatable("outcome.rngambling." + outcome.id()).withStyle(outcome.tier().colour, net.minecraft.ChatFormatting.BOLD))
                .append(Component.literal(": ").withStyle(net.minecraft.ChatFormatting.GRAY))
                .append(Component.translatable("outcome.rngambling." + outcome.id() + ".quip")
                        .withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC)));
        Journal.record(player, outcome);
    }

    /** Every effect there is: the catalog's and every theme's own, by id. */
    public static java.util.Map<String, Outcome> everyEffect() {
        java.util.Map<String, Outcome> all = new java.util.LinkedHashMap<>();
        for (Outcome outcome : ALL) {
            all.put(outcome.id(), outcome);
        }
        for (String zone : Themes.BY_ZONE.keySet()) {
            for (Outcome outcome : Themes.outcomesFor(zone)) {
                all.putIfAbsent(outcome.id(), outcome);
            }
        }
        return all;
    }

    public static Outcome pick(RandomSource random, int luck) {
        return pick(random, luck, ALL);
    }

    /** No variety: every effect at its catalog weight. */
    public static final java.util.function.ToDoubleFunction<Outcome> PLAIN = outcome -> 1.0;

    /** A player's variety factors (see {@link Variety}), or none if the config turns it off. */
    public static java.util.function.ToDoubleFunction<Outcome> variety(ServerPlayer player) {
        double strength = rngambling.RNGamblingConfig.VARIETY_STRENGTH.getAsDouble();
        if (strength <= 0) {
            return PLAIN;
        }
        Variety variety = player.getData(rngambling.RNGambling.VARIETY);
        return outcome -> variety.factor(outcome.id(), strength);
    }

    /** Counts a drawn effect towards the player's variety. */
    static void drawn(ServerPlayer player, Outcome outcome) {
        player.setData(rngambling.RNGambling.VARIETY, player.getData(rngambling.RNGambling.VARIETY).record(outcome.id()));
    }

    public static Outcome pick(RandomSource random, int luck, List<Outcome> outcomes) {
        return pick(random, luck, outcomes, PLAIN);
    }

    /** The two-step draw described above. */
    public static Outcome pick(RandomSource random, int luck, List<Outcome> outcomes, java.util.function.ToDoubleFunction<Outcome> variety) {
        Map<Tier, List<Outcome>> byTier = new EnumMap<>(Tier.class);
        for (Outcome outcome : outcomes) {
            if (outcome.tier() != Tier.WILDCARD || outcome.inBand(luck)) {
                byTier.computeIfAbsent(outcome.tier(), t -> new ArrayList<>()).add(outcome);
            }
        }
        List<Outcome> wildcards = byTier.remove(Tier.WILDCARD);
        if (wildcards != null && (byTier.isEmpty() || random.nextDouble() < WILDCARD_SHARE)) {
            return pickWithin(random, luck, wildcards, variety);
        }
        Map<Tier, Double> chances = tierChances(byTier.keySet(), luck);
        double roll = random.nextDouble();
        for (Map.Entry<Tier, List<Outcome>> entry : byTier.entrySet()) {
            roll -= chances.get(entry.getKey());
            if (roll < 0) {
                return pickWithin(random, luck, entry.getValue(), variety);
            }
        }
        return pickWithin(random, luck, byTier.values().iterator().next(), variety);
    }

    /** Within one tier: by weight, full inside the effect's band, {@link #OUTSIDE_BAND} outside it. */
    static Outcome pickWithin(RandomSource random, int luck, List<Outcome> outcomes, java.util.function.ToDoubleFunction<Outcome> variety) {
        double total = 0;
        for (Outcome outcome : outcomes) {
            total += weightWithin(outcome, luck) * variety.applyAsDouble(outcome);
        }
        double roll = random.nextDouble() * total;
        for (Outcome outcome : outcomes) {
            roll -= weightWithin(outcome, luck) * variety.applyAsDouble(outcome);
            if (roll < 0) {
                return outcome;
            }
        }
        return outcomes.getLast();
    }

    private static double weightWithin(Outcome outcome, int luck) {
        return outcome.weight() * (outcome.inBand(luck) ? 1.0 : OUTSIDE_BAND);
    }

    /** The chance of each tier at a luck (wildcards left out), for tuning and tests. */
    public static Map<Tier, Double> tierShares(int luck) {
        List<Tier> tiers = new ArrayList<>(List.of(Tier.values()));
        tiers.remove(Tier.WILDCARD);
        return tierChances(tiers, luck);
    }
}
