package rngambling.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import rngambling.RNGambling;
import rngambling.luck.Luck;

/**
 * Holds a lucky block's luck. It is also sent to the client, where the luck-seeing glasses
 * (a later step) read it.
 */
public class LuckyBlockEntity extends BlockEntity {
    private int luck;

    public LuckyBlockEntity(BlockPos pos, BlockState state) {
        super(RNGambling.LUCKY_BLOCK_ENTITY.get(), pos, state);
    }

    public int luck() {
        return luck;
    }

    /** Changes the luck (for the luck boosters, later) and tells nearby players' games. */
    public void setLuck(int luck) {
        this.luck = Luck.clamp(luck);
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("luck", luck);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        luck = Luck.clamp(input.getIntOr("luck", 0));
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        Integer fromItem = components.get(RNGambling.LUCK.get());
        luck = fromItem == null ? 0 : Luck.clamp(fromItem);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (luck != 0) {
            components.set(RNGambling.LUCK.get(), luck);
        }
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("luck");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("luck", luck);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
