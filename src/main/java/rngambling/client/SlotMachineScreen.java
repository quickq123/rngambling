package rngambling.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import rngambling.RNGambling;
import rngambling.casino.SlotMachine;
import rngambling.casino.SlotMachineMenu;

/**
 * The slot machine's screen: three reels, the bet, the Spin button, the player's tokens and the
 * pay table. The result comes from the server; the screen spins the reels for show and stops
 * them one by one on it.
 */
public class SlotMachineScreen extends AbstractContainerScreen<SlotMachineMenu> {
    private static final Identifier SYMBOLS = RNGambling.id("textures/gui/slot_symbols.png");
    private static final int WIDTH = 176, HEIGHT = 196;
    private static final int RED = 0xFF6E0E16, RED_DARK = 0xFF420810, GOLD = 0xFFE8BA40, GOLD_DARK = 0xFFA87A1E, WHITE = 0xFFF2EEE4;
    private static final int REEL_W = 36, REEL_H = 40, REELS_X = 31, REELS_Y = 22;
    /** When each reel stops on the screen, in ticks after the spin (the win is paid at {@link SlotMachineMenu#SPIN_TICKS}). */
    private static final int[] STOP_TICKS = {14, 22, 30};

    private final BetControls bets = new BetControls(rngambling.casino.Bets.SLOTS, 30, 90);
    private Button spin;
    private int ticks;
    private int seenSpins;
    private int spinStartedTick = -1000;

    public SlotMachineScreen(SlotMachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        seenSpins = menu.data(SlotMachineMenu.SPINS);
        bets.init(leftPos, topPos, this::addRenderableWidget, this::click);
        spin = addRenderableWidget(Button.builder(Component.translatable("screen.rngambling.slot_machine.spin"), b -> click(SlotMachineMenu.BUTTON_SPIN))
                .bounds(leftPos + 112, topPos + 88, 54, 22).build());
    }

    private void click(int id) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private boolean animating() {
        return ticks - spinStartedTick < STOP_TICKS[2];
    }

    @Override
    public void containerTick() {
        super.containerTick();
        ticks++;
        int spins = menu.data(SlotMachineMenu.SPINS);
        if (spins != seenSpins) {
            seenSpins = spins;
            spinStartedTick = ticks;
        }
        int since = ticks - spinStartedTick;
        Minecraft minecraft = Minecraft.getInstance();
        if (animating() && since % 2 == 0) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.8f, 0.15f));
        }
        for (int stop : STOP_TICKS) {
            if (since == stop) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 1.0f, 0.6f));
            }
        }
        boolean busy = menu.spinning() || animating();
        int tokens = menu.data(SlotMachineMenu.TOKENS);
        bets.update(!busy, menu.bet());
        spin.active = !busy && tokens >= menu.bet();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos, y = topPos;
        // The cabinet: gold rim, red body.
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, GOLD_DARK);
        graphics.fill(x + 2, y + 2, x + WIDTH - 2, y + HEIGHT - 2, GOLD);
        graphics.fill(x + 4, y + 4, x + WIDTH - 4, y + HEIGHT - 4, RED);
        // The reel window.
        graphics.fill(x + REELS_X - 3, y + REELS_Y - 3, x + REELS_X + 3 * REEL_W + 5, y + REELS_Y + REEL_H + 3, GOLD);
        float since = ticks - spinStartedTick + partialTick;
        for (int reel = 0; reel < 3; reel++) {
            int rx = x + REELS_X + reel * (REEL_W + 1);
            graphics.fill(rx, y + REELS_Y, rx + REEL_W, y + REELS_Y + REEL_H, WHITE);
            boolean turning = since < STOP_TICKS[reel];
            SlotMachine.Symbol symbol;
            int bob = 0;
            if (turning) {
                // While turning, show symbols flicking past.
                int step = (int) (since * 2) + reel * 3;
                symbol = SlotMachine.Symbol.values()[Math.floorMod(step * 7 + reel, SlotMachine.Symbol.values().length)];
                bob = (int) ((since * 2 % 1) * 10) - 5;
            } else {
                symbol = menu.reel(reel);
            }
            drawSymbol(graphics, symbol, rx + 2, y + REELS_Y + 4 + bob, 2);
            if (turning) {
                graphics.fill(rx, y + REELS_Y, rx + REEL_W, y + REELS_Y + 6, WHITE);
                graphics.fill(rx, y + REELS_Y + REEL_H - 6, rx + REEL_W, y + REELS_Y + REEL_H, WHITE);
            }
        }
        // The win line.
        graphics.fill(x + REELS_X - 3, y + REELS_Y + REEL_H / 2, x + REELS_X - 1, y + REELS_Y + REEL_H / 2 + 1, RED_DARK);
        // The pay table.
        graphics.fill(x + 8, y + 112, x + WIDTH - 8, y + HEIGHT - 8, RED_DARK);
        SlotMachine.Symbol[] order = {SlotMachine.Symbol.SEVEN, SlotMachine.Symbol.BAR, SlotMachine.Symbol.LUCKY,
                SlotMachine.Symbol.BELL, SlotMachine.Symbol.LEMON, SlotMachine.Symbol.CHERRY};
        for (int i = 0; i < order.length; i++) {
            int px = x + 12 + (i % 2) * 80;
            int py = y + 116 + (i / 2) * 18;
            for (int k = 0; k < 3; k++) {
                drawSymbol(graphics, order[i], px + k * 14, py, 1);
            }
        }
        for (int k = 0; k < 2; k++) {
            drawSymbol(graphics, SlotMachine.Symbol.CHERRY, x + 12 + k * 14, y + 170, 1);
        }
    }

    private void drawSymbol(GuiGraphicsExtractor graphics, SlotMachine.Symbol symbol, int x, int y, float scale) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.blit(RenderPipelines.GUI_TEXTURED, SYMBOLS, 0, 0, symbol.ordinal() * 16.0F, 0.0F, 16, 16,
                16 * SlotMachine.Symbol.values().length, 16);
        graphics.pose().popMatrix();
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.centeredText(font, title, WIDTH / 2, 8, GOLD);
        // The result line.
        Component result;
        int colour = WHITE;
        if (animating() || menu.spinning()) {
            result = Component.translatable("screen.rngambling.slot_machine.spinning");
        } else if (menu.data(SlotMachineMenu.SPINS) == 0) {
            result = Component.translatable("screen.rngambling.slot_machine.ready");
        } else if (menu.data(SlotMachineMenu.LAST_WIN) > 0) {
            boolean prize = menu.reel(0) == SlotMachine.Symbol.LUCKY && menu.reel(1) == SlotMachine.Symbol.LUCKY
                    && menu.reel(2) == SlotMachine.Symbol.LUCKY;
            result = Component.translatable(prize ? "screen.rngambling.slot_machine.win_prize" : "screen.rngambling.slot_machine.win",
                    menu.data(SlotMachineMenu.LAST_WIN), menu.data(SlotMachineMenu.LAST_WIN) - menu.data(SlotMachineMenu.LAST_BET));
            colour = 0xFF7CFF7C;
        } else {
            result = Component.translatable("screen.rngambling.slot_machine.lose");
            colour = 0xFFFFB0B0;
        }
        graphics.centeredText(font, result, WIDTH / 2, REELS_Y + REEL_H + 6, colour);
        graphics.text(font, Component.translatable("screen.rngambling.slot_machine.bet"), 10, 95, GOLD, false);
        bets.drawAmount(graphics, font, menu.bet(), WHITE);
        graphics.text(font, Component.translatable("screen.rngambling.slot_machine.tokens", menu.data(SlotMachineMenu.TOKENS)), 10, 79, WHITE, false);
        // Pay table figures, beside their symbols.
        SlotMachine.Symbol[] order = {SlotMachine.Symbol.SEVEN, SlotMachine.Symbol.BAR, SlotMachine.Symbol.LUCKY,
                SlotMachine.Symbol.BELL, SlotMachine.Symbol.LEMON, SlotMachine.Symbol.CHERRY};
        for (int i = 0; i < order.length; i++) {
            String pays = "x" + order[i].threePays + (order[i] == SlotMachine.Symbol.LUCKY ? "+" : "");
            graphics.text(font, pays, 12 + (i % 2) * 80 + 44, 120 + (i / 2) * 18, GOLD, false);
        }
        graphics.text(font, "x" + SlotMachine.TWO_CHERRIES_PAY, 12 + 30, 174, GOLD, false);
        graphics.text(font, Component.translatable("screen.rngambling.slot_machine.prize_note"), 72, 174, 0xFFE0C090, false);
    }
}
