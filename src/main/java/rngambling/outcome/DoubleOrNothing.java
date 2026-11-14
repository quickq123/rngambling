package rngambling.outcome;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import rngambling.RNGambling;
import rngambling.luck.Luck;

/**
 * WLD-02 Double or Nothing: an offer in chat, [Double] or [Walk away] (clickable; they run
 * {@code /rngambling double accept|walk}). Doubling rerolls at twice the block's luck (so a
 * lucky block gets luckier and an unlucky one worse); walking away (or letting a minute pass)
 * gives a few Casino Tokens.
 */
public final class DoubleOrNothing {
    private record Offer(OutcomeContext context) {
    }

    private static final Map<UUID, Offer> OFFERS = new HashMap<>();
    private static final int WAIT = 20 * 60;

    private DoubleOrNothing() {
    }

    static void offer(OutcomeContext c) {
        Offer mine = new Offer(c);
        OFFERS.put(c.player().getUUID(), mine);
        Component accept = Component.translatable("message.rngambling.double.accept").withStyle(style -> style.withColor(ChatFormatting.GOLD)
                .withClickEvent(new ClickEvent.RunCommand("/rngambling double accept"))
                .withHoverEvent(new HoverEvent.ShowText(Component.translatable("message.rngambling.double.accept_hint"))));
        Component walk = Component.translatable("message.rngambling.double.walk").withStyle(style -> style.withColor(ChatFormatting.GRAY)
                .withClickEvent(new ClickEvent.RunCommand("/rngambling double walk"))
                .withHoverEvent(new HoverEvent.ShowText(Component.translatable("message.rngambling.double.walk_hint"))));
        c.player().sendSystemMessage(Component.translatable("message.rngambling.double.offer").withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(" ").append(accept).append(" ").append(walk));
        Scheduler.later(WAIT + 1, () -> {
            if (OFFERS.get(c.player().getUUID()) == mine) {
                answer(c.player(), false);
            }
        });
    }

    /** The player's answer. Returns false if they have no offer waiting. */
    public static boolean answer(ServerPlayer player, boolean accept) {
        Offer offer = OFFERS.remove(player.getUUID());
        if (offer == null) {
            return false;
        }
        OutcomeContext c = offer.context();
        if (!player.isAlive()) {
            return true;
        }
        if (accept) {
            int doubled = Luck.clamp(c.luck() * 2);
            player.sendSystemMessage(Component.translatable("message.rngambling.double.doubled").withStyle(ChatFormatting.GOLD));
            Outcomes.playRandom(player.level(), player.blockPosition(), player, doubled, c.chain(), "double_or_nothing");
        } else {
            int tokens = 2 + player.getRandom().nextInt(3);
            player.sendSystemMessage(Component.translatable("message.rngambling.double.walked", tokens).withStyle(ChatFormatting.GRAY));
            player.getInventory().placeItemBackInInventory(new ItemStack(RNGambling.CASINO_TOKEN.get(), tokens));
        }
        return true;
    }
}
