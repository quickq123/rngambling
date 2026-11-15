package rngambling.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import rngambling.RNGambling;
import rngambling.casino.Blackjack;

/** Playing cards drawn on the casino screens: a white card, its rank in the corner, its suit large in the middle. */
final class CardArt {
    private static final Identifier SUITS = RNGambling.id("textures/gui/card_suits.png");
    static final String[] RANKS = {"A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"};
    private static final int WHITE = 0xFFF2EEE4, BACK = 0xFFA0141E, GOLD = 0xFFE8BA40;

    private CardArt() {
    }

    /** A card (0-51), or its back if {@code card} is negative, {@code w} x {@code h} at x, y, the suit drawn at {@code scale}. */
    static void draw(GuiGraphicsExtractor graphics, Font font, int card, int x, int y, int w, int h, float scale) {
        graphics.fill(x, y, x + w, y + h, 0xFF202020);
        if (card < 0) {
            graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, BACK);
            graphics.fill(x + 3, y + 3, x + w - 3, y + h - 3, GOLD);
            graphics.fill(x + 4, y + 4, x + w - 4, y + h - 4, BACK);
            return;
        }
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, WHITE);
        int suit = Blackjack.suit(card);
        int colour = suit < 2 ? 0xFFC0182A : 0xFF1A1A1E;
        graphics.text(font, RANKS[Blackjack.rank(card)], x + 3, y + 3, colour, false);
        graphics.blit(RenderPipelines.GUI_TEXTURED, SUITS, x + w - 11, y + h - 11, suit * 8.0F, 0.0F, 8, 8, 32, 8);
        graphics.pose().pushMatrix();
        float size = 8 * scale;
        graphics.pose().translate(x + (w - size) / 2f, y + (h - size) / 2f + 2);
        graphics.pose().scale(scale, scale);
        graphics.blit(RenderPipelines.GUI_TEXTURED, SUITS, 0, 0, suit * 8.0F, 0.0F, 8, 8, 32, 8);
        graphics.pose().popMatrix();
    }
}
