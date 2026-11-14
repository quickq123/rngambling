package rngambling.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import rngambling.RNGambling;
import rngambling.RNGamblingConfig;

/**
 * VBD-20 Hot Potato: explodes when its fuse runs out ({@link RNGambling#FUSE}, the game time it
 * goes off), wherever it is: in a player's inventory (at the player) or lying on the ground (at
 * the item). Throwing it away is the way out. One without a fuse (from the creative tab) is a dud.
 */
public class HotPotatoItem extends Item {
    public static final int FUSE_TICKS = 5 * 20;
    public static final float POWER = 3.0f;

    public HotPotatoItem(Item.Properties properties) {
        super(properties);
    }

    /** A lit Hot Potato, going off {@link #FUSE_TICKS} from now. */
    public static ItemStack lit(Level level) {
        ItemStack stack = new ItemStack(RNGambling.HOT_POTATO.get());
        stack.set(RNGambling.FUSE.get(), level.getGameTime() + FUSE_TICKS);
        return stack;
    }

    private static long ticksLeft(ItemStack stack, Level level) {
        Long fuse = stack.get(RNGambling.FUSE.get());
        return fuse == null ? Long.MAX_VALUE : fuse - level.getGameTime();
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        long left = ticksLeft(stack, level);
        if (left == Long.MAX_VALUE) {
            return;
        }
        if (left <= 0) {
            stack.setCount(0);
            explode(level, owner.getX(), owner.getY() + 0.5, owner.getZ());
            return;
        }
        if (left % 20 == 0) {
            level.playSound(null, owner.blockPosition(), SoundEvents.TNT_PRIMED, SoundSource.PLAYERS, 0.4f, 2.0f - left / (float) FUSE_TICKS);
            if (owner instanceof ServerPlayer player) {
                player.sendOverlayMessage(Component.translatable("item.rngambling.hot_potato.ticking", left / 20).withStyle(ChatFormatting.RED));
            }
        }
    }

    /** Lying on the ground: smokes, and goes off where it lies. */
    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (entity.level() instanceof ServerLevel level) {
            long left = ticksLeft(stack, level);
            if (left == Long.MAX_VALUE) {
                return false;
            }
            if (left <= 0) {
                entity.discard();
                explode(level, entity.getX(), entity.getY(), entity.getZ());
                return true;
            }
            if (left % 4 == 0) {
                level.sendParticles(ParticleTypes.SMOKE, entity.getX(), entity.getY() + 0.3, entity.getZ(), 2, 0.05, 0.05, 0.05, 0.01);
            }
        }
        return false;
    }

    private static void explode(ServerLevel level, double x, double y, double z) {
        float power = Math.min(POWER, (float) RNGamblingConfig.MAX_EXPLOSION.getAsDouble());
        level.explode(null, x, y, z, power, false, Level.ExplosionInteraction.TNT);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable(stack.has(RNGambling.FUSE.get()) ? "item.rngambling.hot_potato.lit" : "item.rngambling.hot_potato.dud")
                .withStyle(ChatFormatting.GRAY));
    }
}
