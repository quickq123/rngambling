package rngambling.outcome;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/**
 * LEG-09 Genie's Wish: a one-row chest window with three Legendary items. Click one to take it;
 * nothing else can be moved. Closing it without choosing grants one of the three at random
 * (a wish isn't wasted).
 */
public class GenieMenu extends ChestMenu {
    static final int[] SLOTS = {2, 4, 6};
    private final SimpleContainer wishes;
    private boolean granted;

    private GenieMenu(int containerId, Inventory inventory, SimpleContainer wishes) {
        super(MenuType.GENERIC_9x1, containerId, inventory, wishes, 1);
        this.wishes = wishes;
    }

    public static void open(ServerPlayer player, List<ItemStack> choices) {
        SimpleContainer wishes = new SimpleContainer(9);
        for (int i = 0; i < SLOTS.length && i < choices.size(); i++) {
            wishes.setItem(SLOTS[i], choices.get(i));
        }
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new GenieMenu(id, inventory, wishes),
                Component.translatable("outcome.rngambling.genies_wish.title")));
    }

    @Override
    public void clicked(int slotIndex, int buttonNum, ContainerInput input, Player player) {
        if (!granted && player instanceof ServerPlayer server && slotIndex >= 0 && slotIndex < 9
                && !wishes.getItem(slotIndex).isEmpty() && (input == ContainerInput.PICKUP || input == ContainerInput.QUICK_MOVE)) {
            grant(server, slotIndex);
            server.closeContainer();
            return;
        }
        // Everything else is refused; the client is told the real contents again.
        sendAllDataToRemote();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!granted && player instanceof ServerPlayer server) {
            grant(server, SLOTS[server.getRandom().nextInt(SLOTS.length)]);
        }
    }

    private void grant(ServerPlayer player, int slot) {
        granted = true;
        ItemStack wish = wishes.getItem(slot).copy();
        wishes.clearContent();
        if (wish.isEmpty()) {
            return;
        }
        player.sendSystemMessage(Component.translatable("outcome.rngambling.genies_wish.granted", wish.getDisplayName()).withStyle(ChatFormatting.GOLD));
        player.level().playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1.0f, 1.2f);
        player.getInventory().placeItemBackInInventory(wish);
    }
}
