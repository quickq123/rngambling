package rngambling.outcome;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import rngambling.RNGambling;
import rngambling.block.LuckyBlockEntity;

/**
 * Where and for whom an effect happens, and helpers effects share. {@code pos} is where the lucky
 * block was (now empty), or the Sandbox arena's centre. {@code chain} is how many lucky blocks
 * deep this one was placed by other effects (0 for an ordinary one).
 */
public record OutcomeContext(ServerLevel level, BlockPos pos, ServerPlayer player, RandomSource random, int luck, int chain) {
    public OutcomeContext(ServerLevel level, BlockPos pos, ServerPlayer player, RandomSource random, int luck) {
        this(level, pos, player, random, luck, 0);
    }

    /** A whole number from min to max, both included. */
    public int between(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    /** True with chance {@code p}. */
    public boolean chance(double p) {
        return random.nextDouble() < p;
    }

    public <T> T pickOne(List<T> options) {
        return options.get(random.nextInt(options.size()));
    }

    /** How far from neutral the luck is, 0 to 1 (for effects that scale with it). */
    public double strength() {
        return Math.min(1.0, Math.abs(luck) / 100.0);
    }

    // ---- items ----

    /** Drops items where the block was, splitting counts over a full stack. */
    public void drop(ItemStack stack) {
        int left = stack.getCount();
        while (left > 0) {
            int count = Math.min(left, stack.getMaxStackSize());
            Block.popResource(level, pos, stack.copyWithCount(count));
            left -= count;
        }
    }

    public void drop(Item item, int count) {
        drop(new ItemStack(item, count));
    }

    public void drop(Item item, int min, int max) {
        drop(new ItemStack(item, between(min, max)));
    }

    public Holder<Enchantment> enchantment(ResourceKey<Enchantment> key) {
        return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }

    /** An item with enchantments: pairs of (enchantment key, level). */
    @SuppressWarnings("unchecked")
    public ItemStack enchanted(Item item, Object... pairs) {
        ItemStack stack = new ItemStack(item);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            stack.enchant(enchantment((ResourceKey<Enchantment>) pairs[i]), (Integer) pairs[i + 1]);
        }
        return stack;
    }

    public ItemStack potion(Item item, Holder<Potion> potion) {
        return PotionContents.createItemStack(item, potion);
    }

    /** Items from a loot table (vanilla's, or any data pack's). */
    public List<ItemStack> loot(String table) {
        LootTable loot = level.getServer().reloadableRegistries().getLootTable(
                ResourceKey.create(Registries.LOOT_TABLE, Identifier.parse(table)));
        LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withLuck(Math.max(0, luck / 25f)).create(LootContextParamSets.CHEST);
        return loot.getRandomItems(params);
    }

    // ---- the player ----

    public void effect(Holder<MobEffect> effect, int seconds, int amplifier) {
        player.addEffect(new MobEffectInstance(effect, seconds < 0 ? MobEffectInstance.INFINITE_DURATION : seconds * 20, amplifier));
    }

    /** Whether the player is still about (online, alive, in the same level), for later steps. */
    public boolean playerHere() {
        return !player.isRemoved() && player.isAlive() && player.level() == level;
    }

    // ---- places ----

    /**
     * A free spot near {@code centre} for a mob to stand: an empty block with an empty block above.
     * Falls back to the centre itself.
     */
    public BlockPos freeSpotNear(BlockPos centre, int radius) {
        for (int tries = 0; tries < 16; tries++) {
            BlockPos spot = centre.offset(between(-radius, radius), between(0, 1), between(-radius, radius));
            if (level.isEmptyBlock(spot) && level.isEmptyBlock(spot.above())) {
                return spot;
            }
        }
        return centre;
    }

    /** {@code count} spots in a ring of {@code radius} around the player, at their height. */
    public List<Vec3> ring(int count, double radius) {
        List<Vec3> spots = new ArrayList<>();
        double offset = random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < count; i++) {
            double angle = offset + 2 * Math.PI * i / count;
            spots.add(player.position().add(radius * Math.cos(angle), 0.1, radius * Math.sin(angle)));
        }
        return spots;
    }

    /** The ground (top of the solid blocks) at x, z, or the player's height if there's nothing. */
    public BlockPos ground(int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return y <= level.getMinY() ? new BlockPos(x, player.getBlockY(), z) : new BlockPos(x, y, z);
    }

    // ---- entities ----

    public void spawn(Entity entity, Vec3 at) {
        entity.snapTo(at.x, at.y, at.z, random.nextFloat() * 360f, 0);
        level.addFreshEntity(entity);
    }

    public <T extends Entity> @Nullable T spawn(EntityType<T> type, Vec3 at) {
        T entity = type.create(level, EntitySpawnReason.EVENT);
        if (entity == null) {
            return null;
        }
        if (entity instanceof net.minecraft.world.entity.Mob mob) {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), EntitySpawnReason.EVENT, null);
        }
        spawn(entity, at);
        return entity;
    }

    /** Spawns {@code count} of a mob near where the block was. */
    public <T extends Entity> List<T> spawnNear(EntityType<T> type, int count, int radius) {
        List<T> spawned = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            T entity = spawn(type, Vec3.atBottomCenterOf(freeSpotNear(pos, radius)));
            if (entity != null) {
                spawned.add(entity);
            }
        }
        return spawned;
    }

    /** Spawns {@code count} of a mob in a ring around the player. */
    public <T extends Entity> List<T> spawnRing(EntityType<T> type, int count, double radius) {
        List<T> spawned = new ArrayList<>();
        for (Vec3 at : ring(count, radius)) {
            T entity = spawn(type, at);
            if (entity != null) {
                spawned.add(entity);
            }
        }
        return spawned;
    }

    public void explode(Vec3 at, float power, boolean fire) {
        float capped = Math.min(power, (float) rngambling.RNGamblingConfig.MAX_EXPLOSION.getAsDouble());
        level.explode(null, at.x, at.y, at.z, capped, fire, Level.ExplosionInteraction.TNT);
    }

    /**
     * Places a lucky block (from a chain effect) at an empty spot near {@code near}, with the given
     * luck, one deeper in the chain than this one. Only into empty space.
     */
    public boolean placeLuckyBlock(BlockPos near, int luck) {
        BlockPos spot = near;
        for (int tries = 0; tries < 12 && !level.isEmptyBlock(spot); tries++) {
            spot = near.offset(between(-2, 2), between(0, 1), between(-2, 2));
        }
        if (!level.isEmptyBlock(spot)) {
            return false;
        }
        level.setBlockAndUpdate(spot, RNGambling.LUCKY_BLOCK.get().defaultBlockState());
        if (level.getBlockEntity(spot) instanceof LuckyBlockEntity lucky) {
            lucky.setLuck(luck);
            lucky.setChain(chain + 1);
        }
        return true;
    }

    /** Runs a server command (for server-wide effects: time, weather). */
    public void command(String command) {
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withSuppressedOutput(), command);
    }
}
