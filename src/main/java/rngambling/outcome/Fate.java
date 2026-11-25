package rngambling.outcome;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import rngambling.RNGambling;

/**
 * What lies ahead of a player's next lucky block, set by the luck-system effects (see
 * {@link Fortunes}) and saved with them, through death:
 * <ul>
 * <li>an <b>omen</b>: the tier their next lucky block will be, foretold (Fortune Teller) or
 * jinxed (Jinxed, always a bad tier). The newest omen replaces an older one.
 * <li><b>insured</b> (Insurance Policy): the next bad effect drawn for them is cancelled.
 * </ul>
 * The omen is used up by the next lucky block they break; the insurance by the next bad effect.
 * Both apply to draws from lucky blocks (and the redraws effects like Coin Flip make), never to
 * effects an admin plays with the effect command.
 *
 * @param omen "" (none), {@link #FORTUNE} or {@link #JINX}
 * @param tier the omen's tier, by {@link Tier#name()} ("" with no omen)
 */
public record Fate(boolean insured, String omen, String tier) {
    public static final String FORTUNE = "fortune", JINX = "jinx";
    public static final Fate NONE = new Fate(false, "", "");
    public static final MapCodec<Fate> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.BOOL.optionalFieldOf("insured", false).forGetter(Fate::insured),
            Codec.STRING.optionalFieldOf("omen", "").forGetter(Fate::omen),
            Codec.STRING.optionalFieldOf("tier", "").forGetter(Fate::tier)
    ).apply(i, Fate::new));

    public boolean isEmpty() {
        return !insured && omen.isEmpty();
    }

    /** The omen's tier, or null with none (or a tier this version doesn't know). */
    public Tier omenTier() {
        if (omen.isEmpty()) {
            return null;
        }
        try {
            return Tier.valueOf(tier);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    public static Fate of(ServerPlayer player) {
        return player.getData(RNGambling.FATE);
    }

    static void set(ServerPlayer player, Fate fate) {
        player.setData(RNGambling.FATE, fate);
    }

    /** Sets an omen for the player's next lucky block, replacing any other. */
    public static void foretell(ServerPlayer player, String omen, Tier tier) {
        Fate fate = of(player);
        set(player, new Fate(fate.insured(), omen, tier.name()));
    }

    /** Insures the player against their next bad effect. Returns false if they already were. */
    public static boolean insure(ServerPlayer player) {
        Fate fate = of(player);
        set(player, new Fate(true, fate.omen(), fate.tier()));
        return !fate.insured();
    }

    /**
     * The effects the player's next lucky block can draw from: only the omen's tier if they have
     * one (using it up, and telling them it came true), else all of {@code open}. An omen whose
     * tier has nothing open here is used up without effect.
     */
    static List<Outcome> applyOmen(ServerPlayer player, List<Outcome> open) {
        Fate fate = of(player);
        Tier tier = fate.omenTier();
        if (fate.omen().isEmpty()) {
            return open;
        }
        set(player, new Fate(fate.insured(), "", ""));
        if (tier == null) {
            return open;
        }
        List<Outcome> narrowed = new ArrayList<>(open);
        narrowed.removeIf(o -> o.tier() != tier);
        if (narrowed.isEmpty()) {
            return open;
        }
        player.sendSystemMessage(Component.translatable(fate.omen().equals(JINX) ? "message.rngambling.fate.jinx_strikes" : "message.rngambling.fate.foretold",
                Component.translatable("tier.rngambling." + tier.name().toLowerCase(java.util.Locale.ROOT)).withStyle(tier.colour, ChatFormatting.BOLD))
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
        return narrowed;
    }

    /**
     * If the player is insured and this effect is bad, uses up the insurance and tells them the
     * effect never happened. Returns true if it was cancelled.
     */
    static boolean claim(ServerPlayer player, Outcome outcome) {
        Fate fate = of(player);
        if (!fate.insured() || outcome.tier().sign >= 0) {
            return false;
        }
        set(player, new Fate(false, fate.omen(), fate.tier()));
        player.sendSystemMessage(Component.translatable("message.rngambling.fate.claim_paid",
                Component.translatable("outcome.rngambling." + outcome.id()).withStyle(outcome.tier().colour, ChatFormatting.BOLD))
                .withStyle(ChatFormatting.GREEN));
        player.sendOverlayMessage(Component.translatable("message.rngambling.fate.claim_overlay").withStyle(ChatFormatting.GREEN));
        player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.PLAYERS, 1.0f, 1.0f);
        player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 0.8f);
        return true;
    }

    /** True if the player holds a Re-roll Ticket in their off hand (used up by {@link #takeTicket}). */
    static boolean holdsTicket(ServerPlayer player) {
        return player.getItemInHand(InteractionHand.OFF_HAND).is(RNGambling.REROLL_TICKET.get());
    }

    static void takeTicket(ServerPlayer player) {
        player.getItemInHand(InteractionHand.OFF_HAND).shrink(1);
    }
}
