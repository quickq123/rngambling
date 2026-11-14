package rngambling.outcome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
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

    /** CAT-03: a dark sculk cavern; sneak past the shriekers to the exit without waking a Warden. */
    public static final class WardensHollow extends Room {
        @Override
        protected Vec3 build() {
            box(-4, -1, -2, 4, 6, 38, Blocks.DEEPSLATE, true);
            for (int z = -1; z <= 37; z++) {
                for (int x = -3; x <= 3; x++) {
                    set(x, -1, z, random.nextInt(3) == 0 ? Blocks.SCULK : Blocks.DEEPSLATE);
                    if (random.nextInt(14) == 0 && Math.abs(x) >= 2) {
                        set(x, 0, z, Blocks.SCULK_SENSOR);
                    }
                }
                if (z % 6 == 3) {
                    set(0, 0, z, Blocks.SCULK_SHRIEKER.defaultBlockState().setValue(BlockStateProperties.CAN_SUMMON, true));
                }
            }
            exit(0, 0, 36);
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 60 * 4));
            return Vec3.atBottomCenterOf(at(0, 0, 0));
        }

        @Override
        protected float startYaw() {
            return 0f; // facing down the cavern (south)
        }

        @Override
        protected int maxTicks() {
            return 20 * 60 * 4;
        }

        @Override
        protected void onEnd(boolean success) {
            super.onEnd(success);
            player.removeEffect(MobEffects.DARKNESS);
        }
    }

    /** CAT-07: a platform above the void, crumbling; survive 30 seconds and an exit appears. Falling is failure. */
    public static final class VoidPlatform extends Room {
        private static final int Y = 100;
        private final List<BlockPos> platform = new ArrayList<>();
        private boolean exitOpen;

        @Override
        protected Vec3 build() {
            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 3; z++) {
                    set(x, Y, z, Blocks.STONE);
                    if (x != 0 || z != 0) {
                        platform.add(at(x, Y, z));
                    }
                }
            }
            Collections.shuffle(platform, new java.util.Random(random.nextLong()));
            giveSandboxOnly(Items.COBBLESTONE, 8);
            message("message.rngambling.room.void_platform.rules");
            return Vec3.atBottomCenterOf(at(0, Y + 1, 0));
        }

        @Override
        protected void tick(int age) {
            if (age % 10 == 9 && !platform.isEmpty() && !exitOpen) {
                BlockPos crumble = platform.removeLast();
                level.destroyBlock(crumble, false);
            }
            if (age == 20 * 30) {
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
            return 20 * 90;
        }
    }

    /**
     * CAT-08: a sealed box filling with lava a layer at a time. Among the cracked bricks in the walls,
     * one pair (two high, at the floor) breaks, and leads out.
     */
    public static final class RisingLavaTomb extends Room {
        private BlockPos weak;
        private int layer;

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
            return Vec3.atBottomCenterOf(at(0, 0, 0));
        }

        @Override
        protected void tick(int age) {
            // A grace of 8 seconds, then a layer every 4.
            if (age >= 160 && (age - 160) % 80 == 0 && layer < 8) {
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
            return 20 * 90;
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

    // ======================= Very good =======================

    /** VGD-09: a gold-lined vault of loot chests; 60 seconds to grab what you can. */
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
            return 20 * 60;
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
                DrownedPit::new, TreasureVault::new, ParkourCourse::new, PuzzleVault::new, TreasureIsland::new, BossBounty::new);
    }

}
