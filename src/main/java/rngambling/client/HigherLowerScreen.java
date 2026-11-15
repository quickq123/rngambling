package rngambling.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import rngambling.casino.Bets;
import rngambling.casino.HigherLowerMenu;

/** The Higher or Lower table's screen: the last card, the card up, the pot, the guesses (each showing what it would pay) and the bet. */
public class HigherLowerScreen extends AbstractContainerScreen<HigherLowerMenu> {
    private static final int WIDTH = 220, HEIGHT = 196;
    private static final int FELT = 0xFF1B3F73, FELT_DARK = 0xFF102A4F, GOLD = 0xFFE8BA40, WOOD = 0xFF5A3418, WHITE = 0xFFF2EEE4;

    private final BetControls bets = new BetControls(Bets.HIGHER_LOWER, 32, 166);
    private Button deal, higher, lower, skip, cash;

    public HigherLowerScreen(HigherLowerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        higher = addRenderableWidget(button(HigherLowerMenu.BUTTON_HIGHER, 12, 92, 96));
        lower = addRenderableWidget(button(HigherLowerMenu.BUTTON_LOWER, 112, 92, 96));
        skip = addRenderableWidget(button(HigherLowerMenu.BUTTON_SKIP, 12, 116, 60));
        cash = addRenderableWidget(button(HigherLowerMenu.BUTTON_CASH, 76, 116, 132));
        deal = addRenderableWidget(button(HigherLowerMenu.BUTTON_DEAL, 116, 166, 40));
        skip.setMessage(Component.translatable("screen.rngambling.higher_lower.skip"));
        deal.setMessage(Component.translatable("screen.rngambling.blackjack.deal"));
        bets.init(leftPos, topPos, this::addRenderableWidget, this::click);
    }

    private Button button(int id, int x, int y, int width) {
        return Button.builder(Component.empty(), b -> click(id)).bounds(leftPos + x, topPos + y, width, 20).build();
    }

    private void click(int id) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    public void containerTick() {
        super.containerTick();
        boolean playing = menu.phase() == HigherLowerMenu.Phase.PLAYING;
        higher.active = lower.active = skip.active = cash.active = playing;
        deal.active = !playing && menu.data(HigherLowerMenu.TOKENS) >= menu.bet();
        higher.setMessage(Component.translatable("screen.rngambling.higher_lower.higher", playing ? menu.data(HigherLowerMenu.IF_HIGHER) : "-"));
        lower.setMessage(Component.translatable("screen.rngambling.higher_lower.lower", playing ? menu.data(HigherLowerMenu.IF_LOWER) : "-"));
        cash.setMessage(Component.translatable("screen.rngambling.higher_lower.cash", playing ? menu.data(HigherLowerMenu.POT) : 0));
        bets.update(!playing, menu.bet());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos, y = topPos;
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, WOOD);
        graphics.fill(x + 3, y + 3, x + WIDTH - 3, y + HEIGHT - 3, GOLD);
        graphics.fill(x + 4, y + 4, x + WIDTH - 4, y + HEIGHT - 4, FELT);
        graphics.fill(x + 8, y + 158, x + WIDTH - 8, y + 159, FELT_DARK);
        int previous = menu.card(HigherLowerMenu.PREVIOUS);
        if (previous >= 0) {
            CardArt.draw(graphics, font, previous, x + 30, y + 40, 24, 32, 1.0f);
        }
        int card = menu.card(HigherLowerMenu.CARD);
        CardArt.draw(graphics, font, card, x + 80, y + 28, 38, 52, 2.5f);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.centeredText(font, title, WIDTH / 2, 8, GOLD);
        if (menu.card(HigherLowerMenu.PREVIOUS) >= 0) {
            graphics.centeredText(font, Component.translatable("screen.rngambling.higher_lower.last"), 42, 28, WHITE);
        }
        graphics.text(font, Component.translatable("screen.rngambling.higher_lower.aces_low"), 132, 70, 0xFFB8C4DA, false);
        graphics.text(font, Component.translatable("screen.rngambling.higher_lower.ties_win"), 132, 80, 0xFFB8C4DA, false);
        if (menu.phase() == HigherLowerMenu.Phase.PLAYING) {
            graphics.text(font, Component.translatable("screen.rngambling.higher_lower.pot"), 132, 30, GOLD, false);
            graphics.text(font, Component.translatable("screen.rngambling.higher_lower.tokens", menu.data(HigherLowerMenu.POT)), 132, 42, WHITE, false);
            graphics.text(font, Component.translatable("screen.rngambling.higher_lower.streak", menu.data(HigherLowerMenu.STREAK)), 132, 56, WHITE, false);
        }
        Component line;
        int colour = WHITE;
        HigherLowerMenu.Result result = menu.result();
        int win = menu.data(HigherLowerMenu.LAST_WIN);
        switch (result) {
            case RIGHT -> {
                line = Component.translatable("screen.rngambling.higher_lower.right");
                colour = 0xFF7CFF7C;
            }
            case WRONG -> {
                line = Component.translatable("screen.rngambling.higher_lower.wrong");
                colour = 0xFFFFB0B0;
            }
            case CASHED -> {
                line = Component.translatable("screen.rngambling.higher_lower.cashed", win);
                colour = 0xFF7CFF7C;
            }
            case LIMIT -> {
                line = Component.translatable("screen.rngambling.higher_lower.limit", win);
                colour = 0xFF7CFF7C;
            }
            case SKIPPED -> line = Component.translatable("screen.rngambling.higher_lower.skipped");
            default -> line = menu.phase() == HigherLowerMenu.Phase.PLAYING
                    ? Component.translatable("screen.rngambling.higher_lower.guess")
                    : Component.translatable("screen.rngambling.slot_machine.ready");
        }
        graphics.centeredText(font, line, WIDTH / 2, 144, colour);
        graphics.text(font, Component.translatable("screen.rngambling.slot_machine.bet"), 12, 171, GOLD, false);
        bets.drawAmount(graphics, font, menu.bet(), WHITE);
        graphics.text(font, Component.translatable("screen.rngambling.slot_machine.tokens", menu.data(HigherLowerMenu.TOKENS)), 160, 171, WHITE, false);
    }
}
