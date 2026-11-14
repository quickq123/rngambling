package rngambling.client;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import rngambling.RNGambling;
import rngambling.casino.Blackjack;
import rngambling.casino.BlackjackMenu;

/** The blackjack table's screen: the dealer's hand, the player's hand, the moves and the bet. */
public class BlackjackScreen extends AbstractContainerScreen<BlackjackMenu> {
    private static final Identifier SUITS = RNGambling.id("textures/gui/card_suits.png");
    private static final int WIDTH = 204, HEIGHT = 190;
    private static final int FELT = 0xFF14632E, FELT_DARK = 0xFF0C4320, GOLD = 0xFFE8BA40, WOOD = 0xFF5A3418, WHITE = 0xFFF2EEE4;
    private static final int CARD_W = 24, CARD_H = 32;
    private static final String[] RANKS = {"A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"};

    private final Button[] bets = new Button[Blackjack.BETS.length];
    private Button deal, hit, stand, doubleDown;

    public BlackjackScreen(BlackjackMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        deal = addRenderableWidget(button("deal", BlackjackMenu.BUTTON_DEAL, 12, 136, 42));
        hit = addRenderableWidget(button("hit", BlackjackMenu.BUTTON_HIT, 58, 136, 42));
        stand = addRenderableWidget(button("stand", BlackjackMenu.BUTTON_STAND, 104, 136, 42));
        doubleDown = addRenderableWidget(button("double", BlackjackMenu.BUTTON_DOUBLE, 150, 136, 42));
        for (int i = 0; i < bets.length; i++) {
            int id = i;
            bets[i] = addRenderableWidget(Button.builder(Component.empty(), b -> click(id))
                    .bounds(leftPos + 34 + i * 25, topPos + 160, 24, 18).build());
        }
    }

    private Button button(String name, int id, int x, int y, int width) {
        return Button.builder(Component.translatable("screen.rngambling.blackjack." + name), b -> click(id))
                .bounds(leftPos + x, topPos + y, width, 20).build();
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
        BlackjackMenu.Phase phase = menu.phase();
        int tokens = menu.data(BlackjackMenu.TOKENS);
        deal.active = phase == BlackjackMenu.Phase.BETTING && tokens >= menu.bet();
        hit.active = stand.active = phase == BlackjackMenu.Phase.PLAYER;
        doubleDown.active = phase == BlackjackMenu.Phase.PLAYER && menu.cards(false).size() == 2 && tokens >= menu.data(BlackjackMenu.STAKE);
        for (int i = 0; i < bets.length; i++) {
            bets[i].active = phase == BlackjackMenu.Phase.BETTING;
            String label = String.valueOf(Blackjack.BETS[i]);
            bets[i].setMessage(Blackjack.BETS[i] == menu.bet()
                    ? Component.literal("[" + label + "]").withStyle(ChatFormatting.YELLOW) : Component.literal(label));
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos, y = topPos;
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, WOOD);
        graphics.fill(x + 3, y + 3, x + WIDTH - 3, y + HEIGHT - 3, GOLD);
        graphics.fill(x + 4, y + 4, x + WIDTH - 4, y + HEIGHT - 4, FELT);
        graphics.fill(x + 8, y + 112, x + WIDTH - 8, y + 113, FELT_DARK);
        drawHand(graphics, menu.cards(true), x + 12, y + 30);
        drawHand(graphics, menu.cards(false), x + 12, y + 78);
    }

    private void drawHand(GuiGraphicsExtractor graphics, List<Integer> cards, int x, int y) {
        for (int i = 0; i < cards.size(); i++) {
            drawCard(graphics, cards.get(i), x + i * (CARD_W + 4), y);
        }
    }

    private void drawCard(GuiGraphicsExtractor graphics, int card, int x, int y) {
        graphics.fill(x, y, x + CARD_W, y + CARD_H, 0xFF202020);
        if (card == BlackjackMenu.HIDDEN) {
            graphics.fill(x + 1, y + 1, x + CARD_W - 1, y + CARD_H - 1, 0xFFA0141E);
            graphics.fill(x + 3, y + 3, x + CARD_W - 3, y + CARD_H - 3, GOLD);
            graphics.fill(x + 4, y + 4, x + CARD_W - 4, y + CARD_H - 4, 0xFFA0141E);
            return;
        }
        graphics.fill(x + 1, y + 1, x + CARD_W - 1, y + CARD_H - 1, WHITE);
        int suit = Blackjack.suit(card);
        int colour = suit < 2 ? 0xFFC0182A : 0xFF1A1A1E;
        graphics.text(font, RANKS[Blackjack.rank(card)], x + 3, y + 3, colour, false);
        graphics.blit(RenderPipelines.GUI_TEXTURED, SUITS, x + CARD_W - 11, y + CARD_H - 11, suit * 8.0F, 0.0F, 8, 8, 32, 8);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x + 6, y + 12);
        graphics.pose().scale(1.5f, 1.5f);
        graphics.blit(RenderPipelines.GUI_TEXTURED, SUITS, 0, 0, suit * 8.0F, 0.0F, 8, 8, 32, 8);
        graphics.pose().popMatrix();
    }

    /** The total the screen may show: only face-up cards count. */
    private static String total(List<Integer> cards) {
        List<Integer> shown = cards.stream().filter(c -> c != BlackjackMenu.HIDDEN).toList();
        if (shown.isEmpty()) {
            return "";
        }
        int total = Blackjack.total(shown);
        return Blackjack.soft(shown) && total < 21 ? (total - 10) + "/" + total : String.valueOf(total);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.centeredText(font, title, WIDTH / 2, 8, GOLD);
        List<Integer> dealer = menu.cards(true);
        List<Integer> player = menu.cards(false);
        graphics.text(font, Component.translatable("screen.rngambling.blackjack.dealer", total(dealer)), 12, 20, WHITE, false);
        graphics.text(font, Component.translatable("screen.rngambling.blackjack.you", total(player)), 12, 68, WHITE, false);
        Component line;
        int colour = WHITE;
        Blackjack.Outcome outcome = menu.outcome();
        if (menu.phase() == BlackjackMenu.Phase.PLAYER) {
            line = Component.translatable("screen.rngambling.blackjack.your_move");
        } else if (menu.phase() == BlackjackMenu.Phase.DEALER) {
            line = Component.translatable("screen.rngambling.blackjack.dealer_plays");
        } else if (outcome == Blackjack.Outcome.NONE) {
            line = Component.translatable("screen.rngambling.slot_machine.ready");
        } else {
            int win = menu.data(BlackjackMenu.LAST_WIN);
            line = Component.translatable("screen.rngambling.blackjack.outcome." + outcome.name().toLowerCase(java.util.Locale.ROOT), win);
            colour = win > menu.data(BlackjackMenu.STAKE) ? 0xFF7CFF7C : win == menu.data(BlackjackMenu.STAKE) ? WHITE : 0xFFFFB0B0;
        }
        graphics.centeredText(font, line, WIDTH / 2, 118, colour);
        graphics.text(font, Component.translatable("screen.rngambling.slot_machine.tokens", menu.data(BlackjackMenu.TOKENS)), 120, 165, WHITE, false);
        graphics.text(font, Component.translatable("screen.rngambling.slot_machine.bet"), 12, 165, GOLD, false);
    }
}
