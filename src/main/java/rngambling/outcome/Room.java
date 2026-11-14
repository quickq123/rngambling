package rngambling.outcome;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import rngambling.RNGambling;

/**
 * A Sandbox room: a challenge built on the Sandbox's plane for one visit (see {@link Rooms}). It's
 * built around {@link #centre}, the player is put at {@link #build}'s spawn point, and the visit
 * ends when the room says it's finished (through the exit portal, a challenge met or failed), when
 * its time runs out, or if the player dies or leaves.
 *
 * <p>By default a room's blocks can't be broken and nothing can be placed in it (so no cutting
 * through a maze or bridging a parkour course); a room can allow either.
 */
public abstract class Room {
    /** The custom-data tag on items a room hands out: they're taken away when the visit ends. */
    public static final String SANDBOX_ONLY_TAG = "rngambling_sandbox_only";

    protected ServerLevel level;
    protected BlockPos centre;
    protected ServerPlayer player;
    protected RandomSource random;
    protected int luck;
    private boolean finished;
    private boolean success;
    private @Nullable ServerBossEvent timer;
    private @Nullable BlockPos exit;
    private @Nullable Vec3 start;

    void attach(ServerLevel level, BlockPos centre, ServerPlayer player, int luck) {
        this.level = level;
        this.centre = centre;
        this.player = player;
        this.random = level.getRandom();
        this.luck = luck;
    }

    /** Builds the room and returns where the player starts. */
    protected abstract Vec3 build();

    /** Which way the player faces at the start. */
    protected float startYaw() {
        return 180f;
    }

    /** The longest the visit can last, in ticks. */
    protected abstract int maxTicks();

    /** Every tick. {@code age} is ticks since the visit started. */
    protected void tick(int age) {
    }

    /** Below this height the player has fallen off (sky rooms): a failure. */
    protected double failBelow() {
        return Double.NEGATIVE_INFINITY;
    }

    /** A fight: the room ends once nothing hostile is left. */
    protected boolean endsWhenSettled() {
        return false;
    }

    protected boolean allowsPlacing() {
        return false;
    }

    protected boolean canBreak(BlockPos pos, BlockState state) {
        return false;
    }

    /** Whether items left in the room come home with the player (not after falling off a parkour course). */
    protected boolean keepsLoot(boolean success) {
        return true;
    }

    /** When the visit ends: tidy up (effects the room gave, its timer). */
    protected void onEnd(boolean success) {
        if (timer != null) {
            timer.removeAllPlayers();
        }
    }

    // ---- for Sandbox ----

    public boolean finished() {
        return finished;
    }

    public boolean succeeded() {
        return success;
    }

    /** Where the way out is (its lower block), if the room has one. */
    public @Nullable BlockPos exitPos() {
        return exit;
    }

    public @Nullable Vec3 startPos() {
        return start;
    }

    /** Builds the room at {@code centre} in any level (for tests, which have no Sandbox): returns where the player starts. */
    public Vec3 buildAt(ServerLevel level, BlockPos centre, ServerPlayer player, int luck) {
        attach(level, centre, player, luck);
        start = build();
        return start;
    }

    /** Runs one tick of the room (for tests). */
    public void tickForTest(int age) {
        tick(age);
    }

    /** Runs the room's end (for tests): removes its bars and effects. */
    public void endForTest() {
        onEnd(success);
    }

    public boolean breakable(BlockPos pos, BlockState state) {
        return canBreak(pos, state);
    }

    /** Ends the room: through the exit (true) or failed (false). */
    public void finish(boolean success) {
        if (!finished) {
            finished = true;
            this.success = success;
        }
    }

    // ---- building helpers (positions relative to the centre) ----

    protected BlockPos at(int dx, int dy, int dz) {
        return centre.offset(dx, dy, dz);
    }

    protected void set(int dx, int dy, int dz, BlockState state) {
        level.setBlock(at(dx, dy, dz), state, Block.UPDATE_CLIENTS);
    }

    protected void set(int dx, int dy, int dz, Block block) {
        set(dx, dy, dz, block.defaultBlockState());
    }

    /** Fills a box (inclusive), solid or only its shell. */
    protected void box(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state, boolean hollow) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                    boolean edge = x == Math.min(x0, x1) || x == Math.max(x0, x1) || y == Math.min(y0, y1) || y == Math.max(y0, y1)
                            || z == Math.min(z0, z1) || z == Math.max(z0, z1);
                    if (!hollow || edge) {
                        set(x, y, z, state);
                    }
                }
            }
        }
    }

    protected void box(int x0, int y0, int z0, int x1, int y1, int z1, Block block, boolean hollow) {
        box(x0, y0, z0, x1, y1, z1, block.defaultBlockState(), hollow);
    }

    /** A chest filled from a loot table when it's first opened. */
    protected void chest(int dx, int dy, int dz, String lootTable) {
        set(dx, dy, dz, Blocks.CHEST);
        RandomizableContainer.setBlockEntityLootTable(level, random, at(dx, dy, dz),
                ResourceKey.create(Registries.LOOT_TABLE, Identifier.parse(lootTable)));
    }

    /** The way out: walk into it. Two tall. */
    protected void exit(int dx, int dy, int dz) {
        exit = at(dx, dy, dz);
        set(dx, dy, dz, RNGambling.SANDBOX_EXIT.get());
        set(dx, dy + 1, dz, RNGambling.SANDBOX_EXIT.get());
    }

    protected <T extends Entity> @Nullable T spawn(EntityType<T> type, double dx, double dy, double dz) {
        T entity = type.create(level, EntitySpawnReason.EVENT);
        if (entity == null) {
            return null;
        }
        entity.snapTo(centre.getX() + dx + 0.5, centre.getY() + dy, centre.getZ() + dz + 0.5, random.nextFloat() * 360f, 0);
        if (entity instanceof Mob mob) {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(entity.blockPosition()), EntitySpawnReason.EVENT, null);
            mob.setPersistenceRequired();
        }
        level.addFreshEntity(entity);
        return entity;
    }

    /** Gives the player items that only exist for this visit (taken away when it ends). */
    protected void giveSandboxOnly(Item item, int count) {
        ItemStack stack = new ItemStack(item, count);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(SANDBOX_ONLY_TAG, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        player.getInventory().add(stack);
    }

    public static boolean isSandboxOnly(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBooleanOr(SANDBOX_ONLY_TAG, false);
    }

    /** A countdown bar for timed rooms; call {@link #updateTimer} each tick. */
    protected void startTimer(String key) {
        timer = new ServerBossEvent(UUID.randomUUID(), Component.translatable(key), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
        timer.addPlayer(player);
    }

    protected void updateTimer(int age) {
        if (timer != null) {
            timer.setProgress(Math.max(0f, 1f - age / (float) maxTicks()));
        }
    }

    /** A big title (and optional subtitle) on the player's screen, with its own timing in ticks. */
    protected void title(Component text, @Nullable Component subtitle, int fadeIn, int stay, int fadeOut) {
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
        if (subtitle != null) {
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(subtitle));
        }
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(text));
    }

    protected void title(Component text, int fadeIn, int stay, int fadeOut) {
        title(text, null, fadeIn, stay, fadeOut);
    }

    protected void message(String key) {
        player.sendSystemMessage(Component.translatable(key).withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
    }
}
