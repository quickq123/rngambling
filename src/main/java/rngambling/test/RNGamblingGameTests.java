package rngambling.test;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import org.slf4j.Logger;
import rngambling.RNGambling;
import rngambling.block.LuckyBlock;
import rngambling.block.LuckyBlockEntity;
import rngambling.luck.Luck;
import rngambling.outcome.Outcome;
import rngambling.outcome.OutcomeContext;
import rngambling.outcome.Outcomes;

/** The mod's game tests. Run: gradlew runGameTestServer (the exit code is the number of failures). */
public final class RNGamblingGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();

    private RNGamblingGameTests() {
    }

    public static final Map<String, Consumer<GameTestHelper>> ALL = Map.ofEntries(
            Map.entry("every_outcome_runs", RNGamblingGameTests::everyOutcomeRuns),
            Map.entry("break_rules", RNGamblingGameTests::breakRules),
            Map.entry("luck_tilts_the_odds", RNGamblingGameTests::luckTiltsTheOdds),
            Map.entry("luck_survives_placing", RNGamblingGameTests::luckSurvivesPlacing),
            Map.entry("mimic_wand", RNGamblingGameTests::mimicWand),
            Map.entry("world_generation", RNGamblingGameTests::worldGeneration),
            Map.entry("luck_zones", RNGamblingGameTests::luckZones),
            Map.entry("casino", RNGamblingGameTests::casino),
            Map.entry("luck_charms", RNGamblingGameTests::luckCharms),
            Map.entry("slot_machine", RNGamblingGameTests::slotMachine),
            Map.entry("roulette", RNGamblingGameTests::roulette),
            Map.entry("blackjack", RNGamblingGameTests::blackjack),
            Map.entry("machines_drop_themselves", RNGamblingGameTests::machinesDropThemselves),
            Map.entry("placing_rolls_luck", RNGamblingGameTests::placingRollsLuck),
            Map.entry("themes", RNGamblingGameTests::themes),
            Map.entry("sandbox_fallback", RNGamblingGameTests::sandboxFallback),
            Map.entry("sandbox_records", RNGamblingGameTests::sandboxRecords),
            Map.entry("rooms_build", RNGamblingGameTests::roomsBuild),
            Map.entry("custom_effects", RNGamblingGameTests::customEffects),
            Map.entry("effect_commands", RNGamblingGameTests::effectCommands));

    /**
     * The slot machine keeps a small house edge (it pays back 90-94% of tokens bet, worked out
     * exactly and by simulation), takes the bet, refuses a bet the player can't cover, and pays the
     * win once the reels have stopped.
     */
    private static void slotMachine(GameTestHelper helper) {
        double exact = rngambling.casino.SlotMachine.returnToPlayer();
        LOGGER.info("slot machine pays back {}% of tokens bet", String.format("%.2f", exact * 100));
        helper.assertTrue(exact > 0.90 && exact < 0.94, "the slot machine should pay back 90-94%, not " + exact);
        RandomSource random = RandomSource.create(7);
        long paid = 0;
        int spins = 300_000;
        for (int i = 0; i < spins; i++) {
            paid += rngambling.casino.SlotMachine.pays(rngambling.casino.SlotMachine.spin(random));
        }
        double simulated = paid / (double) spins;
        LOGGER.info("slot machine simulated over {} spins: {}%", spins, String.format("%.2f", simulated * 100));
        helper.assertTrue(Math.abs(simulated - exact) < 0.02, "a simulation should agree with the exact figure: " + simulated);

        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, RNGambling.SLOT_MACHINE.get());
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        player.getInventory().add(new ItemStack(RNGambling.CASINO_TOKEN.get(), 7));
        player.snapTo(helper.absolutePos(pos).getX() + 1.5, helper.absolutePos(pos).getY(), helper.absolutePos(pos).getZ() + 0.5);
        rngambling.casino.SlotMachineMenu menu = new rngambling.casino.SlotMachineMenu(0, player.getInventory(), helper.absolutePos(pos));
        menu.clickMenuButton(player, 2); // bet 10: more than the 7 tokens held
        menu.clickMenuButton(player, rngambling.casino.SlotMachineMenu.BUTTON_SPIN);
        helper.assertTrue(menu.data(rngambling.casino.SlotMachineMenu.SPINS) == 0
                && rngambling.casino.SlotMachineMenu.countTokens(player.getInventory()) == 7, "a bet of 10 with 7 tokens shouldn't spin");
        menu.clickMenuButton(player, 1); // bet 5
        menu.clickMenuButton(player, rngambling.casino.SlotMachineMenu.BUTTON_SPIN);
        helper.assertTrue(menu.data(rngambling.casino.SlotMachineMenu.SPINS) == 1
                && rngambling.casino.SlotMachineMenu.countTokens(player.getInventory()) == 2, "spinning should take the 5 token bet");
        rngambling.casino.SlotMachine.Symbol[] reels = {menu.reel(0), menu.reel(1), menu.reel(2)};
        int win = 5 * rngambling.casino.SlotMachine.pays(reels);
        helper.runAfterDelay(rngambling.casino.SlotMachineMenu.SPIN_TICKS + 2, () -> {
            menu.broadcastChanges();
            try {
                helper.assertTrue(!menu.spinning() && menu.data(rngambling.casino.SlotMachineMenu.LAST_WIN) == win
                        && rngambling.casino.SlotMachineMenu.countTokens(player.getInventory()) == 2 + win,
                        "the win (" + win + " for " + java.util.Arrays.toString(reels) + ") should be paid once the reels stop");
            } finally {
                helper.getLevel().getServer().getPlayerList().remove(player);
            }
            helper.succeed();
        });
    }

    /**
     * The casino's pieces: the dealer's trades load (with luck on the lucky blocks they sell), the
     * Slot Machine is the dealer's job site, and every village type has a casino house with a
     * Slot Machine in it.
     */
    private static void casino(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var tradeSets = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.TRADE_SET);
        // Which lucky block each level sells (the first level it appears), and what the Blessed one costs.
        java.util.Map<String, Integer> firstLevel = new java.util.HashMap<>();
        String blessedCost = null;
        for (int tier = 1; tier <= 5; tier++) {
            var set = tradeSets.getValue(RNGambling.id("dealer/level_" + tier));
            int expected = tier == 4 ? 1 : 2;
            helper.assertTrue(set != null && set.getTrades().size() == expected, "dealer level " + tier + " should have " + expected + " trades");
            for (var trade : set.getTrades()) {
                // The trade's fields are private, so read it back as JSON.
                var json = net.minecraft.world.item.trading.VillagerTrade.CODEC.encodeStart(
                        level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE), trade.value())
                        .getOrThrow().getAsJsonObject();
                String gives = json.getAsJsonObject("gives").get("id").getAsString();
                if (gives.contains("lucky_block")) {
                    firstLevel.putIfAbsent(gives, tier);
                }
                if (gives.equals("rngambling:blessed_lucky_block")) {
                    var wants = json.getAsJsonObject("wants");
                    blessedCost = wants.get("count").getAsInt() + " " + wants.get("id").getAsString();
                }
            }
        }
        helper.assertTrue(firstLevel.equals(java.util.Map.of("rngambling:lucky_block", 1, "rngambling:safe_lucky_block", 2,
                "rngambling:extreme_lucky_block", 3, "rngambling:blessed_lucky_block", 5)),
                "the dealer should sell Lucky, Safe, Extreme then Blessed blocks, not " + firstLevel);
        helper.assertTrue("16 rngambling:token_pile".equals(blessedCost), "a Blessed Lucky Block should cost 16 piles, not " + blessedCost);
        // Piles: nine tokens make one, and one makes nine.
        var recipes = level.getServer().getRecipeManager();
        for (String recipe : new String[] {"token_pile", "casino_tokens_from_token_pile"}) {
            helper.assertTrue(recipes.byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE,
                    RNGambling.id(recipe))).isPresent(), "the " + recipe + " recipe should load");
        }

        var poi = net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(RNGambling.SLOT_MACHINE.get().defaultBlockState());
        helper.assertTrue(poi.isPresent() && RNGambling.DEALER.get().acquirableJobSite().test(poi.get()),
                "the Slot Machine should be the Casino Dealer's job site");
        helper.assertTrue(poi.get().is(net.minecraft.tags.PoiTypeTags.ACQUIRABLE_JOB_SITE), "villagers should look for it as a job site");

        var pools = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.TEMPLATE_POOL);
        for (String village : new String[] {"plains", "desert", "savanna", "snowy", "taiga"}) {
            var pool = pools.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("village/" + village + "/houses"));
            net.minecraft.world.level.block.Block[] machines = {RNGambling.SLOT_MACHINE.get(), RNGambling.ROULETTE_TABLE.get(),
                    RNGambling.BLACKJACK_TABLE.get()};
            for (int g = 0; g < rngambling.casino.CasinoHouses.GAMES.length; g++) {
                String name = "village/" + village + "/casino_" + rngambling.casino.CasinoHouses.GAMES[g];
                helper.assertTrue(pool.templates.stream().anyMatch(e -> e.toString().contains("rngambling:" + name)),
                        "the " + village + " village houses should include " + name);
                var template = level.getStructureManager().get(RNGambling.id(name));
                helper.assertTrue(template.isPresent(), name + " should load");
                boolean hasMachine = template.get().filterBlocks(BlockPos.ZERO,
                        new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(), machines[g]).size() == 1;
                helper.assertTrue(hasMachine, name + " should have its game in it");
                var machinePoi = net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(machines[g].defaultBlockState());
                helper.assertTrue(machinePoi.isPresent() && RNGambling.DEALER.get().acquirableJobSite().test(machinePoi.get()),
                        machines[g].getName().getString() + " should be a Casino Dealer job site");
            }
        }
        helper.succeed();
    }

    /**
     * A placed lucky block rolls its luck from the item's range (so carrying one somewhere gains
     * nothing); a creative player's fixed test luck is kept, but a survival player's isn't.
     */
    private static void placingRollsLuck(GameTestHelper helper) {
        ServerPlayer survival = player(helper, GameType.SURVIVAL);
        ServerPlayer creative = player(helper, GameType.CREATIVE);
        try {
            BlockPos ground = new BlockPos(2, 1, 2);
            helper.setBlock(ground, Blocks.STONE);
            BlockPos pos = ground.above();
            Object[][] kinds = {{RNGambling.LUCKY_BLOCK_ITEM.get(), -100, 100}, {RNGambling.SAFE_LUCKY_BLOCK_ITEM.get(), -50, 50},
                    {RNGambling.EXTREME_LUCKY_BLOCK_ITEM.get(), -100, 100}, {RNGambling.BLESSED_LUCKY_BLOCK_ITEM.get(), 80, 100}};
            for (Object[] kind : kinds) {
                net.minecraft.world.item.Item item = (net.minecraft.world.item.Item) kind[0];
                java.util.Set<Integer> seen = new java.util.HashSet<>();
                for (int i = 0; i < 40; i++) {
                    int luck = place(helper, survival, new ItemStack(item), ground);
                    helper.assertTrue(luck >= (int) kind[1] && luck <= (int) kind[2], item + " rolled " + luck);
                    if (item == RNGambling.EXTREME_LUCKY_BLOCK_ITEM.get()) {
                        helper.assertTrue(Math.abs(luck) >= 50, "an Extreme Lucky Block rolled " + luck);
                    }
                    seen.add(luck);
                    helper.setBlock(pos, Blocks.AIR);
                }
                helper.assertTrue(seen.size() > 5, item + " should roll a different luck each time: " + seen);
            }
            // Fixed test luck: kept in creative, rolled over in survival.
            helper.assertTrue(place(helper, creative, rngambling.block.LuckyBlockItem.withLuck(-77), ground) == -77,
                    "a creative player's fixed test luck should be kept");
            helper.setBlock(pos, Blocks.AIR);
            int rolled = 0;
            for (int i = 0; i < 10; i++) {
                rolled += place(helper, survival, rngambling.block.LuckyBlockItem.withLuck(-77), ground) == -77 ? 1 : 0;
                helper.setBlock(pos, Blocks.AIR);
            }
            helper.assertTrue(rolled < 3, "a survival player shouldn't keep a fixed luck");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(survival);
            helper.getLevel().getServer().getPlayerList().remove(creative);
        }
        helper.succeed();
    }

    /** Places a lucky block item on top of {@code ground} as {@code player} would, and returns its luck. */
    private static int place(GameTestHelper helper, ServerPlayer player, ItemStack stack, BlockPos ground) {
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
        BlockPos abs = helper.absolutePos(ground);
        stack.useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(Vec3OnTop(abs), net.minecraft.core.Direction.UP, abs, false)));
        return helper.getBlockEntity(ground.above(), LuckyBlockEntity.class).luck();
    }

    private static net.minecraft.world.phys.Vec3 Vec3OnTop(BlockPos pos) {
        return new net.minecraft.world.phys.Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
    }

    /** Every casino machine drops itself when broken, so a player can take it home. */
    private static void machinesDropThemselves(GameTestHelper helper) {
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        try {
            net.minecraft.world.level.block.Block[] machines = {RNGambling.SLOT_MACHINE.get(), RNGambling.ROULETTE_TABLE.get(),
                    RNGambling.BLACKJACK_TABLE.get()};
            for (int i = 0; i < machines.length; i++) {
                BlockPos pos = new BlockPos(1 + 2 * i, 1, 1);
                helper.setBlock(pos, machines[i]);
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(
                        machines[i] == RNGambling.SLOT_MACHINE.get() ? net.minecraft.world.item.Items.IRON_PICKAXE : net.minecraft.world.item.Items.IRON_AXE));
                player.gameMode.destroyBlock(helper.absolutePos(pos));
                helper.assertBlockNotPresent(machines[i], pos);
                helper.assertItemEntityPresent(machines[i].asItem(), pos, 2.0);
            }
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /** Every roulette bet returns exactly 36/37 (a 2.7% house edge), and a spin takes the bet and pays a win. */
    private static void roulette(GameTestHelper helper) {
        for (rngambling.casino.Roulette.Bet bet : rngambling.casino.Roulette.Bet.values()) {
            double back = rngambling.casino.Roulette.returnToPlayer(bet, 17);
            helper.assertTrue(Math.abs(back - 36.0 / 37) < 1e-9, bet + " should return 36/37, not " + back);
        }
        int reds = 0;
        for (int n = 0; n <= 36; n++) {
            reds += rngambling.casino.Roulette.isRed(n) ? 1 : 0;
        }
        helper.assertTrue(reds == 18 && !rngambling.casino.Roulette.isRed(0) && !rngambling.casino.Roulette.isBlack(0),
                "18 red numbers, and zero is neither red nor black");

        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, RNGambling.ROULETTE_TABLE.get());
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        player.getInventory().add(new ItemStack(RNGambling.CASINO_TOKEN.get(), 20));
        rngambling.casino.RouletteMenu menu = new rngambling.casino.RouletteMenu(0, player.getInventory(), helper.absolutePos(pos));
        menu.clickMenuButton(player, 2); // 10 tokens
        menu.clickMenuButton(player, rngambling.casino.RouletteMenu.BUTTON_KIND + rngambling.casino.Roulette.Bet.RED.ordinal());
        menu.clickMenuButton(player, rngambling.casino.RouletteMenu.BUTTON_SPIN);
        int number = menu.data(rngambling.casino.RouletteMenu.RESULT);
        helper.assertTrue(rngambling.casino.Tokens.count(player.getInventory()) == 10 && number >= 0 && number <= 36,
                "a spin should take the 10 token bet and draw a number");
        int win = rngambling.casino.Roulette.isRed(number) ? 20 : 0;
        helper.runAfterDelay(rngambling.casino.RouletteMenu.SPIN_TICKS + 2, () -> {
            menu.broadcastChanges();
            try {
                helper.assertTrue(menu.data(rngambling.casino.RouletteMenu.LAST_WIN) == win
                        && rngambling.casino.Tokens.count(player.getInventory()) == 10 + win,
                        "10 on red with " + number + " should pay " + win);
            } finally {
                helper.getLevel().getServer().getPlayerList().remove(player);
            }
            helper.succeed();
        });
    }

    /**
     * Blackjack keeps a small house edge even against perfect play (a million hands by basic
     * strategy), never shows the dealer's face-down card early, and settles a hand correctly.
     */
    private static void blackjack(GameTestHelper helper) {
        RandomSource random = RandomSource.create(11);
        double net = 0;
        int hands = 1_000_000;
        for (int i = 0; i < hands; i++) {
            net += rngambling.casino.Blackjack.playOneHand(random);
        }
        double edge = -net / hands;
        LOGGER.info("blackjack by basic strategy over {} hands: house edge {}% of the starting bet", hands, String.format("%.2f", edge * 100));
        helper.assertTrue(edge > 0.0 && edge < 0.03, "blackjack should keep a small house edge against perfect play: " + edge);

        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, RNGambling.BLACKJACK_TABLE.get());
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        try {
            player.getInventory().add(new ItemStack(RNGambling.CASINO_TOKEN.get(), 64));
            rngambling.casino.BlackjackMenu menu = new rngambling.casino.BlackjackMenu(0, player.getInventory(), helper.absolutePos(pos));
            int played = 0;
            int hiddenSeen = 0;
            for (int hand = 0; hand < 40; hand++) {
                int before = rngambling.casino.Tokens.count(player.getInventory());
                menu.clickMenuButton(player, rngambling.casino.BlackjackMenu.BUTTON_DEAL);
                helper.assertTrue(rngambling.casino.Tokens.count(player.getInventory()) >= before - 2, "a deal takes at most the 2 token bet");
                if (menu.phase() == rngambling.casino.BlackjackMenu.Phase.PLAYER) {
                    // While the player decides, the dealer's second card is face down in what the screen is sent.
                    helper.assertTrue(menu.cards(true).get(1) == rngambling.casino.BlackjackMenu.HIDDEN, "the dealer's hole card should be hidden");
                    hiddenSeen++;
                    menu.clickMenuButton(player, rngambling.casino.BlackjackMenu.BUTTON_STAND);
                    menu.removed(player); // finishes the dealer's turn at once
                }
                helper.assertTrue(menu.phase() == rngambling.casino.BlackjackMenu.Phase.BETTING, "the hand should be settled");
                java.util.List<Integer> dealer = menu.cards(true);
                helper.assertTrue(!dealer.contains(rngambling.casino.BlackjackMenu.HIDDEN), "the dealer's cards are shown once settled");
                int win = menu.data(rngambling.casino.BlackjackMenu.LAST_WIN);
                helper.assertTrue(rngambling.casino.Tokens.count(player.getInventory()) == before - 2 + win,
                        "the tokens should change by the win less the bet");
                played++;
            }
            helper.assertTrue(played == 40 && hiddenSeen > 20, "40 hands should be played, most with a player's turn");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /** Charms raise a lucky block's luck by their amount, stop at the most there is, and are used up. */
    private static void luckCharms(GameTestHelper helper) {
        ServerPlayer survival = player(helper, GameType.SURVIVAL);
        try {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RNGambling.LUCKY_BLOCK.get());
            LuckyBlockEntity lucky = helper.getBlockEntity(pos, LuckyBlockEntity.class);
            int[] expected = {10, 35, 85, 100};
            net.minecraft.world.item.Item[] charms = {RNGambling.FOUR_LEAF_CLOVER.get(), RNGambling.GOLDEN_HORSESHOE.get(),
                    RNGambling.LUCKY_SEVEN.get(), RNGambling.LUCKY_SEVEN.get()};
            for (int i = 0; i < charms.length; i++) {
                ItemStack charm = new ItemStack(charms[i], 2);
                survival.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, charm);
                BlockPos abs = helper.absolutePos(pos);
                charm.useOn(new net.minecraft.world.item.context.UseOnContext(survival, net.minecraft.world.InteractionHand.MAIN_HAND,
                        new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(abs), net.minecraft.core.Direction.UP, abs, false)));
                helper.assertTrue(lucky.luck() == expected[i], "after charm " + (i + 1) + " luck should be " + expected[i] + ", not " + lucky.luck());
                helper.assertTrue(charm.getCount() == 1, "a charm should be used up");
            }
            // At the most there is, a charm isn't used.
            ItemStack spare = new ItemStack(RNGambling.FOUR_LEAF_CLOVER.get());
            survival.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, spare);
            BlockPos abs = helper.absolutePos(pos);
            spare.useOn(new net.minecraft.world.item.context.UseOnContext(survival, net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(abs), net.minecraft.core.Direction.UP, abs, false)));
            helper.assertTrue(spare.getCount() == 1 && lucky.luck() == Luck.MAX, "a charm shouldn't be used on a block already at the most luck");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(survival);
        }
        helper.succeed();
    }

    /** The shipped zone files load, the Overworld zone applies here, and each zone's rolls stay in its range. */
    private static void luckZones(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        net.minecraft.core.Registry<rngambling.luck.LuckZone> zones = level.registryAccess().lookupOrThrow(rngambling.luck.LuckZone.REGISTRY);
        helper.assertTrue(zones.size() == 7, "7 luck zones should load, not " + zones.size() + ": " + zones.keySet());
        rngambling.luck.LuckZone here = rngambling.luck.LuckZone.at(level, helper.absolutePos(new BlockPos(1, 1, 1)));
        helper.assertTrue(here == zones.getValue(RNGambling.id("overworld")), "the test spot should be in the Overworld zone");

        RandomSource random = RandomSource.create(5);
        rngambling.luck.LuckZone nether = zones.getValue(RNGambling.id("nether"));
        rngambling.luck.LuckZone end = zones.getValue(RNGambling.id("end"));
        rngambling.luck.LuckZone city = zones.getValue(RNGambling.id("ancient_city"));
        rngambling.luck.LuckZone mushrooms = zones.getValue(RNGambling.id("mushroom_fields"));
        int netherGood = 0;
        double mushroomTotal = 0;
        for (int i = 0; i < 1000; i++) {
            int n = nether.luck().roll(random);
            helper.assertTrue(Math.abs(n) >= 50, "Nether luck should have no middle ground: " + n);
            netherGood += n > 0 ? 1 : 0;
            int e = end.luck().roll(random);
            helper.assertTrue(e >= 50 && e <= 100, "End luck should be +50 to +100: " + e);
            int c = city.luck().roll(random);
            helper.assertTrue(Math.abs(c) >= 70, "Ancient City luck should be 70 to 100 either way: " + c);
            mushroomTotal += mushrooms.luck().roll(random);
        }
        helper.assertTrue(netherGood > 400 && netherGood < 600, "Nether luck should be good about half the time: " + netherGood);
        helper.assertTrue(Math.abs(mushroomTotal / 1000 - 20) < 4, "Mushroom Fields luck should average about +20: " + mushroomTotal / 1000);
        helper.assertTrue(nether.caveRarity() == 6 && nether.surfaceRarity() == 0 && end.surfaceRarity() == 6,
                "zone rarities should come from the files");

        // The Mimic Wand follows the zone too: used in the Nether, it never gives middling luck.
        ServerLevel netherLevel = level.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        BlockPos spot = new BlockPos(0, 100, 0);
        ServerPlayer creative = player(helper, GameType.CREATIVE);
        try {
            for (int i = 0; i < 20; i++) {
                netherLevel.setBlockAndUpdate(spot, Blocks.NETHERRACK.defaultBlockState());
                ItemStack wand = new ItemStack(RNGambling.MIMIC_WAND.get());
                creative.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, wand);
                wand.useOn(new net.minecraft.world.item.context.UseOnContext(netherLevel, creative, net.minecraft.world.InteractionHand.MAIN_HAND,
                        wand, new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(spot),
                        net.minecraft.core.Direction.UP, spot, false)));
                helper.assertTrue(netherLevel.getBlockEntity(spot) instanceof LuckyBlockEntity lucky && Math.abs(lucky.luck()) >= 50,
                        "the wand in the Nether should give luck of ±50 or more");
            }
        } finally {
            netherLevel.setBlockAndUpdate(spot, Blocks.AIR.defaultBlockState());
            level.getServer().getPlayerList().remove(creative);
        }
        helper.succeed();
    }

    /**
     * The placement rules on a built scene: a cave placement finds the one block that opens onto
     * a sealed pocket of air, and a surface placement copies the ground but not water.
     */
    private static void worldGeneration(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // A sealed flat cave, 15 x 15: stone floor, a layer of cave air, a stone roof (so it can't see the sky).
        for (int x = 0; x < 15; x++) {
            for (int z = 0; z < 15; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, 2, z), Blocks.CAVE_AIR);
                helper.setBlock(new BlockPos(x, 3, z), Blocks.DEEPSLATE);
            }
        }
        for (int attempt = 0; attempt < 5; attempt++) {
            helper.assertTrue(rngambling.worldgen.LuckyBlockFeature.tryPlace(rngambling.worldgen.LuckyBlockFeature.Kind.CAVE, level,
                    RandomSource.create(attempt), helper.absolutePos(new BlockPos(7, 2, 7))), "a cave placement should find the cave");
        }
        int disguised = 0;
        for (int x = 0; x < 15; x++) {
            for (int z = 0; z < 15; z++) {
                for (int y : new int[] {1, 3}) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (helper.getBlockState(pos).is(RNGambling.MIMIC_LUCKY_BLOCK.get())) {
                        disguised++;
                        LuckyBlockEntity lucky = helper.getBlockEntity(pos, LuckyBlockEntity.class);
                        helper.assertTrue(lucky.mimic() != null && lucky.mimic().is(y == 1 ? Blocks.STONE : Blocks.DEEPSLATE),
                                "it should copy the block it replaced");
                    }
                }
            }
        }
        helper.assertTrue(disguised == 5, "five cave placements should make five disguised floor or roof blocks, not " + disguised);

        // Surface: dirt under open air is copied; water is not.
        BlockPos dirt = new BlockPos(16, 1, 1);
        helper.setBlock(dirt, Blocks.DIRT);
        helper.assertTrue(rngambling.worldgen.LuckyBlockFeature.tryPlace(rngambling.worldgen.LuckyBlockFeature.Kind.SURFACE, level,
                RandomSource.create(2), helper.absolutePos(dirt.above())), "dirt under air should be disguised");
        helper.assertBlockPresent(RNGambling.MIMIC_LUCKY_BLOCK.get(), dirt);
        BlockPos water = new BlockPos(16, 1, 3);
        helper.setBlock(water, Blocks.WATER);
        helper.assertFalse(rngambling.worldgen.LuckyBlockFeature.tryPlace(rngambling.worldgen.LuckyBlockFeature.Kind.SURFACE, level,
                RandomSource.create(3), helper.absolutePos(water.above())), "water can't be disguised");
        helper.succeed();
    }

    /** The Mimic Wand disguises plain blocks, reports and undoes, refuses the rest, and is creative only. */
    private static void mimicWand(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer creative = player(helper, GameType.CREATIVE);
        ServerPlayer survival = player(helper, GameType.SURVIVAL);
        try {
            BlockPos stone = new BlockPos(1, 1, 1);
            BlockPos chest = new BlockPos(3, 1, 1);
            helper.setBlock(stone, Blocks.STONE);
            helper.setBlock(chest, Blocks.CHEST);

            helper.assertTrue(wand(helper, survival, stone) == net.minecraft.world.InteractionResult.FAIL, "survival players can't use the wand");
            helper.assertBlockPresent(Blocks.STONE, stone);
            helper.assertTrue(wand(helper, creative, chest) == net.minecraft.world.InteractionResult.FAIL, "a chest can't be disguised");
            helper.assertBlockPresent(Blocks.CHEST, chest);

            wand(helper, creative, stone);
            helper.assertBlockPresent(RNGambling.MIMIC_LUCKY_BLOCK.get(), stone);
            LuckyBlockEntity lucky = helper.getBlockEntity(stone, LuckyBlockEntity.class);
            helper.assertTrue(lucky.mimic() != null && lucky.mimic().is(Blocks.STONE), "the disguise should copy stone");
            // It mines like stone, and middle-click gives stone.
            BlockPos abs = helper.absolutePos(stone);
            float progress = level.getBlockState(abs).getDestroyProgress(survival, level, abs);
            float stoneProgress = Blocks.STONE.defaultBlockState().getDestroyProgress(survival, level, abs);
            helper.assertTrue(Math.abs(progress - stoneProgress) < 1e-6, "it should mine like stone: " + progress + " vs " + stoneProgress);
            helper.assertTrue(level.getBlockState(abs).getCloneItemStack(abs, level, false, creative).is(Blocks.STONE.asItem()),
                    "middle-click should give stone");
            // It survives a save and load.
            ServerLevel serverLevel = level;
            net.minecraft.world.level.block.entity.BlockEntity reloaded = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                    abs, level.getBlockState(abs), lucky.saveWithFullMetadata(serverLevel.registryAccess()), serverLevel.registryAccess());
            helper.assertTrue(reloaded instanceof LuckyBlockEntity copy && copy.mimic() != null && copy.mimic().is(Blocks.STONE)
                    && copy.luck() == lucky.luck(), "the disguise and luck should be saved");

            // Sneak + use turns it back.
            creative.setShiftKeyDown(true);
            wand(helper, creative, stone);
            creative.setShiftKeyDown(false);
            helper.assertBlockPresent(Blocks.STONE, stone);

            // A survival player breaking a disguised block sets off an outcome and removes it.
            wand(helper, creative, stone);
            survival.snapTo(abs.getX() + 3.5, abs.getY(), abs.getZ() + 0.5);
            survival.gameMode.destroyBlock(abs);
            helper.assertBlockNotPresent(RNGambling.MIMIC_LUCKY_BLOCK.get(), stone);
            clearUp(helper, level, survival);
        } finally {
            level.getServer().getPlayerList().remove(creative);
            level.getServer().getPlayerList().remove(survival);
        }
        helper.succeed();
    }

    private static net.minecraft.world.InteractionResult wand(GameTestHelper helper, ServerPlayer player, BlockPos relative) {
        BlockPos abs = helper.absolutePos(relative);
        ItemStack wand = new ItemStack(RNGambling.MIMIC_WAND.get());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, wand);
        net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(abs), net.minecraft.core.Direction.UP, abs, false);
        return wand.useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
    }

    /**
     * Themes swap outcomes by the zone a block is broken in: the Nether's list has blazes, not
     * zombies, and a block broken in the Nether draws from it; zones without a theme use the
     * ordinary list.
     */
    private static void themes(GameTestHelper helper) {
        java.util.Set<String> nether = new java.util.HashSet<>();
        for (Outcome outcome : rngambling.outcome.Themes.outcomesFor("nether")) {
            nether.add(outcome.id());
        }
        helper.assertTrue(nether.contains("blaze_horde") && !nether.contains("zombie_horde") && nether.contains("netherite_scrap")
                && nether.contains("diamond_haul"), "the Nether's outcomes should swap the zombie horde for blazes and keep the rest: " + nether);
        helper.assertTrue(rngambling.outcome.Themes.outcomesFor("overworld") == Outcomes.ALL, "the Overworld uses the ordinary outcomes");
        for (String zone : rngambling.outcome.Themes.BY_ZONE.keySet()) {
            helper.assertTrue(helper.getLevel().registryAccess().lookupOrThrow(rngambling.luck.LuckZone.REGISTRY)
                    .containsKey(RNGambling.id(zone)), "theme " + zone + " should match a luck zone");
        }
        // Breaking in the Nether: every draw comes from the Nether's list.
        ServerLevel netherLevel = helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        ServerPlayer player = player(helper, GameType.CREATIVE);
        try {
            BlockPos spot = new BlockPos(0, 100, 0);
            netherLevel.setBlockAndUpdate(spot.below(), Blocks.NETHERRACK.defaultBlockState());
            for (int i = 0; i < 30; i++) {
                Outcome outcome = Outcomes.roll(netherLevel, spot, player, 0);
                helper.assertTrue(nether.contains(outcome.id()), outcome.id() + " isn't a Nether outcome");
                for (var entity : netherLevel.getEntities((net.minecraft.world.entity.Entity) null,
                        new net.minecraft.world.phys.AABB(spot).inflate(20), e -> !(e instanceof net.minecraft.world.entity.player.Player))) {
                    entity.discard();
                }
            }
        } finally {
            rngambling.outcome.Scheduler.clear(); // this test only checks which effects are drawn
            for (BlockPos p : BlockPos.betweenClosed(new BlockPos(-6, 95, -6), new BlockPos(6, 110, 6))) {
                if (!netherLevel.getBlockState(p).isAir() && !netherLevel.getBlockState(p).is(Blocks.NETHERRACK)) {
                    netherLevel.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
                }
            }
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    private static Outcome outcome(String id) {
        return Outcomes.ALL.stream().filter(o -> o.id().equals(id)).findFirst().orElseThrow();
    }

    /**
     * The game test server leaves out dimensions added by data files, so the Sandbox isn't there:
     * a grief-like outcome then happens where the block was (the fallback). The real Sandbox is
     * checked in a real world by runClientShots -PshotsWorld=sandbox.
     */
    private static void sandboxFallback(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(level.getServer().getLevel(rngambling.outcome.Sandbox.LEVEL) == null,
                "the game test server has no Sandbox (if it does now, test the visit here instead)");
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        try {
            BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
            player.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, java.util.Set.of(), 0f, 0f, true);
            Outcomes.play(level, at, player, 0, outcome("spider_web_trap"));
            helper.assertTrue(player.level() == level && !rngambling.outcome.Sandbox.visiting(player),
                    "with no Sandbox the player should stay put");
            int webs = 0;
            for (BlockPos p : BlockPos.betweenClosed(at.offset(-3, 0, -3), at.offset(3, 2, 3))) {
                webs += level.getBlockState(p).is(Blocks.COBWEB) ? 1 : 0;
            }
            helper.assertTrue(webs > 0, "the web trap should have happened right here");
            rngambling.outcome.Scheduler.clear();
            clearUp(helper, level, player);
        } finally {
            level.getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /**
     * The world's record of a visit keeps the exact return point and a copy of the inventory;
     * restoring replaces the inventory with the copy (never adds to it), and the copy is kept
     * after the visit ends.
     */
    private static void sandboxRecords(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        try {
            player.getInventory().add(new ItemStack(net.minecraft.world.item.Items.GOLDEN_APPLE, 5));
            player.getInventory().add(new ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
            var back = new rngambling.outcome.Sandbox.ReturnPoint(net.minecraft.world.level.Level.OVERWORLD,
                    new net.minecraft.world.phys.Vec3(12.25, 70.5, -3.125), 33f, 12f);
            var records = rngambling.outcome.SandboxRecords.get(level.getServer());
            records.start(player, back, 1000L);
            var visit = records.get(player.getUUID());
            helper.assertTrue(visit.active() && visit.x() == 12.25 && visit.y() == 70.5 && visit.z() == -3.125 && visit.yaw() == 33f
                    && visit.level().equals("minecraft:overworld") && visit.itemCount() == 2, "the record should keep the exact spot and the copy");
            // The record survives a save and load (it's kept with the world).
            var ops = level.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
            var saved = rngambling.outcome.SandboxRecords.Visit.CODEC.encodeStart(ops, visit).getOrThrow();
            var loaded = rngambling.outcome.SandboxRecords.Visit.CODEC.parse(ops, saved).getOrThrow();
            helper.assertTrue(loaded.itemCount() == 2 && loaded.x() == 12.25, "the record should save and load");
            records.end(player.getUUID());
            helper.assertTrue(!records.get(player.getUUID()).active() && records.get(player.getUUID()).itemCount() == 2,
                    "the copy should be kept after the visit");

            player.getInventory().clearContent();
            player.getInventory().add(new ItemStack(net.minecraft.world.item.Items.DIRT, 7));
            helper.assertTrue(records.restore(player), "there should be a copy to restore");
            int apples = 0;
            int dirt = 0;
            int swords = 0;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                apples += stack.is(net.minecraft.world.item.Items.GOLDEN_APPLE) ? stack.getCount() : 0;
                dirt += stack.is(net.minecraft.world.item.Items.DIRT) ? stack.getCount() : 0;
                swords += stack.is(net.minecraft.world.item.Items.DIAMOND_SWORD) ? 1 : 0;
            }
            helper.assertTrue(apples == 5 && swords == 1 && dirt == 0,
                    "restore should give back the copy exactly: " + apples + " apples, " + swords + " swords, " + dirt + " dirt");
        } finally {
            level.getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /**
     * Every Sandbox room builds (here in the test world, since the test server has no Sandbox),
     * with a clear start and a way out where it should have one; the maze can always be solved, the
     * lava tomb's one weak brick breaks (and only it) and leads out, and the puzzle vault opens.
     */
    private static void roomsBuild(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        try {
            var rooms = rngambling.outcome.Rooms.all();
            for (int i = 0; i < rooms.size(); i++) {
                rngambling.outcome.Room room = rooms.get(i).get();
                String name = room.getClass().getSimpleName();
                BlockPos centre = new BlockPos(origin.getX() + 3000 + 140 * i, 100, origin.getZ());
                net.minecraft.world.phys.Vec3 start = room.buildAt(level, centre, player, 0);
                BlockPos feet = BlockPos.containing(start);
                helper.assertTrue(passable(level, feet) && passable(level, feet.above()), name + ": the start should be clear to stand in");
                boolean hasExit = !(room instanceof rngambling.outcome.Rooms.WitherArena || room instanceof rngambling.outcome.Rooms.EmpoweredWither
                        || room instanceof rngambling.outcome.Rooms.MobArenaWaves || room instanceof rngambling.outcome.Rooms.TreasureIsland
                        || room instanceof rngambling.outcome.Rooms.BossBounty || room instanceof rngambling.outcome.Rooms.VoidPlatform);
                if (hasExit) {
                    helper.assertTrue(room.exitPos() != null && level.getBlockState(room.exitPos()).is(RNGambling.SANDBOX_EXIT.get()),
                            name + ": should have a way out");
                }
                if (room instanceof rngambling.outcome.Rooms.BlindHedgeMaze) {
                    helper.assertTrue(walkable(level, feet, room.exitPos(), 40), name + ": the exit should be reachable");
                }
                if (room instanceof rngambling.outcome.Rooms.RisingLavaTomb) {
                    BlockPos weak = null;
                    for (BlockPos p : BlockPos.betweenClosed(centre.offset(-3, 0, -3), centre.offset(3, 0, 3))) {
                        if (level.getBlockState(p).is(Blocks.INFESTED_CRACKED_STONE_BRICKS)) {
                            weak = p.immutable();
                        }
                    }
                    helper.assertTrue(weak != null && room.breakable(weak, level.getBlockState(weak)), name + ": one weak brick should break");
                    helper.assertFalse(room.breakable(centre.offset(0, 0, -3), Blocks.STONE_BRICKS.defaultBlockState()) && !centre.offset(0, 0, -3).equals(weak),
                            name + ": the other walls shouldn't");
                    helper.assertFalse(walkable(level, feet, room.exitPos(), 10), name + ": sealed until the weak brick goes");
                    helper.assertTrue(room.breakable(weak.above(), level.getBlockState(weak.above())), name + ": the brick above it too");
                    level.setBlockAndUpdate(weak, Blocks.AIR.defaultBlockState());
                    level.setBlockAndUpdate(weak.above(), Blocks.AIR.defaultBlockState());
                    helper.assertTrue(walkable(level, feet, room.exitPos(), 10), name + ": the weak brick should lead to the exit");
                }
                if (room instanceof rngambling.outcome.Rooms.PuzzleVault) {
                    // Every lever combination, one flip at a time (a Gray code): one of them must open the door.
                    boolean opened = false;
                    for (int k = 1; k < 32 && !opened; k++) {
                        int lever = Integer.numberOfTrailingZeros(k);
                        BlockPos at = centre.offset(-4 + 2 * lever, 1, -5);
                        var state = level.getBlockState(at);
                        level.setBlock(at, state.setValue(net.minecraft.world.level.block.LeverBlock.POWERED,
                                !state.getValue(net.minecraft.world.level.block.LeverBlock.POWERED)), 3);
                        room.tickForTest(k);
                        opened = level.getBlockState(centre.offset(0, 0, 5)).isAir();
                    }
                    helper.assertTrue(opened, name + ": the puzzle should be solvable");
                }
                for (var entity : level.getEntities((net.minecraft.world.entity.Entity) null,
                        new net.minecraft.world.phys.AABB(centre).inflate(60, 120, 60), e -> !(e instanceof Player))) {
                    entity.discard();
                }
            }
        } finally {
            player.removeAllEffects();
            level.getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    private static boolean passable(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /** Whether a player could walk (stepping up or down a block) from {@code from} to {@code to}. */
    private static boolean walkable(ServerLevel level, BlockPos from, BlockPos to, int radius) {
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>(java.util.List.of(from));
        java.util.Set<BlockPos> seen = new java.util.HashSet<>(java.util.List.of(from));
        while (!queue.isEmpty()) {
            BlockPos at = queue.poll();
            if (at.equals(to)) {
                return true;
            }
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos next = at.relative(d).above(dy);
                    if (Math.abs(next.getX() - from.getX()) > radius || Math.abs(next.getZ() - from.getZ()) > radius || seen.contains(next)) {
                        continue;
                    }
                    if (passable(level, next) && passable(level, next.above()) && !passable(level, next.below())) {
                        seen.add(next);
                        queue.add(next);
                    }
                }
            }
        }
        return false;
    }

    /** Each outcome runs without error on a survival player. */
    private static void everyOutcomeRuns(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        player.snapTo(pos.getX() + 3.5, pos.getY(), pos.getZ() + 0.5);
        // The ordinary outcomes and every theme's own.
        java.util.Map<String, Outcome> all = new java.util.LinkedHashMap<>();
        for (Outcome outcome : Outcomes.ALL) {
            all.put(outcome.id(), outcome);
        }
        for (String zone : rngambling.outcome.Themes.BY_ZONE.keySet()) {
            for (Outcome outcome : rngambling.outcome.Themes.outcomesFor(zone)) {
                all.put(outcome.id(), outcome);
            }
        }
        try {
            rngambling.outcome.Scheduler.clear();
            for (Outcome outcome : all.values()) {
                try {
                    outcome.action().run(new OutcomeContext(level, pos, player, RandomSource.create(outcome.id().hashCode()), 0));
                    // Its later steps too (TNT raining, a countdown running out, a curse wearing off).
                    rngambling.outcome.Scheduler.runAllNow(20);
                } catch (RuntimeException e) {
                    throw helper.assertionException("outcome " + outcome.id() + " failed: " + e);
                }
                clearUp(helper, level, player);
                player.setHealth(player.getMaxHealth());
                player.snapTo(pos.getX() + 3.5, pos.getY(), pos.getZ() + 0.5);
            }
        } finally {
            rngambling.outcome.Scheduler.clear();
            level.getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /**
     * The "easy custom" effects and Wear and Tear do what they say: wearing items down (or the
     * player, to death if they've no food), the Hot Potato going off on the ground, the Tax
     * Collector's quarter and his drops, the Mystery Item list, the Ghost Block's three vanishings,
     * the Phoenix rising once, the Disco Floor putting the floor back, and which mobs Mob Shuffle swaps.
     */
    private static void customEffects(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        ServerPlayer second = null;
        try {
            rngambling.outcome.Scheduler.clear();
            player.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            OutcomeContext c = new OutcomeContext(level, pos, player, RandomSource.create(7), 0);
            // Wear and Tear: an item with durability is worn to its last point...
            player.getInventory().add(new ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
            ItemStack sword = player.getInventory().getItem(player.getInventory().findSlotMatchingItem(new ItemStack(net.minecraft.world.item.Items.IRON_SWORD)));
            outcome("wear_and_tear").action().run(c);
            helper.assertTrue(sword.getDamageValue() == sword.getMaxDamage() - 1, "the sword should be at its last use");
            helper.assertTrue(player.getHealth() == player.getMaxHealth(), "with something to wear, the player is spared");
            // ...with nothing that can wear, the player is drained: with food on them they live,
            player.getInventory().clearContent();
            player.getInventory().add(new ItemStack(net.minecraft.world.item.Items.BREAD));
            outcome("wear_and_tear").action().run(c);
            helper.assertTrue(player.getHealth() == 1 && player.getFoodData().getFoodLevel() == 1, "health and hunger should be one");
            player.getFoodData().setFoodLevel(0);
            rngambling.outcome.CustomEffects.checkDrainedForTest(player);
            helper.assertTrue(player.isAlive(), "with bread to eat the player should live");
            // ...and without, the last hunger running out kills them.
            player.getInventory().clearContent();
            rngambling.outcome.CustomEffects.checkDrainedForTest(player);
            helper.assertTrue(player.isDeadOrDying(), "with no food the player should starve");

            second = player(helper, GameType.SURVIVAL);
            second.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            c = new OutcomeContext(level, pos, second, RandomSource.create(7), 0);

            // Hot Potato: lying on the ground when its fuse is up, it goes off.
            ItemStack potato = rngambling.item.HotPotatoItem.lit(level);
            potato.set(RNGambling.FUSE.get(), level.getGameTime() - 1);
            var lying = new net.minecraft.world.entity.item.ItemEntity(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, potato);
            level.addFreshEntity(lying);
            helper.assertTrue(potato.getItem().onEntityItemUpdate(potato, lying) && lying.isRemoved(), "the Hot Potato should have gone off");
            outcome("hot_potato").action().run(c);
            helper.assertTrue(second.getInventory().contains(s -> s.is(RNGambling.HOT_POTATO.get()) && s.has(RNGambling.FUSE.get())),
                    "the player should be holding a lit Hot Potato");
            second.getInventory().clearContent();
            clearUp(helper, level, second);

            // Tax Collector: a quarter of the emeralds (rounded up), dropped again when he dies.
            second.getInventory().add(new ItemStack(net.minecraft.world.item.Items.EMERALD, 10));
            outcome("tax_collector").action().run(c);
            helper.assertTrue(second.getInventory().countItem(net.minecraft.world.item.Items.EMERALD) == 7, "he should take 3 of 10 emeralds");
            var collectors = level.getEntitiesOfClass(net.minecraft.world.entity.monster.illager.Pillager.class, new AABB(pos).inflate(8),
                    e -> e.entityTags().contains(rngambling.outcome.CustomEffects.TAX_COLLECTOR_TAG));
            helper.assertTrue(collectors.size() == 1, "one Tax Collector");
            var takings = rngambling.outcome.CustomEffects.takingsForTest(collectors.get(0), level);
            helper.assertTrue(takings.size() == 1 && takings.get(0).getCount() == 3, "he should carry the 3 emeralds: " + takings);
            second.getInventory().clearContent();
            clearUp(helper, level, second);

            // Mystery Item: a long list, from recipes and loot, without the blacklisted items.
            var mystery = rngambling.outcome.MysteryItems.all(level);
            LOGGER.info("Mystery Item list: {} items", mystery.size());
            helper.assertTrue(mystery.size() > 500, "the Mystery Item list is short: " + mystery.size());
            helper.assertTrue(mystery.contains(net.minecraft.world.item.Items.DIAMOND) && mystery.contains(net.minecraft.world.item.Items.NAME_TAG)
                    && mystery.contains(net.minecraft.world.item.Items.OAK_LOG), "diamonds (crafted), name tags (loot), logs (natural) should be in");
            helper.assertFalse(mystery.contains(net.minecraft.world.item.Items.COMMAND_BLOCK) || mystery.contains(RNGambling.LUCKY_BLOCK_ITEM.get())
                    || mystery.contains(RNGambling.MIMIC_WAND.get()), "blacklisted items should be out");
            helper.assertTrue(rngambling.outcome.CustomEffects.genieWishesForTest(c).stream().map(ItemStack::getItem).distinct().count() == 3,
                    "three different wishes");

            // Ghost Block: back three times when struck, then an ordinary lucky block.
            outcome("ghost_block").action().run(c);
            for (int ghost = 3; ghost > 0; ghost--) {
                helper.assertTrue(level.getBlockEntity(pos) instanceof LuckyBlockEntity lucky && lucky.ghosts() == ghost,
                        "the ghost should be back with " + ghost + " left");
                level.getBlockState(pos).attack(level, pos, second);
                helper.assertTrue(level.isEmptyBlock(pos), "struck, the ghost should vanish");
                rngambling.outcome.Scheduler.runAllNow(2);
            }
            helper.assertTrue(level.getBlockEntity(pos) instanceof LuckyBlockEntity lucky && lucky.ghosts() == 0, "now it can be broken");
            level.removeBlock(pos, false);

            // Phoenix Blessing: rises once.
            outcome("phoenix_blessing").action().run(c);
            second.setHealth(0.5f);
            helper.assertTrue(rngambling.outcome.CustomEffects.phoenixRiseForTest(second) && second.getHealth() == second.getMaxHealth() / 2,
                    "the Phoenix should raise the player at half health");
            helper.assertFalse(rngambling.outcome.CustomEffects.phoenixRiseForTest(second), "only once");

            // Disco Floor: the floor flashes, then is exactly as it was.
            BlockPos under = second.blockPosition().below();
            BlockState floor = level.getBlockState(under);
            outcome("disco_floor").action().run(c);
            rngambling.outcome.Scheduler.runAllNow(3);
            helper.assertTrue(level.getBlockState(under) == floor, "the floor should be put back: " + level.getBlockState(under));

            // Dragon Hatchling: hatched tame, tougher than a wolf, and it can't breed.
            outcome("dragon_hatchling").action().run(c);
            var dragons = level.getEntitiesOfClass(rngambling.entity.DragonHatchling.class, new AABB(pos).inflate(6));
            helper.assertTrue(dragons.size() == 1 && dragons.get(0).isOwnedBy(second), "one Dragon Hatchling, tamed by the player");
            helper.assertTrue(dragons.get(0).getMaxHealth() == (float) rngambling.entity.DragonHatchling.HEALTH
                    && dragons.get(0).getHealth() == dragons.get(0).getMaxHealth() && dragons.get(0).fireImmune(), "50 health, fire immune");
            helper.assertTrue(dragons.get(0).getBreedOffspring(level, dragons.get(0)) == null, "hatchlings don't breed");
            clearUp(helper, level, second);

            // Mob Shuffle: wild mobs only.
            var zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
            var wolf = net.minecraft.world.entity.EntityType.WOLF.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
            wolf.tame(second);
            var cow = net.minecraft.world.entity.EntityType.COW.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
            helper.assertTrue(rngambling.outcome.CustomEffects.shufflesForTest(zombie) && rngambling.outcome.CustomEffects.shufflesForTest(cow)
                    && !rngambling.outcome.CustomEffects.shufflesForTest(wolf), "zombies and cows shuffle, pets don't");
            clearUp(helper, level, second);
        } finally {
            rngambling.outcome.Scheduler.clear();
            level.getServer().getPlayerList().remove(player);
            if (second != null) {
                level.getServer().getPlayerList().remove(second);
            }
        }
        helper.succeed();
    }


    /**
     * The admin commands: play a named effect for a named player (luck given or the band's middle),
     * a real draw, the list; an unknown effect is refused. (Run as the server, which is how an
     * operator's command runs; with no player named, the caller is the target.)
     */
    private static void effectCommands(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        var dispatcher = level.getServer().getCommands().getDispatcher();
        var source = level.getServer().createCommandSourceStack().withSuppressedOutput();
        try {
            BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
            player.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, java.util.Set.of(), 0f, 0f, true);
            String who = player.getGameProfile().name();
            helper.assertTrue(dispatcher.execute("rngambling effect play iron_haul " + who + " 25", source) == 1, "play by player");
            var ingots = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(at).inflate(6),
                    e -> e.getItem().is(net.minecraft.world.item.Items.IRON_INGOT));
            helper.assertFalse(ingots.isEmpty(), "Iron Haul should have dropped iron");
            clearUp(helper, level, player);
            helper.assertTrue(dispatcher.execute("rngambling effect play blaze_horde " + who, source) == 1, "a theme's effect, default luck");
            clearUp(helper, level, player);
            helper.assertTrue(dispatcher.execute("rngambling effect roll " + who + " 100", source) == 1, "a real draw");
            rngambling.outcome.Scheduler.clear();
            clearUp(helper, level, player);
            helper.assertTrue(dispatcher.execute("rngambling effect list", source) == rngambling.outcome.Outcomes.everyEffect().size(),
                    "the list should name every effect");
            helper.assertTrue(dispatcher.execute("rngambling effect play no_such_effect " + who, source) == 0, "an unknown effect is refused");
            // The caller as target: a command run as the player themselves.
            helper.assertTrue(dispatcher.execute("rngambling effect play nothing_happens",
                    player.createCommandSourceStack().withPermission(net.minecraft.server.permissions.LevelBasedPermissionSet.OWNER)
                            .withSuppressedOutput()) == 1, "with no player named, the caller");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw helper.assertionException("command failed: " + e.getMessage());
        } finally {
            rngambling.outcome.Scheduler.clear();
            level.getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /** Only a real survival player sets off an outcome: not creative, spectators or machines' fake players. */
    private static void breakRules(GameTestHelper helper) {
        ServerPlayer survival = player(helper, GameType.SURVIVAL);
        ServerPlayer creative = player(helper, GameType.CREATIVE);
        ServerPlayer spectator = player(helper, GameType.SPECTATOR);
        try {
            Player fake = FakePlayerFactory.getMinecraft(helper.getLevel());
            helper.assertTrue(LuckyBlock.countsAsPlayerBreak(survival), "a survival player should count");
            helper.assertFalse(LuckyBlock.countsAsPlayerBreak(creative), "a creative player should not count");
            helper.assertFalse(LuckyBlock.countsAsPlayerBreak(spectator), "a spectator should not count");
            helper.assertFalse(LuckyBlock.countsAsPlayerBreak(fake), "a machine's fake player should not count");
        } finally {
            for (ServerPlayer p : new ServerPlayer[] {survival, creative, spectator}) {
                helper.getLevel().getServer().getPlayerList().remove(p);
            }
        }
        helper.succeed();
    }

    /**
     * The two-step draw: better luck means better tiers, but nothing is ever certain (a +100 block
     * can still go wrong, a -100 block can still go right). Logs the tier shares for tuning, and
     * checks every effect has a message, a sane band and a catalog ID.
     */
    private static void luckTiltsTheOdds(GameTestHelper helper) {
        double previousGood = -1;
        for (int luck : new int[] {-100, -50, 0, 50, 100}) {
            var shares = Outcomes.tierShares(luck);
            double good = 0;
            double bad = 0;
            StringBuilder line = new StringBuilder();
            for (var entry : shares.entrySet()) {
                good += entry.getKey().sign > 0 ? entry.getValue() : 0;
                bad += entry.getKey().sign < 0 ? entry.getValue() : 0;
                line.append(String.format(" %s %.0f%%", entry.getKey().name().toLowerCase(), entry.getValue() * 100));
            }
            LOGGER.info("luck {}: {}% good, {}% bad;{}", luck, Math.round(100 * good), Math.round(100 * bad), line);
            helper.assertTrue(good > previousGood, "good tiers should get likelier with luck " + luck);
            helper.assertTrue(bad > 0.04 && good > 0.04, "nothing should be certain at luck " + luck);
            previousGood = good;
        }
        // The draw follows the shares: at +100 a thousand draws are mostly good, at -100 mostly bad.
        RandomSource random = RandomSource.create(42);
        int goodAtTop = 0;
        int badAtBottom = 0;
        for (int i = 0; i < 1000; i++) {
            goodAtTop += Outcomes.pick(random, 100).tier().sign > 0 ? 1 : 0;
            badAtBottom += Outcomes.pick(random, -100).tier().sign < 0 ? 1 : 0;
        }
        LOGGER.info("1000 draws: {} good at +100, {} bad at -100", goodAtTop, badAtBottom);
        helper.assertTrue(goodAtTop > 750 && badAtBottom > 750, "draws should follow luck: " + goodAtTop + " / " + badAtBottom);
        // Every effect (themes too) has a message and a sane band; catalog IDs are unique.
        var language = net.minecraft.locale.Language.getInstance();
        java.util.Set<String> ids = new java.util.HashSet<>();
        java.util.List<Outcome> all = new java.util.ArrayList<>(Outcomes.ALL);
        for (String zone : rngambling.outcome.Themes.BY_ZONE.keySet()) {
            all.addAll(rngambling.outcome.Themes.outcomesFor(zone));
        }
        for (Outcome outcome : all) {
            helper.assertTrue(language.has("outcome.rngambling." + outcome.id()), "no message for " + outcome.id());
            helper.assertTrue(language.has("outcome.rngambling." + outcome.id() + ".quip"), "no chat one-liner for " + outcome.id());
            ids.add(outcome.id());
        }
        java.util.Set<String> catalogIds = new java.util.HashSet<>();
        for (Outcome outcome : Outcomes.ALL) {
            helper.assertTrue(catalogIds.add(outcome.catalogId()), "catalog ID used twice: " + outcome.catalogId());
        }
        LOGGER.info("{} catalog effects, {} with themes", Outcomes.ALL.size(), ids.size());
        helper.succeed();
    }

    /** Luck carried on the item ends up in the placed block, and back on the item. */
    private static void luckSurvivesPlacing(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, RNGambling.LUCKY_BLOCK.get());
        LuckyBlockEntity lucky = helper.getBlockEntity(pos, LuckyBlockEntity.class);
        ItemStack stack = rngambling.block.LuckyBlockItem.withLuck(65);
        lucky.applyComponentsFromItemStack(stack);
        helper.assertTrue(lucky.luck() == 65, "placed luck should be 65 but is " + lucky.luck());
        lucky.setLuck(500);
        helper.assertTrue(lucky.luck() == Luck.MAX, "luck should be capped at " + Luck.MAX);
        ItemStack back = new ItemStack(RNGambling.LUCKY_BLOCK_ITEM.get());
        back.applyComponents(lucky.collectComponents());
        helper.assertTrue(back.getOrDefault(RNGambling.LUCK.get(), 0) == Luck.MAX, "the item should carry the block's luck");
        helper.succeed();
    }

    /** Removes everything an outcome made, so the next one starts clean. */
    private static void clearUp(GameTestHelper helper, ServerLevel level, ServerPlayer player) {
        AABB area = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(24);
        for (Entity entity : level.getEntities((Entity) null, area, e -> !(e instanceof Player))) {
            entity.discard();
        }
        for (BlockPos spot : BlockPos.betweenClosed(BlockPos.containing(area.getMinPosition()), BlockPos.containing(area.getMaxPosition()))) {
            BlockState state = level.getBlockState(spot);
            if (state.is(Blocks.COBWEB) || state.is(Blocks.FIRE)) {
                level.setBlockAndUpdate(spot, Blocks.AIR.defaultBlockState());
            }
        }
        player.removeAllEffects();
        player.setDeltaMovement(0, 0, 0);
    }

    /** A test player in the given game mode (the test helper's own is always creative). */
    private static ServerPlayer player(GameTestHelper helper, GameType mode) {
        ServerLevel level = helper.getLevel();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "t-" + mode.getName() + "-" + Integer.toHexString(new java.util.Random().nextInt(0x10000))), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public GameType gameMode() {
                return mode;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(mode);
        player.connection.markClientLoaded(); // or it can't be hurt
        return player;
    }
}
