package rngambling.outcome;

import static rngambling.outcome.Outcome.of;
import static rngambling.outcome.Tier.BAD;
import static rngambling.outcome.Tier.GOOD;
import static rngambling.outcome.Tier.NEUTRAL;
import static rngambling.outcome.Tier.VERY_BAD;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import rngambling.RNGambling;
import rngambling.item.PetRockItem;

/**
 * Effects that play with the luck system itself (see {@link Fate}): Fortune Teller, Jinxed,
 * Insurance Policy and the Re-roll Ticket. And the Pet Rock, which plays with nothing at all.
 */
final class Fortunes {
    private Fortunes() {
    }

    /** How often Jinxed's bad tier is Very Bad rather than Bad. */
    static final double JINX_VERY_BAD = 0.3;

    static final List<Outcome> ALL = List.of(
            of("NEU-34", "fortune_teller", NEUTRAL, -15, 15, 5, c -> {
                // The tier is drawn now, at this block's luck (as a block's tier would be), and kept for the next one.
                Tier tier = drawTier(c);
                Fate.foretell(c.player(), Fate.FORTUNE, tier);
                c.player().sendSystemMessage(Component.translatable("message.rngambling.fate.fortune", tierName(tier))
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
                c.level().playSound(null, c.player().blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 0.8f);
            }),
            of("BAD-36", "jinxed", BAD, -35, -10, 5, c -> {
                Tier tier = c.chance(JINX_VERY_BAD) ? VERY_BAD : BAD;
                Fate.foretell(c.player(), Fate.JINX, tier);
                c.player().sendSystemMessage(Component.translatable("message.rngambling.fate.jinxed", tierName(tier))
                        .withStyle(ChatFormatting.DARK_PURPLE));
                c.level().playSound(null, c.player().blockPosition(), SoundEvents.WITCH_CELEBRATE, SoundSource.PLAYERS, 1.0f, 0.7f);
            }),
            of("GOD-43", "insurance_policy", GOOD, 15, 40, 5, c -> {
                boolean fresh = Fate.insure(c.player());
                c.player().sendSystemMessage(Component.translatable(fresh ? "message.rngambling.fate.insured" : "message.rngambling.fate.still_insured")
                        .withStyle(ChatFormatting.GREEN));
                c.level().playSound(null, c.player().blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 1.0f);
            }),
            of("GOD-44", "reroll_ticket", GOOD, 15, 45, 4, c -> c.drop(new ItemStack(RNGambling.REROLL_TICKET.get()))),
            of("NEU-35", "pet_rock", NEUTRAL, -10, 10, 4, c -> c.drop(PetRockItem.adopt(c.random())))
    );

    /** A tier drawn at the block's luck, by the same odds a block's tier is drawn with. */
    static Tier drawTier(OutcomeContext c) {
        double roll = c.random().nextDouble();
        Tier last = NEUTRAL;
        for (Map.Entry<Tier, Double> share : Outcomes.tierShares(c.luck()).entrySet()) {
            last = share.getKey();
            roll -= share.getValue();
            if (roll < 0) {
                return last;
            }
        }
        return last;
    }

    static Component tierName(Tier tier) {
        return Component.translatable("tier.rngambling." + tier.name().toLowerCase(Locale.ROOT)).withStyle(tier.colour, ChatFormatting.BOLD);
    }
}
