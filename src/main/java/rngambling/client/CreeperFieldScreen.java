package rngambling.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import rngambling.casino.Bets;
import rngambling.casino.CreeperField;
import rngambling.casino.CreeperFieldMenu;

/** The Creeper Field's screen: the 5 x 5 field to dig, how many creepers, the pot and the next dig's pot, and the bet. */
public class CreeperFieldScreen extends AbstractContainerScreen<CreeperFieldMenu> {
    private static final int WIDTH = 220, HEIGHT = 196, TILE = 22, GRID_X = 12, GRID_Y = 24;
    private static final int PANEL = 0xFF3B2A1A, PANEL_DARK = 0xFF24180E, GOLD = 0xFFE8BA40, WOOD = 0xFF5A3418, WHITE = 0xFFF2EEE4;
    private static final int GRASS = 0xFF5D9B3A, GRASS_LIGHT = 0xFF79BD4E, GRASS_TOP = 0xFF4A7F2C, DIRT = 0xFF866043, DIRT_DARK = 0xFF5E422D;
    private static final ItemStack EMERALD = new ItemStack(Items.EMERALD), CREEPER = new ItemStack(Items.CREEPER_HEAD);

    private final BetControls bets = new BetControls(Bets.CREEPER_FIELD, 32, 166);
    private final Tile[] tiles = new Tile[CreeperField.BLOCKS];
    private Button start, cash, fewer, more;

    public CreeperFieldScreen(CreeperFieldMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        for (int i = 0; i < CreeperField.BLOCKS; i++) {
            int col = i % CreeperField.SIZE, row = i / CreeperField.SIZE;
            tiles[i] = addRenderableWidget(new Tile(i, leftPos + GRID_X + col * TILE, topPos + GRID_Y + row * TILE));
        }
        fewer = addRenderableWidget(button(Component.literal("-"), CreeperFieldMenu.BUTTON_FEWER, 132, 36, 14));
        more = addRenderableWidget(button(Component.literal("+"), CreeperFieldMenu.BUTTON_MORE, 194, 36, 14));
        cash = addRenderableWidget(button(Component.empty(), CreeperFieldMenu.BUTTON_CASH, 132, 108, 76));
        start = addRenderableWidget(button(Component.translatable("screen.rngambling.creeper_field.start"), CreeperFieldMenu.BUTTON_START, 116, 166, 40));
        bets.init(leftPos, topPos, this::addRenderableWidget, this::click);
    }

    private Button button(Component label, int id, int x, int y, int width) {
        return Button.builder(label, b -> click(id)).bounds(leftPos + x, topPos + y, width, 18).build();
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
        boolean digging = menu.phase() == CreeperFieldMenu.Phase.DIGGING;
        for (int i = 0; i < tiles.length; i++) {
            tiles[i].active = digging && menu.data(i) == CreeperFieldMenu.HIDDEN;
        }
        fewer.active = !digging && menu.creeperCount() > CreeperField.MIN_CREEPERS;
        more.active = !digging && menu.creeperCount() < CreeperField.MAX_CREEPERS;
        cash.active = digging;
        cash.setMessage(Component.translatable("screen.rngambling.creeper_field.cash", digging ? menu.data(CreeperFieldMenu.POT) : 0));
        start.active = !digging && menu.data(CreeperFieldMenu.TOKENS) >= menu.bet();
        bets.update(!digging, menu.bet());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos, y = topPos;
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, WOOD);
        graphics.fill(x + 3, y + 3, x + WIDTH - 3, y + HEIGHT - 3, GOLD);
        graphics.fill(x + 4, y + 4, x + WIDTH - 4, y + HEIGHT - 4, PANEL);
        graphics.fill(x + GRID_X - 2, y + GRID_Y - 2, x + GRID_X + CreeperField.SIZE * TILE + 1, y + GRID_Y + CreeperField.SIZE * TILE + 1, PANEL_DARK);
        graphics.fill(x + 8, y + 158, x + WIDTH - 8, y + 159, PANEL_DARK);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.centeredText(font, title, WIDTH / 2, 8, GOLD);
        graphics.text(font, Component.translatable("screen.rngambling.creeper_field.creepers"), 132, 24, GOLD, false);
        String count = String.valueOf(menu.creeperCount());
        graphics.text(font, count, 170 - font.width(count) / 2, 41, WHITE, false);
        if (menu.phase() == CreeperFieldMenu.Phase.DIGGING) {
            graphics.text(font, Component.translatable("screen.rngambling.creeper_field.pot", menu.data(CreeperFieldMenu.POT)), 132, 62, WHITE, false);
            graphics.text(font, Component.translatable("screen.rngambling.creeper_field.next", menu.data(CreeperFieldMenu.NEXT_POT)), 132, 74, 0xFF9FE07A, false);
            graphics.text(font, Component.translatable("screen.rngambling.creeper_field.dug", menu.data(CreeperFieldMenu.DUG_COUNT),
                    CreeperField.BLOCKS - menu.creeperCount()), 132, 88, 0xFFC8B89A, false);
        }
        Component line;
        int colour = WHITE;
        int win = menu.data(CreeperFieldMenu.LAST_WIN);
        switch (menu.result()) {
            case BOOM -> {
                line = Component.translatable("screen.rngambling.creeper_field.boom");
                colour = 0xFFFFB0B0;
            }
            case CASHED -> {
                line = Component.translatable("screen.rngambling.creeper_field.cashed", win);
                colour = 0xFF7CFF7C;
            }
            case CLEARED -> {
                line = Component.translatable("screen.rngambling.creeper_field.cleared", win);
                colour = 0xFF7CFF7C;
            }
            default -> line = menu.phase() == CreeperFieldMenu.Phase.DIGGING
                    ? Component.translatable("screen.rngambling.creeper_field.dig")
                    : Component.translatable("screen.rngambling.slot_machine.ready");
        }
        graphics.centeredText(font, line, WIDTH / 2, 144, colour);
        graphics.text(font, Component.translatable("screen.rngambling.slot_machine.bet"), 12, 171, GOLD, false);
        bets.drawAmount(graphics, font, menu.bet(), WHITE);
        graphics.text(font, Component.translatable("screen.rngambling.slot_machine.tokens", menu.data(CreeperFieldMenu.TOKENS)), 160, 171, WHITE, false);
    }

    /** One block of the field: grass to dig, then what was under it. */
    private final class Tile extends AbstractButton {
        private final int index;

        Tile(int index, int x, int y) {
            super(x, y, TILE - 1, TILE - 1, Component.translatable("screen.rngambling.creeper_field.block", index + 1));
            this.index = index;
        }

        @Override
        public void onPress(InputWithModifiers input) {
            click(CreeperFieldMenu.BUTTON_TILE + index);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            int state = menu.data(index);
            switch (state) {
                case CreeperFieldMenu.HIDDEN -> {
                    graphics.fill(x, y, x + w, y + h, active && isHoveredOrFocused() ? GRASS_LIGHT : GRASS);
                    graphics.fill(x, y, x + w, y + 3, GRASS_TOP);
                    graphics.fill(x + 4, y + 8, x + 6, y + 10, GRASS_TOP);
                    graphics.fill(x + 13, y + 13, x + 15, y + 15, GRASS_TOP);
                }
                case CreeperFieldMenu.DUG -> {
                    graphics.fill(x, y, x + w, y + h, DIRT);
                    graphics.item(EMERALD, x + (w - 16) / 2, y + (h - 16) / 2);
                }
                case CreeperFieldMenu.BOOM -> {
                    graphics.fill(x, y, x + w, y + h, 0xFFB3261E);
                    graphics.item(CREEPER, x + (w - 16) / 2, y + (h - 16) / 2);
                }
                case CreeperFieldMenu.CREEPER -> {
                    graphics.fill(x, y, x + w, y + h, DIRT_DARK);
                    graphics.item(CREEPER, x + (w - 16) / 2, y + (h - 16) / 2);
                }
                default -> graphics.fill(x, y, x + w, y + h, DIRT_DARK);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
