package rngambling.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.resources.Identifier;
import rngambling.RNGambling;
import rngambling.entity.DragonHatchling;

/**
 * Draws the Dragon Hatchling with its own model and texture: none of a wolf's variant textures,
 * collar or armour layers.
 */
public class DragonHatchlingRenderer extends MobRenderer<DragonHatchling, WolfRenderState, DragonHatchlingModel> {
    private static final Identifier TEXTURE = RNGambling.id("textures/entity/dragon_hatchling.png");

    public DragonHatchlingRenderer(EntityRendererProvider.Context context) {
        super(context, new DragonHatchlingModel(context.bakeLayer(DragonHatchlingModel.LAYER)), 0.5f);
    }

    @Override
    public Identifier getTextureLocation(WolfRenderState state) {
        return TEXTURE;
    }

    @Override
    public WolfRenderState createRenderState() {
        return new WolfRenderState();
    }

    @Override
    public void extractRenderState(DragonHatchling entity, WolfRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.isAngry = entity.isAngry();
        state.isSitting = entity.isInSittingPose();
        state.tailAngle = entity.getTailAngle();
        state.texture = TEXTURE;
    }
}
