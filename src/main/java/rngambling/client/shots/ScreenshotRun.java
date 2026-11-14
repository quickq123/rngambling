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
import rngambling.casino.SlotMachineBlock;

/**
 * A development aid: with {@code -Drngambling.screenshots=true} (gradlew runClientShots) the game
 * makes a flat creative world, builds a test wall, saves pictures to {@code runs/shots/screenshots}
 * and quits. The wall has one column per block kind: real block, disguised copy, real block.
 */
public final class ScreenshotRun {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String WORLD = "rngambling-shots";
    private static final int MAX_TICKS = 20 * 600;

    /** The blocks copied in the test wall, one column each. */
    private static final Block[] KINDS = {
            Blocks.STONE, Blocks.COBBLESTONE, Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE, Blocks.BLACKSTONE,
            Blocks.DARK_OAK_PLANKS, Blocks.OBSIDIAN, Blocks.GRASS_BLOCK, Blocks.SAND, Blocks.BRICKS,
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
                    minecraft.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(1L, NORMAL_WORLD || ZONES || VILLAGES, false),
                            NORMAL_WORLD || ZONES || VILLAGES ? WorldPresets::createNormalWorldDimensions : WorldPresets::createFlatWorldDimensions,
                            new TitleScreen());
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

    /** -PshotsWorld=normal: a normal world instead, to look at world generation. */
    private static final boolean NORMAL_WORLD = "normal".equals(System.getProperty("rngambling.shotsWorld"));
    private static BlockPos surfaceFound;
    private static BlockPos caveFound;
    private static BlockPos caveAir;

    /**
     * World generation: counts the disguised lucky blocks in the chunks around the player, logs
     * them, then pictures the nearest surface one and the nearest cave one with the glasses on.
     */
    private static void planWorldGeneration() {
        then(400, minecraft -> { }); // let the chunks around the player generate
        then(10, minecraft -> onServer(minecraft, ScreenshotRun::survey));
        then(5, minecraft -> {
            minecraft.options.hideGui = false;
            onServer(minecraft, player -> {
                player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,
                        new net.minecraft.world.item.ItemStack(RNGambling.LUCK_GLASSES.get()));
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.NIGHT_VISION, 20 * 600, 0, false, false));
            });
        });
        then(40, minecraft -> onServer(minecraft, player -> {
            if (surfaceFound != null) {
                look(player, Vec3.atCenterOf(surfaceFound).add(2.5, 2.0, 2.5), Vec3.atCenterOf(surfaceFound));
            }
        }));
        then(10, minecraft -> shot(minecraft, "10-surface"));
        then(60, minecraft -> onServer(minecraft, player -> {
            if (caveFound != null) {
                look(player, Vec3.atCenterOf(caveAir), Vec3.atCenterOf(caveFound));
            }
        }));
        then(10, minecraft -> shot(minecraft, "11-cave"));
        then(20, Minecraft::stop);
    }

    private static void look(ServerPlayer player, Vec3 from, Vec3 to) {
        Vec3 look = to.subtract(from);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)));
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.teleportTo(player.level(), from.x, from.y - player.getEyeHeight(), from.z, Set.of(), yaw, pitch, false);
    }

    private static void survey(ServerPlayer player) {
        ServerLevel level = player.level();
        int radius = 6;
        int chunks = 0;
        int surface = 0;
        int cave = 0;
        double bestSurface = Double.MAX_VALUE;
        double bestCave = Double.MAX_VALUE;
        int cx0 = player.blockPosition().getX() >> 4;
        int cz0 = player.blockPosition().getZ() >> 4;
        for (int cx = cx0 - radius; cx <= cx0 + radius; cx++) {
            for (int cz = cz0 - radius; cz <= cz0 + radius; cz++) {
                net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                chunks++;
                for (net.minecraft.world.level.block.entity.BlockEntity entity : chunk.getBlockEntities().values()) {
                    if (!(entity instanceof LuckyBlockEntity lucky)) {
                        continue;
                    }
                    BlockPos pos = lucky.getBlockPos();
                    int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
                    boolean onSurface = pos.getY() >= top - 1;
                    double distance = pos.distSqr(player.blockPosition());
                    LOGGER.info("Screenshot run: {} lucky block at {} copying {} with luck {}", onSurface ? "surface" : "cave",
                            pos.toShortString(), lucky.mimic(), lucky.luck());
                    if (onSurface) {
                        surface++;
                        if (distance < bestSurface) {
                            bestSurface = distance;
                            surfaceFound = pos;
                        }
                    } else {
                        BlockPos air = null;
                        for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
                            if (level.getBlockState(pos.relative(side)).isAir()) {
                                air = pos.relative(side);
                            }
                        }
                        cave++;
                        if (air != null && distance < bestCave) {
                            bestCave = distance;
                            caveFound = pos;
                            caveAir = air;
                        }
                    }
                }
            }
        }
        LOGGER.info("Screenshot run: {} chunks surveyed: {} surface and {} cave lucky blocks (one per {} chunks)",
                chunks, surface, cave, surface + cave == 0 ? "-" : String.format("%.1f", chunks / (double) (surface + cave)));
    }

    /** -PshotsWorld=zones: visits each luck zone in a normal world and surveys its generated lucky blocks. */
    private static final boolean ZONES = "zones".equals(System.getProperty("rngambling.shotsWorld"));

    /**
     * Luck zones: surveys the Overworld around spawn, the Nether, the End's outer islands, the
     * nearest Ancient City and the nearest Woodland Mansion, logging counts and luck for each.
     */
    private static void planZones() {
        then(400, minecraft -> { });
        if (!Boolean.getBoolean("rngambling.structuresOnly")) {
            then(10, minecraft -> onServer(minecraft, player -> zoneSurvey(player, "overworld", null)));
            then(500, minecraft -> onServer(minecraft, player -> go(player, net.minecraft.world.level.Level.NETHER, new Vec3(0.5, 70, 0.5))));
            then(10, minecraft -> onServer(minecraft, player -> zoneSurvey(player, "nether", null)));
            then(600, minecraft -> onServer(minecraft, player -> go(player, net.minecraft.world.level.Level.END, new Vec3(1200.5, 80, 0.5))));
            then(10, minecraft -> onServer(minecraft, player -> zoneSurvey(player, "end", null)));
        }
        String[][] structures = {{"ancient_city", "ancient_cities", "-27"}, {"mansion", "woodland_mansions", "64"}};
        for (String[] structure : structures) {
            // Candidates: the spots the placement could use whose biome looks right, nearest first.
            java.util.List<BlockPos> candidates = new java.util.ArrayList<>();
            BlockPos[] found = new BlockPos[1];
            then(5, minecraft -> onServer(minecraft, player -> candidates.addAll(
                    findStructure(player.level().getServer().overworld(), structure[0], structure[1], Integer.parseInt(structure[2])))));
            // Then the game's own check, one candidate per step (a full search freezes the server for minutes).
            for (int i = 0; i < 80; i++) {
                int index = i;
                then(5, minecraft -> onServer(minecraft, player -> {
                    if (found[0] != null || index >= candidates.size()) {
                        return;
                    }
                    ServerLevel overworld = player.level().getServer().overworld();
                    var result = overworld.getChunkSource().getGenerator().findNearestMapStructure(
                            overworld, structureSet(overworld, structure[0]), candidates.get(index), 0, false);
                    if (result != null) {
                        found[0] = result.getFirst();
                        LOGGER.info("Screenshot run: {} at {} (candidate {})", structure[0], found[0].toShortString(), index + 1);
                    }
                }));
            }
            then(600, minecraft -> onServer(minecraft, player -> {
                if (found[0] == null) {
                    LOGGER.info("Screenshot run: no {} found among {} candidates", structure[0], candidates.size());
                    return;
                }
                go(player, net.minecraft.world.level.Level.OVERWORLD,
                        new Vec3(found[0].getX() + 0.5, structure[0].equals("mansion") ? 110 : -20, found[0].getZ() + 0.5));
            }));
            then(10, minecraft -> onServer(minecraft, player -> {
                if (found[0] != null) {
                    zoneSurvey(player, structure[0], structureSet(player.level(), structure[0]));
                }
            }));
        }
        then(20, Minecraft::stop);
    }

    /**
     * A quick structure search: the game's own (locate) generates terrain to check each candidate
     * and can freeze the server for minutes. This works out the candidate spots from the structure
     * set's placement and the world seed, and returns the nearest whose biome (at {@code biomeY})
     * suits the structure. It's usually right; the survey then says how many lucky blocks are inside.
     */
    private static java.util.List<BlockPos> findStructure(ServerLevel level, String structureName, String setName, int biomeY) {
        var setRegistry = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE_SET);
        var set = setRegistry.getValueOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.STRUCTURE_SET,
                net.minecraft.resources.Identifier.withDefaultNamespace(setName)));
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        if (!(set.placement() instanceof net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement placement)) {
            return out;
        }
        net.minecraft.world.level.levelgen.structure.Structure structure = structureSet(level, structureName).get(0).value();
        long seed = level.getChunkSource().getGeneratorState().getLevelSeed();
        var biomes = level.getChunkSource().getGenerator().getBiomeSource();
        var sampler = level.getChunkSource().randomState().sampler();
        java.util.List<net.minecraft.world.level.ChunkPos> candidates = new java.util.ArrayList<>();
        int cells = 10;
        for (int cx = -cells; cx <= cells; cx++) {
            for (int cz = -cells; cz <= cells; cz++) {
                candidates.add(placement.getPotentialStructureChunk(seed, cx * placement.spacing(), cz * placement.spacing()));
            }
        }
        candidates.sort(java.util.Comparator.comparingLong(c -> (long) c.getMiddleBlockX() * c.getMiddleBlockX() + (long) c.getMiddleBlockZ() * c.getMiddleBlockZ()));
        for (net.minecraft.world.level.ChunkPos chunk : candidates) {
            int x = chunk.getMiddleBlockX();
            int z = chunk.getMiddleBlockZ();
            var biome = biomes.getNoiseBiome(net.minecraft.core.QuartPos.fromBlock(x), net.minecraft.core.QuartPos.fromBlock(biomeY),
                    net.minecraft.core.QuartPos.fromBlock(z), sampler);
            // Likely spots (the biome looks right) first; the game's own check decides.
            out.add(structure.biomes().contains(biome) ? 0 : out.size(), new BlockPos(x, biomeY, z));
        }
        return out;
    }

    private static net.minecraft.core.HolderSet<net.minecraft.world.level.levelgen.structure.Structure> structureSet(ServerLevel level, String name) {
        var registry = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
        return net.minecraft.core.HolderSet.direct(registry.getOrThrow(
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.STRUCTURE,
                        net.minecraft.resources.Identifier.withDefaultNamespace(name))));
    }

    private static void go(ServerPlayer player, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, Vec3 at) {
        ServerLevel level = player.level().getServer().getLevel(dimension);
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.teleportTo(level, at.x, at.y, at.z, Set.of(), 0, 0, false);
    }

    /** Logs the lucky blocks in the chunks around the player: how many, where, and their luck. */
    private static void zoneSurvey(ServerPlayer player, String label,
            net.minecraft.core.HolderSet<net.minecraft.world.level.levelgen.structure.Structure> structure) {
        ServerLevel level = player.level();
        int radius = 6;
        int chunks = 0;
        int surface = 0;
        int inside = 0;
        java.util.List<Integer> lucks = new java.util.ArrayList<>();
        int cx0 = player.blockPosition().getX() >> 4;
        int cz0 = player.blockPosition().getZ() >> 4;
        int structureChunks = 0;
        for (int cx = cx0 - radius; cx <= cx0 + radius; cx++) {
            for (int cz = cz0 - radius; cz <= cz0 + radius; cz++) {
                net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                chunks++;
                if (structure != null && chunk.getAllReferences().keySet().stream().anyMatch(s -> s == structure.get(0).value())) {
                    structureChunks++;
                }
                for (net.minecraft.world.level.block.entity.BlockEntity entity : chunk.getBlockEntities().values()) {
                    if (!(entity instanceof LuckyBlockEntity lucky)) {
                        continue;
                    }
                    BlockPos pos = lucky.getBlockPos();
                    lucks.add(lucky.luck());
                    int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
                    surface += pos.getY() >= top - 1 ? 1 : 0;
                    boolean inStructure = structure != null && level.structureManager().getStructureWithPieceAt(pos, structure).isValid();
                    inside += inStructure ? 1 : 0;
                    if (structure == null || inStructure) {
                        LOGGER.info("Screenshot run: [{}] lucky block at {} copying {} with luck {}", label, pos.toShortString(),
                                lucky.mimic() == null ? "?" : lucky.mimic().getBlock().getName().getString(), lucky.luck());
                    }
                }
            }
        }
        java.util.Collections.sort(lucks);
        long middle = lucks.stream().filter(l -> Math.abs(l) < 50).count();
        long good = lucks.stream().filter(l -> l > 0).count();
        LOGGER.info("Screenshot run: [{}] {} chunks: {} lucky blocks ({} surface, {} cave{}); luck {} to {}, {} good, {} within ±49; all: {}",
                label, chunks, lucks.size(), surface, lucks.size() - surface,
                structure == null ? "" : ", " + inside + " inside the structure, which reaches " + structureChunks + " of the chunks",
                lucks.isEmpty() ? "-" : lucks.getFirst(), lucks.isEmpty() ? "-" : lucks.getLast(), good, middle, lucks);
    }

    /** -PshotsWorld=casino: the plains casino and a villager who should take the job. */
    private static final boolean CASINO = "casino".equals(System.getProperty("rngambling.shotsWorld"));
    private static net.minecraft.world.entity.npc.villager.Villager dealer;
    private static net.minecraft.core.Direction machineFacing = net.minecraft.core.Direction.SOUTH;

    /**
     * The casino: places the plains casino in the flat world, puts an unemployed villager inside,
     * waits for it to take the Slot Machine as its job, then pictures the house, the dealer and
     * its trades.
     */
    private static void planCasino() {
        then(10, minecraft -> {
            minecraft.options.hideGui = true;
            onServer(minecraft, player -> {
                ServerLevel level = player.level();
                base = player.blockPosition().offset(-4, -1, -12);
                level.getServer().getCommands().performPrefixedCommand(
                        level.getServer().createCommandSourceStack().withSuppressedOutput(), "time set noon");
                var template = level.getStructureManager().getOrCreate(RNGambling.id("village/plains/casino_slots"));
                template.placeInWorld(level, base, base, new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),
                        level.getRandom(), net.minecraft.world.level.block.Block.UPDATE_ALL);
                BlockPos machine = template.filterBlocks(base, new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),
                        RNGambling.SLOT_MACHINE.get()).getFirst().pos();
                LOGGER.info("Screenshot run: casino placed at {}, slot machine at {}", base.toShortString(), machine.toShortString());
                machinePos = machine;
                dealer = net.minecraft.world.entity.EntityType.VILLAGER.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
                machineFacing = level.getBlockState(machine).getValue(SlotMachineBlock.FACING);
                BlockPos inside = machine.relative(machineFacing);
                dealer.snapTo(Vec3.atBottomCenterOf(inside));
                level.addFreshEntity(dealer);
            });
        });
        // Wait for the villager to take the job: check every 5 seconds, for up to 2 minutes.
        for (int i = 0; i < 6; i++) {
            int seconds = 5 * (i + 1);
            then(100, minecraft -> onServer(minecraft, player -> {
                if (dealer != null && !dealer.isRemoved()) {
                    var profession = dealer.getVillagerData().profession();
                    if (seconds % 15 == 0 || profession.is(RNGambling.DEALER.getKey())) {
                        LOGGER.info("Screenshot run: after {} s the villager is a {}", seconds, profession.getRegisteredName());
                    }
                }
            }));
        }
        view("20-casino-outside", new Vec3(4, 6, 18), new Vec3(4, 2, 4));
        then(30, minecraft -> onServer(minecraft, player -> {
            if (dealer != null) {
                // The room is too small for a portrait: freeze the dealer out on the grass, facing south, and look from the front.
                dealer.setNoAi(true);
                Vec3 spot = Vec3.atBottomCenterOf(base.offset(4, 1, 16));
                dealer.snapTo(spot.x, spot.y, spot.z, 0, 0);
                dealer.setYHeadRot(0);
                dealer.setYBodyRot(0);
                look(player, spot.add(0, 1.6, 2.4), spot.add(0, 1.2, 0));
            }
        }));
        then(10, minecraft -> shot(minecraft, "21-dealer"));
        then(10, minecraft -> {
            minecraft.options.hideGui = false;
            onServer(minecraft, player -> {
                if (dealer != null) {
                    dealer.mobInteract(player, net.minecraft.world.InteractionHand.MAIN_HAND);
                }
            });
        });
        then(30, minecraft -> shot(minecraft, "22-dealer-trades"));
        // The slot machine: open it with tokens in hand, then spin.
        then(10, minecraft -> onServer(minecraft, player -> {
            player.closeContainer();
            // Stand in front of the machine (the screen closes beyond 8 blocks).
            look(player, Vec3.atCenterOf(machinePos.relative(machineFacing)).add(0, 0.5, 0), Vec3.atCenterOf(machinePos));
            player.getInventory().add(new net.minecraft.world.item.ItemStack(RNGambling.CASINO_TOKEN.get(), 64));
            BlockState state = player.level().getBlockState(machinePos);
            state.useWithoutItem(player.level(), player, new net.minecraft.world.phys.BlockHitResult(
                    Vec3.atCenterOf(machinePos), machineFacing, machinePos, false));
        }));
        then(20, minecraft -> shot(minecraft, "23-slots-ready"));
        then(2, minecraft -> {
            if (minecraft.player != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(minecraft.player.containerMenu.containerId, 1); // bet 5
            }
        });
        // (Each step's wait comes after it.) Spin, picture mid-spin, then once the win is paid.
        then(12, minecraft -> minecraft.gameMode.handleInventoryButtonClick(minecraft.player.containerMenu.containerId,
                rngambling.casino.SlotMachineMenu.BUTTON_SPIN));
        then(40, minecraft -> shot(minecraft, "24-slots-spinning"));
        then(4, minecraft -> shot(minecraft, "25-slots-result"));
        // A few more spins, to see wins and losses.
        for (int i = 0; i < 8; i++) {
            then(48, minecraft -> minecraft.gameMode.handleInventoryButtonClick(minecraft.player.containerMenu.containerId,
                    rngambling.casino.SlotMachineMenu.BUTTON_SPIN));
            int n = i;
            then(4, minecraft -> shot(minecraft, "26-slots-spin-" + n));
        }
        then(20, Minecraft::stop);
    }

    private static BlockPos machinePos = BlockPos.ZERO;

    /** -PshotsWorld=villages: how many of the nearest villages have a casino (from their layouts only). */
    private static final boolean VILLAGES = "villages".equals(System.getProperty("rngambling.shotsWorld"));

    private static void planVillages() {
        java.util.List<BlockPos> candidates = new java.util.ArrayList<>();
        int[] counts = new int[3]; // villages, with a casino, casinos
        then(5, minecraft -> onServer(minecraft, player -> candidates.addAll(
                findStructure(player.level().getServer().overworld(), "village_plains", "villages", 64))));
        for (int i = 0; i < 60; i++) {
            int index = i;
            then(2, minecraft -> onServer(minecraft, player -> {
                if (index >= candidates.size()) {
                    return;
                }
                ServerLevel overworld = player.level().getServer().overworld();
                var villages = overworld.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                        .getOrThrow(net.minecraft.tags.StructureTags.VILLAGE);
                var found = overworld.getChunkSource().getGenerator().findNearestMapStructure(overworld, villages, candidates.get(index), 0, false);
                if (found == null) {
                    return;
                }
                net.minecraft.world.level.ChunkPos chunk = net.minecraft.world.level.ChunkPos.containing(found.getFirst());
                var starts = overworld.getChunk(chunk.x(), chunk.z(), net.minecraft.world.level.chunk.status.ChunkStatus.STRUCTURE_STARTS).getAllStarts();
                for (var start : starts.values()) {
                    if (!start.isValid() || !found.getSecond().value().equals(start.getStructure())) {
                        continue;
                    }
                    long casinos = start.getPieces().stream().filter(p -> p.toString().contains("rngambling:village")).count();
                    counts[0]++;
                    counts[1] += casinos > 0 ? 1 : 0;
                    counts[2] += (int) casinos;
                    LOGGER.info("Screenshot run: {} at {}: {} pieces, {} casino(s)", found.getSecond().getRegisteredName(),
                            found.getFirst().toShortString(), start.getPieces().size(), casinos);
                }
            }));
        }
        then(5, minecraft -> LOGGER.info("Screenshot run: {} villages, {} with a casino ({} casinos)", counts[0], counts[1], counts[2]));
        then(20, Minecraft::stop);
    }

    /** -PshotsWorld=tables: the roulette and blackjack tables, in the world and played. */
    private static final boolean TABLES = "tables".equals(System.getProperty("rngambling.shotsWorld"));
    private static final BlockPos[] tablePos = new BlockPos[2];

    private static void button(Minecraft minecraft, int id) {
        if (minecraft.player != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(minecraft.player.containerMenu.containerId, id);
        }
    }

    private static void openTable(ServerPlayer player, BlockPos pos) {
        player.closeContainer();
        look(player, Vec3.atCenterOf(pos).add(0, 1.2, 2.2), Vec3.atCenterOf(pos));
        player.level().getBlockState(pos).useWithoutItem(player.level(), player,
                new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false));
    }

    private static void planTables() {
        then(10, minecraft -> {
            minecraft.options.hideGui = true;
            onServer(minecraft, player -> {
                ServerLevel level = player.level();
                base = player.blockPosition().offset(0, 0, -4);
                level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withSuppressedOutput(), "time set noon");
                tablePos[0] = base.offset(-2, 0, 0);
                tablePos[1] = base.offset(2, 0, 0);
                level.setBlockAndUpdate(tablePos[0], RNGambling.ROULETTE_TABLE.get().defaultBlockState());
                level.setBlockAndUpdate(tablePos[1], RNGambling.BLACKJACK_TABLE.get().defaultBlockState());
                level.setBlockAndUpdate(base, RNGambling.SLOT_MACHINE.get().defaultBlockState()
                        .setValue(SlotMachineBlock.FACING, net.minecraft.core.Direction.SOUTH));
                player.getInventory().add(new net.minecraft.world.item.ItemStack(RNGambling.CASINO_TOKEN.get(), 64));
            });
        });
        then(30, minecraft -> onServer(minecraft, player -> look(player, Vec3.atCenterOf(base).add(0, 2.5, 4.5), Vec3.atCenterOf(base))));
        then(10, minecraft -> shot(minecraft, "30-tables"));
        // Roulette: 5 on red, spin.
        then(20, minecraft -> {
            minecraft.options.hideGui = false;
            onServer(minecraft, player -> openTable(player, tablePos[0]));
        });
        then(4, minecraft -> button(minecraft, rngambling.casino.Bets.UP_BIG)); // 15
        then(4, minecraft -> button(minecraft, rngambling.casino.RouletteMenu.BUTTON_KIND + rngambling.casino.Roulette.Bet.RED.ordinal()));
        then(10, minecraft -> shot(minecraft, "31-roulette-ready"));
        then(30, minecraft -> button(minecraft, rngambling.casino.RouletteMenu.BUTTON_SPIN));
        then(50, minecraft -> shot(minecraft, "32-roulette-spinning"));
        then(4, minecraft -> shot(minecraft, "33-roulette-result"));
        // Blackjack: bet 6, deal, picture, stand, picture the result.
        then(20, minecraft -> onServer(minecraft, player -> openTable(player, tablePos[1])));
        then(4, minecraft -> button(minecraft, rngambling.casino.Bets.UP)); // 12
        then(10, minecraft -> button(minecraft, rngambling.casino.BlackjackMenu.BUTTON_DEAL));
        then(4, minecraft -> shot(minecraft, "34-blackjack-dealt"));
        then(70, minecraft -> button(minecraft, rngambling.casino.BlackjackMenu.BUTTON_STAND));
        then(4, minecraft -> shot(minecraft, "35-blackjack-result"));
        for (int i = 0; i < 3; i++) {
            int n = i;
            then(10, minecraft -> button(minecraft, rngambling.casino.BlackjackMenu.BUTTON_DEAL));
            then(10, minecraft -> button(minecraft, rngambling.casino.BlackjackMenu.BUTTON_HIT));
            then(70, minecraft -> button(minecraft, rngambling.casino.BlackjackMenu.BUTTON_STAND));
            then(4, minecraft -> shot(minecraft, "36-blackjack-hand-" + n));
            // While the table is open, the hotbar the player sees holds what the server says they have.
            then(10, minecraft -> {
                int client = rngambling.casino.Tokens.count(minecraft.player.getInventory());
                onServer(minecraft, player -> check(rngambling.casino.Tokens.count(player.getInventory()) == client,
                        "the player's own inventory shows the right tokens with the table open: client " + client
                                + ", server " + rngambling.casino.Tokens.count(player.getInventory())));
            });
        }
        then(20, Minecraft::stop);
    }

    /**
     * -PshotsWorld=sandbox: the Sandbox in a real world (the game test server has no datapack
     * dimensions). Logs each check as "SANDBOX CHECK ok/FAILED" and pictures the arena.
     */
    private static final boolean SANDBOX = "sandbox".equals(System.getProperty("rngambling.shotsWorld"));
    private static net.minecraft.world.phys.Vec3 sandboxStart;
    private static BlockPos sandboxMarker;

    private static void check(boolean ok, String what) {
        LOGGER.info("SANDBOX CHECK {}: {}", ok ? "ok" : "FAILED", what);
    }

    private static rngambling.outcome.Outcome outcomeNamed(String id) {
        return rngambling.outcome.Outcomes.ALL.stream().filter(o -> o.id().equals(id)).findFirst().orElseThrow();
    }

    private static void command(ServerPlayer player, String command) {
        MinecraftServer server = player.level().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
    }

    private static void planSandbox() {
        then(20, minecraft -> {
            minecraft.options.hideGui = false;
            onServer(minecraft, player -> {
                ServerLevel level = player.level();
                check(level.getServer().getLevel(rngambling.outcome.Sandbox.LEVEL) != null, "the Sandbox dimension loads in a real world");
                sandboxMarker = player.blockPosition().offset(2, -1, 0);
                level.setBlockAndUpdate(sandboxMarker, net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK.defaultBlockState());
                sandboxStart = new Vec3(player.getX() + 0.37, player.getY(), player.getZ() - 0.21);
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
                player.teleportTo(level, sandboxStart.x, sandboxStart.y, sandboxStart.z, Set.of(), 47f, 5f, false);
                player.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_APPLE, 5));
                // A Sandbox effect where the "lucky block" is: next to the diamond block.
                rngambling.outcome.Outcomes.play(level, sandboxMarker.above(), player, 0, outcomeNamed("anvil_rain"));
                check(rngambling.outcome.Sandbox.inSandbox(player), "a Sandbox effect (Anvil Rain) takes the player to the Sandbox");
                check(player.level().getBlockState(player.blockPosition().below()).is(net.minecraft.world.level.block.Blocks.BEDROCK),
                        "the player stands on the bedrock plane");
            });
        });
        then(20, minecraft -> shot(minecraft, "40-sandbox-tnt"));
        then(170, minecraft -> { });
        then(10, minecraft -> onServer(minecraft, player -> {
            check(!rngambling.outcome.Sandbox.inSandbox(player), "the player is back once the anvils have landed");
            check(player.position().distanceTo(sandboxStart) < 1e-6 && Math.abs(player.getYRot() - 47f) < 0.01,
                    "back exactly where they were: " + player.position() + " vs " + sandboxStart);
            check(player.level().getBlockState(sandboxMarker).is(net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK),
                    "the world where the block was is untouched");
        }));
        then(10, minecraft -> shot(minecraft, "41-sandbox-back"));
        // The Obsidian Cage lasts 30 seconds (with a creeper that won't chase a creative player): an admin brings the player out.
        then(10, minecraft -> onServer(minecraft, player ->
                rngambling.outcome.Outcomes.play(player.level(), sandboxMarker.above(), player, 0, outcomeNamed("obsidian_cage"))));
        then(40, minecraft -> shot(minecraft, "42-sandbox-creeper"));
        then(10, minecraft -> onServer(minecraft, player -> {
            command(player, "rngambling sandbox list");
            command(player, "rngambling sandbox return " + player.getGameProfile().name());
            check(!rngambling.outcome.Sandbox.inSandbox(player) && player.position().distanceTo(sandboxStart) < 1e-6,
                    "/rngambling sandbox return brings the player back exactly");
            // Lose everything, then restore from the copy.
            player.getInventory().clearContent();
            command(player, "rngambling sandbox restore " + player.getGameProfile().name());
            int apples = 0;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                net.minecraft.world.item.ItemStack stack = player.getInventory().getItem(slot);
                apples += stack.is(net.minecraft.world.item.Items.GOLDEN_APPLE) ? stack.getCount() : 0;
            }
            check(apples == 5, "/rngambling sandbox restore gives back the inventory copy (" + apples + " apples)");
            command(player, "rngambling sandbox list");
        }));
        then(20, Minecraft::stop);
    }

    /**
     * -PshotsWorld=rooms: every Sandbox room in a real world: entered, pictured, then left by its
     * exit (a check that walking in ends the visit) or, without one, by the admin return.
     */
    private static final boolean ROOMS = "rooms".equals(System.getProperty("rngambling.shotsWorld"));

    private static void planRooms() {
        var rooms = rngambling.outcome.Rooms.all();
        rngambling.outcome.Room[] current = new rngambling.outcome.Room[1];
        Vec3[] home = new Vec3[1];
        then(20, minecraft -> {
            minecraft.options.hideGui = false; // rooms show their timers and countdowns on the HUD
            onServer(minecraft, player -> {
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
                home[0] = player.position();
                command(player, "difficulty normal"); // so the rooms' mobs show up (the run's world starts peaceful)
            });
        });
        for (int i = 0; i < rooms.size(); i++) {
            int index = i;
            then(60, minecraft -> onServer(minecraft, player -> {
                current[0] = rooms.get(index).get();
                boolean started = rngambling.outcome.Sandbox.startRoom(player, current[0], 0);
                check(started && rngambling.outcome.Sandbox.inSandbox(player), current[0].getClass().getSimpleName() + " starts");
            }));
            then(10, minecraft -> shot(minecraft, String.format("5%02d-room-%s", index, current[0].getClass().getSimpleName())));
            then(20, minecraft -> onServer(minecraft, player -> {
                BlockPos exit = current[0].exitPos();
                if (exit != null && player.level().getBlockState(exit).is(RNGambling.SANDBOX_EXIT.get())) {
                    player.teleportTo(exit.getX() + 0.5, exit.getY(), exit.getZ() + 0.5); // walk into the way out
                } else {
                    rngambling.outcome.Sandbox.bringBack(player);
                }
            }));
            then(10, minecraft -> onServer(minecraft, player -> {
                String name = current[0].getClass().getSimpleName();
                check(!rngambling.outcome.Sandbox.inSandbox(player) && player.position().distanceTo(home[0]) < 1e-6,
                        name + " ends and brings the player back exactly" + (current[0].exitPos() != null
                                ? " (through its exit: " + (current[0].succeeded() ? "success" : "not counted as success") + ")" : ""));
            }));
        }
        then(20, Minecraft::stop);
    }

    /**
     * -PshotsWorld=effects: the effects with something to see: Ore Vision's glow through stone,
     * the Disco Floor, the Llama Spit Party, the Dragon Hatchling (standing and sitting), the
     * Genie's Wish window and a lit Hot Potato.
     */
    private static final boolean EFFECTS = "effects".equals(System.getProperty("rngambling.shotsWorld"));

    private static void play(ServerPlayer player, String id) {
        rngambling.outcome.Outcomes.play(player.level(), player.blockPosition(), player, 0, outcomeNamed(id));
    }

    private static void stand(ServerPlayer player, Vec3 at, float yaw, float pitch) {
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        Vec3 to = at.add(base.getX(), base.getY(), base.getZ());
        player.teleportTo(player.level(), to.x, to.y, to.z, Set.of(), yaw, pitch, false);
    }

    private static void planEffects() {
        then(20, minecraft -> {
            minecraft.options.hideGui = true;
            onServer(minecraft, player -> {
                base = player.blockPosition();
                command(player, "time set noon");
                ServerLevel level = player.level();
                // A stone block with ores sealed inside it, 6 to 12 blocks ahead (south).
                for (BlockPos p : BlockPos.betweenClosed(base.offset(-3, 0, 6), base.offset(3, 4, 12))) {
                    level.setBlockAndUpdate(p, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                }
                net.minecraft.world.level.block.Block[] ores = {Blocks.DIAMOND_ORE, Blocks.GOLD_ORE, Blocks.IRON_ORE, Blocks.REDSTONE_ORE,
                        Blocks.EMERALD_ORE, Blocks.LAPIS_ORE, Blocks.COAL_ORE, Blocks.COPPER_ORE};
                for (int i = 0; i < ores.length; i++) {
                    level.setBlockAndUpdate(base.offset(-2 + (i % 4) + (i / 4), 1 + i / 4 * 2, 8 + i % 3), ores[i].defaultBlockState());
                }
                stand(player, new Vec3(0.5, 0, 0.5), 0f, 15f);
                play(player, "ore_vision");
            });
        });
        view("60-ore-vision", new Vec3(0.5, 3.0, 0.5), new Vec3(0.5, 2.0, 9.0));
        // Disco Floor, away from the stone.
        then(10, minecraft -> onServer(minecraft, player -> {
            stand(player, new Vec3(-12.5, 0, 0.5), 0f, 0f);
            play(player, "disco_floor");
        }));
        view("61-disco-floor", new Vec3(-12.5, 5.0, -4.5), new Vec3(-12.5, 0.0, 0.5));
        then(10, minecraft -> {
            minecraft.options.hideGui = false;
            onServer(minecraft, player -> {
                stand(player, new Vec3(-24.5, 0, 0.5), 0f, 0f);
                play(player, "llama_spit_party");
            });
        });
        then(30, minecraft -> shot(minecraft, "62-llama-spit"));
        then(10, minecraft -> onServer(minecraft, player -> {
            stand(player, new Vec3(-36.5, 0, 0.5), 0f, 0f);
            play(player, "dragon_hatchling");
            var dragons = player.level().getEntitiesOfClass(rngambling.entity.DragonHatchling.class, player.getBoundingBox().inflate(6));
            check(dragons.size() == 1 && dragons.get(0).isOwnedBy(player), "a tame Dragon Hatchling hatches");
            for (var dragon : dragons) {
                dragon.teleportTo(player.getX(), player.getY(), player.getZ() + 3);
                dragon.setYRot(200f);
                dragon.setYBodyRot(200f);
                dragon.setYHeadRot(200f);
                dragon.setNoAi(true);
            }
        }));
        then(10, minecraft -> minecraft.options.hideGui = true);
        view("63-dragon", new Vec3(-38.0, 1.4, 1.6), new Vec3(-36.5, 0.5, 3.5));
        view("64-dragon-side", new Vec3(-33.5, 1.2, 3.5), new Vec3(-36.5, 0.5, 3.5));
        then(5, minecraft -> onServer(minecraft, player -> player.level().getEntitiesOfClass(rngambling.entity.DragonHatchling.class,
                player.getBoundingBox().inflate(12)).forEach(dragon -> dragon.setInSittingPose(true))));
        view("65-dragon-sitting", new Vec3(-38.0, 1.4, 1.6), new Vec3(-36.5, 0.5, 3.5));
        then(10, minecraft -> {
            minecraft.options.hideGui = false;
            onServer(minecraft, player -> {
                stand(player, new Vec3(-48.5, 0, 0.5), 0f, 0f);
                play(player, "genies_wish");
            });
        });
        then(20, minecraft -> {
            check(minecraft.screen != null, "the Genie's Wish window opens");
            shot(minecraft, "66-genies-wish");
        });
        then(10, minecraft -> {
            if (minecraft.player != null) {
                minecraft.player.closeContainer();
            }
        });
        then(10, minecraft -> onServer(minecraft, player -> {
            player.getInventory().clearContent();
            play(player, "hot_potato");
        }));
        then(30, minecraft -> shot(minecraft, "67-hot-potato"));
        then(20, Minecraft::stop);
    }

    private static void plan() {
        if (EFFECTS) {
            planEffects();
            return;
        }
        if (ROOMS) {
            planRooms();
            return;
        }
        if (SANDBOX) {
            planSandbox();
            return;
        }
        if (VILLAGES) {
            planVillages();
            return;
        }
        if (CASINO) {
            planCasino();
            return;
        }
        if (TABLES) {
            planTables();
            return;
        }
        if (ZONES) {
            planZones();
            return;
        }
        if (NORMAL_WORLD) {
            planWorldGeneration();
            return;
        }
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
