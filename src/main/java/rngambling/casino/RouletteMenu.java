package rngambling.casino;

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
 * Playing roulette. The screen asks to choose the bet and spin (menu buttons) and shows what the
 * server decides (data slots); the win is paid once the ball has had time to settle on the
 * screen, or at once if the screen is closed.
 */
public class RouletteMenu extends AbstractContainerMenu {
    /** Buttons: 0-3 change the bet (see {@link Bets}), 10 + {@link Roulette.Bet} ordinal the kind of bet, 30/31 number down/up, 40 spin. */
    public static final int BUTTON_KIND = 10, BUTTON_NUMBER_DOWN = 30, BUTTON_NUMBER_UP = 31, BUTTON_SPIN = 40;
    public static final int SPIN_TICKS = 70;

    public static final int BET = 0, KIND = 1, NUMBER = 2, RESULT = 3, SPINS = 4, PAID = 5, LAST_WIN = 6, TOKENS = 7, LAST_BET = 8;
    private static final int DATA_SIZE = 9;

    private final BlockPos pos;
    private final Player player;
    private final ContainerData data = new SimpleContainerData(DATA_SIZE);
    private int pending;
    private long payAt = -1;

    public RouletteMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(RNGambling.ROULETTE_MENU.get(), containerId);
        this.pos = pos;
        this.player = inventory.player;
        addDataSlots(data);
        data.set(BET, Bets.ROULETTE.start());
        data.set(NUMBER, 17);
        data.set(RESULT, -1);
    }

    public int data(int index) {
        return data.get(index);
    }

    public int bet() {
        return Bets.ROULETTE.clamp(data.get(BET));
    }

    public Roulette.Bet kind() {
        return Roulette.Bet.values()[Math.floorMod(data.get(KIND), Roulette.Bet.values().length)];
    }

    public boolean spinning() {
        return data.get(PAID) != data.get(SPINS);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (spinning()) {
            return true;
        }
        if (Bets.isButton(id)) {
            data.set(BET, Bets.ROULETTE.adjust(bet(), id));
        } else if (id >= BUTTON_KIND && id < BUTTON_KIND + Roulette.Bet.values().length) {
            data.set(KIND, id - BUTTON_KIND);
        } else if (id == BUTTON_NUMBER_DOWN || id == BUTTON_NUMBER_UP) {
            data.set(NUMBER, Math.floorMod(data.get(NUMBER) + (id == BUTTON_NUMBER_UP ? 1 : -1), 37));
            data.set(KIND, Roulette.Bet.NUMBER.ordinal());
        } else if (id == BUTTON_SPIN && player instanceof ServerPlayer serverPlayer) {
            spin(serverPlayer);
        } else {
            return false;
        }
        return true;
    }

    private void spin(ServerPlayer player) {
        int bet = bet();
        if (!Tokens.take(player.getInventory(), bet)) {
            return;
        }
        int number = Roulette.spin(player.level().getRandom());
        data.set(RESULT, number);
        pending = bet * Roulette.returns(kind(), data.get(NUMBER), number);
        payAt = player.level().getGameTime() + SPIN_TICKS;
        data.set(LAST_WIN, 0);
        data.set(LAST_BET, bet);
        data.set(SPINS, data.get(SPINS) + 1);
        player.level().playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.6f, 0.6f);
    }

    private void payOut(Player player, boolean now) {
        if (payAt < 0 || (!now && player.level().getGameTime() < payAt)) {
            return;
        }
        payAt = -1;
        if (pending > 0) {
            Tokens.give(player, pending);
            player.level().playSound(null, pos, pending >= bet() * 30 ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.PLAYER_LEVELUP,
                    SoundSource.BLOCKS, 0.5f, 1.6f);
        }
        data.set(LAST_WIN, pending);
        data.set(PAID, data.get(SPINS));
        pending = 0;
    }

    @Override
    public void broadcastChanges() {
        if (player instanceof ServerPlayer serverPlayer) {
            payOut(player, false);
            // Bets and wins change the inventory while this screen is open: keep the hotbar showing it.
            serverPlayer.inventoryMenu.broadcastChanges();
            data.set(TOKENS, Tokens.count(player.getInventory()));
        }
        super.broadcastChanges();
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer) {
            payOut(player, true);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(pos).is(RNGambling.ROULETTE_TABLE.get())
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
