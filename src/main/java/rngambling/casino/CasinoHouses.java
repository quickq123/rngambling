package rngambling.casino;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.List;
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
import rngambling.RNGambling;

/**
 * Adds a casino to each village type's houses: a vanilla Fletcher House with its fletching table
 * swapped for a Slot Machine (the structure files are made by tools/make_structures.py). Added
 * to the house pools as the server starts, at three times the Fletcher House's weight: in a survey
 * of 19 villages, 9 had a casino (some two).
 */
public final class CasinoHouses {
    /** Village type, and the processor list its Fletcher House uses. */
    private static final String[][] VILLAGES = {
            {"plains", "minecraft:mossify_10_percent"},
            {"taiga", "minecraft:mossify_10_percent"},
            {"desert", "minecraft:empty"},
            {"savanna", "minecraft:empty"},
            {"snowy", "minecraft:empty"},
    };
    private static final int WEIGHT = 6;

    private CasinoHouses() {
    }

    public static void addToVillages(ServerAboutToStartEvent event) {
        RegistryAccess registries = event.getServer().registryAccess();
        Registry<StructureTemplatePool> pools = registries.lookupOrThrow(Registries.TEMPLATE_POOL);
        Registry<StructureProcessorList> processors = registries.lookupOrThrow(Registries.PROCESSOR_LIST);
        for (String[] village : VILLAGES) {
            StructureTemplatePool pool = pools.getValue(Identifier.withDefaultNamespace("village/" + village[0] + "/houses"));
            Holder<StructureProcessorList> processorList = processors.getOrThrow(
                    ResourceKey.create(Registries.PROCESSOR_LIST, Identifier.parse(village[1])));
            if (pool == null) {
                continue;
            }
            StructurePoolElement casino = StructurePoolElement
                    .legacy(RNGambling.MOD_ID + ":village/" + village[0] + "/casino", processorList)
                    .apply(StructureTemplatePool.Projection.RIGID);
            for (int i = 0; i < WEIGHT; i++) {
                pool.templates.add(casino);
            }
            List<Pair<StructurePoolElement, Integer>> raw = new ArrayList<>(pool.rawTemplates);
            raw.add(Pair.of(casino, WEIGHT));
            pool.rawTemplates = raw;
        }
    }
}
