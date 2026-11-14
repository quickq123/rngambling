package rngambling.casino;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import rngambling.RNGambling;

/** Casino Tokens in a player's inventory: counting, taking bets and paying wins. */
public final class Tokens {
    private Tokens() {
    }

    public static int count(Inventory inventory) {
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(RNGambling.CASINO_TOKEN.get())) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** Takes {@code count} tokens if the player has them; returns whether it did. */
    public static boolean take(Inventory inventory, int count) {
        if (count(inventory) < count) {
            return false;
        }
        for (int slot = 0; slot < inventory.getContainerSize() && count > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(RNGambling.CASINO_TOKEN.get())) {
                int taken = Math.min(count, stack.getCount());
                stack.shrink(taken);
                count -= taken;
            }
        }
        inventory.setChanged();
        return true;
    }

    /** Gives tokens (dropping any that don't fit). */
    public static void give(Player player, int count) {
        give(player, new ItemStack(RNGambling.CASINO_TOKEN.get(), count));
    }

    /** Gives any item stack, in full stacks, dropping what doesn't fit. */
    public static void give(Player player, ItemStack stack) {
        int left = stack.getCount();
        while (left > 0) {
            int count = Math.min(left, stack.getMaxStackSize());
            player.getInventory().placeItemBackInInventory(stack.copyWithCount(count));
            left -= count;
        }
    }
}
