package rngambling.outcome;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import rngambling.RNGambling;
import rngambling.RNGamblingConfig;

/**
 * The Sandbox: an endless plane of bedrock under the End's sky, where outcomes that would change
 * or grief the world ({@link Outcome#sandbox()}: explosions, fire, placed or moved blocks) are
 * played out instead. Each visit gets its own arena on the plane, {@link #SPACING} blocks from any
 * other, so players never meet there.
 *
 * <p>Safeguards:
 * <ul>
 * <li>The exact spot (dimension, position, facing) the player broke the block from is remembered
 * three ways: in the running session, on the player, and in the world's {@link SandboxRecords}.
 * <li>A full copy of the player's inventory is taken before they go, and kept with the world
 * (see {@code /rngambling sandbox restore}).
 * <li>They come back once things have settled (no mobs, TNT or falling blocks left; at least
 * {@link #MIN_TICKS}), or after {@link #MAX_TICKS} at the most, with any items and experience left in
 * their arena. If they die there, their drops go back to the spot they left from.
 * <li>If they log out or the server stops mid-visit, they're brought back when they next log in.
 * <li>Admins can bring anyone back at any time: {@code /rngambling sandbox return <player>}.
 * </ul>
 */
public final class Sandbox {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, RNGambling.id("sandbox"));
    /** At least this long there, so the player sees where they are. */
    public static final int MIN_TICKS = 100;
    /** At most this long; then they're brought back and what's left is cleared away. */
    public static final int MAX_TICKS = 20 * 90;

    /** Arenas are this far apart: further than anyone can go in a visit. */
    public static final int SPACING = 4096;
    /** How far around an arena counts as part of it (for "has it settled?" and gathering what's left). */
    private static final int WATCH = 64, WATCH_UP = 64;
    /** Around the centre, the ground is cleared of anything left on it before each visit. */
    private static final int CLEAR = 12, CLEAR_UP = 12;
    /** The plane is one layer of bedrock at y 0; arenas stand on it. */
    private static final int ARENA_Y = 1;
    /** Arena slots run along x; they wrap round after this many (well inside the world border). */
    private static final int SLOTS = 7000;
    private static final String RETURN_TAG = "rngambling_sandbox_return";

    /** Where an outcome is played out: the level, and the spot standing in for the broken block. */
    public record Arena(ServerLevel level, BlockPos centre) {
    }

    /** Exactly where a player is brought back to. */
    public record ReturnPoint(ResourceKey<Level> level, Vec3 pos, float yaw, float pitch) {
        static ReturnPoint of(ServerPlayer player) {
            return new ReturnPoint(player.level().dimension(), player.position(), player.getYRot(), player.getXRot());
        }

        static @Nullable ReturnPoint of(SandboxRecords.@Nullable Visit visit) {
            return visit == null ? null : new ReturnPoint(ResourceKey.create(Registries.DIMENSION, Identifier.parse(visit.level())),
                    new Vec3(visit.x(), visit.y(), visit.z()), visit.yaw(), visit.pitch());
        }

        static @Nullable ReturnPoint of(CompoundTag tag) {
            return tag.isEmpty() ? null : new ReturnPoint(
                    ResourceKey.create(Registries.DIMENSION, Identifier.parse(tag.getStringOr("level", "minecraft:overworld"))),
                    new Vec3(tag.getDoubleOr("x", 0), tag.getDoubleOr("y", 64), tag.getDoubleOr("z", 0)),
                    tag.getFloatOr("yaw", 0), tag.getFloatOr("pitch", 0));
        }

        CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString("level", level.identifier().toString());
            tag.putDouble("x", pos.x);
            tag.putDouble("y", pos.y);
            tag.putDouble("z", pos.z);
            tag.putFloat("yaw", yaw);
            tag.putFloat("pitch", pitch);
            return tag;
        }
    }

    private record Session(UUID player, BlockPos centre, ReturnPoint back, long startTick, int hold, @Nullable Room room) {
        AABB area() {
            return new AABB(centre).inflate(WATCH, 0, WATCH).expandTowards(0, WATCH_UP, 0);
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private Sandbox() {
    }

    public static boolean enabled() {
        return RNGamblingConfig.SANDBOX_ENABLED.getAsBoolean();
    }

    public static boolean inSandbox(Player player) {
        return player.level().dimension() == LEVEL;
    }

    /**
     * Takes a player to a fresh arena in the Sandbox and returns where to play the outcome out, or
     * null if the Sandbox is switched off or missing (the outcome then happens where it is).
     */
    public static @Nullable Arena start(ServerPlayer player) {
        return start(player, 0);
    }

    /** As {@link #start(ServerPlayer)}, the visit lasting at least {@code hold} ticks (for effects that play out over time). */
    public static @Nullable Arena start(ServerPlayer player, int hold) {
        MinecraftServer server = player.level().getServer();
        ServerLevel sandbox = server.getLevel(LEVEL);
        if (!enabled() || sandbox == null || inSandbox(player) || SESSIONS.containsKey(player.getUUID())) {
            return null;
        }
        // A fresh arena each time: slots are kept with the world, so they aren't reused after a restart.
        int slot = SandboxRecords.get(server).takeSlot() % SLOTS;
        BlockPos centre = new BlockPos(SPACING * slot, ARENA_Y, 0);
        prepareArena(sandbox, centre);
        ReturnPoint back = ReturnPoint.of(player);
        begin(player, new Session(player.getUUID(), centre, back, server.getTickCount(), Math.max(MIN_TICKS, hold), null));
        // Stand a few blocks from where the "block" is, facing it.
        player.teleportTo(sandbox, centre.getX() + 0.5, centre.getY(), centre.getZ() + 6.5, Set.of(), 180f, 10f, true);
        return new Arena(sandbox, centre);
    }

    /** Whether rooms can be played now (the Sandbox is on and there). */
    public static boolean available(MinecraftServer server) {
        return enabled() && server.getLevel(LEVEL) != null;
    }

    /**
     * Takes a player into a Sandbox room (see {@link Rooms}): builds it in a fresh arena and puts
     * them at its start. Returns false if the Sandbox is off or missing, or they're already there.
     */
    public static boolean startRoom(ServerPlayer player, Room room, int luck) {
        MinecraftServer server = player.level().getServer();
        ServerLevel sandbox = server.getLevel(LEVEL);
        if (!enabled() || sandbox == null || inSandbox(player) || SESSIONS.containsKey(player.getUUID())) {
            return false;
        }
        int slot = SandboxRecords.get(server).takeSlot() % SLOTS;
        BlockPos centre = new BlockPos(SPACING * slot, ARENA_Y, 0);
        prepareArena(sandbox, centre);
        ReturnPoint back = ReturnPoint.of(player);
        begin(player, new Session(player.getUUID(), centre, back, server.getTickCount(), MIN_TICKS, room));
        Vec3 start = room.buildAt(sandbox, centre, player, luck);
        player.teleportTo(sandbox, start.x, start.y, start.z, Set.of(), room.startYaw(), 0f, true);
        player.fallDistance = 0;
        return true;
    }

    /** Records a visit's start: the session, and the return point and inventory copy on the player and with the world. */
    private static void begin(ServerPlayer player, Session session) {
        SESSIONS.put(player.getUUID(), session);
        player.getPersistentData().put(RETURN_TAG, session.back().toTag());
        SandboxRecords.get(player.level().getServer()).start(player, session.back(), System.currentTimeMillis());
    }

    /** The room a player is in, if any (for the rules on breaking and placing blocks). */
    public static @Nullable Room roomOf(Player player) {
        Session session = SESSIONS.get(player.getUUID());
        return session == null ? null : session.room();
    }

    /** A player walked into an exit portal. */
    public static void reachedExit(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session != null && session.room() != null) {
            session.room().exitReached();
        } else if (session != null) {
            end(player.level().getServer(), player.level().getServer().getLevel(LEVEL), session, player);
        }
    }

    /** Makes sure the ground is there and clear around {@code centre}, with nothing left from an earlier visit. */
    private static void prepareArena(ServerLevel level, BlockPos centre) {
        BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int dx = -CLEAR; dx <= CLEAR; dx++) {
            for (int dz = -CLEAR; dz <= CLEAR; dz++) {
                level.setBlock(centre.offset(dx, -1, dz), bedrock, Block.UPDATE_CLIENTS);
                for (int dy = 0; dy < CLEAR_UP; dy++) {
                    BlockPos pos = centre.offset(dx, dy, dz);
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, air, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        for (Entity entity : level.getEntities((Entity) null, new AABB(centre).inflate(WATCH, WATCH_UP, WATCH), e -> !(e instanceof Player))) {
            entity.discard();
        }
    }

    /** Every tick: ends sessions that have settled, run too long, or whose player has gone. */
    public static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        ServerLevel sandbox = server.getLevel(LEVEL);
        for (Session session : new ArrayList<>(SESSIONS.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(session.player());
            long age = server.getTickCount() - session.startTick();
            boolean gone = player == null || player.isRemoved() || player.isDeadOrDying() || !inSandbox(player);
            Room room = session.room();
            if (room != null) {
                if (!gone) {
                    try {
                        room.tick((int) age);
                    } catch (RuntimeException e) {
                        com.mojang.logging.LogUtils.getLogger().error("A Sandbox room failed; bringing the player back", e);
                        room.finish(false);
                    }
                    if (player.getY() < room.failBelow()) {
                        room.finish(false);
                        player.sendSystemMessage(Component.translatable("message.rngambling.room.fell").withStyle(ChatFormatting.RED));
                    }
                }
                boolean fightOver = room.endsWhenSettled() && age >= MIN_TICKS && sandbox != null && settled(sandbox, session);
                if (age >= room.maxTicks()) {
                    room.finish(false);
                }
                if (gone || room.finished() || fightOver) {
                    end(server, sandbox, session, player != null && !gone ? player : null);
                }
                continue;
            }
            boolean settled = age >= session.hold() && sandbox != null && settled(sandbox, session);
            if (gone || settled || age >= Math.max(MAX_TICKS, session.hold() + 200)) {
                end(server, sandbox, session, player != null && !gone ? player : null);
            }
        }
    }

    /** Nothing left going on in the arena: no mobs, TNT, falling blocks, lightning or projectiles. */
    private static boolean settled(ServerLevel sandbox, Session session) {
        return sandbox.getEntities((Entity) null, session.area(),
                e -> !(e instanceof Player) && !(e instanceof ItemEntity) && !(e instanceof ExperienceOrb)).isEmpty();
    }

    /** Ends a session: brings the player back (if they are still there), with what is left in the arena. */
    private static void end(MinecraftServer server, @Nullable ServerLevel sandbox, Session session, @Nullable ServerPlayer player) {
        SESSIONS.remove(session.player());
        ServerLevel home = levelOf(server, session.back());
        boolean keepLoot = true;
        if (session.room() != null) {
            boolean success = session.room().succeeded();
            keepLoot = session.room().keepsLoot(success);
            if (player != null) {
                session.room().onEnd(success);
            }
        }
        ServerPlayer holder = player != null ? player : server.getPlayerList().getPlayer(session.player());
        if (holder != null && session.room() != null && !keepLoot) {
            // Failed a room that keeps its loot: what was picked up there doesn't come home either.
            session.room().takeBackLoot(holder);
        }
        if (holder != null) {
            // Items a room handed out exist only for the visit.
            var inventory = holder.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                if (Room.isSandboxOnly(inventory.getItem(slot))) {
                    inventory.setItem(slot, net.minecraft.world.item.ItemStack.EMPTY);
                }
            }
        }
        if (player != null) {
            sendBack(player, home, session.back());
        } else {
            // Dead or gone: if they're online outside the Sandbox, they're done; if offline, onLogin brings them back.
            ServerPlayer online = server.getPlayerList().getPlayer(session.player());
            if (online != null && !inSandbox(online)) {
                online.getPersistentData().remove(RETURN_TAG);
                SandboxRecords.get(server).end(session.player());
            }
        }
        if (sandbox == null) {
            return;
        }
        // Items and experience in the arena (loot, or a dead player's drops) go back to where the block was broken.
        Vec3 to = session.back().pos();
        for (Entity entity : sandbox.getEntities((Entity) null, session.area(), e -> !(e instanceof Player))) {
            if (entity instanceof ItemEntity item && keepLoot && !Room.isSandboxOnly(item.getItem())) {
                home.addFreshEntity(new ItemEntity(home, to.x, to.y + 0.5, to.z, item.getItem().copy()));
            } else if (entity instanceof ExperienceOrb orb && keepLoot) {
                ExperienceOrb.award(home, to, orb.getValue());
            }
            entity.discard();
        }
    }

    private static ServerLevel levelOf(MinecraftServer server, ReturnPoint back) {
        ServerLevel level = server.getLevel(back.level());
        return level != null ? level : server.overworld();
    }

    /** Teleports a player to their exact return point and clears the visit. */
    private static void sendBack(ServerPlayer player, ServerLevel home, ReturnPoint back) {
        player.teleportTo(home, back.pos().x, back.pos().y, back.pos().z, Set.of(), back.yaw(), back.pitch(), true);
        player.fallDistance = 0;
        player.getPersistentData().remove(RETURN_TAG);
        SandboxRecords.get(player.level().getServer()).end(player.getUUID());
    }

    /** The best known return point for a player: the running session, the world's record, then the player's own copy. */
    private static @Nullable ReturnPoint returnPointOf(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session != null) {
            return session.back();
        }
        SandboxRecords.Visit visit = SandboxRecords.get(player.level().getServer()).get(player.getUUID());
        if (visit != null && visit.active()) {
            return ReturnPoint.of(visit);
        }
        return ReturnPoint.of(player.getPersistentData().getCompoundOrEmpty(RETURN_TAG));
    }

    /** A player logging in still in the Sandbox (after a log out or crash mid-visit) is brought back. */
    public static void onLogin(ServerPlayer player) {
        if (inSandbox(player) && !SESSIONS.containsKey(player.getUUID())) {
            bringBack(player);
        }
    }

    /**
     * Brings a player out of the Sandbox to their return point (for admins, and on log in): ends
     * their session if there is one, else uses the saved record. With no record at all, they go
     * to the world spawn. Returns where they were sent.
     */
    public static String bringBack(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        Session session = SESSIONS.get(player.getUUID());
        if (session != null) {
            end(server, server.getLevel(LEVEL), session, player);
            return describe(session.back());
        }
        ReturnPoint back = returnPointOf(player);
        if (back == null) {
            ServerLevel overworld = server.overworld();
            BlockPos spawn = overworld.getRespawnData().pos();
            back = new ReturnPoint(overworld.dimension(), Vec3.atBottomCenterOf(spawn), 0, 0);
        }
        sendBack(player, levelOf(server, back), back);
        return describe(back);
    }

    private static String describe(ReturnPoint back) {
        return String.format("%s %.1f %.1f %.1f", back.level().identifier(), back.pos().x, back.pos().y, back.pos().z);
    }

    /** Whether a player is on a Sandbox visit right now. */
    public static boolean visiting(Player player) {
        return SESSIONS.containsKey(player.getUUID());
    }
}
