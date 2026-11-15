package rngambling.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import rngambling.RNGambling;
import rngambling.block.MimicLuckyBlock;

/**
 * The darkening half of a disguised block's "slightly off" look. {@link MimicModel} moves each
 * copied quad's tint layer up by one, so layer 0 here is the plain shade and layer n+1 is the
 * copied block's own tint layer n, shaded. Dark blocks (see {@link MimicLook}) aren't darkened
 * here; they are lightened by an overlay instead.
 */
final class MimicTint {
    /**
     * How much darker a light disguised block is drawn: each colour channel is multiplied by this.
     * 1.0 would be a perfect copy.
     */
    static final float SHADE = 0.76f;

    /** Copied blocks with more tint layers than this keep only the first ones. Vanilla uses at most 2. */
    private static final int COPIED_LAYERS = 4;

    private MimicTint() {
    }

    static void register(RegisterColorHandlersEvent.BlockTintSources event) {
        List<BlockTintSource> layers = new ArrayList<>();
        layers.add(new Plain());
        for (int layer = 0; layer < COPIED_LAYERS; layer++) {
            layers.add(new Copied(layer));
        }
        event.register(layers, RNGambling.MIMIC_LUCKY_BLOCK.get());
    }

    /** Multiplies an ARGB colour's red, green and blue by {@link #SHADE}. */
    static int shade(int argb) {
        int r = Math.round(((argb >> 16) & 0xFF) * SHADE);
        int g = Math.round(((argb >> 8) & 0xFF) * SHADE);
        int b = Math.round((argb & 0xFF) * SHADE);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /** Darkens unless the copied block is one that's lightened instead. */
    private static int shadeFor(BlockAndTintGetter level, BlockPos pos, int argb) {
        BlockState mimic = MimicLuckyBlock.mimicAt(level, pos);
        return mimic != null && MimicLook.lightens(mimic) ? argb : shade(argb);
    }

    /** The copied block's untinted faces. */
    private record Plain() implements BlockTintSource {
        @Override
        public int color(BlockState state) {
            return shade(-1);
        }

        @Override
        public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            return shadeFor(level, pos, -1);
        }
    }

    /** The copied block's own tint layer, shaded. */
    private record Copied(int layer) implements BlockTintSource {
        @Override
        public int color(BlockState state) {
            return shade(-1);
        }

        @Override
        public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            BlockState mimic = MimicLuckyBlock.mimicAt(level, pos);
            BlockTintSource source = mimic == null ? null : Minecraft.getInstance().getBlockColors().getTintSource(mimic, layer);
            return shadeFor(level, pos, source == null ? -1 : source.colorInWorld(mimic, level, pos));
        }
    }
}
