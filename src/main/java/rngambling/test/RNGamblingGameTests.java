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

    public static final Map<String, Consumer<GameTestHelper>> ALL = Map.of(
            "every_outcome_runs", RNGamblingGameTests::everyOutcomeRuns,
            "break_rules", RNGamblingGameTests::breakRules,
            "luck_tilts_the_odds", RNGamblingGameTests::luckTiltsTheOdds,
            "luck_survives_placing", RNGamblingGameTests::luckSurvivesPlacing,
            "mimic_wand", RNGamblingGameTests::mimicWand,
            "world_generation", RNGamblingGameTests::worldGeneration);

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

    /** Each outcome runs without error on a survival player. */
    private static void everyOutcomeRuns(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        player.snapTo(pos.getX() + 3.5, pos.getY(), pos.getZ() + 0.5);
        try {
            for (Outcome outcome : Outcomes.ALL) {
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
        helper.assertTrue(rngambling.block.LuckyBlockItem.luckOf(back) == Luck.MAX, "the item should carry the block's luck");
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
            if (state.is(Blocks.COBWEB)) {
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
