package rngambling.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import rngambling.RNGambling;

/**
 * NEU-35 Pet Rock: a rock with a name, a like and a dislike. It does nothing. Use it to pet it,
 * which also does nothing, but it seems to appreciate it.
 */
public class PetRockItem extends Item {
    /** How many names, likes and dislikes the language file has ({@code item.rngambling.pet_rock.name.<n>} and so on). */
    public static final int NAMES = 12, LIKES = 10, DISLIKES = 10;

    public PetRockItem(Item.Properties properties) {
        super(properties);
    }

    /** A new pet rock, its name, like and dislike chosen at random. */
    public static ItemStack adopt(RandomSource random) {
        ItemStack rock = new ItemStack(RNGambling.PET_ROCK.get());
        rock.set(DataComponents.CUSTOM_NAME, Component.translatable("item.rngambling.pet_rock.name." + random.nextInt(NAMES))
                .withStyle(style -> style.withItalic(false)));
        rock.set(DataComponents.LORE, new ItemLore(List.of(
                Component.translatable("item.rngambling.pet_rock.likes",
                        Component.translatable("item.rngambling.pet_rock.like." + random.nextInt(LIKES))).withStyle(ChatFormatting.GRAY),
                Component.translatable("item.rngambling.pet_rock.dislikes",
                        Component.translatable("item.rngambling.pet_rock.dislike." + random.nextInt(DISLIKES))).withStyle(ChatFormatting.GRAY))));
        return rock;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level instanceof ServerLevel server) {
            ItemStack rock = player.getItemInHand(hand);
            player.sendOverlayMessage(Component.translatable("item.rngambling.pet_rock.petted", rock.getHoverName()).withStyle(ChatFormatting.YELLOW));
            server.sendParticles(ParticleTypes.HEART, player.getX(), player.getEyeY() + 0.3, player.getZ(), 1, 0.2, 0.1, 0.2, 0);
            server.playSound(null, player.blockPosition(), SoundEvents.STONE_HIT, SoundSource.PLAYERS, 0.6f, 1.4f);
            player.getCooldowns().addCooldown(rock, 20);
        }
        return InteractionResult.SUCCESS;
    }
}
