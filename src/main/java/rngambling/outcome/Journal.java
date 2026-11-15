package rngambling.outcome;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import rngambling.RNGambling;
import rngambling.RNGamblingConfig;

/**
 * The Lucky Journal: which effects a player has had happen to them. Each effect is recorded the
 * first time it's announced to them (a surprise only once it's revealed), kept through death and
 * sent to their game, where the Lucky Journal item's screen (client.JournalScreen) shows every
 * effect: the ones they've had with what they do, the rest as "???".
 *
 * <p>Also the server-wide announcement of Legendary and Catastrophic pulls.
 */
public final class Journal {
    private Journal() {
    }

    /** The ids of the effects this player has had, in the order they had them. */
    public static List<String> seen(net.minecraft.world.entity.player.Player player) {
        return player.getData(RNGambling.SEEN_EFFECTS);
    }

    /** How many effects there are to find (the catalog and every zone's own). */
    public static int total() {
        return Outcomes.everyEffect().size();
    }

    /** Records an effect; the first time, tells the player it's new. */
    static void record(ServerPlayer player, Outcome outcome) {
        List<String> seen = seen(player);
        if (seen.contains(outcome.id())) {
            return;
        }
        List<String> more = new ArrayList<>(seen);
        more.add(outcome.id());
        player.setData(RNGambling.SEEN_EFFECTS, List.copyOf(more));
        player.sendSystemMessage(Component.translatable("message.rngambling.journal.new", more.size(), total())
                .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8f, 1.3f);
    }

    /** For the admin command: every effect, or none. */
    public static void setAll(ServerPlayer player, boolean all) {
        player.setData(RNGambling.SEEN_EFFECTS, all ? List.copyOf(Outcomes.everyEffect().keySet()) : List.of());
    }

    /** Tells everyone else on the server about a Legendary or Catastrophic pull (if the config allows). */
    static void broadcast(ServerPlayer player, Outcome outcome) {
        Tier tier = outcome.tier();
        if ((tier != Tier.LEGENDARY && tier != Tier.CATASTROPHIC) || !RNGamblingConfig.ANNOUNCE_BIG_PULLS.getAsBoolean()) {
            return;
        }
        Component line = Component.translatable("outcome.rngambling.announce").withStyle(ChatFormatting.GOLD)
                .append(Component.translatable(tier == Tier.LEGENDARY ? "message.rngambling.broadcast.legendary" : "message.rngambling.broadcast.catastrophic",
                        player.getDisplayName(), Component.translatable("outcome.rngambling." + outcome.id()).withStyle(tier.colour, ChatFormatting.BOLD))
                        .withStyle(ChatFormatting.GRAY));
        for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
            if (other != player) {
                other.sendSystemMessage(line);
            }
        }
    }
}
