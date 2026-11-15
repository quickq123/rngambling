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
            Map.entry("effect_commands", RNGamblingGameTests::effectCommands),
            Map.entry("rooms_play", RNGamblingGameTests::roomsPlay),
            Map.entry("casino_luck", RNGamblingGameTests::casinoLuck),
            Map.entry("casino_games", RNGamblingGameTests::casinoGames),
            Map.entry("journal_and_loot", RNGamblingGameTests::journalAndLoot),
            Map.entry("variety", RNGamblingGameTests::variety));

    /**
     * Rooms played through: on the Void Platform a player who stands still loses their footing
     * (and the whole platform is gone before the way out opens); in Warden's Hollow a sound trap
     * sets off a shrieker; dying in the Sandbox keeps what was lost for the respawn; scares are
     * announced only afterwards.
     */
    private static void roomsPlay(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        try {
            // Void Platform: stand still at the start.
            var platform = new rngambling.outcome.Rooms.VoidPlatform();
            BlockPos centre = new BlockPos(origin.getX() + 2600, 0, origin.getZ());
            net.minecraft.world.phys.Vec3 start = platform.buildAt(level, centre, player, 0);
            player.teleportTo(level, start.x, start.y, start.z, java.util.Set.of(), 0f, 0f, true);
            BlockPos under = BlockPos.containing(start).below();
            int fellAt = -1;
            for (int age = 0; age < 60 + 20 * 30 && fellAt < 0; age++) {
                platform.tickForTest(age);
                if (age < 60) {
                    helper.assertTrue(level.getBlockState(under).is(Blocks.STONE), "nothing crumbles during the countdown");
                }
                if (level.getBlockState(under).isAir()) {
                    fellAt = age;
                }
            }
            LOGGER.info("Void Platform: the block under a player standing still fell at {} ticks", fellAt);
            helper.assertTrue(fellAt > 60 && fellAt < 60 + 20 * 10, "standing still should fail soon after the countdown, not at " + fellAt);
            for (int age = fellAt + 1; age < 60 + 20 * 30; age++) {
                platform.tickForTest(age);
            }
            int left = 0;
            for (BlockPos p : BlockPos.betweenClosed(under.offset(-4, 0, -4), under.offset(4, 0, 4))) {
                left += level.getBlockState(p).isAir() ? 0 : 1;
            }
            helper.assertTrue(left == 0, "the platform should be gone before the way out opens: " + left + " left");

            // Warden's Hollow: a bone trap sets off a shrieker.
            var hollow = new rngambling.outcome.Rooms.WardensHollow();
            BlockPos cave = new BlockPos(origin.getX() + 2800, 100, origin.getZ());
            hollow.buildAt(level, cave, player, 0);
            helper.assertTrue(hollow.exitPos().getZ() - cave.getZ() == rngambling.outcome.Rooms.WardensHollow.LENGTH, "the hollow is 80 long");
            helper.assertTrue(hollow.shriekerSpots().size() >= 9 && !hollow.boneTraps().isEmpty(), "shriekers and sound traps along it");
            int before = player.getWardenSpawnTracker().orElseThrow().getWarningLevel();
            helper.assertTrue(before >= 2, "the deep dark already knows the player");
            BlockPos bone = hollow.boneTraps().iterator().next();
            player.teleportTo(level, bone.getX() + 0.5, bone.getY() + 1, bone.getZ() + 0.5, java.util.Set.of(), 0f, 0f, true);
            hollow.tickForTest(5);
            int after = player.getWardenSpawnTracker().orElseThrow().getWarningLevel();
            helper.assertTrue(after > before, "stepping on bones should set off a shrieker: warning " + before + " -> " + after);

            // Rising Lava Tomb: nothing for the 15-second countdown, then the first layer.
            var tomb = new rngambling.outcome.Rooms.RisingLavaTomb();
            BlockPos tombAt = new BlockPos(origin.getX() + 2700, 100, origin.getZ());
            tomb.buildAt(level, tombAt, player, 0);
            int grace = rngambling.outcome.Rooms.RisingLavaTomb.GRACE;
            for (int age = 0; age < grace; age++) {
                tomb.tickForTest(age);
            }
            helper.assertFalse(level.getBlockState(tombAt.offset(1, 0, 1)).is(Blocks.LAVA), "no lava during the countdown");
            tomb.tickForTest(grace);
            helper.assertTrue(level.getBlockState(tombAt.offset(1, 0, 1)).is(Blocks.LAVA), "the first layer when the countdown ends");
            tomb.finish(false);
            tomb.endForTest();

            // Dying in the Sandbox: what was lost waits for the respawn, less items a room handed out.
            player.getInventory().clearContent();
            ItemStack handedOut = new ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 8);
            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
            tag.putBoolean(rngambling.outcome.Room.SANDBOX_ONLY_TAG, true);
            handedOut.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
            rngambling.outcome.SandboxDeaths.keep(player, java.util.List.of(new ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD)));
            helper.assertTrue(rngambling.outcome.SandboxDeaths.giveBack(player)
                    && player.getInventory().contains(s -> s.is(net.minecraft.world.item.Items.DIAMOND_SWORD)), "the sword should come back");
            helper.assertFalse(rngambling.outcome.SandboxDeaths.giveBack(player), "and only once");

            // Scares are announced after, not before.
            for (String scare : new String[] {"fake_explosion", "fake_death_message", "ghast_scream", "silent_creeper"}) {
                helper.assertTrue(outcome(scare).has(Outcome.Flag.SURPRISE), scare + " should be a surprise");
            }
        } finally {
            rngambling.outcome.Scheduler.clear();
            level.getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

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
        menu.clickMenuButton(player, rngambling.casino.Bets.UP_BIG); // bet 15: more than the 7 tokens held
        menu.clickMenuButton(player, rngambling.casino.SlotMachineMenu.BUTTON_SPIN);
        helper.assertTrue(menu.data(rngambling.casino.SlotMachineMenu.SPINS) == 0
                && rngambling.casino.SlotMachineMenu.countTokens(player.getInventory()) == 7, "a bet of 15 with 7 tokens shouldn't spin");
        menu.clickMenuButton(player, rngambling.casino.Bets.DOWN_BIG); // bet 5
        menu.clickMenuButton(player, rngambling.casino.SlotMachineMenu.BUTTON_SPIN);
        helper.assertTrue(menu.data(rngambling.casino.SlotMachineMenu.SPINS) == 1
                && rngambling.casino.SlotMachineMenu.countTokens(player.getInventory()) == 2, "spinning should take the 5 token bet");
        rngambling.casino.SlotMachine.Symbol[] reels = {menu.reel(0), menu.reel(1), menu.reel(2)};
        int win = 5 * rngambling.casino.SlotMachine.pays(reels);

        // Every spin's books balance: after it, the tokens are what they were, less the bet, plus the win
        // (three cherries at a bet of 5: 15 + 40 = 55 from 20). 2,000 real spins through the menu, each paid by closing it.
        ServerPlayer gambler = player(helper, GameType.SURVIVAL);
        try {
            gambler.snapTo(helper.absolutePos(pos).getX() + 1.5, helper.absolutePos(pos).getY(), helper.absolutePos(pos).getZ() + 0.5);
            var machine = new rngambling.casino.SlotMachineMenu(1, gambler.getInventory(), helper.absolutePos(pos));
            helper.assertTrue(machine.bet() == 5, "a slot machine starts at a bet of 5");
            int cherryWins = 0;
            for (int spin = 0; spin < 2000; spin++) {
                if (rngambling.casino.SlotMachineMenu.countTokens(gambler.getInventory()) < 5) {
                    gambler.getInventory().add(new ItemStack(RNGambling.CASINO_TOKEN.get(), 20));
                }
                int before = rngambling.casino.SlotMachineMenu.countTokens(gambler.getInventory());
                machine.clickMenuButton(gambler, rngambling.casino.SlotMachineMenu.BUTTON_SPIN);
                rngambling.casino.SlotMachine.Symbol[] stop = {machine.reel(0), machine.reel(1), machine.reel(2)};
                machine.removed(gambler); // pays at once
                int after = rngambling.casino.SlotMachineMenu.countTokens(gambler.getInventory());
                int pays = 5 * rngambling.casino.SlotMachine.pays(stop);
                helper.assertTrue(after == before - 5 + pays, "spin " + spin + " " + java.util.Arrays.toString(stop) + ": "
                        + before + " - 5 + " + pays + " should be " + (before - 5 + pays) + ", not " + after);
                if (stop[0] == rngambling.casino.SlotMachine.Symbol.CHERRY && stop[1] == stop[0] && stop[2] == stop[0]) {
                    cherryWins++;
                }
            }
            LOGGER.info("slot accounting: 2000 spins balanced, {} of them three cherries", cherryWins);
            helper.assertTrue(cherryWins > 0, "some spins should have been three cherries");
            gambler.getInventory().clearContent();
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(gambler);
        }
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
            int expected = tier == 2 || tier == 3 ? 3 : 2; // with the luck potions
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
        for (String recipe : new String[] {"token_pile", "casino_tokens_from_token_pile", "slot_machine", "roulette_table", "blackjack_table"}) {
            helper.assertTrue(recipes.byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE,
                    RNGambling.id(recipe))).isPresent(), "the " + recipe + " recipe should load");
        }

        var poi = net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(RNGambling.SLOT_MACHINE.get().defaultBlockState());
        helper.assertTrue(poi.isPresent() && RNGambling.DEALER.get().acquirableJobSite().test(poi.get()),
                "the Slot Machine should be the Casino Dealer's job site");
        helper.assertTrue(poi.get().is(net.minecraft.tags.PoiTypeTags.ACQUIRABLE_JOB_SITE), "villagers should look for it as a job site");

        var pools = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.TEMPLATE_POOL);
        // How often, and in which style other mods' villages get them.
        helper.assertTrue(rngambling.casino.CasinoHouses.weightEach(87, 6) == 2, "6% of vanilla's plains houses is about 2 per game");
        helper.assertTrue(rngambling.casino.CasinoHouses.weightEach(87, 0) == 0, "0% turns casinos off");
        helper.assertTrue(rngambling.casino.CasinoHouses.weightEach(10, 1) == 1, "any percent gives at least 1 each");
        helper.assertTrue("desert".equals(rngambling.casino.CasinoHouses.styleFor("mesa_fortified")), "a mesa village gets the desert style");
        helper.assertTrue("snowy".equals(rngambling.casino.CasinoHouses.styleFor("christmas")), "a christmas village gets the snowy style");
        helper.assertTrue("taiga".equals(rngambling.casino.CasinoHouses.styleFor("mountain")), "a mountain village gets the taiga style");
        helper.assertTrue("plains".equals(rngambling.casino.CasinoHouses.styleFor("beach")), "anything else gets the plains style");
        helper.assertTrue(rngambling.casino.CasinoHouses.styleFor("jungle_tree") == null
                && rngambling.casino.CasinoHouses.styleFor("underground") == null, "no casinos up trees or underground");
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
        for (int i = 0; i < 5; i++) {
            menu.clickMenuButton(player, rngambling.casino.Bets.UP); // 5 up to 10 tokens
        }
        helper.assertTrue(menu.bet() == 10, "five steps up from 5 should bet 10, not " + menu.bet());
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
            menu.clickMenuButton(player, rngambling.casino.Bets.DOWN_BIG); // 10 down to the least, 2
            menu.clickMenuButton(player, rngambling.casino.Bets.DOWN); // and no lower
            helper.assertTrue(menu.bet() == 2, "blackjack's least bet is 2, not " + menu.bet());
            menu.clickMenuButton(player, rngambling.casino.Bets.UP);
            helper.assertTrue(menu.bet() == 4, "blackjack steps by 2 (bets stay even), not " + menu.bet());
            menu.clickMenuButton(player, rngambling.casino.Bets.DOWN);
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
    /** Variety: effects a player has had (often, or lately) come up less within their tier, so more of the catalog turns up. */
    private static void variety(GameTestHelper helper) {
        var fresh = rngambling.outcome.Variety.EMPTY;
        helper.assertTrue(fresh.factor("endermite_burst") == 1.0, "an effect never had keeps its full weight");
        var once = fresh.record("endermite_burst");
        helper.assertTrue(Math.abs(once.factor("endermite_burst") - 1 / 6.6) < 1e-9 && once.factor("pig_rain") == 1.0,
                "right after it happens, about a third of its weight; others untouched");
        var many = once;
        for (int i = 0; i < 100; i++) {
            many = many.record("endermite_burst");
        }
        helper.assertTrue(many.factor("endermite_burst") == rngambling.outcome.Variety.FLOOR, "never below the floor: " + many.factor("endermite_burst"));
        helper.assertTrue(many.times("endermite_burst") == 101, "counted");

        // 300 draws in a row by one player at luck 0, with and without variety: more different effects, fewer quick repeats.
        int[] distinct = new int[2], repeats = new int[2];
        for (int mode = 0; mode < 2; mode++) for (int seed = 0; seed < 10; seed++) {
            RandomSource random = RandomSource.create(seed);
            var tally = rngambling.outcome.Variety.EMPTY;
            java.util.Set<String> seen = new java.util.HashSet<>();
            java.util.List<String> lastTen = new java.util.ArrayList<>();
            for (int i = 0; i < 300; i++) {
                var current = tally;
                Outcome drawn = Outcomes.pick(random, 0, Outcomes.ALL, mode == 0 ? Outcomes.PLAIN : o -> current.factor(o.id()));
                if (i < 100) {
                    seen.add(drawn.id());
                }
                repeats[mode] += lastTen.contains(drawn.id()) ? 1 : 0;
                lastTen.add(drawn.id());
                if (lastTen.size() > 10) {
                    lastTen.removeFirst();
                }
                tally = tally.record(drawn.id());
            }
            distinct[mode] += seen.size();
        }
        LOGGER.info("Variety (10 players): different effects in the first 100 draws {} plain, {} varied; repeats within 10 draws over 300: {} plain, {} varied",
                distinct[0], distinct[1], repeats[0], repeats[1]);
        helper.assertTrue(distinct[1] > distinct[0] && repeats[1] < repeats[0], "variety should spread the draws");

        // A real roll counts towards the player's tally.
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        try {
            BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
            player.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            Outcome rolled = Outcomes.roll(helper.getLevel(), pos, player, 0);
            helper.assertTrue(player.getData(RNGambling.VARIETY).times(rolled.id()) == 1 && player.getData(RNGambling.VARIETY).draws() == 1,
                    "a roll is counted");
            rngambling.outcome.Scheduler.clear();
            clearUp(helper, helper.getLevel(), player);
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    /** The Lucky Journal records each effect once; the dealer sells luck potions; chests hold lucky loot. */
    private static void journalAndLoot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        try {
            helper.assertTrue(rngambling.outcome.Journal.seen(player).isEmpty(), "a new player's journal is empty");
            BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
            player.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            Outcome legendary = outcome("god_sword");
            Outcomes.play(level, pos, player, 90, legendary); // a Legendary: also announced to the server
            Outcomes.play(level, pos, player, 90, legendary);
            helper.assertTrue(rngambling.outcome.Journal.seen(player).equals(java.util.List.of("god_sword")), "recorded once: " + rngambling.outcome.Journal.seen(player));
            rngambling.outcome.Journal.setAll(player, true);
            helper.assertTrue(rngambling.outcome.Journal.seen(player).size() == rngambling.outcome.Journal.total(), "reveal fills the journal");
            rngambling.outcome.Journal.setAll(player, false);
            helper.assertTrue(rngambling.outcome.Journal.seen(player).isEmpty(), "reset empties it");
            helper.assertTrue("nether".equals(rngambling.outcome.Themes.zoneOf("blaze_horde")) && "zombie_horde".equals(rngambling.outcome.Themes.replaces("blaze_horde"))
                    && rngambling.outcome.Themes.zoneOf("god_sword") == null, "zone variants know their zone");
            clearUp(helper, level, player);
        } finally {
            player.discard();
        }

        // The dealer's potion trades.
        var trades = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.VILLAGER_TRADE);
        for (String trade : new String[] {"dealer/2/tokens_potion_of_luck", "dealer/3/tokens_splash_bad_luck", "dealer/4/piles_strong_luck"}) {
            helper.assertTrue(trades.getValue(RNGambling.id(trade)) != null, "the dealer should have the " + trade + " trade");
        }

        // A dungeon chest, rolled many times, turns up tokens and luck potions.
        var table = level.getServer().reloadableRegistries().getLootTable(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.LOOT_TABLE, net.minecraft.resources.Identifier.withDefaultNamespace("chests/simple_dungeon")));
        int tokens = 0, potions = 0, blocks = 0;
        for (int i = 0; i < 300; i++) {
            var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                    .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO)))
                    .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
            for (ItemStack stack : table.getRandomItems(params)) {
                tokens += stack.is(RNGambling.CASINO_TOKEN.get()) || stack.is(RNGambling.TOKEN_PILE.get()) ? 1 : 0;
                potions += stack.is(net.minecraft.world.item.Items.POTION) && stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS) != null
                        && (stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS).is(net.minecraft.world.item.alchemy.Potions.LUCK)
                        || stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS).is(RNGambling.STRONG_LUCK)) ? 1 : 0;
                blocks += stack.getItem() instanceof net.minecraft.world.item.BlockItem bi && bi.getBlock() instanceof rngambling.block.LuckyBlock ? 1 : 0;
            }
        }
        LOGGER.info("300 dungeon chests: {} with tokens, {} luck potions, {} lucky blocks", tokens, potions, blocks);
        helper.assertTrue(tokens > 50 && potions > 10 && blocks > 20, "dungeon chests should hold lucky loot: " + tokens + "/" + potions + "/" + blocks);
        helper.succeed();
    }

    /** Higher or Lower: one game played by a fixed plan (the likelier guess each time, cash out after {@code stopAfter} right guesses); returns what comes back. */
    private static int playHigherLower(RandomSource random, int bet, int stopAfter, int luck) {
        int card = rngambling.casino.Blackjack.draw(random);
        int pot = bet;
        double odds = 1;
        for (int k = 0; k < stopAfter; k++) {
            var guess = rngambling.casino.HigherLower.chance(rngambling.casino.HigherLower.Guess.HIGHER, card) >= 0.5
                    ? rngambling.casino.HigherLower.Guess.HIGHER : rngambling.casino.HigherLower.Guess.LOWER;
            int before = card;
            int next = rngambling.casino.CasinoLuck.spin(random, luck, () -> rngambling.casino.Blackjack.draw(random),
                    x -> rngambling.casino.HigherLower.wins(guess, before, x));
            if (!rngambling.casino.HigherLower.wins(guess, before, next)) {
                return 0;
            }
            odds /= rngambling.casino.HigherLower.chance(guess, before);
            pot = rngambling.casino.HigherLower.pot(bet, odds, pot);
            card = next;
        }
        return pot;
    }

    /** Creeper Field: one game digging {@code digs} blocks then cashing out; returns what comes back. */
    private static int playCreeperField(RandomSource random, int bet, int creepers, int digs) {
        int hidden = rngambling.casino.CreeperField.BLOCKS, left = creepers, pot = bet;
        for (int k = 0; k < digs && hidden > left; k++) {
            if (random.nextInt(hidden) < left) {
                return 0;
            }
            hidden--;
            pot = rngambling.casino.CreeperField.pot(bet, creepers, k + 1, pot);
        }
        return pot;
    }

    /** The two newer casino games: no plan beats the house, the pots stay whole tokens, and the menus keep every token. */
    private static void casinoGames(GameTestHelper helper) {
        RandomSource random = RandomSource.create(9);
        int n = 100_000;
        for (int bet : new int[] {5, 10, 100}) {
            for (int stop : new int[] {1, 2, 3, 5, 8}) {
                double back = 0;
                for (int i = 0; i < n; i++) {
                    back += playHigherLower(random, bet, stop, 0);
                }
                double rtp = back / n / bet;
                LOGGER.info("Higher or Lower, bet {}, cash out after {}: {}", bet, stop, String.format("%.3f", rtp));
                helper.assertTrue(rtp < 0.985 && rtp > 0.75, "Higher or Lower at bet " + bet + ", " + stop + " guesses: " + rtp);
            }
            for (int creepers : new int[] {1, 3, 5, 10, 20}) {
                for (int digs : new int[] {1, 3, 6}) {
                    if (digs > rngambling.casino.CreeperField.BLOCKS - creepers) {
                        continue;
                    }
                    // Exact: the chance of digging that many safe blocks is 1 / the fair odds.
                    int pot = bet;
                    for (int k = 1; k <= digs; k++) {
                        pot = rngambling.casino.CreeperField.pot(bet, creepers, k, pot);
                    }
                    if (bet * rngambling.casino.CreeperField.odds(creepers, digs) > rngambling.casino.HigherLower.TABLE_LIMIT) {
                        helper.assertTrue(pot == rngambling.casino.HigherLower.TABLE_LIMIT, "pots stop at the table limit");
                        continue; // the limit caps the top end on purpose
                    }
                    double rtp = pot / rngambling.casino.CreeperField.odds(creepers, digs) / bet;
                    LOGGER.info("Creeper Field, bet {}, {} creepers, {} digs: {}", bet, creepers, digs, String.format("%.3f", rtp));
                    helper.assertTrue(rtp < 0.985 && rtp > 0.7, "Creeper Field at bet " + bet + ", " + creepers + " creepers, " + digs + " digs: " + rtp);
                }
            }
        }
        double lucky = 0;
        for (int i = 0; i < n; i++) {
            lucky += playHigherLower(random, 10, 3, 2);
        }
        LOGGER.info("Higher or Lower with Luck II, 3 guesses: {}", String.format("%.3f", lucky / n / 10));
        helper.assertTrue(lucky / n / 10 > 1.05, "Luck II should tip Higher or Lower to the player");

        // The menus: tokens in = tokens out, whatever happens.
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        try {
            BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
            for (int game = 0; game < 40; game++) {
                player.getInventory().clearContent();
                player.getInventory().add(new ItemStack(RNGambling.CASINO_TOKEN.get(), 64));
                var hilo = new rngambling.casino.HigherLowerMenu(0, player.getInventory(), at);
                hilo.clickMenuButton(player, rngambling.casino.HigherLowerMenu.BUTTON_DEAL);
                helper.assertTrue(rngambling.casino.Tokens.count(player.getInventory()) == 54, "Deal should take the bet of 10");
                for (int g = 0; g < 4 && hilo.phase() == rngambling.casino.HigherLowerMenu.Phase.PLAYING; g++) {
                    hilo.clickMenuButton(player, rngambling.casino.HigherLowerMenu.BUTTON_HIGHER);
                }
                int pot = hilo.data(rngambling.casino.HigherLowerMenu.POT);
                boolean playing = hilo.phase() == rngambling.casino.HigherLowerMenu.Phase.PLAYING;
                hilo.removed(player); // closing cashes out
                int expected = 54 + (playing ? pot : 0);
                helper.assertTrue(rngambling.casino.Tokens.count(player.getInventory()) == expected,
                        "Higher or Lower should pay exactly the pot: " + rngambling.casino.Tokens.count(player.getInventory()) + " vs " + expected);

                player.getInventory().clearContent();
                player.getInventory().add(new ItemStack(RNGambling.CASINO_TOKEN.get(), 64));
                var field = new rngambling.casino.CreeperFieldMenu(0, player.getInventory(), at);
                field.clickMenuButton(player, rngambling.casino.CreeperFieldMenu.BUTTON_MORE); // 4 creepers
                field.clickMenuButton(player, rngambling.casino.CreeperFieldMenu.BUTTON_START);
                for (int tile = 0; tile < 25 && field.phase() == rngambling.casino.CreeperFieldMenu.Phase.DIGGING; tile += 3) {
                    field.clickMenuButton(player, rngambling.casino.CreeperFieldMenu.BUTTON_TILE + tile);
                }
                boolean digging = field.phase() == rngambling.casino.CreeperFieldMenu.Phase.DIGGING;
                int fieldPot = field.data(rngambling.casino.CreeperFieldMenu.POT);
                int lastWin = field.data(rngambling.casino.CreeperFieldMenu.LAST_WIN);
                field.clickMenuButton(player, rngambling.casino.CreeperFieldMenu.BUTTON_CASH);
                int paid = digging ? fieldPot : lastWin;
                helper.assertTrue(rngambling.casino.Tokens.count(player.getInventory()) == 54 + paid,
                        "Creeper Field should pay exactly the pot: " + rngambling.casino.Tokens.count(player.getInventory()) + " vs " + (54 + paid));
                int creepers = 0;
                for (int i = 0; i < 25; i++) {
                    int shown = field.data(i);
                    creepers += shown == rngambling.casino.CreeperFieldMenu.CREEPER || shown == rngambling.casino.CreeperFieldMenu.BOOM ? 1 : 0;
                }
                helper.assertTrue(creepers == 4, "the field should show its 4 creepers at the end: " + creepers);
            }
        } finally {
            player.discard();
        }
        var recipes = helper.getLevel().getServer().getRecipeManager();
        for (String recipe : new String[] {"higher_lower_table", "creeper_field"}) {
            helper.assertTrue(recipes.byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE,
                    RNGambling.id(recipe))).isPresent(), "the " + recipe + " recipe should load");
        }
        helper.succeed();
    }

    /** Luck and Bad Luck: their levels, what they add to lucky blocks, how they tilt each casino game, and the luck potions. */
    private static void casinoLuck(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        try {
            helper.assertTrue(rngambling.luck.PlayerLuck.level(player) == 0, "no effects, no luck");
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.LUCK, 200, 0));
            helper.assertTrue(rngambling.luck.PlayerLuck.blockBonus(player) == 10, "Luck I adds 10");
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.LUCK, 200, 4));
            helper.assertTrue(rngambling.luck.PlayerLuck.blockBonus(player) == 20, "Luck V counts as II: 20");
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.UNLUCK, 200, 0));
            helper.assertTrue(rngambling.luck.PlayerLuck.level(player) == 1, "Luck II and Bad Luck I make 1");
            player.removeAllEffects();
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.UNLUCK, 200, 1));
            helper.assertTrue(rngambling.luck.PlayerLuck.blockBonus(player) == -20, "Bad Luck II takes 20");
            player.removeAllEffects();
        } finally {
            player.discard();
        }

        // What comes back of what is bet, at each luck level, over many plays.
        RandomSource random = RandomSource.create(42);
        int n = 200_000;
        double[] slots = new double[5], red = new double[5], jack = new double[5];
        for (int luck = -2; luck <= 2; luck++) {
            int l = luck;
            double s = 0, r = 0, b = 0;
            for (int i = 0; i < n; i++) {
                var reels = rngambling.casino.CasinoLuck.spin(random, l, () -> rngambling.casino.SlotMachine.spin(random),
                        x -> rngambling.casino.SlotMachine.pays(x) > 0);
                s += rngambling.casino.SlotMachine.pays(reels);
                int number = rngambling.casino.CasinoLuck.spin(random, l, () -> rngambling.casino.Roulette.spin(random),
                        x -> rngambling.casino.Roulette.wins(rngambling.casino.Roulette.Bet.RED, 0, x));
                r += rngambling.casino.Roulette.returns(rngambling.casino.Roulette.Bet.RED, 0, number);
                b += 1 + rngambling.casino.Blackjack.playOneHand(random, l);
            }
            slots[luck + 2] = s / n;
            red[luck + 2] = r / n;
            jack[luck + 2] = b / n;
            LOGGER.info("Casino at luck {}: slots {}, red {}, blackjack {}", luck, String.format("%.3f", s / n), String.format("%.3f", r / n),
                    String.format("%.3f", b / n));
        }
        for (double[] game : new double[][] {slots, red, jack}) {
            helper.assertTrue(game[0] < game[1] && game[1] < game[2] && game[2] < game[3] && game[3] < game[4], "luck should order the returns");
            helper.assertTrue(game[2] < 1.0, "without luck the house wins");
            helper.assertTrue(game[3] > 1.0 && game[3] < 1.09, "Luck I: a slight edge for the player");
            helper.assertTrue(game[4] > 1.09 && game[4] < 1.2, "Luck II: a good edge for the player");
            helper.assertTrue(game[1] < 0.95, "Bad Luck I: worse than the house's usual edge");
        }

        // Brewing: a Pile of Tokens makes Luck; glowstone strengthens; a fermented spider eye turns it bad.
        var brewing = level.potionBrewing();
        java.util.function.BiFunction<net.minecraft.world.item.Item, net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion>, ItemStack> brew =
                (ingredient, from) -> brewing.mix(new ItemStack(ingredient), net.minecraft.world.item.alchemy.PotionContents.createItemStack(
                        net.minecraft.world.item.Items.POTION, from));
        java.util.function.BiPredicate<ItemStack, net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion>> is = (stack, potion) ->
                stack.getOrDefault(net.minecraft.core.component.DataComponents.POTION_CONTENTS, net.minecraft.world.item.alchemy.PotionContents.EMPTY).is(potion);
        helper.assertTrue(is.test(brew.apply(RNGambling.TOKEN_PILE.get(), net.minecraft.world.item.alchemy.Potions.AWKWARD), net.minecraft.world.item.alchemy.Potions.LUCK),
                "awkward + token pile = luck");
        helper.assertTrue(is.test(brew.apply(net.minecraft.world.item.Items.GLOWSTONE_DUST, net.minecraft.world.item.alchemy.Potions.LUCK), RNGambling.STRONG_LUCK),
                "luck + glowstone = luck II");
        helper.assertTrue(is.test(brew.apply(net.minecraft.world.item.Items.FERMENTED_SPIDER_EYE, net.minecraft.world.item.alchemy.Potions.LUCK), RNGambling.BAD_LUCK),
                "luck + fermented spider eye = bad luck");
        helper.assertTrue(is.test(brew.apply(net.minecraft.world.item.Items.GLOWSTONE_DUST, RNGambling.BAD_LUCK), RNGambling.STRONG_BAD_LUCK),
                "bad luck + glowstone = bad luck II");
        helper.succeed();
    }

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

            // Trapdoor Drop: its 2x2 holds every block the player's feet can rest on, wherever they stand.
            for (double[] at : new double[][] {{10.5, 4.0, 10.5}, {10.95, 4.0, 10.05}, {-3.3, 4.0, -7.99}, {0.01, 4.0, 0.99}}) {
                var columns = rngambling.outcome.Catalog.trapdoorColumns(new net.minecraft.world.phys.Vec3(at[0], at[1], at[2]));
                for (double dx : new double[] {-0.3, 0.3}) {
                    for (double dz : new double[] {-0.3, 0.3}) {
                        BlockPos foot = BlockPos.containing(at[0] + dx, at[1], at[2] + dz);
                        helper.assertTrue(columns.contains(foot), "the trapdoor at " + java.util.Arrays.toString(at) + " misses " + foot);
                    }
                }
            }

            // Lucky Sense: every lucky block nearby glows, disguised ones too, then the glows go.
            BlockPos plainSpot = pos.offset(4, 0, 0), disguisedSpot = pos.offset(-4, 0, 2);
            level.setBlockAndUpdate(plainSpot, RNGambling.LUCKY_BLOCK.get().defaultBlockState());
            level.setBlockAndUpdate(disguisedSpot, RNGambling.MIMIC_LUCKY_BLOCK.get().defaultBlockState());
            if (level.getBlockEntity(disguisedSpot) instanceof rngambling.block.LuckyBlockEntity disguised) {
                disguised.setMimic(Blocks.STONE.defaultBlockState());
                disguised.setLuck(-60);
            }
            var sensed = rngambling.outcome.CustomEffects.luckyBlocksNearForTest(level, pos, 64);
            helper.assertTrue(sensed.stream().anyMatch(e -> e.getBlockPos().equals(plainSpot))
                    && sensed.stream().anyMatch(e -> e.getBlockPos().equals(disguisedSpot)), "Lucky Sense should find both lucky blocks: " + sensed.size());
            outcome("lucky_sense").action().run(c);
            var senseGlows = level.getEntitiesOfClass(net.minecraft.world.entity.Display.BlockDisplay.class, new AABB(pos).inflate(8),
                    e -> e.entityTags().contains(rngambling.outcome.CustomEffects.TEMPORARY_TAG));
            helper.assertTrue(senseGlows.size() >= 2 && senseGlows.stream().allMatch(Entity::hasGlowingTag), "both should glow: " + senseGlows.size());
            rngambling.outcome.Scheduler.clear();
            helper.assertTrue(senseGlows.stream().allMatch(Entity::isRemoved), "the glows should go when the effect ends");
            level.setBlockAndUpdate(plainSpot, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(disguisedSpot, Blocks.AIR.defaultBlockState());

            // Second batch: items are moved or changed, never lost.
            second.getInventory().clearContent();
            second.getInventory().setItem(3, new ItemStack(net.minecraft.world.item.Items.BREAD, 5));
            outcome("spoiled_food").action().run(c);
            helper.assertTrue(second.getInventory().getItem(3).is(net.minecraft.world.item.Items.ROTTEN_FLESH)
                    && second.getInventory().getItem(3).getCount() == 5, "the bread should have gone off, all 5");
            for (int slot = 0; slot < 9; slot++) {
                second.getInventory().setItem(slot, new ItemStack(net.minecraft.world.item.Items.STICK, slot + 1));
            }
            outcome("hotbar_shuffle").action().run(c);
            int sticks = 0;
            for (int slot = 0; slot < 9; slot++) {
                sticks += second.getInventory().getItem(slot).getCount();
            }
            helper.assertTrue(sticks == 45, "the hotbar shuffle should keep every item: " + sticks);
            second.getInventory().clearContent();
            second.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
            outcome("lost_armor").action().run(c);
            helper.assertTrue(second.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty()
                    && !level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(pos).inflate(8),
                            e -> e.getItem().is(net.minecraft.world.item.Items.IRON_CHESTPLATE)).isEmpty(), "the chestplate should fly off, not vanish");
            clearUp(helper, level, second);

            // The best gear goes past vanilla's limits.
            second.getInventory().clearContent();
            outcome("god_sword").action().run(c);
            var swords = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(pos).inflate(8),
                    e -> e.getItem().is(net.minecraft.world.item.Items.NETHERITE_SWORD));
            var sharpness = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                    .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS);
            helper.assertTrue(!swords.isEmpty() && swords.get(0).getItem().getEnchantments().getLevel(sharpness) == 7
                    || second.getInventory().contains(st -> st.is(net.minecraft.world.item.Items.NETHERITE_SWORD) && st.getEnchantments().getLevel(sharpness) == 7),
                    "the God Sword should have Sharpness VII");
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

            // Tokens: Token Toss and Token Jackpot drop them.
            outcome("token_toss").action().run(c);
            outcome("token_jackpot").action().run(c);
            var dropped = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(pos).inflate(6));
            helper.assertTrue(dropped.stream().anyMatch(e -> e.getItem().is(RNGambling.CASINO_TOKEN.get()))
                    && dropped.stream().anyMatch(e -> e.getItem().is(RNGambling.TOKEN_PILE.get())), "tokens and piles should drop");
            clearUp(helper, level, second);
            second.getInventory().clearContent();

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
            helper.assertTrue(language.has("outcome.rngambling." + outcome.id() + ".desc"), "no Lucky Journal description for " + outcome.id());
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
