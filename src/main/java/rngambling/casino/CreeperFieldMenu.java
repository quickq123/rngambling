package rngambling.casino;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import rngambling.RNGambling;
import rngambling.luck.PlayerLuck;

/**
 * Playing Creeper Field (rules in {@link CreeperField}). The creepers' places live on the server
 * only; the screen sees a block once it's dug, and the whole field when the game ends. Closing the
 * screen mid-game cashes out.
 *
 * <p>Luck makes every pot bigger, Bad Luck smaller (see {@link CasinoLuck#potFactor}): the next
 * pot shown already counts it.
 */
public class CreeperFieldMenu extends AbstractContainerMenu {
    /** Buttons: 0-3 the bet (see {@link Bets}), then these, then {@link #BUTTON_TILE} + the block's index (0-24, row by row). */
    public static final int BUTTON_START = 10, BUTTON_CASH = 11, BUTTON_FEWER = 12, BUTTON_MORE = 13, BUTTON_TILE = 100;

    public enum Phase { BETTING, DIGGING }

    public enum Result { NONE, BOOM, CASHED, CLEARED }

    /** What a block shows: still grass, dug and safe, the creeper that went off, and at the end the rest. */
    public static final int HIDDEN = 0, DUG = 1, BOOM = 2, CREEPER = 3, SAFE = 4;

    // Data slots: the 25 blocks, then the rest.
    public static final int PHASE = CreeperField.BLOCKS, RESULT = PHASE + 1, BET = PHASE + 2, CREEPERS = PHASE + 3, POT = PHASE + 4,
            NEXT_POT = PHASE + 5, TOKENS = PHASE + 6, LAST_WIN = PHASE + 7, DUG_COUNT = PHASE + 8;
    private static final int DATA_SIZE = PHASE + 9;

    private final BlockPos pos;
    private final Player player;
    private final ContainerData data = new SimpleContainerData(DATA_SIZE);
    private final boolean[] creepers = new boolean[CreeperField.BLOCKS];
    private Phase phase = Phase.BETTING;
    private int stake, pot, dug, count;

    public CreeperFieldMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(RNGambling.CREEPER_FIELD_MENU.get(), containerId);
        this.pos = pos;
        this.player = inventory.player;
        addDataSlots(data);
        data.set(BET, Bets.CREEPER_FIELD.start());
        data.set(CREEPERS, CreeperField.START_CREEPERS);
    }

    public int data(int index) {
        return data.get(index);
    }

    public int bet() {
        return Bets.CREEPER_FIELD.clamp(data.get(BET));
    }

    public int creeperCount() {
        return Math.max(CreeperField.MIN_CREEPERS, Math.min(CreeperField.MAX_CREEPERS, data.get(CREEPERS)));
    }

    public Phase phase() {
        return Phase.values()[Math.floorMod(data.get(PHASE), Phase.values().length)];
    }

    public Result result() {
        return Result.values()[Math.floorMod(data.get(RESULT), Result.values().length)];
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return true;
        }
        if (Bets.isButton(id) && phase == Phase.BETTING) {
            data.set(BET, Bets.CREEPER_FIELD.adjust(bet(), id));
        } else if ((id == BUTTON_FEWER || id == BUTTON_MORE) && phase == Phase.BETTING) {
            data.set(CREEPERS, Math.max(CreeperField.MIN_CREEPERS, Math.min(CreeperField.MAX_CREEPERS, creeperCount() + (id == BUTTON_MORE ? 1 : -1))));
        } else if (id == BUTTON_START && phase == Phase.BETTING) {
            start(serverPlayer);
        } else if (id == BUTTON_CASH && phase == Phase.DIGGING) {
            cashOut(serverPlayer, Result.CASHED);
        } else if (id >= BUTTON_TILE && id < BUTTON_TILE + CreeperField.BLOCKS && phase == Phase.DIGGING) {
            dig(serverPlayer, id - BUTTON_TILE);
        } else {
            return Bets.isButton(id) || (id >= BUTTON_START && id <= BUTTON_MORE) || (id >= BUTTON_TILE && id < BUTTON_TILE + CreeperField.BLOCKS);
        }
        sync();
        return true;
    }

    private void start(ServerPlayer player) {
        if (!Tokens.take(player.getInventory(), bet())) {
            return;
        }
        stake = bet();
        pot = stake;
        dug = 0;
        count = creeperCount();
        java.util.Arrays.fill(creepers, false);
        RandomSource random = player.level().getRandom();
        for (int placed = 0; placed < count; ) {
            int at = random.nextInt(CreeperField.BLOCKS);
            if (!creepers[at]) {
                creepers[at] = true;
                placed++;
            }
        }
        for (int i = 0; i < CreeperField.BLOCKS; i++) {
            data.set(i, HIDDEN);
        }
        phase = Phase.DIGGING;
        data.set(LAST_WIN, 0);
        data.set(RESULT, Result.NONE.ordinal());
        sound(SoundEvents.GRASS_BREAK, 1.0f);
    }

    private void dig(ServerPlayer player, int at) {
        if (data.get(at) != HIDDEN) {
            return;
        }
        if (creepers[at]) {
            data.set(at, BOOM);
            phase = Phase.BETTING;
            pot = 0;
            data.set(LAST_WIN, 0);
            data.set(RESULT, Result.BOOM.ordinal());
            reveal();
            player.level().playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.5f, 1.2f);
            return;
        }
        data.set(at, DUG);
        dug++;
        pot = CreeperField.pot(stake, count, dug, pot, PlayerLuck.level(player));
        sound(SoundEvents.GRAVEL_BREAK, 1.0f + dug * 0.03f);
        if (dug == CreeperField.BLOCKS - count || pot >= HigherLower.TABLE_LIMIT) {
            cashOut(player, Result.CLEARED);
        }
    }

    private void cashOut(Player player, Result result) {
        phase = Phase.BETTING;
        Tokens.give(player, pot);
        data.set(LAST_WIN, pot);
        data.set(RESULT, result.ordinal());
        reveal();
        if (pot > stake) {
            sound(SoundEvents.PLAYER_LEVELUP, 1.4f);
        }
        pot = 0;
    }

    /** At the end: every block shows what it was. */
    private void reveal() {
        for (int i = 0; i < CreeperField.BLOCKS; i++) {
            if (data.get(i) == HIDDEN) {
                data.set(i, creepers[i] ? CREEPER : SAFE);
            }
        }
    }

    private void sound(SoundEvent sound, float pitch) {
        player.level().playSound(null, pos, sound, SoundSource.BLOCKS, 0.6f, pitch);
    }

    private void sync() {
        data.set(PHASE, phase.ordinal());
        data.set(POT, pot);
        data.set(DUG_COUNT, dug);
        data.set(NEXT_POT, phase == Phase.DIGGING ? CreeperField.pot(stake, count, dug + 1, pot, PlayerLuck.level(player)) : 0);
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
        if (player instanceof ServerPlayer && phase == Phase.DIGGING) {
            cashOut(player, Result.CASHED);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(pos).is(RNGambling.CREEPER_FIELD.get())
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
