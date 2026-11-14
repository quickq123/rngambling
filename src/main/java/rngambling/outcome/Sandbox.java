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
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import rngambling.RNGambling;
import rngambling.RNGamblingConfig;

/**
 * The Sandbox: a void dimension where outcomes that would change or grief the world
 * ({@link Outcome#sandbox()}: explosions, fire, placed or moved blocks) are played out instead,
 * each in a fresh sealed bedrock box.
 *
 * <p>Safeguards:
 * <ul>
 * <li>The exact spot (dimension, position, facing) the player broke the block from is remembered
 * three ways: in the running session, on the player, and in the world's {@link SandboxRecords}.
 * <li>A full copy of the player's inventory is taken before they go, and kept with the world
 * (see {@code /rngambling sandbox restore}).
 * <li>They come back once things have settled (no mobs, TNT or falling blocks left; at least
 * {@link #MIN_TICKS}), or after {@link #MAX_TICKS} at the most, with any items and experience left in
 * the box. If they die there, their drops go back to the spot they left from.
 * <li>If they log out or the server stops mid-visit, they're brought back when they next log in.
 * <li>Admins can bring anyone back at any time: {@code /rngambling sandbox return <player>}.
 * </ul>
 */
public final class Sandbox {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, RNGambling.id("sandbox"));
    /** At least this long in the box, so the player sees where they are. */
    public static final int MIN_TICKS = 100;
    /** At most this long; then they're brought back and what's left is cleared away. */
    public static final int MAX_TICKS = 20 * 90;

    /** The box: inside, 21 x 10 x 21 around the centre (y 0-9 above the floor). */
    private static final int HALF = 10, HEIGHT = 10, ARENA_Y = 64, SPACING = 512;
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

    private record Session(UUID player, BlockPos centre, ReturnPoint back, long startTick) {
        AABB inside() {
            return new AABB(centre.getX() - HALF, centre.getY(), centre.getZ() - HALF,
                    centre.getX() + HALF + 1, centre.getY() + HEIGHT, centre.getZ() + HALF + 1);
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static int nextSlot;

    private Sandbox() {
    }

    public static boolean enabled() {
        return RNGamblingConfig.SANDBOX_ENABLED.getAsBoolean();
    }

    public static boolean inSandbox(Player player) {
        return player.level().dimension() == LEVEL;
    }

    /**
     * Takes a player to a fresh box in the Sandbox and returns where to play the outcome out, or
     * null if the Sandbox is switched off or missing (the outcome then happens where it is).
     */
    public static @Nullable Arena start(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        ServerLevel sandbox = server.getLevel(LEVEL);
        if (!enabled() || sandbox == null || inSandbox(player) || SESSIONS.containsKey(player.getUUID())) {
            return null;
        }
        BlockPos centre = new BlockPos(SPACING * (nextSlot++ % 4000), ARENA_Y, 0);
        buildBox(sandbox, centre);
        ReturnPoint back = ReturnPoint.of(player);
        SESSIONS.put(player.getUUID(), new Session(player.getUUID(), centre, back, server.getTickCount()));
        // Remembered on the player and with the world too, with a copy of the inventory, before they go.
        player.getPersistentData().put(RETURN_TAG, back.toTag());
        SandboxRecords.get(server).start(player, back, System.currentTimeMillis());
        // Stand a few blocks from where the "block" is, facing it.
        player.teleportTo(sandbox, centre.getX() + 0.5, centre.getY(), centre.getZ() + 6.5, Set.of(), 180f, 10f, true);
        player.sendSystemMessage(Component.translatable("message.rngambling.sandbox.enter").withStyle(ChatFormatting.LIGHT_PURPLE));
        return new Arena(sandbox, centre);
    }

    /** Builds (or rebuilds) a sealed, lit bedrock box around {@code centre}, empty inside. */
    private static void buildBox(ServerLevel level, BlockPos centre) {
        BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState light = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15);
        for (int dx = -HALF - 1; dx <= HALF + 1; dx++) {
            for (int dz = -HALF - 1; dz <= HALF + 1; dz++) {
                for (int dy = -1; dy <= HEIGHT; dy++) {
                    boolean shell = Math.abs(dx) == HALF + 1 || Math.abs(dz) == HALF + 1 || dy == -1 || dy == HEIGHT;
                    boolean lamp = !shell && dy == HEIGHT - 1 && dx % 5 == 0 && dz % 5 == 0;
                    level.setBlock(centre.offset(dx, dy, dz), shell ? bedrock : lamp ? light : air, Block.UPDATE_CLIENTS);
                }
            }
        }
        AABB box = new AABB(centre).inflate(HALF + 2, HEIGHT + 2, HALF + 2);
        for (Entity entity : level.getEntities((Entity) null, box, e -> !(e instanceof Player))) {
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
            boolean settled = age >= MIN_TICKS && sandbox != null && settled(sandbox, session);
            if (gone || settled || age >= MAX_TICKS) {
                end(server, sandbox, session, player != null && !gone ? player : null);
            }
        }
    }

    /** Nothing left going on in the box: no mobs, TNT, falling blocks, lightning or projectiles. */
    private static boolean settled(ServerLevel sandbox, Session session) {
        return sandbox.getEntities((Entity) null, session.inside(),
                e -> !(e instanceof Player) && !(e instanceof ItemEntity) && !(e instanceof ExperienceOrb)).isEmpty();
    }

    /** Ends a session: brings the player back (if they're still there), with what's left in the box. */
    private static void end(MinecraftServer server, @Nullable ServerLevel sandbox, Session session, @Nullable ServerPlayer player) {
        SESSIONS.remove(session.player());
        ServerLevel home = levelOf(server, session.back());
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
        // Items and experience in the box (loot, or a dead player's drops) go back to where the block was broken.
        Vec3 to = session.back().pos();
        for (Entity entity : sandbox.getEntities((Entity) null, session.inside().inflate(1), e -> !(e instanceof Player))) {
            if (entity instanceof ItemEntity item) {
                home.addFreshEntity(new ItemEntity(home, to.x, to.y + 0.5, to.z, item.getItem().copy()));
            } else if (entity instanceof ExperienceOrb orb) {
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
        player.sendSystemMessage(Component.translatable("message.rngambling.sandbox.leave").withStyle(ChatFormatting.LIGHT_PURPLE));
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
