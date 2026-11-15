package rngambling.casino;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import rngambling.RNGambling;

/**
 * Playing blackjack. The hands live on the server; the screen is sent the cards it may see
 * (data slots), so the dealer's face-down card isn't known to the player's game until it's
 * turned over. The dealer plays out its hand one card at a time. Closing the screen mid-hand
 * stands and settles the hand at once.
 */
public class BlackjackMenu extends AbstractContainerMenu {
    /** Buttons: 0-2 bet size, then the moves. */
    public static final int BUTTON_DEAL = 10, BUTTON_HIT = 11, BUTTON_STAND = 12, BUTTON_DOUBLE = 13;
    public static final int MAX_CARDS = 10;
    /** A card slot's value: 0 no card, 1-52 a card (card + 1), {@link #HIDDEN} the dealer's face-down card. */
    public static final int HIDDEN = 53;
    private static final int DEALER_CARD_TICKS = 14;

    public enum Phase { BETTING, PLAYER, DEALER }

    // Data slots: the player's cards, the dealer's cards, then the rest.
    public static final int PLAYER_CARDS = 0, DEALER_CARDS = MAX_CARDS, PHASE = 2 * MAX_CARDS, OUTCOME = PHASE + 1, BET = PHASE + 2,
            STAKE = PHASE + 3, TOKENS = PHASE + 4, LAST_WIN = PHASE + 5, HANDS = PHASE + 6;
    private static final int DATA_SIZE = PHASE + 7;

    private final BlockPos pos;
    private final Player player;
    private final ContainerData data = new SimpleContainerData(DATA_SIZE);
    private final List<Integer> playerHand = new ArrayList<>();
    private final List<Integer> dealerHand = new ArrayList<>();
    private Phase phase = Phase.BETTING;
    private int stake;
    private long nextDealerCard;

    public BlackjackMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(RNGambling.BLACKJACK_MENU.get(), containerId);
        this.pos = pos;
        this.player = inventory.player;
        addDataSlots(data);
        data.set(BET, Bets.BLACKJACK.start());
    }

    public int data(int index) {
        return data.get(index);
    }

    public int bet() {
        return Bets.BLACKJACK.clamp(data.get(BET));
    }

    public Phase phase() {
        return Phase.values()[Math.floorMod(data.get(PHASE), Phase.values().length)];
    }

    public Blackjack.Outcome outcome() {
        return Blackjack.Outcome.values()[Math.floorMod(data.get(OUTCOME), Blackjack.Outcome.values().length)];
    }

    /** The cards the screen may show: -1 none, {@link #HIDDEN} face down, else the card. */
    public List<Integer> cards(boolean dealer) {
        List<Integer> cards = new ArrayList<>();
        for (int i = 0; i < MAX_CARDS; i++) {
            int value = data.get((dealer ? DEALER_CARDS : PLAYER_CARDS) + i);
            if (value == 0) {
                break;
            }
            cards.add(value == HIDDEN ? HIDDEN : value - 1);
        }
        return cards;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return true;
        }
        if (Bets.isButton(id) && phase == Phase.BETTING) {
            data.set(BET, Bets.BLACKJACK.adjust(bet(), id));
        } else if (id == BUTTON_DEAL && phase == Phase.BETTING) {
            deal(serverPlayer);
        } else if (id == BUTTON_HIT && phase == Phase.PLAYER) {
            playerHand.add(CasinoLuck.playerCard(serverPlayer.level().getRandom(), luck(serverPlayer), playerHand));
            sound(SoundEvents.BOOK_PAGE_TURN, 1.4f);
            if (Blackjack.total(playerHand) > 21) {
                settle(serverPlayer, Blackjack.Outcome.BUST);
            } else if (Blackjack.total(playerHand) == 21) {
                startDealer(serverPlayer);
            }
        } else if (id == BUTTON_STAND && phase == Phase.PLAYER) {
            startDealer(serverPlayer);
        } else if (id == BUTTON_DOUBLE && phase == Phase.PLAYER && playerHand.size() == 2 && Tokens.take(serverPlayer.getInventory(), stake)) {
            stake *= 2;
            playerHand.add(CasinoLuck.playerCard(serverPlayer.level().getRandom(), luck(serverPlayer), playerHand));
            sound(SoundEvents.BOOK_PAGE_TURN, 1.4f);
            if (Blackjack.total(playerHand) > 21) {
                settle(serverPlayer, Blackjack.Outcome.BUST);
            } else {
                startDealer(serverPlayer);
            }
        } else {
            return id >= 0 && id <= BUTTON_DOUBLE;
        }
        sync();
        return true;
    }

    private void deal(ServerPlayer player) {
        if (!Tokens.take(player.getInventory(), bet())) {
            return;
        }
        stake = bet();
        playerHand.clear();
        dealerHand.clear();
        var random = player.level().getRandom();
        int luck = luck(player);
        playerHand.add(CasinoLuck.playerCard(random, luck, playerHand));
        dealerHand.add(CasinoLuck.dealerCard(random, luck, dealerHand));
        playerHand.add(CasinoLuck.playerCard(random, luck, playerHand));
        dealerHand.add(CasinoLuck.dealerCard(random, luck, dealerHand));
        data.set(OUTCOME, Blackjack.Outcome.NONE.ordinal());
        data.set(LAST_WIN, 0);
        data.set(HANDS, data.get(HANDS) + 1);
        sound(SoundEvents.BOOK_PAGE_TURN, 1.2f);
        boolean playerBlackjack = Blackjack.blackjack(playerHand);
        boolean dealerBlackjack = Blackjack.blackjack(dealerHand);
        if (playerBlackjack || dealerBlackjack) {
            settle(player, playerBlackjack && dealerBlackjack ? Blackjack.Outcome.PUSH
                    : playerBlackjack ? Blackjack.Outcome.BLACKJACK : Blackjack.Outcome.LOSE);
        } else {
            phase = Phase.PLAYER;
        }
    }

    /** The player's luck level (Luck minus Bad Luck), which tilts the cards: see CasinoLuck. */
    private static int luck(Player player) {
        return rngambling.luck.PlayerLuck.level(player);
    }

    private void startDealer(ServerPlayer player) {
        phase = Phase.DEALER;
        nextDealerCard = player.level().getGameTime() + DEALER_CARD_TICKS;
    }

    /** The dealer's turn, one step at a time: draw while it must, then settle. */
    private void dealerStep(Player player, boolean all) {
        while (phase == Phase.DEALER && (all || player.level().getGameTime() >= nextDealerCard)) {
            if (Blackjack.dealerHits(dealerHand)) {
                dealerHand.add(CasinoLuck.dealerCard(player.level().getRandom(), luck(player), dealerHand));
                sound(SoundEvents.BOOK_PAGE_TURN, 1.0f);
                nextDealerCard = player.level().getGameTime() + DEALER_CARD_TICKS;
            } else {
                settle(player, Blackjack.compare(playerHand, dealerHand));
            }
        }
    }

    private void settle(Player player, Blackjack.Outcome outcome) {
        phase = Phase.BETTING;
        int win = Blackjack.returns(outcome, stake);
        if (win > 0) {
            Tokens.give(player, win);
        }
        data.set(OUTCOME, outcome.ordinal());
        data.set(LAST_WIN, win);
        if (win > stake) {
            sound(outcome == Blackjack.Outcome.BLACKJACK ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.PLAYER_LEVELUP, 1.4f);
        }
    }

    private void sound(net.minecraft.sounds.SoundEvent sound, float pitch) {
        player.level().playSound(null, pos, sound, SoundSource.BLOCKS, 0.6f, pitch);
    }

    private void sound(net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> sound, float pitch) {
        sound(sound.value(), pitch);
    }

    /** Copies the hands into the data slots, keeping the dealer's second card face down during the player's turn. */
    private void sync() {
        for (int i = 0; i < MAX_CARDS; i++) {
            data.set(PLAYER_CARDS + i, i < playerHand.size() ? playerHand.get(i) + 1 : 0);
            boolean hidden = i == 1 && phase == Phase.PLAYER;
            data.set(DEALER_CARDS + i, i < dealerHand.size() ? (hidden ? HIDDEN : dealerHand.get(i) + 1) : 0);
        }
        data.set(PHASE, phase.ordinal());
        data.set(STAKE, stake);
    }

    @Override
    public void broadcastChanges() {
        if (player instanceof ServerPlayer serverPlayer) {
            dealerStep(player, false);
            // Bets and wins change the inventory while this screen is open: keep the hotbar showing it.
            serverPlayer.inventoryMenu.broadcastChanges();
            sync();
            data.set(TOKENS, Tokens.count(player.getInventory()));
        }
        super.broadcastChanges();
    }

    /** Leaving mid-hand stands: the dealer plays out at once and the hand is settled. */
    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer) {
            if (phase == Phase.PLAYER) {
                phase = Phase.DEALER;
            }
            dealerStep(player, true);
            sync();
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(pos).is(RNGambling.BLACKJACK_TABLE.get())
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
