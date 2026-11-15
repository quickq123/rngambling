package rngambling.casino;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import org.jspecify.annotations.Nullable;
import rngambling.RNGambling;
import rngambling.RNGamblingConfig;

/**
 * Adds casinos to villages' house pools: a vanilla Fletcher House with its fletching table
 * swapped for a casino game, one variant per game and one style per vanilla village type (the
 * structure files are made by tools/make_structures.py). Added as the server starts.
 *
 * <p>Vanilla's five village types get their own style. With {@code casinos_in_modded_villages}
 * on, any other mod's village house pool ({@code <mod>:village/<type>/house} or {@code houses},
 * as ChoiceTheorem's Overhauled Village and many others name them) gets the nearest vanilla
 * style too: their roads join buildings with the same jigsaw ({@code minecraft:building_entrance}).
 *
 * <p>How often: {@code village_casino_percent} of each pool's picks (by weight) are a casino.
 * The default, 6, is about what vanilla had before it was configurable (three times the Fletcher
 * House's weight); in a survey of 19 vanilla villages, 9 had a casino.
 */
public final class CasinoHouses {
    /** Vanilla's village types, each with the processor list its Fletcher House uses. */
    private static final Map<String, String> STYLES = Map.of(
            "plains", "minecraft:mossify_10_percent",
            "taiga", "minecraft:mossify_10_percent",
            "desert", "minecraft:empty",
            "savanna", "minecraft:empty",
            "snowy", "minecraft:empty");
    /** The variants, one per game. */
    public static final String[] GAMES = {"slots", "roulette", "blackjack"};
    /** {@code village/<type>/house} or {@code houses}, in any namespace (vanilla's zombie variants are deeper, so they're left alone). */
    private static final Pattern HOUSE_POOL = Pattern.compile("village/([a-z0-9_]+)/houses?");
    /** Village types whose houses don't stand on ordinary ground (up trees, underground, underwater): a casino wouldn't fit. */
    private static final List<String> UNSUITABLE = List.of("tree", "underground", "underwater", "ocean", "sky", "floating");

    private CasinoHouses() {
    }

    /** The vanilla style a village type gets, or null for one a casino shouldn't go in. */
    public static @Nullable String styleFor(String type) {
        if (STYLES.containsKey(type)) {
            return type;
        }
        for (String word : UNSUITABLE) {
            if (type.contains(word)) {
                return null;
            }
        }
        if (type.contains("desert") || type.contains("mesa") || type.contains("badlands") || type.contains("oasis")) {
            return "desert";
        }
        if (type.contains("savanna")) {
            return "savanna";
        }
        if (type.contains("snow") || type.contains("ice") || type.contains("igloo") || type.contains("christmas") || type.contains("frozen")
                || type.contains("alpine") || type.contains("tundra")) {
            return "snowy";
        }
        if (type.contains("taiga") || type.contains("mountain") || type.contains("dark_forest") || type.contains("spruce") || type.contains("halloween")) {
            return "taiga";
        }
        return "plains";
    }

    /**
     * Each game's weight in a pool whose other houses weigh {@code others} in all, so the casinos
     * together are {@code percent} of the picks. At least 1 each when the percent isn't 0.
     */
    public static int weightEach(int others, int percent) {
        if (percent <= 0) {
            return 0;
        }
        double all = others * percent / (100.0 - percent);
        return Math.max(1, (int) Math.round(all / GAMES.length));
    }

    public static void addToVillages(ServerAboutToStartEvent event) {
        int percent = RNGamblingConfig.VILLAGE_CASINO_PERCENT.get();
        boolean modded = RNGamblingConfig.CASINOS_IN_MODDED_VILLAGES.get();
        if (percent <= 0) {
            return;
        }
        RegistryAccess registries = event.getServer().registryAccess();
        Registry<StructureTemplatePool> pools = registries.lookupOrThrow(Registries.TEMPLATE_POOL);
        Registry<StructureProcessorList> processors = registries.lookupOrThrow(Registries.PROCESSOR_LIST);
        List<String> added = new ArrayList<>();
        for (var entry : pools.entrySet()) {
            Identifier id = entry.getKey().identifier();
            Matcher match = HOUSE_POOL.matcher(id.getPath());
            if (!match.matches()) {
                continue;
            }
            boolean vanilla = id.getNamespace().equals("minecraft");
            if (vanilla ? !STYLES.containsKey(match.group(1)) || !id.getPath().endsWith("houses") : !modded) {
                continue;
            }
            String style = styleFor(match.group(1));
            if (style == null) {
                continue;
            }
            StructureTemplatePool pool = entry.getValue();
            int others = 0;
            for (Pair<StructurePoolElement, Integer> raw : pool.rawTemplates) {
                others += raw.getSecond();
            }
            int weight = weightEach(others, percent);
            Holder<StructureProcessorList> processorList = processors.getOrThrow(
                    ResourceKey.create(Registries.PROCESSOR_LIST, Identifier.parse(STYLES.get(style))));
            List<Pair<StructurePoolElement, Integer>> raw = new ArrayList<>(pool.rawTemplates);
            for (String game : GAMES) {
                StructurePoolElement casino = StructurePoolElement
                        .legacy(RNGambling.MOD_ID + ":village/" + style + "/casino_" + game, processorList)
                        .apply(StructureTemplatePool.Projection.RIGID);
                for (int i = 0; i < weight; i++) {
                    pool.templates.add(casino);
                }
                raw.add(Pair.of(casino, weight));
            }
            pool.rawTemplates = raw;
            added.add(id + " (" + style + ")");
        }
        com.mojang.logging.LogUtils.getLogger().info("Casinos ({}% of houses) added to {} village house pools: {}", percent, added.size(), added);
    }
}
