package rngambling.luck;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;

/**
 * How a generated lucky block's luck is rolled. In a luck zone file:
 * <pre>
 *   "luck": {"type": "spread", "mean": 0, "spread": 30}
 *   "luck": {"type": "extremes", "min": 50, "max": 100, "good_chance": 0.5}
 * </pre>
 */
public sealed interface LuckRoll {
    int roll(RandomSource random);

    String type();

    Codec<LuckRoll> CODEC = Codec.STRING.dispatch("type", LuckRoll::type, type -> switch (type) {
        case "spread" -> Spread.CODEC;
        case "extremes" -> Extremes.CODEC;
        default -> throw new IllegalArgumentException("Unknown luck roll type " + type + "; use spread or extremes");
    });

    /**
     * A bell curve: most rolls near {@code mean}, about 2 in 3 within {@code spread} of it, and
     * about 1 in 20 more than twice {@code spread} away.
     */
    record Spread(int mean, int spread) implements LuckRoll {
        static final MapCodec<Spread> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.intRange(Luck.MIN, Luck.MAX).fieldOf("mean").forGetter(Spread::mean),
                Codec.intRange(0, 200).fieldOf("spread").forGetter(Spread::spread)
        ).apply(i, Spread::new));

        @Override
        public int roll(RandomSource random) {
            return Luck.clamp((int) Math.round(mean + random.nextGaussian() * spread));
        }

        @Override
        public String type() {
            return "spread";
        }
    }

    /**
     * No middle ground: luck between {@code min} and {@code max} in size (evenly), good (positive)
     * with chance {@code good_chance} and bad otherwise.
     */
    record Extremes(int min, int max, float goodChance) implements LuckRoll {
        static final MapCodec<Extremes> CODEC = RecordCodecBuilder.<Extremes>mapCodec(i -> i.group(
                Codec.intRange(0, Luck.MAX).fieldOf("min").forGetter(Extremes::min),
                Codec.intRange(0, Luck.MAX).fieldOf("max").forGetter(Extremes::max),
                Codec.floatRange(0, 1).optionalFieldOf("good_chance", 0.5f).forGetter(Extremes::goodChance)
        ).apply(i, Extremes::new)).validate(e -> e.min <= e.max
                ? com.mojang.serialization.DataResult.success(e)
                : com.mojang.serialization.DataResult.error(() -> "extremes: min " + e.min + " is more than max " + e.max));

        @Override
        public int roll(RandomSource random) {
            int size = min + random.nextInt(max - min + 1);
            return random.nextFloat() < goodChance ? size : -size;
        }

        @Override
        public String type() {
            return "extremes";
        }
    }
}
