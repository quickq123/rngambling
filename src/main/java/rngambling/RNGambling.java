package rngambling;

import com.mojang.serialization.Codec;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import rngambling.test.RNGamblingGameTests;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import rngambling.block.LuckyBlock;
import rngambling.block.LuckyBlockEntity;
import rngambling.block.LuckyBlockItem;
import rngambling.block.MimicLuckyBlock;
import rngambling.item.MimicWandItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.levelgen.feature.Feature;
import rngambling.worldgen.LuckyBlockFeature;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import rngambling.casino.CasinoHouses;
import rngambling.casino.SlotMachineBlock;
import rngambling.item.LuckCharmItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import rngambling.luck.Luck;

@Mod(RNGambling.MOD_ID)
public final class RNGambling {
    public static final String MOD_ID = "rngambling";

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    private static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MOD_ID);
    private static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    /** A lucky block's luck, from -100 to +100, carried on the item and copied into the placed block. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> LUCK =
            DATA_COMPONENTS.registerComponentType("luck", builder -> builder
                    .persistent(Codec.intRange(Luck.MIN, Luck.MAX))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** The bought (plain-textured) lucky block. The world-generated mimic comes later. */
    public static final DeferredBlock<LuckyBlock> LUCKY_BLOCK = BLOCKS.registerBlock("lucky_block", LuckyBlock::new,
            properties -> properties.mapColor(MapColor.COLOR_RED).strength(0.5f).sound(SoundType.WOOD));
    /**
     * Lucky blocks as items: the block's luck is rolled from the item's range when it's placed
     * (see LuckyBlockItem). The variants are sold by the Casino Dealer at higher levels.
     */
    public static final DeferredItem<LuckyBlockItem> LUCKY_BLOCK_ITEM = ITEMS.registerItem("lucky_block",
            properties -> new LuckyBlockItem(LUCKY_BLOCK.get(), properties, rngambling.block.LuckyBlock.RANDOM, "random", true),
            () -> new Item.Properties().useBlockDescriptionPrefix());
    public static final DeferredItem<LuckyBlockItem> SAFE_LUCKY_BLOCK_ITEM = ITEMS.registerItem("safe_lucky_block",
            properties -> new LuckyBlockItem(LUCKY_BLOCK.get(), properties, new rngambling.luck.LuckRoll.Even(-50, 50), "safe", false),
            () -> new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<LuckyBlockItem> EXTREME_LUCKY_BLOCK_ITEM = ITEMS.registerItem("extreme_lucky_block",
            properties -> new LuckyBlockItem(LUCKY_BLOCK.get(), properties, new rngambling.luck.LuckRoll.Extremes(50, 100, 0.5f), "extreme", false),
            () -> new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<LuckyBlockItem> BLESSED_LUCKY_BLOCK_ITEM = ITEMS.registerItem("blessed_lucky_block",
            properties -> new LuckyBlockItem(LUCKY_BLOCK.get(), properties, new rngambling.luck.LuckRoll.Even(80, 100), "blessed", false),
            () -> new Item.Properties().rarity(Rarity.EPIC));
    /**
     * The disguised lucky block: copies another block's look (a few percent darker), sound and
     * hardness. It has no item; it's made by world generation (later) or the Mimic Wand.
     */
    public static final DeferredBlock<MimicLuckyBlock> MIMIC_LUCKY_BLOCK = BLOCKS.registerBlock("mimic_lucky_block", MimicLuckyBlock::new,
            properties -> properties.mapColor(MapColor.STONE).strength(1.5f));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LuckyBlockEntity>> LUCKY_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("lucky_block", () -> new BlockEntityType<>(LuckyBlockEntity::new,
                    LUCKY_BLOCK.get(), MIMIC_LUCKY_BLOCK.get()));

    /** Creative only: turns a block into a disguised lucky block copying it. */
    public static final DeferredItem<MimicWandItem> MIMIC_WAND = ITEMS.registerItem("mimic_wand", MimicWandItem::new,
            () -> new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));

    /** The casino's currency: won from lucky blocks, gambled in the casino, traded with its villager. */
    public static final DeferredItem<Item> CASINO_TOKEN = ITEMS.registerSimpleItem("casino_token");
    /** Nine Casino Tokens, crafted together (and back apart), for the dealer's dearest trades. */
    public static final DeferredItem<Item> TOKEN_PILE = ITEMS.registerSimpleItem("token_pile");

    /**
     * Creative only: worn as a helmet, shows every nearby lucky block's luck through walls.
     * Drawn by the client's LuckOverlay.
     */
    public static final DeferredItem<Item> LUCK_GLASSES = ITEMS.registerItem("luck_glasses", Item::new,
            () -> new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).component(DataComponents.EQUIPPABLE,
                    Equippable.builder(EquipmentSlot.HEAD)
                            .setAsset(ResourceKey.create(EquipmentAssets.ROOT_ID, id("luck_glasses")))
                            .setEquipSound(SoundEvents.ARMOR_EQUIP_GENERIC)
                            .build()));

    /** The Casino Dealer's job site. Not craftable: found only in village casinos. */
    public static final DeferredBlock<SlotMachineBlock> SLOT_MACHINE = BLOCKS.registerBlock("slot_machine", SlotMachineBlock::new,
            properties -> properties.mapColor(MapColor.COLOR_RED).strength(3.5f).sound(SoundType.METAL).requiresCorrectToolForDrops());
    public static final DeferredItem<BlockItem> SLOT_MACHINE_ITEM = ITEMS.registerSimpleBlockItem(SLOT_MACHINE);

    private static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MOD_ID);
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<rngambling.casino.SlotMachineMenu>> SLOT_MACHINE_MENU =
            MENUS.register("slot_machine", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(
                    (containerId, inventory, buffer) -> new rngambling.casino.SlotMachineMenu(containerId, inventory, buffer.readBlockPos())));

    /** Casino tables: like the Slot Machine, found in village casinos, never crafted, but they drop themselves. */
    public static final DeferredBlock<rngambling.casino.CasinoTableBlock> ROULETTE_TABLE = BLOCKS.registerBlock("roulette_table",
            properties -> new rngambling.casino.CasinoTableBlock(rngambling.casino.RouletteMenu::new, properties),
            properties -> properties.mapColor(MapColor.COLOR_GREEN).strength(2.5f).sound(SoundType.WOOD));
    public static final DeferredItem<BlockItem> ROULETTE_TABLE_ITEM = ITEMS.registerSimpleBlockItem(ROULETTE_TABLE);
    public static final DeferredBlock<rngambling.casino.CasinoTableBlock> BLACKJACK_TABLE = BLOCKS.registerBlock("blackjack_table",
            properties -> new rngambling.casino.CasinoTableBlock(rngambling.casino.BlackjackMenu::new, properties),
            properties -> properties.mapColor(MapColor.COLOR_GREEN).strength(2.5f).sound(SoundType.WOOD));
    public static final DeferredItem<BlockItem> BLACKJACK_TABLE_ITEM = ITEMS.registerSimpleBlockItem(BLACKJACK_TABLE);
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<rngambling.casino.RouletteMenu>> ROULETTE_MENU =
            MENUS.register("roulette", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(
                    (containerId, inventory, buffer) -> new rngambling.casino.RouletteMenu(containerId, inventory, buffer.readBlockPos())));
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<rngambling.casino.BlackjackMenu>> BLACKJACK_MENU =
            MENUS.register("blackjack", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(
                    (containerId, inventory, buffer) -> new rngambling.casino.BlackjackMenu(containerId, inventory, buffer.readBlockPos())));

    /** One-use luck charms, sold by the Casino Dealer. */
    public static final DeferredItem<LuckCharmItem> FOUR_LEAF_CLOVER = ITEMS.registerItem("four_leaf_clover",
            properties -> new LuckCharmItem(10, properties), () -> new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<LuckCharmItem> GOLDEN_HORSESHOE = ITEMS.registerItem("golden_horseshoe",
            properties -> new LuckCharmItem(25, properties), () -> new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<LuckCharmItem> LUCKY_SEVEN = ITEMS.registerItem("lucky_seven",
            properties -> new LuckCharmItem(50, properties), () -> new Item.Properties().rarity(Rarity.EPIC));

    private static final DeferredRegister<PoiType> POI_TYPES = DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, MOD_ID);
    /** Casino machines, as places a villager can work. */
    public static final DeferredHolder<PoiType, PoiType> CASINO_MACHINE = POI_TYPES.register("casino_machine",
            () -> new PoiType(com.google.common.collect.ImmutableSet.<net.minecraft.world.level.block.state.BlockState>builder()
                    .addAll(SLOT_MACHINE.get().getStateDefinition().getPossibleStates())
                    .addAll(ROULETTE_TABLE.get().getStateDefinition().getPossibleStates())
                    .addAll(BLACKJACK_TABLE.get().getStateDefinition().getPossibleStates())
                    .build(), 1, 1));

    private static final DeferredRegister<VillagerProfession> PROFESSIONS = DeferredRegister.create(Registries.VILLAGER_PROFESSION, MOD_ID);
    /** Works a casino machine; trades Casino Tokens for lucky blocks and luck charms (data/rngambling/trade_set/dealer). */
    public static final DeferredHolder<VillagerProfession, VillagerProfession> DEALER = PROFESSIONS.register("dealer", () -> {
        it.unimi.dsi.fastutil.ints.Int2ObjectMap<ResourceKey<net.minecraft.world.item.trading.TradeSet>> trades =
                new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>();
        for (int level = 1; level <= 5; level++) {
            trades.put(level, ResourceKey.create(Registries.TRADE_SET, id("dealer/level_" + level)));
        }
        return new VillagerProfession(Component.translatable("entity.rngambling.villager.dealer"),
                poi -> poi.is(CASINO_MACHINE.getKey()), poi -> poi.is(CASINO_MACHINE.getKey()),
                com.google.common.collect.ImmutableSet.of(), com.google.common.collect.ImmutableSet.of(),
                SoundEvents.VILLAGER_WORK_CLERIC, trades);
    });

    private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, MOD_ID);
    /** World generation (see LuckyBlockFeature, and data/rngambling/worldgen and neoforge/biome_modifier). */
    public static final DeferredHolder<Feature<?>, LuckyBlockFeature> SURFACE_LUCKY_BLOCK = FEATURES.register("surface_lucky_block",
            () -> new LuckyBlockFeature(LuckyBlockFeature.Kind.SURFACE));
    public static final DeferredHolder<Feature<?>, LuckyBlockFeature> CAVE_LUCKY_BLOCK = FEATURES.register("cave_lucky_block",
            () -> new LuckyBlockFeature(LuckyBlockFeature.Kind.CAVE));
    public static final DeferredHolder<Feature<?>, LuckyBlockFeature> STRUCTURE_LUCKY_BLOCK = FEATURES.register("structure_lucky_block",
            () -> new LuckyBlockFeature(LuckyBlockFeature.Kind.STRUCTURE));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_TAB = CREATIVE_TABS.register(MOD_ID,
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.rngambling"))
                    .icon(() -> new ItemStack(LUCKY_BLOCK_ITEM.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(LUCKY_BLOCK_ITEM.get());
                        output.accept(SAFE_LUCKY_BLOCK_ITEM.get());
                        output.accept(EXTREME_LUCKY_BLOCK_ITEM.get());
                        output.accept(BLESSED_LUCKY_BLOCK_ITEM.get());
                        // Test blocks with a fixed luck (kept only when a creative player places them).
                        for (int luck : new int[] {0, 50, 100, -50, -100}) {
                            output.accept(LuckyBlockItem.withLuck(luck));
                        }
                        output.accept(CASINO_TOKEN.get());
                        output.accept(TOKEN_PILE.get());
                        output.accept(FOUR_LEAF_CLOVER.get());
                        output.accept(GOLDEN_HORSESHOE.get());
                        output.accept(LUCKY_SEVEN.get());
                        output.accept(SLOT_MACHINE_ITEM.get());
                        output.accept(ROULETTE_TABLE_ITEM.get());
                        output.accept(BLACKJACK_TABLE_ITEM.get());
                        output.accept(MIMIC_WAND.get());
                        output.accept(LUCK_GLASSES.get());
                    })
                    .build());

    public RNGambling(IEventBus modBus, ModContainer container) {
        DATA_COMPONENTS.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        CREATIVE_TABS.register(modBus);
        FEATURES.register(modBus);
        POI_TYPES.register(modBus);
        MENUS.register(modBus);
        PROFESSIONS.register(modBus);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(CasinoHouses::addToVillages);
        // The Sandbox dimension (see rngambling.outcome.Sandbox), and its admin commands.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.RegisterCommandsEvent event) ->
                rngambling.outcome.SandboxCommands.register(event.getDispatcher()));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) ->
                rngambling.outcome.Sandbox.tick(event.getServer()));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                rngambling.outcome.Sandbox.onLogin(player);
            }
        });
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, RNGamblingConfig.SPEC);
        // Luck zones: datapack files in data/<namespace>/rngambling/luck_zone/ (see LuckZone).
        modBus.addListener((net.neoforged.neoforge.registries.DataPackRegistryEvent.NewRegistry event) ->
                event.dataPackRegistry(rngambling.luck.LuckZone.REGISTRY, rngambling.luck.LuckZone.CODEC));
        if (Boolean.getBoolean("rngambling.gameTests")) {
            registerGameTests(modBus);
        }
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    /** Only in the game test run (see build.gradle): registers the tests in rngambling.test.RNGamblingGameTests. */
    private static void registerGameTests(IEventBus modBus) {
        DeferredRegister<Consumer<GameTestHelper>> functions = DeferredRegister.create(Registries.TEST_FUNCTION, MOD_ID);
        RNGamblingGameTests.ALL.forEach((name, body) -> functions.register(name, () -> body));
        functions.register(modBus);
        modBus.addListener((RegisterGameTestsEvent event) -> {
            Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(id("default"));
            for (String name : RNGamblingGameTests.ALL.keySet()) {
                event.registerTest(id(name), new FunctionGameTestInstance(
                        ResourceKey.create(Registries.TEST_FUNCTION, id(name)),
                        // Padding keeps tests that build or scan beyond the small test structure apart.
                        new TestData<>(environment, id("empty"), 400, 0, true, net.minecraft.world.level.block.Rotation.NONE,
                                false, 1, 1, false, 32)));
            }
        });
    }
}
