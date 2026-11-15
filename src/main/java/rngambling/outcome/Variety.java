package rngambling.outcome;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Keeps a player's lucky blocks varied: how often, and how recently, each effect has happened to
 * them. Within a tier (never across tiers, which luck alone decides), an effect's weight is
 * multiplied by {@link #factor}: lower the more it has happened, and lower still if it happened in
 * the last few draws. It never reaches zero (at least {@link #FLOOR}), so nothing is blocked, and
 * effects a player hasn't had yet keep their full weight.
 *
 * <p>Right after an effect: about 0.15 of its weight; five draws later about 0.25; fifteen later about
 * 0.5; after a long while 0.63 if it has happened once, 0.25 at five times, never below 0.1.
 */
public record Variety(int draws, Map<String, Entry> effects) {
    /** How fast "recently" fades: each draw since multiplies an occurrence's pull by this. */
    public static final double RECENT_DECAY = 0.85;
    /** How strongly a recent occurrence lowers the weight. */
    public static final double RECENT_WEIGHT = 5.0;
    /** How strongly each occurrence ever lowers the weight. */
    public static final double TOTAL_WEIGHT = 0.6;
    /** The least an effect's weight is multiplied by. */
    public static final double FLOOR = 0.1;
    /** How many recent occurrences of each effect are remembered. */
    private static final int KEEP_RECENT = 4;

    /** One effect: how many times it has happened, and at which draws (the last few). */
    public record Entry(int total, List<Integer> recent) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("total").forGetter(Entry::total),
                Codec.INT.listOf().fieldOf("recent").forGetter(Entry::recent)
        ).apply(i, Entry::new));
        static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Entry::total,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), Entry::recent,
                Entry::new);
    }

    public static final Variety EMPTY = new Variety(0, Map.of());
    public static final MapCodec<Variety> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.fieldOf("draws").forGetter(Variety::draws),
            Codec.unboundedMap(Codec.STRING, Entry.CODEC).fieldOf("effects").forGetter(Variety::effects)
    ).apply(i, Variety::new));
    public static final StreamCodec<ByteBuf, Variety> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Variety::draws,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, Entry.STREAM_CODEC), Variety::effects,
            Variety::new);

    public boolean isEmpty() {
        return draws == 0 && effects.isEmpty();
    }

    /** How many times this effect has happened to the player. */
    public int times(String id) {
        Entry entry = effects.get(id);
        return entry == null ? 0 : entry.total();
    }

    /** What this effect's weight is multiplied by, for this player, at their next draw (at strength 1). */
    public double factor(String id) {
        return factor(id, 1.0);
    }

    /**
     * The same at a strength (config {@code variety_strength}): 0 leaves every weight alone, 1 is
     * the default, and higher pushes repeats further down. Both pulls scale with it, and so does the
     * floor (0.1 up to strength 1, then 0.1 / strength), so a strong setting can make a repeat rare.
     */
    public double factor(String id, double strength) {
        Entry entry = effects.get(id);
        if (entry == null || strength <= 0) {
            return 1.0;
        }
        double recent = 0;
        for (int at : entry.recent()) {
            recent += Math.pow(RECENT_DECAY, Math.max(0, draws - at));
        }
        double floor = FLOOR / Math.max(1.0, strength);
        return Math.max(floor, 1.0 / (1.0 + strength * (RECENT_WEIGHT * recent + TOTAL_WEIGHT * entry.total())));
    }

    /** After a draw: one more draw, and one more of this effect. */
    public Variety record(String id) {
        int now = draws + 1;
        Map<String, Entry> more = new HashMap<>(effects);
        Entry old = more.get(id);
        List<Integer> recent = new ArrayList<>(old == null ? List.of() : old.recent());
        recent.add(now);
        while (recent.size() > KEEP_RECENT) {
            recent.removeFirst();
        }
        more.put(id, new Entry((old == null ? 0 : old.total()) + 1, List.copyOf(recent)));
        return new Variety(now, Map.copyOf(more));
    }
}
