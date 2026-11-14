package rngambling.outcome;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The way out of a Sandbox room: walk into it to finish the room (a success). Only found in the
 * Sandbox; it can't be broken, and has no item.
 */
public class SandboxExitBlock extends Block implements net.minecraft.world.level.block.LiquidBlockContainer {
    public static final MapCodec<SandboxExitBlock> CODEC = simpleCodec(SandboxExitBlock::new);

    public SandboxExitBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean precise) {
        if (!level.isClientSide() && entity instanceof ServerPlayer player && Sandbox.inSandbox(player)) {
            Sandbox.reachedExit(player);
        }
    }

    /** Water and lava flow around it rather than washing it away (it has no collision, like a torch). */
    @Override
    public boolean canPlaceLiquid(net.minecraft.world.entity.@org.jspecify.annotations.Nullable LivingEntity user,
            net.minecraft.world.level.BlockGetter level, BlockPos pos, BlockState state, net.minecraft.world.level.material.Fluid fluid) {
        return false;
    }

    @Override
    public boolean placeLiquid(net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockState state,
            net.minecraft.world.level.material.FluidState fluid) {
        return false;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        for (int i = 0; i < 3; i++) {
            level.addParticle(ParticleTypes.PORTAL, pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble() * 2,
                    pos.getZ() + random.nextDouble(), (random.nextDouble() - 0.5) * 0.5, 0.2, (random.nextDouble() - 0.5) * 0.5);
        }
    }
}
