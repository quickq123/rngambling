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
 * Playing a slot machine. Everything is decided on the server: the screen only asks to change
 * the bet or spin (menu buttons), and shows the reels and tokens it is sent (data slots). A win
 * is paid once the reels have had time to stop on the screen, or at once if the screen is closed.
 */
public class SlotMachineMenu extends AbstractContainerMenu {
    public static final int BUTTON_SPIN = 10;
    /** Buttons 0 to 3 change the bet (see {@link Bets}). */
    public static final int SPIN_TICKS = 32;

    // Data slots, synced to the screen.
    public static final int REEL_0 = 0, REEL_1 = 1, REEL_2 = 2, SPINS = 3, LAST_WIN = 4, BET = 5, TOKENS = 6, PAID = 7, LAST_BET = 8;
    private static final int DATA_SIZE = 9;

    private final BlockPos pos;
    private final Player player;
    private final ContainerData data;
    private int pendingTokens;
    private boolean pendingPrize;
    private long payAt = -1;

    /** On the server. */
    public SlotMachineMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(RNGambling.SLOT_MACHINE_MENU.get(), containerId);
        this.pos = pos;
        this.player = inventory.player;
        this.data = new SimpleContainerData(DATA_SIZE);
        addDataSlots(data);
        data.set(BET, Bets.SLOTS.start());
        data.set(REEL_0, SlotMachine.Symbol.SEVEN.ordinal());
        data.set(REEL_1, SlotMachine.Symbol.SEVEN.ordinal());
        data.set(REEL_2, SlotMachine.Symbol.SEVEN.ordinal());
    }

    public int data(int index) {
        return data.get(index);
    }

    public SlotMachine.Symbol reel(int index) {
        return SlotMachine.Symbol.values()[Math.floorMod(data.get(REEL_0 + index), SlotMachine.Symbol.values().length)];
    }

    public int bet() {
        return Bets.SLOTS.clamp(data.get(BET));
    }

    /** Whether a spin's win is still to be paid (the reels are still turning on the screen). */
    public boolean spinning() {
        return data.get(PAID) != data.get(SPINS);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (Bets.isButton(id)) {
            if (!spinning()) {
                data.set(BET, Bets.SLOTS.adjust(bet(), id));
            }
            return true;
        }
        if (id == BUTTON_SPIN && player instanceof ServerPlayer serverPlayer) {
            spin(serverPlayer);
            return true;
        }
        return false;
    }

    private void spin(ServerPlayer player) {
        int bet = bet();
        if (spinning() || countTokens(player.getInventory()) < bet) {
            return;
        }
        takeTokens(player.getInventory(), bet);
        var random = player.level().getRandom();
        SlotMachine.Symbol[] reels = CasinoLuck.spin(random, rngambling.luck.PlayerLuck.level(player),
                () -> SlotMachine.spin(random), r -> SlotMachine.pays(r) > 0);
        for (int i = 0; i < 3; i++) {
            data.set(REEL_0 + i, reels[i].ordinal());
        }
        pendingTokens = bet * SlotMachine.pays(reels);
        pendingPrize = SlotMachine.winsLuckyBlock(reels);
        payAt = player.level().getGameTime() + SPIN_TICKS;
        data.set(LAST_WIN, 0);
        data.set(LAST_BET, bet);
        data.set(SPINS, data.get(SPINS) + 1);
        player.level().playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8f, 0.7f);
    }

    /** Pays the spin's win, if it's due (or {@code now}). */
    private void payOut(Player player, boolean now) {
        if (payAt < 0 || (!now && player.level().getGameTime() < payAt)) {
            return;
        }
        payAt = -1;
        if (pendingTokens > 0) {
            give(player, new ItemStack(RNGambling.CASINO_TOKEN.get(), pendingTokens));
        }
        if (pendingPrize) {
            give(player, new ItemStack(RNGambling.LUCKY_BLOCK_ITEM.get()));
        }
        if (pendingTokens > 0 || pendingPrize) {
            boolean big = pendingTokens >= bet() * 18 || pendingPrize;
            player.level().playSound(null, pos, big ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.PLAYER_LEVELUP,
                    SoundSource.BLOCKS, big ? 0.6f : 0.5f, big ? 1.0f : 1.6f);
        }
        data.set(LAST_WIN, pendingTokens);
        data.set(PAID, data.get(SPINS));
        pendingTokens = 0;
        pendingPrize = false;
    }

    private static void give(Player player, ItemStack stack) {
        int left = stack.getCount();
        while (left > 0) {
            int count = Math.min(left, stack.getMaxStackSize());
            player.getInventory().placeItemBackInInventory(stack.copyWithCount(count));
            left -= count;
        }
    }

    public static int countTokens(Inventory inventory) {
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(RNGambling.CASINO_TOKEN.get())) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void takeTokens(Inventory inventory, int count) {
        for (int slot = 0; slot < inventory.getContainerSize() && count > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(RNGambling.CASINO_TOKEN.get())) {
                int taken = Math.min(count, stack.getCount());
                stack.shrink(taken);
                count -= taken;
            }
        }
        inventory.setChanged();
    }

    /** Runs every tick while the screen is open, on the server: pays wins when due, and keeps the token count current. */
    @Override
    public void broadcastChanges() {
        if (player instanceof ServerPlayer serverPlayer) {
            payOut(player, false);
            // Bets and wins change the inventory while this screen is open: keep the hotbar showing it.
            serverPlayer.inventoryMenu.broadcastChanges();
            data.set(TOKENS, countTokens(player.getInventory()));
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
        return player.level().getBlockState(pos).is(RNGambling.SLOT_MACHINE.get())
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
