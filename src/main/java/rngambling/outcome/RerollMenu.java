package rngambling.outcome;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/**
 * GOD-44 Re-roll Ticket, used: a lucky block broken with the ticket in the off hand draws two
 * effects, and this one-row window shows both (named, in their tier's colour, with their
 * one-liners). Click one to have it; nothing else can be moved. Closing the window without
 * choosing plays one of the two at random (as the Genie does).
 */
public class RerollMenu extends ChestMenu {
    static final int[] SLOTS = {2, 6};
    private final SimpleContainer cards;
    private final List<Outcome> options;
    private final Choice choice;
    private boolean chosen;

    /** Plays the chosen effect. */
    interface Choice {
        void play(ServerPlayer player, Outcome outcome);
    }

    private RerollMenu(int containerId, Inventory inventory, SimpleContainer cards, List<Outcome> options, Choice choice) {
        super(MenuType.GENERIC_9x1, containerId, inventory, cards, 1);
        this.cards = cards;
        this.options = options;
        this.choice = choice;
    }

    static void open(ServerPlayer player, List<Outcome> options, Choice choice) {
        SimpleContainer cards = new SimpleContainer(9);
        for (int i = 0; i < SLOTS.length && i < options.size(); i++) {
            cards.setItem(SLOTS[i], card(options.get(i)));
        }
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new RerollMenu(id, inventory, cards, options, choice),
                Component.translatable("outcome.rngambling.reroll_ticket.title")));
    }

    /** A piece of paper named after the effect, its tier and one-liner beneath. */
    static ItemStack card(Outcome outcome) {
        ItemStack card = new ItemStack(Items.PAPER);
        card.set(DataComponents.CUSTOM_NAME, Component.translatable("outcome.rngambling." + outcome.id())
                .withStyle(style -> style.withColor(outcome.tier().colour).withBold(true).withItalic(false)));
        card.set(DataComponents.LORE, new ItemLore(List.of(
                Component.translatable("tier.rngambling." + outcome.tier().name().toLowerCase(java.util.Locale.ROOT))
                        .withStyle(style -> style.withColor(outcome.tier().colour).withItalic(false)),
                Component.translatable("outcome.rngambling." + outcome.id() + ".quip").withStyle(ChatFormatting.GRAY))));
        return card;
    }

    @Override
    public void clicked(int slotIndex, int buttonNum, ContainerInput input, Player player) {
        if (!chosen && player instanceof ServerPlayer server && (input == ContainerInput.PICKUP || input == ContainerInput.QUICK_MOVE)) {
            for (int i = 0; i < SLOTS.length && i < options.size(); i++) {
                if (SLOTS[i] == slotIndex) {
                    choose(server, i);
                    server.closeContainer();
                    return;
                }
            }
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
        if (!chosen && player instanceof ServerPlayer server && !options.isEmpty()) {
            choose(server, server.getRandom().nextInt(options.size()));
        }
    }

    private void choose(ServerPlayer player, int index) {
        chosen = true;
        cards.clearContent();
        if (!player.hasDisconnected()) {
            choice.play(player, options.get(index));
        }
    }

    /** Draws two different effects from {@code open} (one if there's only one) and offers them. */
    static void offer(ServerLevel level, BlockPos pos, ServerPlayer player, int luck, int chain, List<Outcome> open) {
        Outcome first = Outcomes.pick(level.getRandom(), luck, open, Outcomes.variety(player));
        List<Outcome> rest = new java.util.ArrayList<>(open);
        rest.removeIf(o -> o.id().equals(first.id()));
        List<Outcome> options = rest.isEmpty() ? List.of(first)
                : List.of(first, Outcomes.pick(level.getRandom(), luck, rest, Outcomes.variety(player)));
        BlockPos at = pos.immutable();
        open(player, options, (who, outcome) -> Outcomes.playDrawn(level, at, who, luck, chain, outcome));
    }
}
