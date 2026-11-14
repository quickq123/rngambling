package rngambling.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Which way a disguised block is drawn "slightly off". Light blocks are darkened (see
 * {@link MimicTint}); dark ones are lightened instead (see {@link MimicModel}'s overlay), since
 * darkening an already dark texture barely shows. A tint can only darken, so lightening is done
 * by drawing a faint white layer over the copied block.
 */
final class MimicLook {
    /**
     * Textures darker than this on average (0 black, 1 white) are lightened. 0.36 takes in
     * deepslate (0.32), basalt, blackstone, obsidian, mud and the dark planks, but not stone (0.49)
     * or dirt (0.40), which darken visibly enough.
     */
    static final double DARK_BELOW = 0.36;

    /** By copied block state; cleared whenever textures and models are reloaded. */
    private static final Map<BlockState, Boolean> DARK = new ConcurrentHashMap<>();

    private MimicLook() {
    }

    static void clear() {
        DARK.clear();
    }

    /** Whether this copied block is dark enough to be lightened rather than darkened. */
    static boolean lightens(BlockState copied) {
        return DARK.computeIfAbsent(copied, state -> brightness(state) < DARK_BELOW);
    }

    /** The average brightness of the block's main (particle) texture, by its visible pixels. */
    static double brightness(BlockState state) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager().getBlockStateModelSet().getParticleMaterial(state).sprite();
        NativeImage image = sprite.contents().getOriginalImage();
        if (image.isClosed()) {
            return 1.0;
        }
        int width = sprite.contents().width();
        int height = sprite.contents().height(); // the first frame of an animated texture
        double total = 0;
        int counted = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = image.getPixel(x, y);
                if (ARGB.alpha(pixel) < 128) {
                    continue;
                }
                total += (0.2126 * ARGB.red(pixel) + 0.7152 * ARGB.green(pixel) + 0.0722 * ARGB.blue(pixel)) / 255.0;
                counted++;
            }
        }
        return counted == 0 ? 1.0 : total / counted;
    }
}
