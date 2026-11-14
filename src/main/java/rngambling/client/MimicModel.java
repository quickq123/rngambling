package rngambling.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import net.minecraft.util.TriState;
import org.jspecify.annotations.Nullable;
import rngambling.RNGambling;
import rngambling.block.MimicLuckyBlock;

/**
 * Draws a disguised lucky block as the block it copies. The copied block's model parts are
 * reused with every quad's tint layer moved up by one: layer 0 (which the copied block's
 * untinted faces now use) is the plain shade, and layer n+1 is the copied block's own tint n
 * (grass, leaves), shaded too. See {@link MimicTint}. A disguised block whose copied block isn't
 * known yet is drawn with its own model.
 */
final class MimicModel extends DelegateBlockStateModel {
    /** The shaded copy of each model part, made once: parts belong to baked models, which don't change. */
    private static final Map<BlockStateModelPart, BlockStateModelPart> SHADED = new ConcurrentHashMap<>();

    private MimicModel(BlockStateModel own) {
        super(own);
    }

    /** Wraps the disguised lucky block's model, once all models are baked. */
    static void wrap(ModelEvent.ModifyBakingResult event) {
        SHADED.clear();
        Map<BlockState, BlockStateModel> models = event.getBakingResult().blockStateModels();
        for (BlockState state : RNGambling.MIMIC_LUCKY_BLOCK.get().getStateDefinition().getPossibleStates()) {
            BlockStateModel own = models.get(state);
            if (own != null) {
                models.put(state, new MimicModel(own));
            }
        }
    }

    private static @Nullable BlockStateModel modelOf(@Nullable BlockState mimic) {
        return mimic == null ? null : Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(mimic);
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
        BlockState mimic = MimicLuckyBlock.mimicAt(level, pos);
        BlockStateModel copied = modelOf(mimic);
        if (copied == null) {
            delegate.collectParts(level, pos, state, random, parts);
            return;
        }
        List<BlockStateModelPart> original = new ArrayList<>();
        copied.collectParts(level, pos, mimic, random, original);
        for (BlockStateModelPart part : original) {
            parts.add(SHADED.computeIfAbsent(part, ShadedPart::new));
        }
    }

    /** Breaking and running particles use the copied block's texture. */
    @Override
    public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        BlockState mimic = MimicLuckyBlock.mimicAt(level, pos);
        BlockStateModel copied = modelOf(mimic);
        return copied != null ? copied.particleMaterial(level, pos, mimic) : delegate.particleMaterial(level, pos, state);
    }

    @Override
    public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        BlockState mimic = MimicLuckyBlock.mimicAt(level, pos);
        BlockStateModel copied = modelOf(mimic);
        return copied != null ? copied.materialFlags(level, pos, mimic) : delegate.materialFlags(level, pos, state);
    }

    /** A copied model part whose quads all take a tint, so the shade reaches every face. */
    private static final class ShadedPart implements BlockStateModelPart {
        private final BlockStateModelPart original;
        private final List<BakedQuad> unculled;
        private final Map<Direction, List<BakedQuad>> culled = new EnumMap<>(Direction.class);

        ShadedPart(BlockStateModelPart original) {
            this.original = original;
            this.unculled = shade(original.getQuads(null));
            for (Direction side : Direction.values()) {
                culled.put(side, shade(original.getQuads(side)));
            }
        }

        private static List<BakedQuad> shade(List<BakedQuad> quads) {
            List<BakedQuad> shaded = new ArrayList<>(quads.size());
            for (BakedQuad quad : quads) {
                BakedQuad.MaterialInfo info = quad.materialInfo();
                BakedQuad.MaterialInfo moved = new BakedQuad.MaterialInfo(info.sprite(), info.layer(), info.itemRenderType(),
                        info.tintIndex() + 1, info.shade(), info.lightEmission(), info.ambientOcclusion());
                shaded.add(new BakedQuad(quad.position0(), quad.position1(), quad.position2(), quad.position3(),
                        quad.packedUV0(), quad.packedUV1(), quad.packedUV2(), quad.packedUV3(),
                        quad.direction(), moved, quad.bakedNormals(), quad.bakedColors()));
            }
            return List.copyOf(shaded);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable Direction direction) {
            return direction == null ? unculled : culled.get(direction);
        }

        @Override
        @Deprecated
        public boolean useAmbientOcclusion() {
            return original.useAmbientOcclusion();
        }

        @Override
        public TriState ambientOcclusion() {
            return original.ambientOcclusion();
        }

        @Override
        public Material.Baked particleMaterial() {
            return original.particleMaterial();
        }

        @Override
        public int materialFlags() {
            return original.materialFlags();
        }
    }
}
