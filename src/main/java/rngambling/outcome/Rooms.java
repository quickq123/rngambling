package rngambling.outcome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import rngambling.RNGambling;

/**
 * The effect catalog's Sandbox rooms, built in code (each visit gets a fresh one on the
 * Sandbox's plane; see {@link Room} and {@link Sandbox#startRoom}). First versions, to be tuned
 * in playtesting.
 */
public final class Rooms {
    private Rooms() {
    }

    private static final String LEGENDARY_CHEST = "minecraft:chests/end_city_treasure";

    // ======================= Catastrophic =======================

    /** CAT-01: a sealed obsidian arena with a freshly spawned Wither. Over when it dies (or you do). */
    public static final class WitherArena extends Room {
        @Override
        protected Vec3 build() {
            box(-14, -1, -14, 14, 10, 14, Blocks.OBSIDIAN, true);
            for (int x = -12; x <= 12; x += 6) {
                for (int z = -12; z <= 12; z += 6) {
                    set(x, 9, z, Blocks.GLOWSTONE);
                }
            }
            WitherBoss wither = spawn(EntityType.WITHER, 0, 2, -6);
            if (wither != null) {
                wither.makeInvulnerable(); // the charging-up start, as when built by hand
            }
            return Vec3.atBottomCenterOf(at(0, 0, 10));
        }

        @Override
        protected int maxTicks() {
            return 20 * 60 * 5;
        }

        @Override
        protected boolean endsWhenSettled() {
            return true;
        }

        @Override
        protected boolean canBreak(BlockPos pos, BlockState state) {
            return false;
        }
    }

    /** CAT-20: a buffed Wither with minions in a ruined arena; a huge loot chest if it's beaten. */
    public static final class EmpoweredWither extends Room {
        private WitherBoss wither;
        private int rewardAt = -1;

        @Override
        protected Vec3 build() {
            // A ruined ring of stone bricks, broken in places.
            for (int x = -16; x <= 16; x++) {
                for (int z = -16; z <= 16; z++) {
                    double r = Math.sqrt(x * x + z * z);
                    if (r >= 15 && r < 16.2) {
                        int height = 2 + random.nextInt(5);
                        for (int y = 0; y < height; y++) {
                            set(x, y, z, random.nextInt(4) == 0 ? Blocks.CRACKED_STONE_BRICKS : random.nextInt(3) == 0
                                    ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS);
                        }
                    }
                }
            }
            wither = spawn(EntityType.WITHER, 0, 3, -6);
            if (wither != null) {
                wither.getAttribute(Attributes.MAX_HEALTH).addPermanentModifier(new AttributeModifier(
                        Identifier.fromNamespaceAndPath("rngambling", "empowered"), 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
                wither.getAttribute(Attributes.ARMOR).addPermanentModifier(new AttributeModifier(
                        Identifier.fromNamespaceAndPath("rngambling", "empowered_armor"), 6, AttributeModifier.Operation.ADD_VALUE));
                wither.setHealth(wither.getMaxHealth());
                wither.makeInvulnerable();
            }
            return Vec3.atBottomCenterOf(at(0, 0, 10));
        }

        @Override
        protected void tick(int age) {
            boolean alive = wither != null && wither.isAlive();
            if (alive && age % 400 == 399) {
                spawn(EntityType.WITHER_SKELETON, wither.getX() - centre.getX() - 1, 0, wither.getZ() - centre.getZ());
                spawn(EntityType.WITHER_SKELETON, wither.getX() - centre.getX() + 1, 0, wither.getZ() - centre.getZ());
            }
            if (!alive && rewardAt < 0) {
                rewardAt = age;
                chest(0, 0, 0, LEGENDARY_CHEST);
                chest(1, 0, 0, "minecraft:chests/bastion_treasure");
                message("message.rngambling.room.empowered_wither.won");
            }
            if (rewardAt >= 0 && age - rewardAt > 20 * 30) {
                finish(true);
            }
        }

        @Override
        protected int maxTicks() {
            return 20 * 60 * 6;
        }
    }

    /**
     * CAT-03: a long, dark deepslate and sculk cavern down to the way out. Shriekers that can
     * summon a Warden line it, and it's laid with sound traps: bone piles that rattle and dark
     * pressure plates that click, both of which set off the nearest shrieker however carefully
     * the player moves, and sculk sensors that hear anyone who doesn't sneak. The deep dark already
     * knows the player (warning level 2), so two more shrieks bring the Warden.
     */
    public static final class WardensHollow extends Room {
        public static final int LENGTH = 80;
        private final java.util.Set<BlockPos> bones = new java.util.HashSet<>();
        private final List<BlockPos> shriekers = new ArrayList<>();
        private BlockPos lastTrap;

        @Override
        protected Vec3 build() {
            box(-4, -1, -2, 4, 6, LENGTH + 2, Blocks.DEEPSLATE, true);
            for (int z = -1; z <= LENGTH + 1; z++) {
                for (int x = -3; x <= 3; x++) {
                    set(x, -1, z, random.nextInt(3) == 0 ? Blocks.SCULK : Blocks.DEEPSLATE);
                    if (z < 3 || z > LENGTH - 3) {
                        continue; // a clear start and finish
                    }
                    int roll = random.nextInt(100);
                    if (roll < 7 && Math.abs(x) >= 2) {
                        set(x, 0, z, Blocks.SCULK_SENSOR);
                    } else if (roll < 12) {
                        set(x, -1, z, Blocks.BONE_BLOCK); // rattles underfoot
                        bones.add(at(x, -1, z));
                    } else if (roll < 16 && Math.abs(x) <= 2) {
                        set(x, 0, z, Blocks.POLISHED_BLACKSTONE_PRESSURE_PLATE); // clicks
                    }
                }
                if (z % 8 == 4 && z < LENGTH - 2) {
                    int x = (z / 8) % 2 == 0 ? -3 : 3;
                    set(x, 0, z, Blocks.SCULK_SHRIEKER.defaultBlockState().setValue(BlockStateProperties.CAN_SUMMON, true));
                    shriekers.add(at(x, 0, z));
                }
            }
            exit(0, 0, LENGTH);
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 60 * 5));
            player.getWardenSpawnTracker().ifPresent(tracker -> tracker.setWarningLevel(Math.max(tracker.getWarningLevel(), 2)));
            message("message.rngambling.room.wardens_hollow.rules");
            return Vec3.atBottomCenterOf(at(0, 0, 0));
        }

        @Override
        protected void tick(int age) {
            // A sound trap: standing on a bone pile or a pressure plate sets off the nearest shrieker.
            BlockPos feet = player.blockPosition();
            boolean onTrap = bones.contains(feet.below()) || level.getBlockState(feet).is(Blocks.POLISHED_BLACKSTONE_PRESSURE_PLATE);
            if (!onTrap) {
                lastTrap = null;
                return;
            }
            if (feet.equals(lastTrap)) {
                return; // once per step onto it
            }
            lastTrap = feet;
            if (bones.contains(feet.below())) {
                level.playSound(null, feet, SoundEvents.SKELETON_HURT, SoundSource.BLOCKS, 1.2f, 0.6f);
            }
            shriekers.stream().min(java.util.Comparator.comparingDouble(p -> p.distSqr(feet))).ifPresent(nearest -> {
                if (level.getBlockEntity(nearest) instanceof net.minecraft.world.level.block.entity.SculkShriekerBlockEntity shrieker) {
                    shrieker.tryShriek(level, player);
                }
            });
        }

        @Override
        protected float startYaw() {
            return 0f; // facing down the cavern (south)
        }

        @Override
        protected int maxTicks() {
            return 20 * 60 * 5;
        }

        @Override
        protected void onEnd(boolean success) {
            super.onEnd(success);
            player.removeEffect(MobEffects.DARKNESS);
        }

        /** For tests: the trap and shrieker spots. */
        public java.util.Set<BlockPos> boneTraps() {
            return bones;
        }

        public List<BlockPos> shriekerSpots() {
            return shriekers;
        }
    }

    /**
     * CAT-07: a 9x9 platform above the void. After a three-second countdown it crumbles: a block
     * darkens, then falls a moment later, and every fourth to go is the one under the player, so
     * standing still is no defence. By about 25 seconds it's all gone; the player's 8 cobblestone
     * are their lifeline. Hold on for 30 seconds and a way out appears. Falling is failure.
     */
    public static final class VoidPlatform extends Room {
        private static final int Y = 100, HALF = 4, COUNTDOWN = 60, EVERY = 6, WARNING = 12, SURVIVE = 20 * 30;
        private final List<BlockPos> platform = new ArrayList<>();
        private final java.util.Map<BlockPos, Integer> falling = new java.util.HashMap<>();
        private int crumbles;
        private boolean exitOpen;

        @Override
        protected Vec3 build() {
            for (int x = -HALF; x <= HALF; x++) {
                for (int z = -HALF; z <= HALF; z++) {
                    set(x, Y, z, Blocks.STONE);
                    platform.add(at(x, Y, z));
                }
            }
            Collections.shuffle(platform, new java.util.Random(random.nextLong()));
            giveSandboxOnly(Items.COBBLESTONE, 8);
            message("message.rngambling.room.void_platform.rules");
            return Vec3.atBottomCenterOf(at(0, Y + 1, 0));
        }

        @Override
        protected void tick(int age) {
            if (age < COUNTDOWN) {
                if (age % 20 == 0) {
                    title(Component.literal(String.valueOf(3 - age / 20)).withStyle(ChatFormatting.RED), 0, 20, 0);
                    level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 1f, 1f);
                }
                return;
            }
            if (age == COUNTDOWN) {
                title(Component.translatable("message.rngambling.room.void_platform.go").withStyle(ChatFormatting.GOLD), 0, 20, 10);
                level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 1f, 2f);
            }
            // Blocks marked to fall, falling.
            falling.entrySet().removeIf(entry -> {
                if (age < entry.getValue()) {
                    return false;
                }
                if (level.getBlockState(entry.getKey()).is(Blocks.COBBLED_DEEPSLATE)) {
                    level.destroyBlock(entry.getKey(), false);
                }
                return true;
            });
            if (!exitOpen && (age - COUNTDOWN) % EVERY == 0) {
                BlockPos under = player.blockPosition().below();
                BlockPos next = null;
                if (crumbles % 4 == 3 && platform.contains(under)) {
                    next = under; // the one they're standing on
                } else {
                    while (!platform.isEmpty() && next == null) {
                        BlockPos candidate = platform.removeLast();
                        next = level.getBlockState(candidate).is(Blocks.STONE) ? candidate : null;
                    }
                }
                if (next != null) {
                    platform.remove(next);
                    crumbles++;
                    level.setBlock(next, Blocks.COBBLED_DEEPSLATE.defaultBlockState(), Block.UPDATE_CLIENTS);
                    level.playSound(null, next, SoundEvents.STONE_HIT, SoundSource.BLOCKS, 1f, 0.6f);
                    falling.put(next, age + WARNING);
                }
            }
            if (age == COUNTDOWN + SURVIVE) {
                exitOpen = true;
                BlockPos under = player.blockPosition().below();
                BlockPos spot = under.relative(Direction.Plane.HORIZONTAL.getRandomDirection(random), 2);
                level.setBlock(spot, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                exit(spot.getX() - centre.getX(), spot.getY() - centre.getY() + 1, spot.getZ() - centre.getZ());
                message("message.rngambling.room.exit_open");
            }
        }

        @Override
        protected double failBelow() {
            return centre.getY() + Y - 8;
        }

        @Override
        protected boolean allowsPlacing() {
            return true;
        }

        @Override
        protected int maxTicks() {
            return 20 * 100;
        }
    }

    /**
     * CAT-08: a sealed box filling with lava a layer at a time. Among the cracked bricks in the walls,
     * one pair (two high, at the floor) breaks, and leads out.
     */
    public static final class RisingLavaTomb extends Room {
        /** Ticks before the lava comes, and between layers. */
        public static final int GRACE = 20 * 15, EVERY = 20 * 5;
        private BlockPos weak;
        private int layer;
        private net.minecraft.server.level.ServerBossEvent countdown;

        @Override
        protected Vec3 build() {
            box(-3, -1, -3, 3, 9, 3, Blocks.STONE_BRICKS, true);
            // Cracked decoys that don't break, and one that does.
            List<BlockPos> wall = new ArrayList<>();
            for (int y = 0; y <= 1; y++) {
                for (int i = -2; i <= 2; i++) {
                    wall.add(at(i, y, -3));
                    wall.add(at(i, y, 3));
                    wall.add(at(-3, y, i));
                    wall.add(at(3, y, i));
                }
            }
            Collections.shuffle(wall, new java.util.Random(random.nextLong()));
            for (int i = 0; i < 5; i++) {
                level.setBlock(wall.get(i), Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            // The weak pair: at floor level, two high, so a player can walk out.
            weak = wall.stream().filter(p -> p.getY() == centre.getY()).findFirst().orElseThrow();
            level.setBlock(weak, Blocks.INFESTED_CRACKED_STONE_BRICKS.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(weak.above(), Blocks.INFESTED_CRACKED_STONE_BRICKS.defaultBlockState(), Block.UPDATE_CLIENTS);
            // Behind the weak brick, a short tunnel to the exit.
            Direction out = weak.getX() - centre.getX() == -3 ? Direction.WEST : weak.getX() - centre.getX() == 3 ? Direction.EAST
                    : weak.getZ() - centre.getZ() == -3 ? Direction.NORTH : Direction.SOUTH;
            BlockPos tunnel = weak.relative(out);
            Direction side = out.getClockWise();
            for (int i = 0; i <= 3; i++) {
                // A short enclosed tunnel of its own (floor, walls, roof), two high.
                BlockPos p = tunnel.relative(out, i);
                BlockState brick = Blocks.STONE_BRICKS.defaultBlockState();
                level.setBlock(p.below(), brick, Block.UPDATE_CLIENTS);
                level.setBlock(p.above(2), brick, Block.UPDATE_CLIENTS);
                for (int h = 0; h <= 1; h++) {
                    level.setBlock(p.above(h), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    level.setBlock(p.above(h).relative(side), brick, Block.UPDATE_CLIENTS);
                    level.setBlock(p.above(h).relative(side.getOpposite()), brick, Block.UPDATE_CLIENTS);
                }
            }
            level.setBlock(tunnel.relative(out, 4), Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(tunnel.relative(out, 4).above(), Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_CLIENTS);
            BlockPos exit = tunnel.relative(out, 3);
            exit(exit.getX() - centre.getX(), weak.getY() - centre.getY(), exit.getZ() - centre.getZ());
            set(0, 8, 0, Blocks.GLOWSTONE);
            message("message.rngambling.room.rising_lava_tomb.rules");
            title(Component.translatable("message.rngambling.room.rising_lava_tomb.title").withStyle(ChatFormatting.GOLD),
                    Component.translatable("message.rngambling.room.rising_lava_tomb.subtitle"), 10, 80, 20);
            countdown = new net.minecraft.server.level.ServerBossEvent(java.util.UUID.randomUUID(),
                    Component.translatable("message.rngambling.room.rising_lava_tomb.bar", GRACE / 20),
                    net.minecraft.world.BossEvent.BossBarColor.RED, net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS);
            countdown.addPlayer(player);
            return Vec3.atBottomCenterOf(at(0, 0, 0));
        }

        @Override
        protected void tick(int age) {
            if (age < GRACE) {
                int left = (GRACE - age + 19) / 20;
                countdown.setProgress(1f - age / (float) GRACE);
                if (age % 20 == 0) {
                    countdown.setName(Component.translatable("message.rngambling.room.rising_lava_tomb.bar", left));
                    if (left <= 5) {
                        title(Component.literal(String.valueOf(left)).withStyle(ChatFormatting.RED), 0, 20, 0);
                        level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 1f, 1f);
                    }
                }
                return;
            }
            if (age == GRACE) {
                title(Component.translatable("message.rngambling.room.rising_lava_tomb.rising").withStyle(ChatFormatting.RED), 0, 30, 10);
                countdown.setName(Component.translatable("message.rngambling.room.rising_lava_tomb.rising"));
            }
            // Until the next layer, on the bar.
            countdown.setProgress(1f - ((age - GRACE) % EVERY) / (float) EVERY);
            if ((age - GRACE) % EVERY == 0 && layer < 8) {
                level.playSound(null, at(0, layer, 0), SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.BLOCKS, 1f, 0.8f);
                for (int x = -2; x <= 2; x++) {
                    for (int z = -2; z <= 2; z++) {
                        if (level.getBlockState(at(x, layer, z)).isAir()) {
                            set(x, layer, z, Blocks.LAVA);
                        }
                    }
                }
                layer++;
            }
        }

        @Override
        protected boolean canBreak(BlockPos pos, BlockState state) {
            return pos.equals(weak) || pos.equals(weak.above());
        }

        @Override
        protected int maxTicks() {
            return GRACE + 8 * EVERY + 20 * 30;
        }

        @Override
        protected void onEnd(boolean success) {
            super.onEnd(success);
            if (countdown != null) {
                countdown.removeAllPlayers();
            }
        }
    }

    /** CAT-14: a flooded prismarine chamber with two Elder Guardians; swim to the far exit. */
    public static final class ElderGuardianMonument extends Room {
        @Override
        protected Vec3 build() {
            box(-8, -1, -8, 8, 9, 8, Blocks.PRISMARINE_BRICKS, true);
            box(-7, 0, -7, 7, 8, 7, Blocks.WATER, false);
            for (int x = -6; x <= 6; x += 6) {
                for (int z = -6; z <= 6; z += 6) {
                    set(x, 9, z, Blocks.SEA_LANTERN);
                }
            }
            spawn(EntityType.ELDER_GUARDIAN, -3, 4, 0);
            spawn(EntityType.ELDER_GUARDIAN, 3, 4, 0);
            set(0, 0, -8, Blocks.SEA_LANTERN);
            exit(0, 1, -7);
            return Vec3.atBottomCenterOf(at(0, 0, 6));
        }

        @Override
        protected int maxTicks() {
            return 20 * 60 * 3;
        }

        @Override
        protected void onEnd(boolean success) {
            super.onEnd(success);
            player.removeEffect(MobEffects.MINING_FATIGUE);
        }
    }

    /** CAT-17: a fortress corridor full of blazes and wither skeletons; fight through to the exit. */
    public static final class FortressAmbush extends Room {
        @Override
        protected Vec3 build() {
            box(-3, -1, -2, 3, 5, 42, Blocks.NETHER_BRICKS, true);
            for (int z = 0; z <= 40; z += 4) {
                set(-3, 2, z, Blocks.NETHER_BRICK_FENCE);
                set(3, 2, z, Blocks.NETHER_BRICK_FENCE);
                set(0, 4, z, Blocks.SHROOMLIGHT);
            }
            for (int z = 8; z <= 36; z += 9) {
                spawn(EntityType.BLAZE, random.nextInt(3) - 1, 2, z);
                spawn(EntityType.WITHER_SKELETON, random.nextInt(3) - 1, 0, z + 3);
            }
            exit(0, 0, 41);
            return Vec3.atBottomCenterOf(at(0, 0, 0));
        }

        @Override
        protected float startYaw() {
            return 0f;
        }

        @Override
        protected int maxTicks() {
            return 20 * 60 * 3;
        }
    }

    // ======================= Very bad =======================

    /** VBD-15: a hedge maze under Blindness with zombies about; find the exit. A fresh maze every time. */
    public static final class BlindHedgeMaze extends Room {
        private static final int CELLS = 8, CELL = 3;

        @Override
        protected Vec3 build() {
            int size = CELLS * CELL;
            box(0, -1, 0, size, -1, size, Blocks.GRASS_BLOCK, false);
            BlockState hedge = Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);
            box(0, 0, 0, size, 2, size, hedge, false);
            // Carve paths with a random walk (recursive backtracker): every cell reachable, one way through.
            boolean[][] seen = new boolean[CELLS][CELLS];
            java.util.ArrayDeque<int[]> stack = new java.util.ArrayDeque<>();
            stack.push(new int[] {0, 0});
            seen[0][0] = true;
            carve(0, 0);
            while (!stack.isEmpty()) {
                int[] cell = stack.peek();
                List<int[]> next = new ArrayList<>();
                for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int nx = cell[0] + d[0], nz = cell[1] + d[1];
                    if (nx >= 0 && nz >= 0 && nx < CELLS && nz < CELLS && !seen[nx][nz]) {
                        next.add(new int[] {nx, nz});
                    }
                }
                if (next.isEmpty()) {
                    stack.pop();
                    continue;
                }
                int[] to = next.get(random.nextInt(next.size()));
                seen[to[0]][to[1]] = true;
                carve(to[0], to[1]);
                // The wall between the two cells.
                int wx = Math.min(cell[0], to[0]) * CELL + (cell[0] != to[0] ? CELL : 1);
                int wz = Math.min(cell[1], to[1]) * CELL + (cell[1] != to[1] ? CELL : 1);
                for (int i = 0; i < CELL - 1; i++) {
                    int x = cell[0] != to[0] ? wx : wx + i;
                    int z = cell[1] != to[1] ? wz : wz + i;
                    for (int y = 0; y <= 2; y++) {
                        set(x, y, z, Blocks.AIR);
                    }
                }
                stack.push(to);
            }
            int far = (CELLS - 1) * CELL + 1;
            exit(far, 0, far);
            for (int i = 0; i < 4; i++) {
                spawn(EntityType.ZOMBIE, (2 + random.nextInt(CELLS - 2)) * CELL + 1, 0, (2 + random.nextInt(CELLS - 2)) * CELL + 1);
            }
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20 * 60 * 4));
            return Vec3.atBottomCenterOf(at(1, 0, 1));
        }

        private void carve(int cx, int cz) {
            for (int x = 1; x < CELL; x++) {
                for (int z = 1; z < CELL; z++) {
                    for (int y = 0; y <= 2; y++) {
                        set(cx * CELL + x, y, cz * CELL + z, Blocks.AIR);
                    }
                }
            }
        }

        @Override
        protected float startYaw() {
            return -45f;
        }

        @Override
        protected int maxTicks() {
            return 20 * 60 * 4;
        }

        @Override
        protected void onEnd(boolean success) {
            super.onEnd(success);
            player.removeEffect(MobEffects.BLINDNESS);
        }
    }

    /** VBD-16: three waves of tougher mobs in a walled arena; a small reward at the end. */
    public static final class MobArenaWaves extends Room {
        private int wave;
        private int clearedAt = -1;
        private int rewardAt = -1;

        @Override
        protected Vec3 build() {
            box(-12, -1, -12, 12, 5, 12, Blocks.STONE_BRICKS, true);
            box(-11, 5, -11, 11, 5, 11, Blocks.AIR, false);
            for (int i = -10; i <= 10; i += 5) {
                set(i, 4, -12, Blocks.GLOWSTONE);
                set(i, 4, 12, Blocks.GLOWSTONE);
                set(-12, 4, i, Blocks.GLOWSTONE);
                set(12, 4, i, Blocks.GLOWSTONE);
            }
            spawnWave();
            return Vec3.atBottomCenterOf(at(0, 0, 0));
        }

        private void spawnWave() {
            wave++;
            message("message.rngambling.room.mob_arena_waves.wave" + wave);
            List<EntityType<? extends Mob>> mobs = switch (wave) {
                case 1 -> List.of(EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SKELETON);
                case 2 -> List.of(EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SKELETON, EntityType.SPIDER,
                        EntityType.SPIDER, EntityType.CREEPER);
                default -> List.of(EntityType.VINDICATOR, EntityType.VINDICATOR, EntityType.WITCH, EntityType.WITCH, EntityType.HUSK,
                        EntityType.STRAY, EntityType.STRAY);
            };
            for (int i = 0; i < mobs.size(); i++) {
                double angle = 2 * Math.PI * i / mobs.size();
                Mob mob = spawn(mobs.get(i), 9 * Math.cos(angle), 0, 9 * Math.sin(angle));
                if (mob != null) {
                    mob.setTarget(player);
                }
            }
        }

        @Override
        protected void tick(int age) {
            if (age % 10 != 0 || rewardAt >= 0) {
                if (rewardAt >= 0 && age - rewardAt > 200) {
                    finish(true);
                }
                return;
            }
            boolean clear = level.getEntitiesOfClass(Mob.class, new AABB(centre).inflate(14, 8, 14), m -> m instanceof Enemy && m.isAlive()).isEmpty();
            if (clear && clearedAt < 0) {
                clearedAt = age;
            }
            if (clearedAt >= 0 && age - clearedAt >= 60) {
                clearedAt = -1;
                if (wave < 3) {
                    spawnWave();
                } else {
                    rewardAt = age;
                    message("message.rngambling.room.mob_arena_waves.won");
                    Block.popResource(level, centre, new ItemStack(Items.IRON_INGOT, 12));
                    Block.popResource(level, centre, new ItemStack(Items.EMERALD, 8));
                    Block.popResource(level, centre, new ItemStack(Items.GOLDEN_APPLE, 2));
                }
            }
        }

        @Override
        protected int maxTicks() {
            return 20 * 60 * 5;
        }
    }

    /** VBD-17: stranded on a tiny floating island; bridge to the exit island with the blocks you're given. */
    public static final class SkyblockStrand extends Room {
        private static final int Y = 90;

        @Override
        protected Vec3 build() {
            box(-1, Y - 2, -1, 1, Y - 2, 1, Blocks.DIRT, false);
            box(-1, Y - 1, -1, 1, Y - 1, 1, Blocks.GRASS_BLOCK, false);
            box(-1, Y - 2, -20, 1, Y - 2, -18, Blocks.DIRT, false);
            box(-1, Y - 1, -20, 1, Y - 1, -18, Blocks.GRASS_BLOCK, false);
            exit(0, Y, -19);
            giveSandboxOnly(Items.COBBLESTONE, 24);
            message("message.rngambling.room.skyblock_strand.rules");
            return Vec3.atBottomCenterOf(at(0, Y, 0));
        }

        @Override
        protected double failBelow() {
            return centre.getY() + Y - 10;
        }

        @Override
        protected boolean allowsPlacing() {
            return true;
        }

        @Override
        protected int maxTicks() {
            return 20 * 60 * 3;
        }
    }

    /** VBD-24: the bottom of a deep flooded pit with Drowned and tridents; swim up to the exit. */
    public static final class DrownedPit extends Room {
        private static final int DEPTH = 18;

        @Override
        protected Vec3 build() {
            box(-5, -1, -5, 5, DEPTH + 1, 5, Blocks.MOSSY_COBBLESTONE, true);
            box(-4, 0, -4, 4, DEPTH, 4, Blocks.WATER, false);
            box(-4, DEPTH + 1, -4, 4, DEPTH + 1, 4, Blocks.AIR, false);
            set(0, DEPTH + 1, -5, Blocks.MOSSY_COBBLESTONE);
            // A ledge with the exit at the surface.
            set(3, DEPTH, 3, Blocks.MOSSY_COBBLESTONE);
            exit(3, DEPTH + 1, 3);
            for (int i = 0; i < 4; i++) {
                var drowned = spawn(EntityType.DROWNED, random.nextInt(7) - 3, 0, random.nextInt(7) - 3);
                if (drowned != null) {
                    drowned.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
                }
            }
            return Vec3.atBottomCenterOf(at(-3, 0, -3));
        }

        @Override
        protected int maxTicks() {
            return 20 * 60 * 2;
        }
    }

    /**
     * GOD-31 Casino Invitation: a casino in the Sandbox. Red carpet and gold trim, two slot
     * machines, a roulette table and a blackjack table, and a Casino Dealer to trade with; 8
     * tokens on the house. Nothing in it can be broken or taken. Leave by the way out just
     * outside the front door, whenever (up to 10 minutes): winnings come home with you.
     */
    public static final class CasinoRoom extends Room {
        public static final int TOKENS = 8;

        @Override
        protected Vec3 build() {
            // A 13 x 11 hall (inside), 5 high, door in the south wall.
            box(-7, -1, -6, 7, 5, 6, Blocks.DARK_OAK_PLANKS, true);
            box(-6, -1, -5, 6, -1, 5, Blocks.RED_CONCRETE, false);
            box(-6, 4, -5, 6, 4, 5, Blocks.DARK_OAK_PLANKS, false);
            for (int x = -6; x <= 6; x++) {
                set(x, 3, -6, Blocks.YELLOW_CONCRETE); // gold-coloured trim (nothing worth mining)
                set(x, 3, 6, Blocks.YELLOW_CONCRETE);
            }
            for (int z = -5; z <= 5; z++) {
                set(-7, 3, z, Blocks.YELLOW_CONCRETE);
                set(7, 3, z, Blocks.YELLOW_CONCRETE);
            }
            for (int x : new int[] {-4, 0, 4}) {
                for (int z : new int[] {-3, 2}) {
                    set(x, 4, z, Blocks.SEA_LANTERN);
                }
            }
            // The games: slot machines on the north wall, the tables in the middle.
            BlockState slots = RNGambling.SLOT_MACHINE.get().defaultBlockState()
                    .setValue(rngambling.casino.SlotMachineBlock.FACING, Direction.SOUTH);
            set(-3, 0, -5, slots);
            set(-1, 0, -5, slots);
            set(1, 0, -5, slots);
            set(3, 0, -5, slots);
            set(-4, 0, 0, RNGambling.ROULETTE_TABLE.get());
            set(4, 0, 0, RNGambling.BLACKJACK_TABLE.get());
            // The doorway, and the way out just outside it.
            set(0, 0, 6, Blocks.AIR);
            set(0, 1, 6, Blocks.AIR);
            box(-2, -1, 7, 2, -1, 9, Blocks.RED_CONCRETE, false);
            set(-1, 0, 7, Blocks.LANTERN);
            set(1, 0, 7, Blocks.LANTERN);
            exit(0, 0, 8);
            // The dealer, at the back corner.
            var dealer = spawn(EntityType.VILLAGER, -5, 0, -4);
            if (dealer != null) {
                var profession = level.registryAccess().lookupOrThrow(Registries.VILLAGER_PROFESSION).getOrThrow(RNGambling.DEALER.getKey());
                dealer.setVillagerData(dealer.getVillagerData().withProfession(profession).withLevel(1));
                dealer.setVillagerXp(1); // some experience, so it keeps its job without claiming a workstation
            }
            player.getInventory().placeItemBackInInventory(new ItemStack(RNGambling.CASINO_TOKEN.get(), TOKENS));
            message("message.rngambling.room.casino.rules");
            return Vec3.atBottomCenterOf(at(0, 0, 4));
        }

        @Override
        protected float startYaw() {
            return 180f; // facing into the hall (north)
        }

        @Override
        protected int maxTicks() {
            return 20 * 60 * 10;
        }
    }

    // ======================= Very good =======================

    /** VGD-09: a gold-lined vault of loot chests (only the chests can be broken); 40 seconds to grab what you can. */
    public static final class TreasureVault extends Room {
        @Override
        protected Vec3 build() {
            box(-6, -1, -6, 6, 6, 6, Blocks.GOLD_BLOCK, true);
            for (int x = -4; x <= 4; x += 4) {
                set(x, 6, 0, Blocks.GLOWSTONE);
            }
            String[] tables = {"minecraft:chests/simple_dungeon", "minecraft:chests/desert_pyramid", "minecraft:chests/bastion_treasure",
                    "minecraft:chests/end_city_treasure", "minecraft:chests/buried_treasure", "minecraft:chests/stronghold_corridor"};
            for (int i = 0; i < tables.length; i++) {
                chest(-5 + 2 * i, 0, -5, tables[i]);
            }
            exit(0, 0, 5);
            startTimer("message.rngambling.room.timer");
            return Vec3.atBottomCenterOf(at(0, 0, 2));
        }

        @Override
        protected void tick(int age) {
            updateTimer(age);
        }

        @Override
        protected int maxTicks() {
            return 20 * 40;
        }

        @Override
        protected boolean canBreak(BlockPos pos, BlockState state) {
            return state.is(Blocks.CHEST);
        }
    }

    /** VGD-10: a parkour course in the sky with a reward chest at the end. Fall and you go home with nothing. */
    public static final class ParkourCourse extends Room {
        private static final int Y = 60;

        @Override
        protected Vec3 build() {
            box(-1, Y - 1, -1, 1, Y - 1, 1, Blocks.QUARTZ_BLOCK, false);
            int x = 0, y = Y - 1, z = 0;
            for (int jump = 0; jump < 14; jump++) {
                z -= 2 + random.nextInt(2);
                x += random.nextInt(3) - 1;
                y += random.nextInt(3) == 0 ? 1 : 0;
                set(x, y, z, jump % 3 == 2 ? Blocks.GOLD_BLOCK : Blocks.QUARTZ_BLOCK);
            }
            z -= 3;
            box(x - 1, y, z - 2, x + 1, y, z, Blocks.QUARTZ_BLOCK, false);
            chest(x, y + 1, z - 2, LEGENDARY_CHEST);
            exit(x + 1, y + 1, z - 1);
            message("message.rngambling.room.parkour_course.rules");
            return Vec3.atBottomCenterOf(at(0, Y, 0));
        }

        @Override
        protected double failBelow() {
            return centre.getY() + Y - 8;
        }

        @Override
        protected boolean keepsLoot(boolean success) {
            return success;
        }

        @Override
        protected boolean canBreak(BlockPos pos, BlockState state) {
            return state.is(Blocks.CHEST);
        }

        @Override
        protected int maxTicks() {
            return 20 * 60 * 4;
        }
    }

    /**
     * VGD-11: a lever puzzle. Each lever flips its lamp and the lamps beside it; light every lamp to
     * open the door to the reward chamber.
     */
    public static final class PuzzleVault extends Room {
        private static final int LAMPS = 5;
        private final boolean[] levers = new boolean[LAMPS];
        private final boolean[] lit = new boolean[LAMPS];
        private boolean solved;

        @Override
        protected Vec3 build() {
            box(-6, -1, -6, 6, 6, 10, Blocks.QUARTZ_BLOCK, true);
            box(-5, 0, 5, 5, 5, 5, Blocks.QUARTZ_BLOCK, false); // the wall between the room and the reward chamber
            set(0, 0, 5, Blocks.IRON_BLOCK);
            set(0, 1, 5, Blocks.IRON_BLOCK);
            chest(0, 0, 8, LEGENDARY_CHEST);
            exit(2, 0, 8);
            set(-3, 5, 0, Blocks.GLOWSTONE);
            set(3, 5, 0, Blocks.GLOWSTONE);
            // Levers on the north wall, lamps high above them (apart, so the levers don't light them).
            for (int i = 0; i < LAMPS; i++) {
                int x = -4 + 2 * i;
                set(x, 1, -5, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.WALL)
                        .setValue(LeverBlock.FACING, Direction.SOUTH));
            }
            // A start that can be solved: some lamps lit, as if a few levers had been pulled.
            for (int presses = 2 + random.nextInt(3); presses > 0; presses--) {
                press(random.nextInt(LAMPS));
            }
            if (allLit()) {
                press(2);
            }
            drawLamps();
            message("message.rngambling.room.puzzle_vault.rules");
            return Vec3.atBottomCenterOf(at(0, 0, -2));
        }

        private void press(int i) {
            for (int j = i - 1; j <= i + 1; j++) {
                if (j >= 0 && j < LAMPS) {
                    lit[j] = !lit[j];
                }
            }
        }

        private boolean allLit() {
            for (boolean b : lit) {
                if (!b) {
                    return false;
                }
            }
            return true;
        }

        private void drawLamps() {
            for (int i = 0; i < LAMPS; i++) {
                set(-4 + 2 * i, 4, -5, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, lit[i]));
            }
        }

        @Override
        protected void tick(int age) {
            if (solved) {
                return;
            }
            boolean changed = false;
            for (int i = 0; i < LAMPS; i++) {
                BlockState lever = level.getBlockState(at(-4 + 2 * i, 1, -5));
                boolean on = lever.hasProperty(LeverBlock.POWERED) && lever.getValue(LeverBlock.POWERED);
                if (on != levers[i]) {
                    levers[i] = on;
                    press(i);
                    changed = true;
                }
            }
            if (changed) {
                drawLamps();
                if (allLit()) {
                    solved = true;
                    set(0, 0, 5, Blocks.AIR);
                    set(0, 1, 5, Blocks.AIR);
                    level.playSound(null, at(0, 1, 5), SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0f, 1.0f);
                    message("message.rngambling.room.puzzle_vault.solved");
                }
            }
        }

        @Override
        protected boolean canBreak(BlockPos pos, BlockState state) {
            return state.is(Blocks.CHEST);
        }

        @Override
        protected int maxTicks() {
            return 20 * 60 * 4;
        }
    }

    // ======================= Legendary =======================

    /** LEG-06: a tropical island scattered with loot chests; three minutes to explore. */
    public static final class TreasureIsland extends Room {
        @Override
        protected Vec3 build() {
            for (int x = -15; x <= 15; x++) {
                for (int z = -15; z <= 15; z++) {
                    double r = Math.sqrt(x * x + z * z);
                    if (r <= 9) {
                        box(x, 0, z, x, r <= 6 ? 2 : 1, z, Blocks.SAND, false);
                    } else if (r <= 13.5) {
                        set(x, 0, z, Blocks.SAND);
                        set(x, 1, z, Blocks.WATER);
                    } else if (r <= 14.6) {
                        box(x, 0, z, x, 1, z, Blocks.SAND, false);
                    }
                }
            }
            for (int[] palm : new int[][] {{-4, -3}, {3, -5}, {5, 3}}) {
                for (int y = 3; y <= 7; y++) {
                    set(palm[0], y, palm[1], Blocks.JUNGLE_LOG);
                }
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    for (int i = 1; i <= 2; i++) {
                        set(palm[0] + d.getStepX() * i, 7, palm[1] + d.getStepZ() * i,
                                Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true));
                    }
                }
            }
            chest(-2, 3, 2, "minecraft:chests/buried_treasure");
            chest(4, 3, -1, "minecraft:chests/shipwreck_treasure");
            chest(-6, 2, -1, "minecraft:chests/buried_treasure");
            chest(1, 3, -4, LEGENDARY_CHEST);
            startTimer("message.rngambling.room.timer");
            return Vec3.atBottomCenterOf(at(0, 3, 0));
        }

        @Override
        protected void tick(int age) {
            updateTimer(age);
        }

        @Override
        protected boolean canBreak(BlockPos pos, BlockState state) {
            return true; // a holiday: dig, chop, take it all
        }

        @Override
        protected int maxTicks() {
            return 20 * 60 * 3;
        }
    }

    /** LEG-07 accepted: a boss fight (an illager warlord on a ravager, with guards); a massive reward if won. */
    public static final class BossBounty extends Room {
        private boolean rewarded;
        private int wonAt = -1;

        @Override
        protected Vec3 build() {
            box(-12, -1, -12, 12, 6, 12, Blocks.DARK_OAK_PLANKS, true);
            box(-11, 6, -11, 11, 6, 11, Blocks.AIR, false);
            for (int i = -10; i <= 10; i += 5) {
                set(i, 3, -12, Blocks.LANTERN);
                set(i, 3, 12, Blocks.LANTERN);
            }
            var ravager = spawn(EntityType.RAVAGER, 0, 0, -7);
            var evoker = spawn(EntityType.EVOKER, 0, 0, -7);
            if (ravager != null && evoker != null) {
                evoker.startRiding(ravager, true, false);
                evoker.setCustomName(net.minecraft.network.chat.Component.translatable("message.rngambling.room.boss_bounty.boss"));
                ravager.setTarget(player);
            }
            for (int i = -2; i <= 2; i += 2) {
                spawn(EntityType.VINDICATOR, i * 2, 0, -5);
            }
            return Vec3.atBottomCenterOf(at(0, 0, 8));
        }

        @Override
        protected void tick(int age) {
            if (age % 10 != 0) {
                return;
            }
            boolean clear = level.getEntitiesOfClass(Mob.class, new AABB(centre).inflate(14, 8, 14), m -> m instanceof Enemy && m.isAlive()).isEmpty();
            if (clear && !rewarded) {
                rewarded = true;
                wonAt = age;
                message("message.rngambling.room.boss_bounty.won");
                Block.popResource(level, centre, new ItemStack(Items.NETHERITE_INGOT, 2));
                Outcomes.playTier(level, centre, player, 100, 0, Tier.LEGENDARY);
                Outcomes.playTier(level, centre, player, 100, 0, Tier.LEGENDARY);
            }
            if (wonAt >= 0 && age - wonAt > 300) {
                finish(true);
            }
        }

        @Override
        protected int maxTicks() {
            return 20 * 60 * 5;
        }
    }

    /** For tests and the screenshot run: one of each room. */
    public static List<java.util.function.Supplier<Room>> all() {
        return List.of(WitherArena::new, EmpoweredWither::new, WardensHollow::new, VoidPlatform::new, RisingLavaTomb::new,
                ElderGuardianMonument::new, FortressAmbush::new, BlindHedgeMaze::new, MobArenaWaves::new, SkyblockStrand::new,
                DrownedPit::new, TreasureVault::new, ParkourCourse::new, PuzzleVault::new, TreasureIsland::new, BossBounty::new,
                CasinoRoom::new);
    }

}
