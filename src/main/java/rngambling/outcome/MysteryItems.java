package rngambling.outcome;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.common.Tags;
import org.jspecify.annotations.Nullable;
import rngambling.RNGambling;

/**
 * GOD-29 Mystery Item and LEG-19 Mystery Treasure: any item that can be crafted or found in the
 * world, from vanilla or any installed mod. The list is built from (1) every recipe's result,
 * (2) every item named in a chest, entity, fishing, archaeology or gameplay loot table, and (3)
 * what natural blocks drop (ores, logs, leaves, flowers, dirt, sand, stone), less the
 * {@code #rngambling:lucky_blacklist} item tag. Built once, then again after a data pack reload.
 */
public final class MysteryItems {
    public static final TagKey<Item> BLACKLIST = TagKey.create(Registries.ITEM, RNGambling.id("lucky_blacklist"));
    private static final List<TagKey<Block>> NATURAL = List.of(Tags.Blocks.ORES, BlockTags.LOGS, BlockTags.LEAVES, BlockTags.FLOWERS,
            BlockTags.DIRT, BlockTags.SAND, BlockTags.BASE_STONE_OVERWORLD, BlockTags.BASE_STONE_NETHER);

    private static @Nullable RecipeManager builtFor;
    private static List<Item> items = List.of();

    private MysteryItems() {
    }

    /** The list, rebuilt when the data packs have changed (a new recipe manager means a reload). */
    public static synchronized List<Item> all(ServerLevel level) {
        MinecraftServer server = level.getServer();
        if (builtFor != server.getRecipeManager()) {
            builtFor = server.getRecipeManager();
            items = build(level);
            com.mojang.logging.LogUtils.getLogger().info("Mystery Item: {} items can be drawn", items.size());
        }
        return items;
    }

    /** One random item; stackable ones as a small stack. */
    public static ItemStack draw(OutcomeContext c) {
        List<Item> all = all(c.level());
        if (all.isEmpty()) {
            return new ItemStack(Items.DIAMOND);
        }
        ItemStack stack = new ItemStack(c.pickOne(all));
        if (stack.getMaxStackSize() > 1) {
            stack.setCount(c.between(Math.min(4, stack.getMaxStackSize()), Math.min(16, stack.getMaxStackSize())));
        }
        return stack;
    }

    private static List<Item> build(ServerLevel level) {
        MinecraftServer server = level.getServer();
        Set<Item> found = new LinkedHashSet<>();
        // 1) Recipe results, read from each recipe's display (so other mods' recipe types count too).
        var context = SlotDisplayContext.fromLevel(level);
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            try {
                for (RecipeDisplay display : holder.value().display()) {
                    for (ItemStack result : display.result().resolveForStacks(context)) {
                        found.add(result.getItem());
                    }
                }
            } catch (RuntimeException e) {
                // A recipe that can't show its result (some mods' special recipes): skip it.
            }
        }
        // 2) World loot: items named in loot tables other than block drops.
        var lookup = server.reloadableRegistries().lookup();
        RegistryOps<JsonElement> ops = lookup.createSerializationContext(JsonOps.INSTANCE);
        lookup.lookupOrThrow(Registries.LOOT_TABLE).listElements().forEach(table -> {
            if (!table.key().identifier().getPath().startsWith("blocks/")) {
                namedIn(table.value(), ops, found);
            }
        });
        // 3) What natural blocks drop.
        var tables = lookup.lookupOrThrow(Registries.LOOT_TABLE);
        for (TagKey<Block> tag : NATURAL) {
            for (Holder<Block> block : BuiltInRegistries.BLOCK.getTagOrEmpty(tag)) {
                Optional<ResourceKey<LootTable>> key = block.value().getLootTable();
                key.flatMap(tables::get).ifPresent(table -> namedIn(table.value(), ops, found));
            }
        }
        List<Item> list = new ArrayList<>();
        for (Item item : found) {
            if (item != Items.AIR && !new ItemStack(item).is(BLACKLIST)) {
                list.add(item);
            }
        }
        return List.copyOf(list);
    }

    /** Adds every item a loot table names ("type": "minecraft:item" entries), read from its JSON form. */
    private static void namedIn(LootTable table, RegistryOps<JsonElement> ops, Set<Item> found) {
        try {
            LootTable.DIRECT_CODEC.encodeStart(ops, table).result().ifPresent(json -> walk(json, found));
        } catch (RuntimeException e) {
            // A loot table with something that can't be written out: skip it.
        }
    }

    private static void walk(JsonElement json, Set<Item> found) {
        if (json.isJsonArray()) {
            json.getAsJsonArray().forEach(element -> walk(element, found));
        } else if (json.isJsonObject()) {
            JsonObject object = json.getAsJsonObject();
            if (object.has("type") && object.get("type").isJsonPrimitive() && "minecraft:item".equals(object.get("type").getAsString())
                    && object.has("name") && object.get("name").isJsonPrimitive()) {
                Identifier id = Identifier.tryParse(object.get("name").getAsString());
                if (id != null) {
                    BuiltInRegistries.ITEM.getOptional(id).ifPresent(found::add);
                }
            }
            object.entrySet().forEach(entry -> walk(entry.getValue(), found));
        }
    }
}
