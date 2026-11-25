package rngambling.outcome;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;
import rngambling.RNGambling;

/**
 * The skill rooms: Sandbox challenges that score the player instead of rewarding or punishing
 * them outright. Each ends with a {@link Medal}: gold, silver, bronze or none, and the medal
 * decides the prize ({@link #prize}). Falling off or running out of time costs nothing but the
 * chance: nothing is taken, and in these rooms falls and ender pearls don't hurt.
 *
 * <p>Timed courses (Ice Run, Bounce House, Crumbling Path, the Speedrun Course) start their clock
 * when the player first moves away from the start, and show it over the hotbar.
 */
public final class SkillRooms {
    private SkillRooms() {
    }

    public enum Medal {
        NONE(ChatFormatting.GRAY), BRONZE(ChatFormatting.GOLD), SILVER(ChatFormatting.WHITE), GOLD(ChatFormatting.YELLOW);

        public final ChatFormatting colour;

        Medal(ChatFormatting colour) {
            this.colour = colour;
        }

        Component label() {
            return Component.translatable("message.rngambling.room.medal." + name().toLowerCase(java.util.Locale.ROOT)).withStyle(colour, ChatFormatting.BOLD);
        }
    }

    /** What each medal wins. */
    public static List<ItemStack> prize(Medal medal) {
        return switch (medal) {
            case GOLD -> List.of(new ItemStack(RNGambling.BLESSED_LUCKY_BLOCK_ITEM.get()), new ItemStack(RNGambling.LUCKY_BLOCK_ITEM.get(), 2),
                    new ItemStack(RNGambling.CASINO_TOKEN.get(), 16));
            case SILVER -> List.of(new ItemStack(RNGambling.LUCKY_BLOCK_ITEM.get(), 2), new ItemStack(RNGambling.CASINO_TOKEN.get(), 8));
            case BRONZE -> List.of(new ItemStack(RNGambling.LUCKY_BLOCK_ITEM.get()), new ItemStack(RNGambling.CASINO_TOKEN.get(), 4));
            case NONE -> List.of();
        };
    }

    /** A room that ends in a medal. */
    public abstract static class SkillRoom extends Room {
        private Medal medal;
        /** The age the clock started at, or -1 before the player has moved off the start. */
        protected int clockStart = -1;
        protected int now;

        /** The medal won, or null while the room is going. */
        public Medal medal() {
            return medal;
        }

        /** Ends the room with a medal: the prize goes into the player's inventory (anything that won't fit drops, and comes home). */
        protected void award(Medal won) {
            if (medal != null || finished()) {
                return;
            }
            medal = won;
            List<ItemStack> prize = prize(won);
            for (ItemStack stack : prize) {
                player.getInventory().placeItemBackInInventory(stack.copy());
            }
            if (won == Medal.NONE) {
                player.sendSystemMessage(Component.translatable("message.rngambling.room.medal.none_line").withStyle(ChatFormatting.GRAY));
            } else {
                Component list = Component.empty();
                for (int i = 0; i < prize.size(); i++) {
                    if (i > 0) {
                        list = list.copy().append(i == prize.size() - 1 ? " and " : ", ");
                    }
                    list = list.copy().append(Component.literal(prize.get(i).getCount() + "× ")).append(prize.get(i).getHoverName());
                }
                player.sendSystemMessage(Component.translatable("message.rngambling.room.medal.won", won.label(), list).withStyle(ChatFormatting.GREEN));
                title(won.label(), 5, 40, 10);
                level.playSound(null, player.blockPosition(), won == Medal.GOLD ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.PLAYER_LEVELUP,
                        SoundSource.PLAYERS, 1.0f, 1.0f);
            }
            finish(won != Medal.NONE);
        }

        @Override
        protected boolean preventsDamage(DamageSource source) {
            return source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.ENDER_PEARL);
        }

        @Override
        protected void tick(int age) {
            now = age;
            skillTick(age);
        }

        protected void skillTick(int age) {
        }

        // ---- the clock, for timed courses ----

        /** Starts the clock once the player has left the start, and shows it. */
        protected void runClock(int age, int goldTicks, int silverTicks) {
            Vec3 start = startPos();
            if (clockStart < 0 && start != null && player.position().subtract(start).horizontalDistance() > 1.5) {
                clockStart = age;
            }
            if (age % 2 == 0) {
                player.sendOverlayMessage(Component.translatable("message.rngambling.room.clock", seconds(clockStart < 0 ? 0 : age - clockStart),
                        seconds(goldTicks), seconds(silverTicks)).withStyle(ChatFormatting.AQUA));
            }
        }

        /** The medal for finishing in this time. */
        protected Medal byTime(int goldTicks, int silverTicks) {
            int ticks = clockStart < 0 ? 0 : now - clockStart;
            return ticks <= goldTicks ? Medal.GOLD : ticks <= silverTicks ? Medal.SILVER : Medal.BRONZE;
        }

        static String seconds(int ticks) {
            return String.format(java.util.Locale.ROOT, "%.1f", ticks / 20.0);
        }

        protected void sound(SoundEvent sound, float pitch) {
            level.playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, 1.0f, pitch);
        }
    }

    /** A timed course in the sky: the way out at the end wins a medal by time; falling ends it with none. */
    abstract static class Course extends SkillRoom {
        protected static final int Y = 60;
        protected int goldTicks, silverTicks;

        /** Sets the medal times from the course's length: gold at {@code gold} blocks a second, silver at {@code silver}. */
        protected void par(double blocks, double gold, double silver) {
            goldTicks = (int) Math.ceil(blocks / gold * 20);
            silverTicks = (int) Math.ceil(blocks / silver * 20);
        }

        @Override
        protected void skillTick(int age) {
            runClock(age, goldTicks, silverTicks);
        }

        @Override
        protected void onExit() {
            award(byTime(goldTicks, silverTicks));
        }

        @Override
        protected double failBelow() {
            return centre.getY() + Y - 10;
        }

        @Override
        protected int maxTicks() {
            return 20 * 60 * 3;
        }

        /** A flat square of blocks (the start and the finish). */
        protected void pad(int x, int y, int z, int half, Block block) {
            box(x - half, y, z - half, x + half, y, z + half, block, false);
        }
    }

    // ======================= Dropper =======================

    /**
     * NEU-36 Dropper: a 100-block shaft with five floors across it, each with one opening
     * (narrower as you go), and water at the bottom. Steer through the openings; landing on a
     * floor ends the drop. Medal by floors passed: all and the water, gold; 3, silver; 2, bronze.
     */
    public static final class Dropper extends SkillRoom {
        static final int TOP = 104, HALF = 4, COUNTDOWN = 60;
        static final int[] FLOORS = {86, 68, 50, 32, 14};
        static final int[][] OPENINGS = {{3, 3}, {3, 3}, {3, 2}, {2, 3}, {2, 2}};
        private static final Block[] WALLS = {Blocks.LIGHT_BLUE_CONCRETE, Blocks.LIME_CONCRETE, Blocks.YELLOW_CONCRETE,
                Blocks.ORANGE_CONCRETE, Blocks.RED_CONCRETE, Blocks.PURPLE_CONCRETE};
        /** Each floor's opening: its lowest x and z corner (relative). */
        final int[][] holes = new int[FLOORS.length][2];

        @Override
        protected Vec3 build() {
            for (int y = 0; y <= TOP; y++) {
                Block wall = WALLS[Math.min(WALLS.length - 1, (TOP - y) / 18)];
                for (int i = -HALF - 1; i <= HALF + 1; i++) {
                    Block here = y % 6 == 3 && Math.abs(i) == 2 ? Blocks.SEA_LANTERN : wall;
                    set(i, y, -HALF - 1, here);
                    set(i, y, HALF + 1, here);
                    set(-HALF - 1, y, i, here);
                    set(HALF + 1, y, i, here);
                }
            }
            box(-HALF, 0, -HALF, HALF, 1, HALF, Blocks.WATER, false);
            // Each opening within two blocks of the one above it, so there's always time to steer there.
            int cx = 0, cz = 0;
            for (int f = 0; f < FLOORS.length; f++) {
                int w = OPENINGS[f][0], d = OPENINGS[f][1];
                cx = Math.clamp(cx + random.nextInt(5) - 2, -HALF, HALF - w + 1);
                cz = Math.clamp(cz + random.nextInt(5) - 2, -HALF, HALF - d + 1);
                holes[f][0] = cx;
                holes[f][1] = cz;
                for (int x = -HALF; x <= HALF; x++) {
                    for (int z = -HALF; z <= HALF; z++) {
                        boolean hole = x >= cx && x < cx + w && z >= cz && z < cz + d;
                        if (!hole) {
                            set(x, FLOORS[f], z, Blocks.SMOOTH_STONE);
                        }
                    }
                }
            }
            set(0, TOP - 3, 0, Blocks.GLASS);
            message("message.rngambling.room.dropper.rules");
            return Vec3.atBottomCenterOf(at(0, TOP - 2, 0));
        }

        @Override
        protected void skillTick(int age) {
            if (age < COUNTDOWN) {
                if (age % 20 == 0) {
                    title(Component.literal(String.valueOf(3 - age / 20)).withStyle(ChatFormatting.AQUA), 0, 20, 0);
                    sound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f);
                }
                return;
            }
            if (age == COUNTDOWN) {
                set(0, TOP - 3, 0, Blocks.AIR);
                title(Component.translatable("message.rngambling.room.dropper.go").withStyle(ChatFormatting.GOLD), 0, 20, 10);
                sound(SoundEvents.NOTE_BLOCK_PLING.value(), 2f);
            }
            if (player.isInWater()) {
                award(Medal.GOLD);
            } else if (player.onGround() && player.getY() < centre.getY() + TOP - 4) {
                int passed = passed(player.getY() - centre.getY());
                player.sendSystemMessage(Component.translatable("message.rngambling.room.dropper.splat", passed, FLOORS.length)
                        .withStyle(ChatFormatting.RED));
                sound(SoundEvents.SLIME_SQUISH, 0.6f);
                award(passed >= 3 ? Medal.SILVER : passed >= 2 ? Medal.BRONZE : Medal.NONE);
            }
        }

        /** How many floors are above this height. */
        static int passed(double y) {
            int passed = 0;
            for (int floor : FLOORS) {
                if (y < floor + 0.5) {
                    passed++;
                }
            }
            return passed;
        }

        @Override
        protected int maxTicks() {
            return 20 * 45;
        }
    }

    // ======================= Ice Run =======================

    /**
     * NEU-37 Ice Run: a winding course of packed ice, then blue ice, with turns and gaps to jump.
     * Sliding is the skill: overshoot a turn and you're off.
     */
    public static final class IceRun extends Course {
        @Override
        protected Vec3 build() {
            pad(0, Y - 1, 0, 1, Blocks.SNOW_BLOCK);
            int x = 0, z = -2;
            double length = 0;
            for (int segment = 0; segment < 9; segment++) {
                Block ice = segment < 4 ? Blocks.PACKED_ICE : Blocks.BLUE_ICE;
                if (segment % 3 == 2) {
                    // A turn: a 3-wide strip sideways (no gap), then on again.
                    int dir = random.nextBoolean() ? 1 : -1;
                    int len = 4 + random.nextInt(3);
                    for (int i = 0; i < len; i++) {
                        box(x + dir * i, Y - 1, z - 1, x + dir * i, Y - 1, z + 1, ice, false);
                    }
                    x += dir * (len - 1);
                    z -= 2;
                    length += len + 2;
                    continue;
                }
                int len = 4 + random.nextInt(4);
                for (int i = 0; i < len; i++) {
                    box(x - 1, Y - 1, z - i, x + 1, Y - 1, z - i, ice, false);
                }
                z -= len;
                int gap = segment == 0 ? 1 : 2 + random.nextInt(2);
                z -= gap;
                length += len + gap;
            }
            pad(x, Y - 1, z - 1, 1, Blocks.SNOW_BLOCK);
            exit(x, Y, z - 2);
            par(length, 6.5, 4.0);
            message("message.rngambling.room.ice_run.rules");
            return Vec3.atBottomCenterOf(at(0, Y, 0));
        }
    }

    // ======================= Bounce House =======================

    /**
     * NEU-38 Bounce House: honey platforms stepping down through the sky, with a slime pad below
     * each gap. Drop onto the slime (not sneaking) and the bounce carries you up onto the next
     * platform, which is a little lower.
     */
    public static final class BounceHouse extends Course {
        static final int HOPS = 8, TOP = Y + 18;

        @Override
        protected Vec3 build() {
            pad(0, TOP, 0, 1, Blocks.STONE);
            int x = 0, z = 0, h = TOP;
            for (int hop = 0; hop < HOPS; hop++) {
                int dx = random.nextInt(3) - 1;
                // The slime pad: one block's gap past the platform, four below it.
                pad(x + dx, h - 4, z - 4, 1, Blocks.SLIME_BLOCK);
                x += dx;
                z -= 9;
                h -= 2;
                pad(x, h, z, 1, hop == HOPS - 1 ? Blocks.STONE : Blocks.HONEY_BLOCK);
            }
            exit(x, h + 1, z);
            par(HOPS * 9, 3.6, 2.4);
            message("message.rngambling.room.bounce_house.rules");
            return Vec3.atBottomCenterOf(at(0, TOP + 1, 0));
        }

        @Override
        protected double failBelow() {
            return centre.getY() + TOP - 2 * HOPS - 12;
        }
    }

    // ======================= Crumbling Path =======================

    /**
     * NEU-39 Crumbling Path: paths of stone bricks that crack the moment you step on them and fall
     * away a second later, with easy jumps on solid quartz between them. Keep moving, but mind the
     * turns and the jumps.
     */
    public static final class CrumblingPath extends Course {
        public static final int CRACK = 12;
        /** Path blocks a player has stepped on, and the age they fall at. */
        private final Map<BlockPos, Integer> crumbling = new HashMap<>();
        private final java.util.Set<BlockPos> path = new java.util.HashSet<>();

        @Override
        protected Vec3 build() {
            pad(0, Y - 1, 0, 1, Blocks.QUARTZ_BLOCK);
            int x = 0, y = Y - 1, z = -2;
            double length = 0;
            for (int section = 0; section < 4; section++) {
                // A crumbling path with a turn or two.
                int legs = 2 + random.nextInt(2);
                for (int leg = 0; leg < legs; leg++) {
                    boolean sideways = leg % 2 == 1;
                    int dir = random.nextBoolean() ? 1 : -1;
                    int len = sideways ? 3 + random.nextInt(2) : 4 + random.nextInt(3);
                    for (int i = 0; i < len; i++) {
                        if (sideways) {
                            x += dir;
                        } else if (i > 0 || leg > 0) {
                            z -= 1;
                        }
                        crumble(x, y, z);
                    }
                    length += len;
                }
                // Then a few easy jumps on solid blocks.
                int jumps = 2 + random.nextInt(3);
                for (int j = 0; j < jumps; j++) {
                    z -= 2 + random.nextInt(2);
                    x += random.nextInt(3) - 1;
                    y += random.nextInt(4) == 0 ? 1 : 0;
                    set(x, y, z, Blocks.QUARTZ_BLOCK);
                    length += 3;
                }
                z -= 2;
            }
            pad(x, y, z - 1, 1, Blocks.QUARTZ_BLOCK);
            exit(x, y + 1, z - 2);
            par(length, 4.5, 3.0);
            message("message.rngambling.room.crumbling_path.rules");
            return Vec3.atBottomCenterOf(at(0, Y, 0));
        }

        private void crumble(int x, int y, int z) {
            set(x, y, z, Blocks.STONE_BRICKS);
            path.add(at(x, y, z));
        }

        @Override
        protected void skillTick(int age) {
            super.skillTick(age);
            if (player.onGround()) {
                // The blocks under the player's feet (up to four, at the corners of their footprint).
                var box = player.getBoundingBox();
                for (double px : new double[] {box.minX + 0.01, box.maxX - 0.01}) {
                    for (double pz : new double[] {box.minZ + 0.01, box.maxZ - 0.01}) {
                        BlockPos under = BlockPos.containing(px, box.minY - 0.05, pz);
                        if (path.remove(under)) {
                            crumbling.put(under, age + CRACK);
                            level.setBlock(under, Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), Block.UPDATE_CLIENTS);
                            level.playSound(null, under, SoundEvents.STONE_HIT, SoundSource.BLOCKS, 0.8f, 0.7f);
                        }
                    }
                }
            }
            crumbling.entrySet().removeIf(entry -> {
                if (age < entry.getValue()) {
                    return false;
                }
                level.destroyBlock(entry.getKey(), false);
                return true;
            });
        }

        /** For tests: whether a path block is still whole. */
        public boolean whole(BlockPos pos) {
            return path.contains(pos);
        }
    }

    // ======================= Ender Pearl Hop =======================

    /**
     * NEU-40 Ender Pearl Hop: floating islands too far apart to jump, and a handful of ender
     * pearls (three spare). Medal by pearls left at the way out: three, gold; one or two, silver;
     * none, bronze.
     */
    public static final class EnderPearlHop extends Course {
        static final int ISLANDS = 6, SPARE = 3;

        @Override
        protected Vec3 build() {
            island(0, Y - 1, 0);
            int x = 0, y = Y - 1, z = 0;
            for (int i = 0; i < ISLANDS; i++) {
                z -= 13 + random.nextInt(5);
                x = Math.clamp(x + random.nextInt(13) - 6, -12, 12);
                y = Math.clamp(y + random.nextInt(7) - 3, Y - 6, Y + 6);
                island(x, y, z);
            }
            exit(x, y + 1, z);
            giveSandboxOnly(Items.ENDER_PEARL, ISLANDS + SPARE);
            message("message.rngambling.room.ender_pearl_hop.rules");
            return Vec3.atBottomCenterOf(at(0, Y, 0));
        }

        private void island(int x, int y, int z) {
            box(x - 1, y - 1, z - 1, x + 1, y - 1, z + 1, Blocks.DIRT, false);
            box(x - 1, y, z - 1, x + 1, y, z + 1, Blocks.MOSS_BLOCK, false);
            set(x, y - 2, z, Blocks.DIRT);
        }

        @Override
        protected void skillTick(int age) {
            if (age % 10 == 0) {
                player.sendOverlayMessage(Component.translatable("message.rngambling.room.ender_pearl_hop.left", pearls()).withStyle(ChatFormatting.DARK_AQUA));
            }
        }

        int pearls() {
            int count = 0;
            var inventory = player.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                if (inventory.getItem(slot).is(Items.ENDER_PEARL) && isSandboxOnly(inventory.getItem(slot))) {
                    count += inventory.getItem(slot).getCount();
                }
            }
            return count;
        }

        @Override
        protected void onExit() {
            int left = pearls();
            award(left >= SPARE ? Medal.GOLD : left >= 1 ? Medal.SILVER : Medal.BRONZE);
        }

        @Override
        protected double failBelow() {
            return centre.getY() + Y - 20;
        }
    }

    // ======================= Tower Climb =======================

    /**
     * NEU-41 Tower Climb: up the inside of a tower by jumps along the walls, ladders, vines and
     * scaffolding. A fall only costs the climb back (falls don't hurt here). Medal by the highest
     * point reached in three minutes: a third of the way, bronze; two thirds, silver; the way out
     * at the top, gold.
     */
    public static final class TowerClimb extends SkillRoom {
        static final int HALF = 4, TARGET = 44;
        /** The cells along the inside of the walls, in order around. */
        private static final List<int[]> RING = new ArrayList<>();
        static {
            for (int x = -HALF; x < HALF; x++) RING.add(new int[] {x, -HALF, 0});
            for (int z = -HALF; z < HALF; z++) RING.add(new int[] {HALF, z, 1});
            for (int x = HALF; x > -HALF; x--) RING.add(new int[] {x, HALF, 2});
            for (int z = HALF; z > -HALF; z--) RING.add(new int[] {-HALF, z, 3});
        }

        private int top, best;

        @Override
        protected Vec3 build() {
            int wallTop = TARGET + 8;
            box(-HALF - 1, 0, -HALF - 1, HALF + 1, wallTop, HALF + 1, Blocks.STONE_BRICKS, true);
            box(-HALF, wallTop, -HALF, HALF, wallTop, HALF, Blocks.AIR, false);
            box(-HALF, 0, -HALF, HALF, 0, HALF, Blocks.MOSS_BLOCK, false);
            for (int y = 4; y < wallTop; y += 6) {
                for (int[] lamp : new int[][] {{0, -HALF - 1}, {0, HALF + 1}, {-HALF - 1, 0}, {HALF + 1, 0}}) {
                    set(lamp[0], y, lamp[1], Blocks.GLOWSTONE);
                }
            }
            int i = 1, p = 0, step = 0;
            set(RING.get(i)[0], 1, RING.get(i)[1], Blocks.OAK_PLANKS);
            p = 1;
            while (p < TARGET) {
                if (step % 2 == 0) {
                    for (int j = 0; j < 3 && p < TARGET; j++) {
                        i += 2;
                        p += 1;
                        int[] cell = cell(i);
                        set(cell[0], p, cell[1], Blocks.OAK_PLANKS);
                    }
                } else {
                    i += 1;
                    if (corner(i)) {
                        i += 1;
                        int[] bridge = cell(i - 1);
                        set(bridge[0], p, bridge[1], Blocks.OAK_PLANKS);
                    }
                    int[] cell = cell(i);
                    climb(cell, p, (step / 2) % 3);
                    i += 1;
                    if (corner(i)) {
                        int[] bridge = cell(i);
                        set(bridge[0], p + 4, bridge[1], Blocks.OAK_PLANKS);
                        i += 1;
                    }
                    p += 4;
                    int[] landing = cell(i);
                    set(landing[0], p, landing[1], Blocks.OAK_PLANKS);
                }
                step++;
            }
            top = p;
            // The top: a floor across the tower inside the path, and the way out on it.
            box(-HALF + 1, p, -HALF + 1, HALF - 1, p, HALF - 1, Blocks.OAK_PLANKS, false);
            exit(0, p + 1, 0);
            message("message.rngambling.room.tower_climb.rules");
            return Vec3.atBottomCenterOf(at(0, 1, 0));
        }

        private static int[] cell(int i) {
            return RING.get(Math.floorMod(i, RING.size()));
        }

        private static boolean corner(int i) {
            int[] c = cell(i);
            return Math.abs(c[0]) == HALF && Math.abs(c[1]) == HALF;
        }

        /** A five-high ladder, vine or scaffolding at a cell, from just above {@code p}. */
        private void climb(int[] cell, int p, int kind) {
            Direction away = switch (cell[2]) {
                case 0 -> Direction.SOUTH; // wall to the north
                case 1 -> Direction.WEST;
                case 2 -> Direction.NORTH;
                default -> Direction.EAST;
            };
            if (kind == 2) {
                set(cell[0], p, cell[1], Blocks.OAK_PLANKS);
            }
            for (int y = p + 1; y <= p + 5; y++) {
                BlockState state = switch (kind) {
                    case 0 -> Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, away);
                    case 1 -> Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(away.getOpposite()), true);
                    default -> Blocks.SCAFFOLDING.defaultBlockState().setValue(ScaffoldingBlock.DISTANCE, 0).setValue(ScaffoldingBlock.BOTTOM, false);
                };
                set(cell[0], y, cell[1], state);
            }
        }

        /** The top platform's height (relative), for tests and the medals. */
        public int top() {
            return top;
        }

        @Override
        protected void skillTick(int age) {
            if (player.onGround() || player.onClimbable()) {
                best = Math.max(best, (int) Math.floor(player.getY() - centre.getY()) - 1);
            }
            if (age % 4 == 0) {
                player.sendOverlayMessage(Component.translatable("message.rngambling.room.tower_climb.height", Math.max(0, best), top,
                        top / 3, 2 * top / 3).withStyle(ChatFormatting.AQUA));
            }
            if (age >= maxTicks() - 1) {
                award(byHeight());
            }
        }

        Medal byHeight() {
            return best >= 2 * top / 3 ? Medal.SILVER : best >= top / 3 ? Medal.BRONZE : Medal.NONE;
        }

        /** For tests: as if the player had stood this high. */
        public void reached(int height) {
            best = Math.max(best, height);
        }

        public Medal medalNow() {
            return byHeight();
        }

        @Override
        protected void onExit() {
            award(Medal.GOLD);
        }

        @Override
        protected int maxTicks() {
            return 20 * 60 * 3;
        }
    }

    // ======================= Block Party =======================

    /**
     * NEU-42 Block Party: a dance floor of coloured tiles. Each round a colour is called; when the
     * time's up, every other colour drops away. Ten rounds, each quicker than the last. Medal by
     * rounds survived: 4, bronze; 7, silver; all 10, gold.
     */
    public static final class BlockParty extends SkillRoom {
        static final int Y = 40, TILES = 8, ROUNDS = 10, START = 60, AFTER = 40;
        static final DyeColor[] COLOURS = {DyeColor.WHITE, DyeColor.RED, DyeColor.ORANGE, DyeColor.YELLOW, DyeColor.LIME,
                DyeColor.LIGHT_BLUE, DyeColor.BLUE, DyeColor.MAGENTA};
        private final DyeColor[][] floor = new DyeColor[TILES][TILES];
        private int round, survived, roundStart = -1;
        private DyeColor called;
        private boolean dropped;

        @Override
        protected Vec3 build() {
            int half = TILES;
            // A low glass rail round the floor, so nobody runs off the edge.
            for (int i = -half - 1; i <= half; i++) {
                for (int y = Y + 1; y <= Y + 2; y++) {
                    set(i, y, -half - 1, Blocks.GLASS);
                    set(i, y, half, Blocks.GLASS);
                    set(-half - 1, y, i, Blocks.GLASS);
                    set(half, y, i, Blocks.GLASS);
                }
            }
            for (int i = -half - 1; i <= half; i += 4) {
                set(i, Y + 3, -half - 1, Blocks.SEA_LANTERN);
                set(i, Y + 3, half, Blocks.SEA_LANTERN);
            }
            newFloor();
            message("message.rngambling.room.block_party.rules");
            return Vec3.atBottomCenterOf(at(0, Y + 1, 0));
        }

        /** The countdown for a round: four seconds at first, down to just over one. */
        static int countdown(int round) {
            return Math.max(24, 84 - 6 * round);
        }

        private void newFloor() {
            for (int tx = 0; tx < TILES; tx++) {
                for (int tz = 0; tz < TILES; tz++) {
                    floor[tx][tz] = COLOURS[random.nextInt(COLOURS.length)];
                }
            }
            called = COLOURS[random.nextInt(COLOURS.length)];
            for (int k = 0; k < 3; k++) {
                floor[random.nextInt(TILES)][random.nextInt(TILES)] = called;
            }
            drawFloor(false);
        }

        private void drawFloor(boolean onlyCalled) {
            for (int tx = 0; tx < TILES; tx++) {
                for (int tz = 0; tz < TILES; tz++) {
                    BlockState state = onlyCalled && floor[tx][tz] != called ? Blocks.AIR.defaultBlockState() : concrete(floor[tx][tz]);
                    for (int dx = 0; dx < 2; dx++) {
                        for (int dz = 0; dz < 2; dz++) {
                            set(-TILES + 2 * tx + dx, Y, -TILES + 2 * tz + dz, state);
                        }
                    }
                }
            }
        }

        public static BlockState concrete(DyeColor colour) {
            Block block = switch (colour) {
                case RED -> Blocks.RED_CONCRETE;
                case ORANGE -> Blocks.ORANGE_CONCRETE;
                case YELLOW -> Blocks.YELLOW_CONCRETE;
                case LIME -> Blocks.LIME_CONCRETE;
                case LIGHT_BLUE -> Blocks.LIGHT_BLUE_CONCRETE;
                case BLUE -> Blocks.BLUE_CONCRETE;
                case MAGENTA -> Blocks.MAGENTA_CONCRETE;
                default -> Blocks.WHITE_CONCRETE;
            };
            return block.defaultBlockState();
        }

        private Component colourName() {
            return Component.translatable("color.minecraft." + called.getSerializedName())
                    .withStyle(style -> style.withColor(called.getTextColor()).withBold(true));
        }

        @Override
        protected void skillTick(int age) {
            if (player.getY() < centre.getY() + Y - 2) {
                player.sendSystemMessage(Component.translatable("message.rngambling.room.block_party.out", survived).withStyle(ChatFormatting.RED));
                award(medal(survived));
                return;
            }
            if (age < START) {
                if (age % 20 == 0) {
                    title(Component.literal(String.valueOf(3 - age / 20)).withStyle(ChatFormatting.LIGHT_PURPLE), 0, 20, 0);
                }
                return;
            }
            if (roundStart < 0) {
                startRound(age);
            }
            int t = age - roundStart, count = countdown(round);
            if (!dropped) {
                if (t % 10 == 0) {
                    player.sendOverlayMessage(Component.translatable("message.rngambling.room.block_party.stand", colourName(),
                            SkillRoom.seconds(count - t)));
                    sound(SoundEvents.NOTE_BLOCK_HAT.value(), 1.0f + t / (float) count);
                }
                if (t >= count) {
                    dropped = true;
                    drawFloor(true);
                    sound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6f);
                }
            } else if (t >= count + AFTER) {
                survived++;
                if (survived >= ROUNDS) {
                    award(Medal.GOLD);
                    return;
                }
                round++;
                newFloor();
                startRound(age);
            }
        }

        private void startRound(int age) {
            roundStart = age;
            dropped = false;
            title(colourName(), Component.translatable("message.rngambling.room.block_party.round", round + 1, ROUNDS), 0, 30, 5);
            sound(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.2f);
        }

        static Medal medal(int survived) {
            return survived >= ROUNDS ? Medal.GOLD : survived >= 7 ? Medal.SILVER : survived >= 4 ? Medal.BRONZE : Medal.NONE;
        }

        /** For tests: the colour called this round. */
        public DyeColor called() {
            return called;
        }

        public int survived() {
            return survived;
        }

        @Override
        protected int maxTicks() {
            int total = START;
            for (int r = 0; r < ROUNDS; r++) {
                total += countdown(r) + AFTER;
            }
            return total + 100;
        }
    }

    // ======================= Simon Says =======================

    /**
     * NEU-43 Simon Says: four coloured pads, each with a button beneath. The pads light up in a
     * sequence; press the buttons in the same order. Each round adds one. Medal by sequences
     * repeated: 3, bronze; 5, silver; 8 (ten long), gold. A wrong button, or ten seconds idle,
     * ends the game.
     */
    public static final class SimonSays extends SkillRoom {
        static final int[] PADS = {-6, -2, 2, 6};
        static final Block[] COLOURS = {Blocks.RED_CONCRETE, Blocks.BLUE_CONCRETE, Blocks.LIME_CONCRETE, Blocks.YELLOW_CONCRETE};
        static final float[] PITCHES = {0.5f, 0.63f, 0.75f, 1.0f};
        static final int FIRST = 3, GOLD_ROUNDS = 8, FLASH = 12, GAP = 6, IDLE = 20 * 10;
        private final List<Integer> sequence = new ArrayList<>();
        private final boolean[] pressed = new boolean[PADS.length];
        private final int[] lit = new int[PADS.length];
        private boolean playing;
        private int playbackStart, input, lastInput, rounds, pause = 40;

        @Override
        protected Vec3 build() {
            box(-8, -1, -7, 8, 6, 5, Blocks.QUARTZ_BLOCK, true);
            for (int x : new int[] {-5, 0, 5}) {
                set(x, 6, 0, Blocks.GLOWSTONE);
            }
            for (int p = 0; p < PADS.length; p++) {
                box(PADS[p] - 1, 2, -7, PADS[p] + 1, 4, -7, COLOURS[p], false);
                set(PADS[p], 1, -7, COLOURS[p]);
                set(PADS[p], 1, -6, Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.WALL)
                        .setValue(ButtonBlock.FACING, Direction.SOUTH));
            }
            for (int i = 0; i < FIRST; i++) {
                sequence.add(random.nextInt(PADS.length));
            }
            message("message.rngambling.room.simon_says.rules");
            return Vec3.atBottomCenterOf(at(0, 0, 2));
        }

        @Override
        protected void skillTick(int age) {
            for (int p = 0; p < PADS.length; p++) {
                if (lit[p] > 0 && --lit[p] == 0) {
                    set(PADS[p], 3, -7, COLOURS[p]);
                }
            }
            if (pause > 0) {
                if (--pause == 0) {
                    playing = true;
                    playbackStart = age;
                    title(Component.translatable("message.rngambling.room.simon_says.watch").withStyle(ChatFormatting.AQUA), 0, 20, 5);
                }
                readButtons(); // presses while paused don't count
                return;
            }
            if (playing) {
                int t = age - playbackStart;
                int index = t / (FLASH + GAP);
                if (t % (FLASH + GAP) == 0 && index < sequence.size()) {
                    flash(sequence.get(index));
                }
                readButtons();
                if (index >= sequence.size()) {
                    playing = false;
                    input = 0;
                    lastInput = age;
                    title(Component.translatable("message.rngambling.room.simon_says.go").withStyle(ChatFormatting.GREEN), 0, 15, 5);
                }
                return;
            }
            for (int p : readButtons()) {
                flash(p);
                lastInput = age;
                if (p != sequence.get(input)) {
                    player.sendSystemMessage(Component.translatable("message.rngambling.room.simon_says.wrong", rounds).withStyle(ChatFormatting.RED));
                    sound(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 0.5f);
                    award(medal(rounds));
                    return;
                }
                if (++input == sequence.size()) {
                    rounds++;
                    if (rounds >= GOLD_ROUNDS) {
                        award(Medal.GOLD);
                        return;
                    }
                    player.sendOverlayMessage(Component.translatable("message.rngambling.room.simon_says.done", rounds).withStyle(ChatFormatting.GREEN));
                    sequence.add(random.nextInt(PADS.length));
                    pause = 30;
                    return;
                }
            }
            if (age - lastInput > IDLE) {
                player.sendSystemMessage(Component.translatable("message.rngambling.room.simon_says.idle", rounds).withStyle(ChatFormatting.RED));
                award(medal(rounds));
            }
            if (age >= maxTicks() - 1) {
                award(medal(rounds));
            }
        }

        /** Buttons newly pressed since last tick (each counts once per press). */
        private List<Integer> readButtons() {
            List<Integer> fresh = new ArrayList<>();
            for (int p = 0; p < PADS.length; p++) {
                BlockState button = level.getBlockState(at(PADS[p], 1, -6));
                boolean on = button.hasProperty(ButtonBlock.POWERED) && button.getValue(ButtonBlock.POWERED);
                if (on && !pressed[p]) {
                    fresh.add(p);
                }
                pressed[p] = on;
            }
            return fresh;
        }

        private void flash(int p) {
            set(PADS[p], 3, -7, Blocks.SEA_LANTERN);
            lit[p] = FLASH;
            level.playSound(null, at(PADS[p], 3, -6), SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.BLOCKS, 1.0f, PITCHES[p] * 2);
            level.sendParticles(ParticleTypes.NOTE, at(PADS[p], 3, -6).getX() + 0.5, at(PADS[p], 3, -6).getY() + 0.5,
                    at(PADS[p], 3, -6).getZ() + 0.5, 3, 0.4, 0.4, 0.1, 1);
        }

        static Medal medal(int rounds) {
            return rounds >= GOLD_ROUNDS ? Medal.GOLD : rounds >= 5 ? Medal.SILVER : rounds >= 3 ? Medal.BRONZE : Medal.NONE;
        }

        /** For tests: the sequence to repeat, and whether it's the player's turn. */
        public List<Integer> sequence() {
            return List.copyOf(sequence);
        }

        public boolean awaitingInput() {
            return !playing && pause == 0 && !finished();
        }

        public int rounds() {
            return rounds;
        }

        /** For tests: where a pad's button is (relative to the centre). */
        public static BlockPos button(int pad) {
            return new BlockPos(PADS[pad], 1, -6);
        }

        @Override
        protected float startYaw() {
            return 180f;
        }

        @Override
        protected int maxTicks() {
            return 20 * 60 * 5;
        }
    }
}
