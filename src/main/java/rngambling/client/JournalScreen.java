package rngambling.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import rngambling.outcome.Journal;
import rngambling.outcome.Outcome;
import rngambling.outcome.Outcomes;
import rngambling.outcome.Themes;
import rngambling.outcome.Tier;

/**
 * The Lucky Journal's screen: every effect by tier. Effects the player has had show their name;
 * picked, the right-hand page shows its chat line, luck range, where it happens and what it does
 * (scroll with the mouse wheel). The rest are "???". Read from the player's synced journal.
 */
public class JournalScreen extends Screen {
    private static final int WIDTH = 380, HEIGHT = 222, ROWS = 8;
    private static final int COVER = 0xFF5B3A1E, COVER_EDGE = 0xFF3B2412, PAPER = 0xFFEFE6CF, PAPER_DARK = 0xFFD9CCAA, INK = 0xFF3A2E1F,
            FADED = 0xFF8C7B60, GOLD = 0xFFB8871B;
    private static final Tier[] TIERS = {Tier.LEGENDARY, Tier.VERY_GOOD, Tier.GOOD, Tier.NEUTRAL, Tier.BAD, Tier.VERY_BAD,
            Tier.CATASTROPHIC, Tier.WILDCARD};
    /** Tier colours that read on paper (chat's own are for a dark background). */
    private static final int[] INK_COLOURS = {0xFF9C1F9A, 0xFF0E7C86, 0xFF2E7D22, 0xFF8A7400, 0xFFB06000, 0xFFC0282A, 0xFF8E0000, 0xFF4A4A4A};

    private final List<Button> tierButtons = new ArrayList<>(), rows = new ArrayList<>();
    private Button previous, next;
    private int tier, page, scroll;
    private Outcome picked;
    private int left, top;

    public JournalScreen() {
        super(Component.translatable("screen.rngambling.journal.title"));
    }

    private static List<String> seen() {
        var player = Minecraft.getInstance().player;
        return player == null ? List.of() : Journal.seen(player);
    }

    private List<Outcome> inTier() {
        List<Outcome> list = new ArrayList<>();
        for (Outcome outcome : Outcomes.everyEffect().values()) {
            if (outcome.tier() == TIERS[tier]) {
                list.add(outcome);
            }
        }
        return list;
    }

    private int pages() {
        return Math.max(1, (inTier().size() + ROWS - 1) / ROWS);
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        tierButtons.clear();
        rows.clear();
        for (int i = 0; i < TIERS.length; i++) {
            int index = i;
            tierButtons.add(addRenderableWidget(Button.builder(Component.empty(), b -> {
                tier = index;
                page = 0;
            }).bounds(left + 10, top + 30 + i * 21, 108, 19).build()));
        }
        for (int i = 0; i < ROWS; i++) {
            int row = i;
            rows.add(addRenderableWidget(Button.builder(Component.empty(), b -> pick(row)).bounds(left + 124, top + 30 + i * 21, 104, 19).build()));
        }
        previous = addRenderableWidget(Button.builder(Component.literal("<"), b -> page = Math.max(0, page - 1))
                .bounds(left + 124, top + 198, 20, 16).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), b -> page = Math.min(pages() - 1, page + 1))
                .bounds(left + 208, top + 198, 20, 16).build());
    }

    /** For the screenshot run: shows a tier's page and picks one of its rows. */
    public void select(int tierIndex, int pageIndex, int row) {
        tier = tierIndex;
        page = pageIndex;
        pick(row);
    }

    private void pick(int row) {
        List<Outcome> list = inTier();
        int index = page * ROWS + row;
        if (index < list.size() && seen().contains(list.get(index).id())) {
            picked = list.get(index);
            scroll = 0;
        }
    }

    @Override
    public void tick() {
        List<String> seen = seen();
        for (int i = 0; i < TIERS.length; i++) {
            int found = 0, all = 0;
            for (Outcome outcome : Outcomes.everyEffect().values()) {
                if (outcome.tier() == TIERS[i]) {
                    all++;
                    found += seen.contains(outcome.id()) ? 1 : 0;
                }
            }
            tierButtons.get(i).setMessage(Component.translatable("screen.rngambling.journal.tier",
                    Component.translatable("tier.rngambling." + TIERS[i].name().toLowerCase(java.util.Locale.ROOT)), found, all)
                    .withStyle(i == tier ? ChatFormatting.UNDERLINE : ChatFormatting.RESET));
        }
        List<Outcome> list = inTier();
        page = Math.min(page, pages() - 1);
        for (int i = 0; i < ROWS; i++) {
            int index = page * ROWS + i;
            Button row = rows.get(i);
            row.visible = index < list.size();
            if (row.visible) {
                boolean known = seen.contains(list.get(index).id());
                row.active = known;
                row.setMessage(known ? Component.translatable("outcome.rngambling." + list.get(index).id()) : Component.literal("???"));
            }
        }
        previous.active = page > 0;
        next.active = page < pages() - 1;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        graphics.fill(left - 4, top - 4, left + WIDTH + 4, top + HEIGHT + 4, COVER_EDGE);
        graphics.fill(left - 3, top - 3, left + WIDTH + 3, top + HEIGHT + 3, COVER);
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, PAPER);
        graphics.fill(left + 234, top + 6, left + 235, top + HEIGHT - 6, PAPER_DARK); // the spine
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        List<String> seen = seen();
        int total = Outcomes.everyEffect().size();
        graphics.text(font, title, left + 10, top + 10, INK, false);
        String count = Component.translatable("screen.rngambling.journal.found", seen.size(), total).getString();
        graphics.text(font, count, left + 228 - font.width(count), top + 10, FADED, false);
        String pageText = (page + 1) + " / " + pages();
        graphics.text(font, pageText, left + 176 - font.width(pageText) / 2, top + 202, FADED, false);
        drawPage(graphics);
    }

    /** The right-hand page: the picked effect, or how to use the journal. */
    private void drawPage(GuiGraphicsExtractor graphics) {
        int x = left + 242, y = top + 10, w = WIDTH - 250;
        List<FormattedCharSequence> lines = new ArrayList<>();
        List<Integer> colours = new ArrayList<>();
        if (picked == null) {
            add(lines, colours, Component.translatable("screen.rngambling.journal.help"), w, FADED);
        } else {
            int ink = INK_COLOURS[java.util.Arrays.asList(TIERS).indexOf(picked.tier())];
            add(lines, colours, Component.translatable("outcome.rngambling." + picked.id()).withStyle(ChatFormatting.BOLD), w, ink);
            add(lines, colours, Component.translatable("screen.rngambling.journal.luck",
                    Component.translatable("tier.rngambling." + picked.tier().name().toLowerCase(java.util.Locale.ROOT)),
                    signed(picked.luckMin()), signed(picked.luckMax())), w, FADED);
            String zone = Themes.zoneOf(picked.id());
            if (zone != null) {
                String replaced = Themes.replaces(picked.id());
                add(lines, colours, replaced != null
                        ? Component.translatable("screen.rngambling.journal.zone_swap", Component.translatable("zone.rngambling." + zone),
                                Component.translatable("outcome.rngambling." + replaced))
                        : Component.translatable("screen.rngambling.journal.zone_only", Component.translatable("zone.rngambling." + zone)), w, GOLD);
            }
            if (picked.sandbox() || picked.has(Outcome.Flag.ROOM)) {
                add(lines, colours, Component.translatable("screen.rngambling.journal.sandbox"), w, GOLD);
            }
            add(lines, colours, Component.literal(""), w, INK);
            add(lines, colours, Component.literal("“").append(Component.translatable("outcome.rngambling." + picked.id() + ".quip"))
                    .append("”").withStyle(ChatFormatting.ITALIC), w, FADED);
            add(lines, colours, Component.literal(""), w, INK);
            add(lines, colours, Component.translatable("outcome.rngambling." + picked.id() + ".desc"), w, INK);
        }
        int visible = (HEIGHT - 20) / 10;
        scroll = Math.max(0, Math.min(scroll, lines.size() - visible));
        graphics.enableScissor(x, y, x + w, y + visible * 10);
        for (int i = scroll; i < lines.size(); i++) {
            graphics.text(font, lines.get(i), x, y + (i - scroll) * 10, colours.get(i), false);
        }
        graphics.disableScissor();
        if (lines.size() > visible) {
            String more = scroll + visible < lines.size() ? "▼" : "▲";
            graphics.text(font, more, left + WIDTH - 12, top + HEIGHT - 14, FADED, false);
        }
    }

    private void add(List<FormattedCharSequence> lines, List<Integer> colours, Component text, int width, int colour) {
        List<FormattedCharSequence> split = text.getString().isEmpty() ? List.of(FormattedCharSequence.EMPTY) : font.split(text, width);
        for (FormattedCharSequence line : split) {
            lines.add(line);
            colours.add(colour);
        }
    }

    private static String signed(int luck) {
        return luck > 0 ? "+" + luck : String.valueOf(luck);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (x >= left + 236) {
            scroll -= (int) Math.signum(scrollY) * 3;
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
