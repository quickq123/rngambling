package rngambling.casino;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * A casino slot machine: right-click to play (see {@link SlotMachineMenu}), and the Casino
 * Dealer's job site. It can't be crafted: it's only found in village casinos.
 */
public class SlotMachineBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<SlotMachineBlock> CODEC = simpleCodec(SlotMachineBlock::new);

    public SlotMachineBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Opens the slot machine's screen. */
    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider(
                    (containerId, inventory, who) -> new SlotMachineMenu(containerId, inventory, pos),
                    net.minecraft.network.chat.Component.translatable("block.rngambling.slot_machine")), buffer -> buffer.writeBlockPos(pos));
        }
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    /** Faces the player who places it. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
}
