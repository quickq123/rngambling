package rngambling.outcome;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import rngambling.RNGambling;

/**
 * Dying in the Sandbox costs nothing but the trip: the player's drops (less items a room handed
 * out) and experience are kept with them, not scattered over the Sandbox, and given back when
 * they respawn. Saved with the player ({@link RNGambling#SANDBOX_DEATH}), so a restart between
 * death and respawn loses nothing.
 */
public final class SandboxDeaths {
    /** What a player lost dying in the Sandbox, waiting for their respawn. */
    public record Kit(List<ItemStack> items, int levels, float progress, int total) {
        public static final Kit EMPTY = new Kit(List.of(), 0, 0, 0);
        public static final MapCodec<Kit> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                ItemStack.CODEC.listOf().fieldOf("items").forGetter(Kit::items),
                Codec.INT.fieldOf("levels").forGetter(Kit::levels),
                Codec.FLOAT.fieldOf("progress").forGetter(Kit::progress),
                Codec.INT.fieldOf("total").forGetter(Kit::total)).apply(instance, Kit::new));

        public boolean isEmpty() {
            return items.isEmpty() && levels == 0 && progress == 0 && total == 0;
        }
    }

    private SandboxDeaths() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(SandboxDeaths::onDrops);
        NeoForge.EVENT_BUS.addListener((LivingExperienceDropEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player && Sandbox.inSandbox(player)) {
                event.setCanceled(true); // kept in the kit instead
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player && !event.isEndConquered()) {
                giveBack(player);
            }
        });
    }

    private static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !Sandbox.inSandbox(player)) {
            return;
        }
        List<ItemStack> items = new ArrayList<>();
        for (ItemEntity drop : event.getDrops()) {
            if (!drop.getItem().isEmpty() && !Room.isSandboxOnly(drop.getItem())) {
                items.add(drop.getItem().copy());
            }
        }
        keep(player, items);
        event.setCanceled(true);
    }

    /** Puts a dead player's drops and experience aside (added to any already waiting). */
    public static void keep(ServerPlayer player, List<ItemStack> items) {
        Kit before = player.getData(RNGambling.SANDBOX_DEATH.get());
        List<ItemStack> all = new ArrayList<>(before.items());
        all.addAll(items);
        player.setData(RNGambling.SANDBOX_DEATH.get(), new Kit(all, player.experienceLevel, player.experienceProgress, player.totalExperience));
    }

    /** On respawn: everything back. Returns whether there was anything. */
    public static boolean giveBack(ServerPlayer player) {
        Kit kit = player.getData(RNGambling.SANDBOX_DEATH.get());
        if (kit.isEmpty()) {
            return false;
        }
        player.removeData(RNGambling.SANDBOX_DEATH.get());
        for (ItemStack stack : kit.items()) {
            player.getInventory().placeItemBackInInventory(stack.copy());
        }
        player.setExperienceLevels(kit.levels());
        player.experienceProgress = kit.progress();
        player.totalExperience = kit.total();
        player.sendSystemMessage(Component.translatable("message.rngambling.sandbox.death_kept").withStyle(ChatFormatting.LIGHT_PURPLE));
        return true;
    }
}
