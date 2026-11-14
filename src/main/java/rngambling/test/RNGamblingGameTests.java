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
            Map.entry("themes", RNGamblingGameTests::themes));

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
            helper.assertTrue(set != null && set.getTrades().size() == 2, "dealer level " + tier + " should have 2 trades");
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
        helper.assertTrue(nether.contains("blazes") && !nether.contains("zombies") && nether.contains("netherite_scrap")
                && nether.contains("diamonds"), "the Nether's outcomes should swap zombies for blazes and keep the rest: " + nether);
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
            for (BlockPos p : BlockPos.betweenClosed(new BlockPos(-6, 95, -6), new BlockPos(6, 110, 6))) {
                if (!netherLevel.getBlockState(p).isAir() && !netherLevel.getBlockState(p).is(Blocks.NETHERRACK)) {
                    netherLevel.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
                }
            }
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
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
            for (Outcome outcome : all.values()) {
                try {
                    outcome.action().run(new OutcomeContext(level, pos, player, RandomSource.create(outcome.id().hashCode()), 0));
                } catch (RuntimeException e) {
                    throw helper.assertionException("outcome " + outcome.id() + " failed: " + e);
                }
                clearUp(helper, level, player);
            }
        } finally {
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

    /** More luck means better odds, but bad outcomes never vanish. Logs the shares for tuning. */
    private static void luckTiltsTheOdds(GameTestHelper helper) {
        double previousGood = -1;
        for (int luck : new int[] {-100, -50, 0, 50, 100}) {
            double total = 0, good = 0, bad = 0;
            for (Outcome outcome : Outcomes.ALL) {
                double weight = outcome.weight() * Luck.weightFactor(luck, outcome.rating());
                total += weight;
                good += outcome.rating() > 0 ? weight : 0;
                bad += outcome.rating() < 0 ? weight : 0;
            }
            LOGGER.info("luck {}: {}% good, {}% bad", luck, Math.round(100 * good / total), Math.round(100 * bad / total));
            helper.assertTrue(good / total > previousGood, "good outcomes should get likelier with luck " + luck);
            helper.assertTrue(bad / total > 0.05, "bad outcomes should stay possible at luck " + luck);
            previousGood = good / total;
        }
        // The draw itself follows the weights: at +100, a thousand draws are mostly good.
        RandomSource random = RandomSource.create(42);
        int goodDraws = 0;
        for (int i = 0; i < 1000; i++) {
            goodDraws += Outcomes.pick(random, 100).rating() > 0 ? 1 : 0;
        }
        helper.assertTrue(goodDraws > 650, "at +100 luck, only " + goodDraws + " of 1000 draws were good");
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
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "test-" + mode.getName()), false);
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
        return player;
    }
}
