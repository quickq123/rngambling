package rngambling.outcome;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;

/**
 * LEG-07 Boss Bounty: an offer in chat, [Accept] or [Decline] (clickable; they run
 * {@code /rngambling bounty accept|decline}). Accepting starts the boss fight room for a massive
 * reward; declining (or letting a minute pass) gives a smaller reward, a Good roll.
 */
public final class Bounty {
    private record Offer(OutcomeContext context, long expires) {
    }

    private static final Map<UUID, Offer> OFFERS = new HashMap<>();
    private static final int WAIT = 20 * 60;

    private Bounty() {
    }

    static void offer(OutcomeContext c) {
        OFFERS.put(c.player().getUUID(), new Offer(c, c.level().getGameTime() + WAIT));
        Component accept = Component.translatable("message.rngambling.bounty.accept").withStyle(style -> style.withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent.RunCommand("/rngambling bounty accept"))
                .withHoverEvent(new HoverEvent.ShowText(Component.translatable("message.rngambling.bounty.accept_hint"))));
        Component decline = Component.translatable("message.rngambling.bounty.decline").withStyle(style -> style.withColor(ChatFormatting.GRAY)
                .withClickEvent(new ClickEvent.RunCommand("/rngambling bounty decline"))
                .withHoverEvent(new HoverEvent.ShowText(Component.translatable("message.rngambling.bounty.decline_hint"))));
        c.player().sendSystemMessage(Component.translatable("message.rngambling.bounty.offer").withStyle(ChatFormatting.GOLD)
                .append(" ").append(accept).append(" ").append(decline));
        // Unanswered after a minute: taken as declined.
        Scheduler.later(WAIT + 1, () -> {
            Offer offer = OFFERS.get(c.player().getUUID());
            if (offer != null && offer.context() == c) {
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
        if (accept && player.isAlive() && Sandbox.startRoom(player, new Rooms.BossBounty(), c.luck())) {
            return true;
        }
        player.sendSystemMessage(Component.translatable("message.rngambling.bounty.declined").withStyle(ChatFormatting.GRAY));
        Outcomes.playTier(player.level(), player.blockPosition(), player, c.luck(), c.chain(), Tier.GOOD);
        return true;
    }
}
