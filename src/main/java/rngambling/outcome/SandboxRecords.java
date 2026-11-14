package rngambling.outcome;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import rngambling.RNGambling;

/**
 * The server's record of Sandbox visits, kept with the world (so it survives restarts and can be
 * read while a player is offline). For each player, their latest visit: the exact spot to bring
 * them back to, when it started, whether it's still going, and a full copy of their inventory as
 * it was just before they were taken (kept after the visit too, for admins to restore with
 * {@code /rngambling sandbox restore}).
 */
public final class SandboxRecords extends SavedData {
    /** One visit. {@code inventory} is every inventory slot in order (armour and off-hand included). */
    public record Visit(String name, String level, double x, double y, double z, float yaw, float pitch,
            long startedAt, boolean active, List<ItemStack> inventory) {
        public static final Codec<Visit> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(Visit::name),
                Codec.STRING.fieldOf("level").forGetter(Visit::level),
                Codec.DOUBLE.fieldOf("x").forGetter(Visit::x),
                Codec.DOUBLE.fieldOf("y").forGetter(Visit::y),
                Codec.DOUBLE.fieldOf("z").forGetter(Visit::z),
                Codec.FLOAT.fieldOf("yaw").forGetter(Visit::yaw),
                Codec.FLOAT.fieldOf("pitch").forGetter(Visit::pitch),
                Codec.LONG.fieldOf("started_at").forGetter(Visit::startedAt),
                Codec.BOOL.fieldOf("active").forGetter(Visit::active),
                ItemStack.OPTIONAL_CODEC.listOf().fieldOf("inventory").forGetter(Visit::inventory)
        ).apply(i, Visit::new));

        Visit ended() {
            return new Visit(name, level, x, y, z, yaw, pitch, startedAt, false, inventory);
        }

        public int itemCount() {
            return (int) inventory.stream().filter(stack -> !stack.isEmpty()).count();
        }
    }

    private static final Codec<SandboxRecords> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Visit.CODEC)
            .xmap(SandboxRecords::new, records -> records.visits);
    static final SavedDataType<SandboxRecords> TYPE = new SavedDataType<>(RNGambling.id("sandbox_visits"), SandboxRecords::new, CODEC, null);

    private final Map<UUID, Visit> visits;

    public SandboxRecords() {
        this(Map.of());
    }

    private SandboxRecords(Map<UUID, Visit> visits) {
        this.visits = new HashMap<>(visits);
    }

    public static SandboxRecords get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public Visit get(UUID player) {
        return visits.get(player);
    }

    public Map<UUID, Visit> all() {
        return Map.copyOf(visits);
    }

    /** Records the start of a visit, with a copy of the player's whole inventory. */
    public void start(ServerPlayer player, Sandbox.ReturnPoint back, long now) {
        visits.put(player.getUUID(), new Visit(player.getGameProfile().name(), back.level().identifier().toString(),
                back.pos().x, back.pos().y, back.pos().z, back.yaw(), back.pitch(), now, true, copyOf(player.getInventory())));
        setDirty();
    }

    public void end(UUID player) {
        Visit visit = visits.get(player);
        if (visit != null && visit.active()) {
            visits.put(player, visit.ended());
            setDirty();
        }
    }

    public static List<ItemStack> copyOf(Inventory inventory) {
        List<ItemStack> copy = new ArrayList<>(inventory.getContainerSize());
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            copy.add(inventory.getItem(slot).copy());
        }
        return copy;
    }

    /** Replaces a player's inventory with the copy taken when their last visit started. Returns false if there's none. */
    public boolean restore(ServerPlayer player) {
        Visit visit = visits.get(player.getUUID());
        if (visit == null) {
            return false;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            inventory.setItem(slot, slot < visit.inventory().size() ? visit.inventory().get(slot).copy() : ItemStack.EMPTY);
        }
        inventory.setChanged();
        player.containerMenu.broadcastChanges();
        return true;
    }
}
