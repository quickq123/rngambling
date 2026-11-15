package rngambling.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import rngambling.casino.Roulette;
import rngambling.casino.RouletteMenu;

/**
 * The roulette table's screen. The wheel is shown as a strip of its pockets (in wheel order)
 * sliding past a marker and slowing to a stop on the number the server drew.
 */
public class RouletteScreen extends AbstractContainerScreen<RouletteMenu> {
    private static final int WIDTH = 204, HEIGHT = 184;
    private static final int FELT = 0xFF14632E, FELT_DARK = 0xFF0C4320, GOLD = 0xFFE8BA40, WOOD = 0xFF5A3418, WHITE = 0xFFF2EEE4;
    private static final int POCKET_RED = 0xFFC0182A, POCKET_BLACK = 0xFF1A1A1E, POCKET_GREEN = 0xFF1C9A48;
    private static final int STRIP_X = 12, STRIP_Y = 20, STRIP_W = WIDTH - 24, STRIP_H = 24, CELL = 22;
    /** How long the ball takes to settle on the screen (the win is paid at {@link RouletteMenu#SPIN_TICKS}). */
    private static final int ANIMATION_TICKS = 60;

    private static final Roulette.Bet[] KIND_BUTTONS = {
            Roulette.Bet.RED, Roulette.Bet.BLACK, Roulette.Bet.GREEN, Roulette.Bet.ODD, Roulette.Bet.EVEN,
            Roulette.Bet.LOW, Roulette.Bet.HIGH, Roulette.Bet.DOZEN_1, Roulette.Bet.DOZEN_2, Roulette.Bet.DOZEN_3};
    private final Button[] kinds = new Button[KIND_BUTTONS.length];
    private final BetControls bets = new BetControls(rngambling.casino.Bets.ROULETTE, 32, 140);
    private Button numberDown, numberUp, number, spin;

    private int ticks;
    private int seenSpins;
    private int startTick = -1000;
    /** Where the strip rests (in pockets, wheel order) and where a spin started from and goes to. */
    private float from, to;

    public RouletteScreen(RouletteMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        seenSpins = menu.data(RouletteMenu.SPINS);
        int result = menu.data(RouletteMenu.RESULT);
        from = to = wheelIndex(result < 0 ? 0 : result);
        for (int i = 0; i < KIND_BUTTONS.length; i++) {
            int id = RouletteMenu.BUTTON_KIND + KIND_BUTTONS[i].ordinal();
            kinds[i] = addRenderableWidget(Button.builder(Component.empty(), b -> click(id))
                    .bounds(leftPos + 12 + (i % 5) * 36, topPos + 74 + (i / 5) * 20, 35, 18).build());
        }
        numberDown = addRenderableWidget(Button.builder(Component.literal("<"), b -> click(RouletteMenu.BUTTON_NUMBER_DOWN))
                .bounds(leftPos + 57, topPos + 114, 14, 18).build());
        number = addRenderableWidget(Button.builder(Component.empty(), b -> click(RouletteMenu.BUTTON_KIND + Roulette.Bet.NUMBER.ordinal()))
                .bounds(leftPos + 72, topPos + 114, 70, 18).build());
        numberUp = addRenderableWidget(Button.builder(Component.literal(">"), b -> click(RouletteMenu.BUTTON_NUMBER_UP))
                .bounds(leftPos + 143, topPos + 114, 14, 18).build());
        bets.init(leftPos, topPos, this::addRenderableWidget, this::click);
        spin = addRenderableWidget(Button.builder(Component.translatable("screen.rngambling.slot_machine.spin"), b -> click(RouletteMenu.BUTTON_SPIN))
                .bounds(leftPos + 128, topPos + 138, 64, 22).build());
    }

    private void click(int id) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private static int wheelIndex(int number) {
        for (int i = 0; i < Roulette.WHEEL.length; i++) {
            if (Roulette.WHEEL[i] == number) {
                return i;
            }
        }
        return 0;
    }

    private boolean animating() {
        return ticks - startTick < ANIMATION_TICKS;
    }

    private float position(float partialTick) {
        float t = Math.min(1f, (ticks - startTick + partialTick) / ANIMATION_TICKS);
        float eased = 1 - (1 - t) * (1 - t) * (1 - t);
        return from + (to - from) * eased;
    }

    private static Component kindLabel(Roulette.Bet kind) {
        return Component.translatable("screen.rngambling.roulette.bet." + kind.name().toLowerCase(java.util.Locale.ROOT));
    }

    @Override
    public void containerTick() {
        super.containerTick();
        ticks++;
        int spins = menu.data(RouletteMenu.SPINS);
        if (spins != seenSpins) {
            seenSpins = spins;
            from = position(0) % Roulette.WHEEL.length;
            float target = wheelIndex(menu.data(RouletteMenu.RESULT));
            to = target + Roulette.WHEEL.length * 3;
            while (to - from < Roulette.WHEEL.length * 2) {
                to += Roulette.WHEEL.length;
            }
            startTick = ticks;
        }
        if (animating()) {
            // A click each time a pocket passes the marker.
            int before = (int) position(-1);
            int now = (int) position(0);
            if (now != before) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 2.0f, 0.08f));
            }
        }
        boolean busy = menu.spinning() || animating();
        for (int i = 0; i < kinds.length; i++) {
            kinds[i].active = !busy;
            kinds[i].setMessage(chosen(kindLabel(KIND_BUTTONS[i]), KIND_BUTTONS[i] == menu.kind()));
        }
        numberDown.active = numberUp.active = number.active = !busy;
        number.setMessage(chosen(Component.translatable("screen.rngambling.roulette.bet.number", menu.data(RouletteMenu.NUMBER)),
                menu.kind() == Roulette.Bet.NUMBER));
        bets.update(!busy, menu.bet());
        spin.active = !busy && menu.data(RouletteMenu.TOKENS) >= menu.bet();
    }

    private static Component chosen(Component label, boolean chosen) {
        return chosen ? Component.literal("[").append(label).append("]").withStyle(ChatFormatting.YELLOW) : label;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos, y = topPos;
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, WOOD);
        graphics.fill(x + 3, y + 3, x + WIDTH - 3, y + HEIGHT - 3, GOLD);
        graphics.fill(x + 4, y + 4, x + WIDTH - 4, y + HEIGHT - 4, FELT);
        // The wheel strip.
        int sx = x + STRIP_X, sy = y + STRIP_Y;
        graphics.fill(sx - 2, sy - 2, sx + STRIP_W + 2, sy + STRIP_H + 2, GOLD);
        graphics.fill(sx, sy, sx + STRIP_W, sy + STRIP_H, FELT_DARK);
        graphics.enableScissor(sx, sy, sx + STRIP_W, sy + STRIP_H);
        float position = position(partialTick);
        int centre = sx + STRIP_W / 2;
        int whole = (int) Math.floor(position);
        float fraction = position - whole;
        for (int j = -6; j <= 6; j++) {
            int number = Roulette.WHEEL[Math.floorMod(whole + j, Roulette.WHEEL.length)];
            int cx = Math.round(centre + (j - fraction) * CELL - CELL / 2f);
            int colour = number == 0 ? POCKET_GREEN : Roulette.isRed(number) ? POCKET_RED : POCKET_BLACK;
            graphics.fill(cx + 1, sy + 1, cx + CELL - 1, sy + STRIP_H - 1, colour);
            graphics.centeredText(font, String.valueOf(number), cx + CELL / 2, sy + 8, WHITE);
        }
        graphics.disableScissor();
        // The marker: a gold notch above and below the middle pocket.
        for (int i = 0; i < 4; i++) {
            graphics.fill(centre - 4 + i, sy - 3 + i, centre + 4 - i, sy - 2 + i, GOLD);
            graphics.fill(centre - 4 + i, sy + STRIP_H + 2 - i, centre + 4 - i, sy + STRIP_H + 3 - i, GOLD);
        }
        graphics.fill(x + 8, y + 164, x + WIDTH - 8, y + HEIGHT - 6, FELT_DARK);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.centeredText(font, title, WIDTH / 2, 8, GOLD);
        Component result;
        int colour = WHITE;
        int number = menu.data(RouletteMenu.RESULT);
        if (animating() || menu.spinning()) {
            result = Component.translatable("screen.rngambling.roulette.spinning");
        } else if (number < 0) {
            result = Component.translatable("screen.rngambling.slot_machine.ready");
        } else {
            Component landed = Component.translatable(number == 0 ? "screen.rngambling.roulette.green"
                    : Roulette.isRed(number) ? "screen.rngambling.roulette.red" : "screen.rngambling.roulette.black", number);
            int win = menu.data(RouletteMenu.LAST_WIN);
            int bet = menu.data(RouletteMenu.LAST_BET);
            result = win > bet ? Component.translatable("screen.rngambling.roulette.win", landed, win, win - bet)
                    : win > 0 ? Component.translatable("screen.rngambling.roulette.push", landed, win)
                    : Component.translatable("screen.rngambling.roulette.lose", landed, bet);
            colour = win > bet ? 0xFF7CFF7C : win > 0 ? WHITE : 0xFFFFB0B0;
        }
        graphics.centeredText(font, result, WIDTH / 2, 50, colour);
        graphics.text(font, Component.translatable("screen.rngambling.slot_machine.tokens", menu.data(RouletteMenu.TOKENS)), 12, 62, WHITE, false);
        graphics.text(font, Component.translatable("screen.rngambling.slot_machine.bet"), 12, 145, GOLD, false);
        bets.drawAmount(graphics, font, menu.bet(), WHITE);
        Roulette.Bet kind = menu.kind();
        graphics.text(font, Component.translatable("screen.rngambling.roulette.pays", menu.bet(),
                kind == Roulette.Bet.NUMBER ? Component.translatable("screen.rngambling.roulette.bet.number", menu.data(RouletteMenu.NUMBER)) : kindLabel(kind),
                kind.returns - 1), 12, 168, 0xFFE0C090, false);
    }
}
