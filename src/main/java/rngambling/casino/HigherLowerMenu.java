package rngambling.casino;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import rngambling.RNGambling;
import rngambling.luck.PlayerLuck;

/**
 * Playing Higher or Lower (rules in {@link HigherLower}). The game lives on the server; the screen
 * is sent the cards and the pots as data slots. Closing the screen mid-game cashes out.
 */
public class HigherLowerMenu extends AbstractContainerMenu {
    /** Buttons: 0-3 the bet (see {@link Bets}), then the moves. */
    public static final int BUTTON_DEAL = 10, BUTTON_HIGHER = 11, BUTTON_LOWER = 12, BUTTON_SKIP = 13, BUTTON_CASH = 14;

    public enum Phase { BETTING, PLAYING }

    /** How the last move went, for the screen's message. */
    public enum Result { NONE, RIGHT, WRONG, CASHED, LIMIT, SKIPPED }

    // Data slots. Cards are stored +1 (0: none).
    public static final int CARD = 0, PREVIOUS = 1, PHASE = 2, RESULT = 3, BET = 4, POT = 5, IF_HIGHER = 6, IF_LOWER = 7,
            TOKENS = 8, LAST_WIN = 9, STREAK = 10;
    private static final int DATA_SIZE = 11;

    private final BlockPos pos;
    private final Player player;
    private final ContainerData data = new SimpleContainerData(DATA_SIZE);
    private Phase phase = Phase.BETTING;
    private int card = -1;
    private int stake;
    private int pot;
    /** The fair odds of every right guess so far, multiplied. */
    private double odds = 1;

    public HigherLowerMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(RNGambling.HIGHER_LOWER_MENU.get(), containerId);
        this.pos = pos;
        this.player = inventory.player;
        addDataSlots(data);
        data.set(BET, Bets.HIGHER_LOWER.start());
    }

    public int data(int index) {
        return data.get(index);
    }

    public int bet() {
        return Bets.HIGHER_LOWER.clamp(data.get(BET));
    }

    public Phase phase() {
        return Phase.values()[Math.floorMod(data.get(PHASE), Phase.values().length)];
    }

    public Result result() {
        return Result.values()[Math.floorMod(data.get(RESULT), Result.values().length)];
    }

    /** A card slot as a card, or -1 for none. */
    public int card(int slot) {
        return data.get(slot) - 1;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return true;
        }
        if (Bets.isButton(id) && phase == Phase.BETTING) {
            data.set(BET, Bets.HIGHER_LOWER.adjust(bet(), id));
        } else if (id == BUTTON_DEAL && phase == Phase.BETTING) {
            deal(serverPlayer);
        } else if ((id == BUTTON_HIGHER || id == BUTTON_LOWER) && phase == Phase.PLAYING) {
            guess(serverPlayer, id == BUTTON_HIGHER ? HigherLower.Guess.HIGHER : HigherLower.Guess.LOWER);
        } else if (id == BUTTON_SKIP && phase == Phase.PLAYING) {
            data.set(PREVIOUS, card + 1);
            card = Blackjack.draw(serverPlayer.level().getRandom());
            data.set(RESULT, Result.SKIPPED.ordinal());
            sound(SoundEvents.BOOK_PAGE_TURN, 1.0f);
        } else if (id == BUTTON_CASH && phase == Phase.PLAYING) {
            cashOut(serverPlayer, Result.CASHED);
        } else {
            return id >= 0 && id <= BUTTON_CASH;
        }
        sync();
        return true;
    }

    private void deal(ServerPlayer player) {
        if (!Tokens.take(player.getInventory(), bet())) {
            return;
        }
        stake = bet();
        pot = stake;
        odds = 1;
        card = Blackjack.draw(player.level().getRandom());
        phase = Phase.PLAYING;
        data.set(PREVIOUS, 0);
        data.set(STREAK, 0);
        data.set(LAST_WIN, 0);
        data.set(RESULT, Result.NONE.ordinal());
        sound(SoundEvents.BOOK_PAGE_TURN, 1.2f);
    }

    private void guess(ServerPlayer player, HigherLower.Guess guess) {
        var random = player.level().getRandom();
        int before = card;
        int next = Blackjack.draw(random);
        data.set(PREVIOUS, before + 1);
        card = next;
        if (HigherLower.wins(guess, before, next)) {
            odds /= HigherLower.chance(guess, before);
            pot = HigherLower.pot(stake, odds, pot, PlayerLuck.level(player));
            data.set(STREAK, data.get(STREAK) + 1);
            data.set(RESULT, Result.RIGHT.ordinal());
            sound(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.0f + Math.min(1.0f, data.get(STREAK) * 0.1f));
            if (pot >= HigherLower.TABLE_LIMIT) {
                cashOut(player, Result.LIMIT);
            }
        } else {
            phase = Phase.BETTING;
            pot = 0;
            data.set(LAST_WIN, 0);
            data.set(RESULT, Result.WRONG.ordinal());
            sound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6f);
        }
    }

    private void cashOut(Player player, Result result) {
        phase = Phase.BETTING;
        Tokens.give(player, pot);
        data.set(LAST_WIN, pot);
        data.set(RESULT, result.ordinal());
        if (pot > stake) {
            sound(SoundEvents.PLAYER_LEVELUP, 1.4f);
        }
        pot = 0;
    }

    private void sound(SoundEvent sound, float pitch) {
        player.level().playSound(null, pos, sound, SoundSource.BLOCKS, 0.6f, pitch);
    }

    private void sync() {
        data.set(CARD, phase == Phase.PLAYING || card >= 0 ? card + 1 : 0);
        data.set(PHASE, phase.ordinal());
        data.set(POT, pot);
        if (phase == Phase.PLAYING) {
            data.set(IF_HIGHER, HigherLower.pot(stake, odds / HigherLower.chance(HigherLower.Guess.HIGHER, card), pot, PlayerLuck.level(player)));
            data.set(IF_LOWER, HigherLower.pot(stake, odds / HigherLower.chance(HigherLower.Guess.LOWER, card), pot, PlayerLuck.level(player)));
        }
    }

    @Override
    public void broadcastChanges() {
        if (player instanceof ServerPlayer serverPlayer) {
            // Bets and wins change the inventory while this screen is open: keep the hotbar showing it.
            serverPlayer.inventoryMenu.broadcastChanges();
            sync();
            data.set(TOKENS, Tokens.count(player.getInventory()));
        }
        super.broadcastChanges();
    }

    /** Leaving mid-game cashes out. */
    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer && phase == Phase.PLAYING) {
            cashOut(player, Result.CASHED);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(pos).is(RNGambling.HIGHER_LOWER_TABLE.get())
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
