package rngambling.client;

import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import rngambling.casino.Bets;

/**
 * The bet chooser the casino screens share: « − amount + », where « and » change the bet by 10 and
 * − and + by one step (2 at blackjack). The amount is drawn between the buttons.
 */
final class BetControls {
    static final int WIDTH = 78;
    private final Bets rules;
    private final int x, y;
    private final Button[] buttons = new Button[4];

    /** {@code x}, {@code y}: where the controls go, relative to the screen's corner. */
    BetControls(Bets rules, int x, int y) {
        this.rules = rules;
        this.x = x;
        this.y = y;
    }

    void init(int left, int top, Consumer<Button> add, IntConsumer click) {
        String[] labels = {"«", "-", "+", "»"};
        int[] at = {0, 15, 51, 64};
        int[] widths = {14, 12, 12, 14};
        for (int i = 0; i < 4; i++) {
            int id = Bets.DOWN_BIG + i;
            int step = i == 0 || i == 3 ? Bets.BIG_STEP : rules.step();
            buttons[i] = Button.builder(Component.literal(labels[i]), b -> click.accept(id))
                    .bounds(left + x + at[i], top + y, widths[i], 18)
                    .tooltip(Tooltip.create(Component.translatable(i < 2 ? "screen.rngambling.bet.down" : "screen.rngambling.bet.up", step)))
                    .build();
            add.accept(buttons[i]);
        }
    }

    /** Each tick: which buttons can be pressed. */
    void update(boolean active, int bet) {
        buttons[0].active = buttons[1].active = active && bet > rules.min();
        buttons[2].active = buttons[3].active = active && bet < rules.max();
    }

    /** The amount, between the buttons (in the screen's label coordinates). */
    void drawAmount(GuiGraphicsExtractor graphics, Font font, int bet, int colour) {
        String text = String.valueOf(bet);
        graphics.text(font, text, x + 39 - font.width(text) / 2, y + 5, colour, false);
    }
}
