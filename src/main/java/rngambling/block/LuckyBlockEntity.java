package rngambling.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import rngambling.RNGambling;
import rngambling.luck.Luck;

/**
 * Holds a lucky block's luck and, for a disguised lucky block, the block it copies. Both are
 * sent to the client: the copied block to draw it, the luck for the luck-seeing glasses (later).
 */
public class LuckyBlockEntity extends BlockEntity {
    private int luck;
    private @Nullable BlockState mimic;

    public LuckyBlockEntity(BlockPos pos, BlockState state) {
        super(RNGambling.LUCKY_BLOCK_ENTITY.get(), pos, state);
    }

    public int luck() {
        return luck;
    }

    /** Changes the luck (for the luck boosters, later) and tells nearby players' games. */
    public void setLuck(int luck) {
        this.luck = Luck.clamp(luck);
        changed();
    }

    /** The block a disguised lucky block looks like, or null for a plain one. */
    public @Nullable BlockState mimic() {
        return mimic;
    }

    public void setMimic(@Nullable BlockState mimic) {
        this.mimic = mimic;
        changed();
    }

    /** For world generation: sets both without telling anyone, since no one is watching a chunk being made. */
    public void initialise(@Nullable BlockState mimic, int luck) {
        this.mimic = mimic;
        this.luck = Luck.clamp(luck);
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("luck", luck);
        if (mimic != null) {
            output.store("mimic", BlockState.CODEC, mimic);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        luck = Luck.clamp(input.getIntOr("luck", 0));
        BlockState before = mimic;
        mimic = input.read("mimic", BlockState.CODEC).orElse(null);
        // In the player's game: the block arrives before its data, so redraw it once the copied block is known.
        if (level != null && level.isClientSide() && before != mimic) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
        }
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
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
