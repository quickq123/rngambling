package rngambling.client.shots;

import com.mojang.logging.LogUtils;
import java.util.ArrayDeque;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import rngambling.RNGambling;
import rngambling.block.LuckyBlockEntity;

/**
 * A development aid: with {@code -Drngambling.screenshots=true} (gradlew runClientShots) the game
 * makes a flat creative world, builds a test wall, saves pictures to {@code runs/shots/screenshots}
 * and quits. The wall has one column per block kind: real block, disguised copy, real block.
 */
public final class ScreenshotRun {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String WORLD = "rngambling-shots";
    private static final int MAX_TICKS = 20 * 120;

    /** The blocks copied in the test wall, one column each. */
    private static final Block[] KINDS = {
            Blocks.STONE, Blocks.COBBLESTONE, Blocks.DEEPSLATE, Blocks.DIRT, Blocks.GRASS_BLOCK,
            Blocks.SAND, Blocks.OAK_PLANKS, Blocks.BRICKS, Blocks.IRON_ORE, Blocks.SANDSTONE,
    };

    private record Step(int waitTicks, Consumer<Minecraft> action) {
    }

    private static final ArrayDeque<Step> STEPS = new ArrayDeque<>();
    private static int ticks;
    private static int waiting;
    private static boolean worldAsked;
    private static boolean planned;
    private static BlockPos base;

    private ScreenshotRun() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean("rngambling.screenshots");
    }

    /** Called once every client tick while the run is switched on. */
    public static void tick(Minecraft minecraft) {
        if (++ticks > MAX_TICKS) {
            LOGGER.error("Screenshot run: timed out");
            ticks = Integer.MIN_VALUE;
            STEPS.clear();
            STEPS.add(new Step(20, Minecraft::stop));
            planned = true;
            return;
        }
        if (!planned) {
            if (minecraft.level == null) {
                if (!worldAsked && minecraft.getOverlay() == null && minecraft.screen != null && ticks > 40) {
                    worldAsked = true;
                    minecraft.options.pauseOnLostFocus = false;
                    minecraft.options.onboardAccessibility = false;
                    LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE,
                            new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
                    minecraft.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(1L, false, false),
                            WorldPresets::createFlatWorldDimensions, new TitleScreen());
                }
                return;
            }
            if (minecraft.player == null || minecraft.getSingleplayerServer() == null) {
                return;
            }
            planned = true;
            plan();
            waiting = 60;
            return;
        }
        if (waiting > 0) {
            waiting--;
            return;
        }
        Step step = STEPS.poll();
        if (step == null) {
            return;
        }
        try {
            step.action().accept(minecraft);
        } catch (RuntimeException e) {
            LOGGER.error("Screenshot run: a step failed", e);
        }
        waiting = step.waitTicks();
    }

    private static void then(int waitTicks, Consumer<Minecraft> action) {
        STEPS.add(new Step(waitTicks, action));
    }

    private static void onServer(Minecraft minecraft, Consumer<ServerPlayer> action) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        server.execute(() -> {
            try {
                action.accept(server.getPlayerList().getPlayers().getFirst());
            } catch (RuntimeException e) {
                LOGGER.error("Screenshot run: a server step failed", e);
            }
        });
    }

    private static void shot(Minecraft minecraft, String name) {
        Screenshot.grab(minecraft.gameDirectory, name + ".png", minecraft.getMainRenderTarget(), 1,
                message -> LOGGER.info("Screenshot run: {}", message.getString()));
    }

    /** Puts the player's eyes at one point (relative to the scene), looking at another, then takes a picture. */
    private static void view(String name, Vec3 eye, Vec3 target) {
        then(30, minecraft -> onServer(minecraft, player -> {
            Vec3 from = eye.add(base.getX(), base.getY(), base.getZ());
            Vec3 to = target.add(base.getX(), base.getY(), base.getZ());
            Vec3 look = to.subtract(from);
            float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
            float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)));
            player.getAbilities().mayfly = true;
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.teleportTo(player.level(), from.x, from.y - player.getEyeHeight(), from.z, Set.of(), yaw, pitch, false);
        }));
        then(8, minecraft -> shot(minecraft, name));
    }

    private static void plan() {
        then(10, minecraft -> {
            minecraft.options.hideGui = true;
            onServer(minecraft, ScreenshotRun::buildScene);
        });
        // The whole wall, then closer, then at a slant (where shading differences show most).
        view("01-wall", new Vec3(9.5, 2.5, 6.0), new Vec3(9.5, 2.0, 0.0));
        view("02-wall-left", new Vec3(4.5, 2.5, 3.2), new Vec3(4.5, 2.0, 0.0));
        view("03-wall-right", new Vec3(14.5, 2.5, 3.2), new Vec3(14.5, 2.0, 0.0));
        view("04-slant", new Vec3(-1.0, 3.5, 4.0), new Vec3(8.0, 1.5, 0.0));
        // Disguised grass blocks in the ground, from above.
        view("05-ground", new Vec3(9.5, 5.0, 7.5), new Vec3(9.5, 0.0, 3.5));
        // The Luck Glasses: worn, every lucky block's luck shows, the buried one through the ground.
        then(5, minecraft -> {
            minecraft.options.hideGui = false;
            onServer(minecraft, player -> player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,
                    new net.minecraft.world.item.ItemStack(RNGambling.LUCK_GLASSES.get())));
        });
        view("06-glasses", new Vec3(9.5, 3.0, 9.0), new Vec3(9.5, 1.0, 0.0));
        // Worn, seen from the front.
        then(5, minecraft -> {
            minecraft.options.hideGui = true;
            minecraft.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
        });
        view("07-glasses-worn", new Vec3(9.5, 2.6, 8.0), new Vec3(9.5, 2.6, 0.0));
        then(5, minecraft -> minecraft.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
        then(20, Minecraft::stop);
    }

    /**
     * On the server: a wall at z = 0, column x = 2i + 1 per kind, rows y = 1 (real), 2 (disguised), 3 (real),
     * with a plain lucky block at the end, and a few disguised grass blocks in the ground in front.
     */
    private static void buildScene(ServerPlayer player) {
        ServerLevel level = player.level();
        base = player.blockPosition().offset(-10, -1, -6);
        MinecraftServer server = level.getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "time set noon");
        for (int i = 0; i < KINDS.length; i++) {
            BlockPos column = base.offset(2 * i + 1, 0, 0);
            BlockState real = KINDS[i].defaultBlockState();
            level.setBlockAndUpdate(column.above(1), real);
            disguise(level, column.above(2), real);
            if (level.getBlockEntity(column.above(2)) instanceof LuckyBlockEntity lucky) {
                lucky.setLuck(-90 + 20 * i); // a spread of luck for the glasses to show
            }
            level.setBlockAndUpdate(column.above(3), real);
        }
        // A lucky block buried in the ground, which only the glasses reveal.
        level.setBlockAndUpdate(base.offset(4, -2, 4), RNGambling.LUCKY_BLOCK.get().defaultBlockState());
        if (level.getBlockEntity(base.offset(4, -2, 4)) instanceof LuckyBlockEntity buried) {
            buried.setLuck(77);
        }
        level.setBlockAndUpdate(base.offset(2 * KINDS.length + 1, 2, 0), RNGambling.LUCKY_BLOCK.get().defaultBlockState());
        // The ground of a flat world is grass at base.y; disguise a few blocks of it.
        for (int x : new int[] {7, 9, 12}) {
            BlockPos ground = base.offset(x, 0, 3 + (x % 3));
            disguise(level, ground, level.getBlockState(ground));
        }
    }

    private static void disguise(ServerLevel level, BlockPos pos, BlockState real) {
        level.setBlockAndUpdate(pos, RNGambling.MIMIC_LUCKY_BLOCK.get().defaultBlockState());
        if (level.getBlockEntity(pos) instanceof LuckyBlockEntity lucky) {
            lucky.setMimic(real);
        }
    }
}
