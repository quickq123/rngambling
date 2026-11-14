package rngambling.casino;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A casino table (roulette, blackjack): right-click to play. Like every casino machine it can't
 * be crafted, but drops itself when broken and works wherever it's placed.
 */
public class CasinoTableBlock extends Block {
    /** Makes the table's menu for a player. */
    @FunctionalInterface
    public interface MenuFactory {
        AbstractContainerMenu create(int containerId, Inventory inventory, BlockPos pos);
    }

    private final MenuFactory menu;

    public CasinoTableBlock(MenuFactory menu, BlockBehaviour.Properties properties) {
        super(properties);
        this.menu = menu;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(properties -> new CasinoTableBlock(menu, properties));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider((containerId, inventory, who) -> menu.create(containerId, inventory, pos),
                    Component.translatable(getDescriptionId())), buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.SUCCESS;
    }
}
