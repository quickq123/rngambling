package rngambling.outcome;

import com.mojang.math.Transformation;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import org.joml.Vector3f;
import rngambling.RNGambling;
import rngambling.block.LuckyBlockEntity;
import rngambling.item.HotPotatoItem;

/**
 * The catalog's "easy custom" effects, and Wear and Tear: things that need a little more than
 * vanilla's parts (a tick task, an event, a small menu). Wired into {@link Catalog#ALL}.
 */
public final class CustomEffects {
    private CustomEffects() {
    }

    /** Entities that only exist for an effect: removed if they're found again after a restart (see EffectEvents). */
    public static final String TEMPORARY_TAG = "rngambling_temporary";
    /** Llama Spit Party's spit: harmless. */
    public static final String HARMLESS_TAG = "rngambling_harmless";
    /** The Tax Collector, and where he keeps what he took (his persistent data). */
    public static final String TAX_COLLECTOR_TAG = "rngambling_tax_collector", TAKINGS = "rngambling_takings";

    // ---- for tests ----

    public static void checkDrainedForTest(ServerPlayer player) {
        checkDrained(player);
    }

    public static List<ItemStack> takingsForTest(Entity collector, ServerLevel level) {
        return takings(collector, level);
    }

    public static List<ItemStack> genieWishesForTest(OutcomeContext c) {
        return genieWishes(c);
    }

    public static boolean phoenixRiseForTest(ServerPlayer player) {
        return phoenixRise(player);
    }

    public static List<LuckyBlockEntity> luckyBlocksNearForTest(ServerLevel level, BlockPos centre, int radius) {
        return luckyBlocksNear(level, centre, radius);
    }

    public static boolean shufflesForTest(Mob mob) {
        return shuffles(mob);
    }

    // ---------------------------------------------------------------- Casino Tokens

    /** GOD-30 Token Toss: a handful of Casino Tokens, more the luckier the block. */
    static void tokenToss(OutcomeContext c) {
        c.drop(RNGambling.CASINO_TOKEN.get(), c.between(8, 16) + (int) Math.round(8 * c.strength()));
        c.level().playSound(null, c.pos(), SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1f, 1.6f);
    }

    /** VGD-24 Token Jackpot: Piles of Tokens, and the casino's jackpot fanfare. */
    static void tokenJackpot(OutcomeContext c) {
        c.drop(RNGambling.TOKEN_PILE.get(), c.between(2, 4));
        c.drop(RNGambling.CASINO_TOKEN.get(), c.between(4, 8));
        c.level().playSound(null, c.pos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 0.8f, 1f);
        Catalog.fireworks(c, 2);
    }

    // ---------------------------------------------------------------- VBD-20 Hot Potato

    static void hotPotato(OutcomeContext c) {
        c.player().getInventory().placeItemBackInInventory(HotPotatoItem.lit(c.level()));
        c.player().sendSystemMessage(Component.translatable("outcome.rngambling.hot_potato.warning").withStyle(ChatFormatting.RED));
    }

    // ---------------------------------------------------------------- VBD-30 Wear and Tear

    /** Players drained by Wear and Tear: they die if their last hunger runs out with no food on them. */
    static final Map<UUID, Long> DRAINED = new HashMap<>();

    /**
     * One random item with durability (anywhere in the inventory, armour and off hand too) drops to
     * its last point. If they carry nothing that can wear, the player pays instead: health and
     * hunger to one, and if that last hunger runs out with no food to eat, they die.
     */
    static void wearAndTear(OutcomeContext c) {
        var inventory = c.player().getInventory();
        List<ItemStack> wearable = new ArrayList<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isDamageableItem() && stack.getDamageValue() < stack.getMaxDamage() - 1) {
                wearable.add(stack);
            }
        }
        if (!wearable.isEmpty()) {
            ItemStack worn = c.pickOne(wearable);
            worn.setDamageValue(worn.getMaxDamage() - 1);
            c.level().playSound(null, c.player().blockPosition(), SoundEvents.ITEM_BREAK.value(), SoundSource.PLAYERS, 1.0f, 0.8f);
            c.player().sendSystemMessage(Component.translatable("outcome.rngambling.wear_and_tear.item", worn.getDisplayName())
                    .withStyle(ChatFormatting.RED));
            return;
        }
        c.player().setHealth(1.0f);
        c.player().getFoodData().setFoodLevel(1);
        c.player().getFoodData().setSaturation(0);
        c.effect(MobEffects.HUNGER, 60, 0); // the last point of hunger wears away in under a minute
        DRAINED.put(c.player().getUUID(), c.level().getGameTime() + 20 * 60 * 5);
        c.level().sendParticles(ParticleTypes.SOUL, c.player().getX(), c.player().getY() + 1, c.player().getZ(), 30, 0.4, 0.6, 0.4, 0.02);
        c.player().sendSystemMessage(Component.translatable("outcome.rngambling.wear_and_tear.drained").withStyle(ChatFormatting.DARK_RED));
    }

    /** Each player tick (EffectEvents): the Wear and Tear deadline. */
    static void checkDrained(ServerPlayer player) {
        Long until = DRAINED.get(player.getUUID());
        if (until == null) {
            return;
        }
        int food = player.getFoodData().getFoodLevel();
        if (!player.isAlive() || player.level().getGameTime() > until || food >= 2) {
            DRAINED.remove(player.getUUID()); // ate something (or it wore off): saved
            return;
        }
        if (food == 0 && !hasFood(player)) {
            DRAINED.remove(player.getUUID());
            player.hurtServer(player.level(), player.damageSources().starve(), Float.MAX_VALUE);
        }
    }

    static boolean hasFood(ServerPlayer player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).has(DataComponents.FOOD)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- BAD-25 Tax Collector

    private static final List<Item> TAXED = List.of(Items.EMERALD, Items.EMERALD_BLOCK, Items.GOLD_NUGGET, Items.GOLD_INGOT, Items.GOLD_BLOCK, Items.RAW_GOLD);
    private static final Codec<List<ItemStack>> STACKS = ItemStack.CODEC.listOf();

    static void taxCollector(OutcomeContext c) {
        // A quarter of each (rounded up), taken from wherever it is in the inventory.
        var inventory = c.player().getInventory();
        List<ItemStack> taken = new ArrayList<>();
        for (Item item : TAXED) {
            int total = inventory.countItem(item);
            int due = (total + 3) / 4;
            for (int slot = 0; slot < inventory.getContainerSize() && due > 0; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (stack.is(item)) {
                    int take = Math.min(due, stack.getCount());
                    taken.add(stack.split(take));
                    due -= take;
                }
            }
        }
        var collector = c.spawn(EntityType.PILLAGER, Vec3.atBottomCenterOf(c.freeSpotNear(c.player().blockPosition(), 2)));
        if (collector == null) {
            taken.forEach(stack -> c.player().getInventory().placeItemBackInInventory(stack));
            return;
        }
        collector.setCustomName(Component.translatable("entity.rngambling.tax_collector"));
        collector.setCustomNameVisible(true);
        collector.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        collector.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0);
        collector.goalSelector.removeAllGoals(goal -> true);
        collector.targetSelector.removeAllGoals(goal -> true);
        collector.goalSelector.addGoal(0, new FloatGoal(collector));
        collector.setPersistenceRequired();
        collector.addTag(TAX_COLLECTOR_TAG);
        collector.addTag(TEMPORARY_TAG);
        STACKS.encodeStart(c.level().registryAccess().createSerializationContext(NbtOps.INSTANCE), taken).result()
                .ifPresent(tag -> collector.getPersistentData().put(TAKINGS, tag));
        c.player().sendSystemMessage(Component.translatable(taken.isEmpty() ? "outcome.rngambling.tax_collector.nothing"
                : "outcome.rngambling.tax_collector.took").withStyle(ChatFormatting.RED));
        // He runs from the player for a minute, then is gone with the takings.
        Scheduler.repeat(10, 120, i -> {
            if (!collector.isAlive()) {
                return;
            }
            if (i == 119) {
                c.level().sendParticles(ParticleTypes.POOF, collector.getX(), collector.getY() + 1, collector.getZ(), 20, 0.3, 0.5, 0.3, 0.02);
                collector.discard();
                return;
            }
            Vec3 away = collector.position().subtract(c.player().position()).multiply(1, 0, 1);
            if (away.lengthSqr() < 0.01) {
                away = new Vec3(1, 0, 0);
            }
            Vec3 to = collector.position().add(away.normalize().scale(12));
            collector.getNavigation().moveTo(to.x, to.y, to.z, 1.25);
        });
    }

    /** When the Tax Collector dies, he drops what he took (EffectEvents). */
    static List<ItemStack> takings(Entity collector, ServerLevel level) {
        Tag tag = collector.getPersistentData().get(TAKINGS);
        if (tag == null) {
            return List.of();
        }
        collector.getPersistentData().remove(TAKINGS);
        return STACKS.parse(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), tag).result().orElse(List.of());
    }

    // ---------------------------------------------------------------- NEU-15 / GOD-13 golem escorts

    /** Mobs that follow the player for a while: every second, back to them if further than {@code range}. */
    private static void follow(OutcomeContext c, List<? extends PathfinderMob> mobs, double range, int seconds) {
        Scheduler.repeat(20, seconds, i -> {
            for (PathfinderMob mob : mobs) {
                if (mob.isAlive() && c.playerHere() && mob.level() == c.player().level() && mob.distanceTo(c.player()) > range) {
                    if (mob.distanceTo(c.player()) > 48) {
                        Vec3 near = Vec3.atBottomCenterOf(c.freeSpotNear(c.player().blockPosition(), 2));
                        mob.teleportTo(near.x, near.y, near.z);
                    } else {
                        mob.getNavigation().moveTo(c.player(), 1.1);
                    }
                }
            }
        });
    }

    static void snowGolemEscort(OutcomeContext c) {
        follow(c, c.spawnRing(EntityType.SNOW_GOLEM, 2, 2), 6, 5 * 60);
    }

    static void ironGolemBodyguard(OutcomeContext c) {
        List<IronGolem> golems = c.spawnRing(EntityType.IRON_GOLEM, 1, 2.5);
        golems.forEach(golem -> golem.setPlayerCreated(true)); // never turns on the player
        follow(c, golems, 8, 10 * 60);
    }

    // ---------------------------------------------------------------- GOD-17 Ore Vision

    private record OreGlow(TagKey<Block> tag, int colour) {
    }

    private static final List<OreGlow> GLOWS = List.of(new OreGlow(Tags.Blocks.ORES_DIAMOND, 0x4AEDD9), new OreGlow(Tags.Blocks.ORES_EMERALD, 0x17DD62),
            new OreGlow(Tags.Blocks.ORES_GOLD, 0xFCEE4B), new OreGlow(Tags.Blocks.ORES_IRON, 0xD8AF93), new OreGlow(Tags.Blocks.ORES_COPPER, 0xE77C56),
            new OreGlow(Tags.Blocks.ORES_REDSTONE, 0xFF2020), new OreGlow(Tags.Blocks.ORES_LAPIS, 0x345EC3), new OreGlow(Tags.Blocks.ORES_COAL, 0x404040),
            new OreGlow(Tags.Blocks.ORES_QUARTZ, 0xF0EAE0), new OreGlow(Tags.Blocks.ORES_NETHERITE_SCRAP, 0x8B5A4A));
    static final int ORE_RADIUS = 16, MAX_GLOWS = 300;

    /** Glowing copies of every ore within 16 blocks (up to 300, nearest first): the glow shows through walls. Gone after a minute. */
    static void oreVision(OutcomeContext c) {
        BlockPos centre = c.player().blockPosition();
        List<BlockPos> ores = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(centre.offset(-ORE_RADIUS, -ORE_RADIUS, -ORE_RADIUS), centre.offset(ORE_RADIUS, ORE_RADIUS, ORE_RADIUS))) {
            if (p.distSqr(centre) <= ORE_RADIUS * ORE_RADIUS && c.level().getBlockState(p).is(Tags.Blocks.ORES)) {
                ores.add(p.immutable());
            }
        }
        ores.sort(Comparator.comparingDouble(p -> p.distSqr(centre)));
        List<Display.BlockDisplay> glows = new ArrayList<>();
        for (BlockPos p : ores.subList(0, Math.min(MAX_GLOWS, ores.size()))) {
            BlockState state = c.level().getBlockState(p);
            Display.BlockDisplay glow = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, c.level());
            glow.setBlockState(state);
            glow.setGlowingTag(true);
            glow.setGlowColorOverride(GLOWS.stream().filter(g -> state.is(g.tag())).findFirst().map(OreGlow::colour).orElse(0xFFFFFF));
            // A hair bigger than the block, so it doesn't flicker against it.
            glow.setTransformation(new Transformation(new Vector3f(-0.005f), null, new Vector3f(1.01f), null));
            glow.addTag(TEMPORARY_TAG);
            glow.setPos(p.getX(), p.getY(), p.getZ());
            c.level().addFreshEntity(glow);
            glows.add(glow);
        }
        c.player().sendSystemMessage(Component.translatable("outcome.rngambling.ore_vision.found", ores.size()).withStyle(ChatFormatting.AQUA));
        Scheduler.undoLater(20 * 60, () -> glows.forEach(Entity::discard));
    }

    // ---------------------------------------------------------------- GOD-32 Lucky Sense

    /** How far Lucky Sense reaches, in blocks (it looks through loaded chunks' block entities, not every block). */
    static final int SENSE_RADIUS = 64, MAX_SENSED = 100;

    /** Lucky blocks within this many blocks of {@code centre}, nearest first: every kind, disguised ones included. */
    static List<LuckyBlockEntity> luckyBlocksNear(ServerLevel level, BlockPos centre, int radius) {
        List<LuckyBlockEntity> found = new ArrayList<>();
        int fromX = (centre.getX() - radius) >> 4, toX = (centre.getX() + radius) >> 4;
        int fromZ = (centre.getZ() - radius) >> 4, toZ = (centre.getZ() + radius) >> 4;
        for (int cx = fromX; cx <= toX; cx++) {
            for (int cz = fromZ; cz <= toZ; cz++) {
                var chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (var entity : chunk.getBlockEntities().values()) {
                    if (entity instanceof LuckyBlockEntity lucky && !entity.isRemoved()
                            && entity.getBlockPos().distSqr(centre) <= (double) radius * radius) {
                        found.add(lucky);
                    }
                }
            }
        }
        found.sort(Comparator.comparingDouble(e -> e.getBlockPos().distSqr(centre)));
        return found;
    }

    /**
     * Every lucky block within 64 blocks (up to 100, nearest first) glows through walls for two
     * minutes, disguised ones included, each in its luck's colour (red bad, white even, green good).
     */
    static void luckySense(OutcomeContext c) {
        List<LuckyBlockEntity> found = luckyBlocksNear(c.level(), c.player().blockPosition(), SENSE_RADIUS);
        // Not the block that was just broken (if it's still there for a moment).
        found.removeIf(e -> e.getBlockPos().equals(c.pos()));
        List<Display.BlockDisplay> glows = new ArrayList<>();
        for (LuckyBlockEntity lucky : found.subList(0, Math.min(MAX_SENSED, found.size()))) {
            BlockPos p = lucky.getBlockPos();
            BlockState look = lucky.mimic() != null ? lucky.mimic() : lucky.getBlockState();
            Display.BlockDisplay glow = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, c.level());
            glow.setBlockState(look);
            glow.setGlowingTag(true);
            glow.setGlowColorOverride(rngambling.luck.Luck.colour(lucky.luck()) & 0xFFFFFF);
            glow.setTransformation(new Transformation(new Vector3f(-0.005f), null, new Vector3f(1.01f), null));
            glow.addTag(TEMPORARY_TAG);
            glow.setPos(p.getX(), p.getY(), p.getZ());
            c.level().addFreshEntity(glow);
            glows.add(glow);
        }
        c.player().sendSystemMessage(Component.translatable(found.isEmpty() ? "outcome.rngambling.lucky_sense.none" : "outcome.rngambling.lucky_sense.found",
                found.size()).withStyle(ChatFormatting.GREEN));
        c.level().playSound(null, c.player().blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.2f);
        Scheduler.undoLater(20 * 120, () -> glows.forEach(Entity::discard));
    }

    // ---------------------------------------------------------------- GOD-29 / LEG-19 Mystery Item, Mystery Treasure

    static void mysteryItem(OutcomeContext c) {
        c.drop(MysteryItems.draw(c));
    }

    static void mysteryTreasure(OutcomeContext c) {
        for (int i = 0; i < 3; i++) {
            c.drop(MysteryItems.draw(c));
        }
    }

    // ---------------------------------------------------------------- LEG-09 Genie's Wish

    static List<ItemStack> genieWishes(OutcomeContext c) {
        List<ItemStack> pool = new ArrayList<>(List.of(
                c.enchanted(Items.NETHERITE_SWORD, Enchantments.SHARPNESS, 7, Enchantments.LOOTING, 4, Enchantments.MENDING, 1),
                c.enchanted(Items.NETHERITE_PICKAXE, Enchantments.EFFICIENCY, 7, Enchantments.FORTUNE, 4, Enchantments.MENDING, 1),
                c.enchanted(Items.ELYTRA, Enchantments.UNBREAKING, 5, Enchantments.MENDING, 1),
                c.enchanted(Items.TRIDENT, Enchantments.LOYALTY, 3, Enchantments.IMPALING, 7, Enchantments.MENDING, 1),
                c.enchanted(Items.MACE, Enchantments.DENSITY, 7, Enchantments.WIND_BURST, 2),
                new ItemStack(Items.NETHER_STAR),
                new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 3),
                new ItemStack(Items.TOTEM_OF_UNDYING, 2),
                new ItemStack(Items.NETHERITE_INGOT, 4),
                new ItemStack(Items.BEACON),
                new ItemStack(Items.DIAMOND_BLOCK, 8),
                new ItemStack(RNGambling.BLESSED_LUCKY_BLOCK_ITEM.get(), 2)));
        java.util.Collections.shuffle(pool, new java.util.Random(c.random().nextLong()));
        return List.copyOf(pool.subList(0, 3));
    }

    static void geniesWish(OutcomeContext c) {
        c.level().sendParticles(ParticleTypes.ENCHANT, c.player().getX(), c.player().getY() + 1.5, c.player().getZ(), 60, 0.6, 0.8, 0.6, 0.5);
        GenieMenu.open(c.player(), genieWishes(c));
    }

    // ---------------------------------------------------------------- LEG-11 Phoenix Blessing

    static void phoenixBlessing(OutcomeContext c) {
        c.player().setData(RNGambling.PHOENIX.get(), true);
        c.level().sendParticles(ParticleTypes.FLAME, c.player().getX(), c.player().getY() + 1, c.player().getZ(), 60, 0.5, 0.8, 0.5, 0.05);
        c.player().sendSystemMessage(Component.translatable("outcome.rngambling.phoenix_blessing.given").withStyle(ChatFormatting.GOLD));
    }

    /** On death (EffectEvents): rises again, once, in a burst of fire. True if it saved them. */
    static boolean phoenixRise(ServerPlayer player) {
        if (!player.getData(RNGambling.PHOENIX.get())) {
            return false;
        }
        player.setData(RNGambling.PHOENIX.get(), false);
        ServerLevel level = player.level();
        player.setHealth(player.getMaxHealth() / 2);
        player.clearFire();
        player.removeAllEffects();
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 40));
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.REGENERATION, 20 * 10, 1));
        level.broadcastEntityEvent(player, net.minecraft.world.entity.EntityEvent.PROTECTED_FROM_DEATH);
        level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1, player.getZ(), 200, 1.5, 1, 1.5, 0.2);
        level.sendParticles(ParticleTypes.LAVA, player.getX(), player.getY() + 1, player.getZ(), 30, 1, 0.5, 1, 0);
        level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.5f, 0.6f);
        // The burst: sets nearby hostile mobs alight and throws them back.
        for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(6), mob -> mob instanceof Enemy)) {
            mob.igniteForSeconds(8);
            Vec3 push = mob.position().subtract(player.position()).normalize();
            mob.knockback(1.5, -push.x, -push.z);
        }
        player.sendSystemMessage(Component.translatable("outcome.rngambling.phoenix_blessing.rise").withStyle(ChatFormatting.GOLD));
        return true;
    }

    // ---------------------------------------------------------------- LEG-16 Dragon Hatchling

    static void dragonHatchling(OutcomeContext c) {
        var dragon = c.spawn(RNGambling.DRAGON_HATCHLING.get(), Vec3.atBottomCenterOf(c.freeSpotNear(c.pos(), 2)));
        if (dragon != null) {
            dragon.tame(c.player());
            dragon.setPersistenceRequired();
            c.level().sendParticles(ParticleTypes.FLAME, dragon.getX(), dragon.getY() + 0.5, dragon.getZ(), 40, 0.4, 0.4, 0.4, 0.05);
            c.level().sendParticles(ParticleTypes.HEART, dragon.getX(), dragon.getY() + 1.2, dragon.getZ(), 5, 0.3, 0.2, 0.3, 0);
            c.player().sendSystemMessage(Component.translatable("outcome.rngambling.dragon_hatchling.hatched").withStyle(ChatFormatting.GOLD));
        }
    }

    // ---------------------------------------------------------------- VGD-19 Villager Friends

    static void villagerFriends(OutcomeContext c) {
        var librarian = friend(c, VillagerProfession.LIBRARIAN);
        if (librarian != null) {
            librarian.setOffers(offers(
                    trade(new ItemCost(Items.EMERALD, 8), new ItemCost(Items.BOOK), c.enchanted(Items.ENCHANTED_BOOK, Enchantments.MENDING, 1)),
                    trade(new ItemCost(Items.EMERALD, 6), new ItemCost(Items.BOOK), c.enchanted(Items.ENCHANTED_BOOK, Enchantments.UNBREAKING, 3)),
                    trade(new ItemCost(Items.EMERALD, 6), new ItemCost(Items.BOOK), c.enchanted(Items.ENCHANTED_BOOK, Enchantments.EFFICIENCY, 5)),
                    trade(new ItemCost(Items.EMERALD, 8), new ItemCost(Items.BOOK), c.enchanted(Items.ENCHANTED_BOOK, Enchantments.PROTECTION, 4)),
                    trade(new ItemCost(Items.EMERALD, 2), null, new ItemStack(Items.EXPERIENCE_BOTTLE, 8))));
        }
        var smith = friend(c, VillagerProfession.TOOLSMITH);
        if (smith != null) {
            smith.setOffers(offers(
                    trade(new ItemCost(Items.EMERALD, 4), null, new ItemStack(Items.DIAMOND)),
                    trade(new ItemCost(Items.EMERALD, 12), null, c.enchanted(Items.DIAMOND_PICKAXE, Enchantments.EFFICIENCY, 4, Enchantments.UNBREAKING, 3)),
                    trade(new ItemCost(Items.EMERALD, 12), null, c.enchanted(Items.DIAMOND_AXE, Enchantments.EFFICIENCY, 4, Enchantments.UNBREAKING, 3)),
                    trade(new ItemCost(Items.EMERALD, 16), null, c.enchanted(Items.DIAMOND_CHESTPLATE, Enchantments.PROTECTION, 3, Enchantments.UNBREAKING, 3)),
                    trade(new ItemCost(Items.EMERALD, 3), null, new ItemStack(Items.GOLDEN_CARROT, 8))));
        }
    }

    private static Villager friend(OutcomeContext c, net.minecraft.resources.ResourceKey<VillagerProfession> profession) {
        Villager villager = c.spawn(EntityType.VILLAGER, Vec3.atBottomCenterOf(c.freeSpotNear(c.pos(), 2)));
        if (villager != null) {
            // A master (level 5), so they keep their job without a workstation and never learn new trades.
            villager.setVillagerData(villager.getVillagerData().withProfession(c.level().registryAccess(), profession).withLevel(5));
            villager.setVillagerXp(250);
            villager.setPersistenceRequired();
            c.level().sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getY() + 1.5, villager.getZ(), 15, 0.4, 0.4, 0.4, 0);
        }
        return villager;
    }

    private static MerchantOffer trade(ItemCost cost, @org.jspecify.annotations.Nullable ItemCost second, ItemStack result) {
        return new MerchantOffer(cost, Optional.ofNullable(second), result, 12, 0, 0.0f);
    }

    private static MerchantOffers offers(MerchantOffer... offers) {
        MerchantOffers list = new MerchantOffers();
        list.addAll(List.of(offers));
        return list;
    }

    // ---------------------------------------------------------------- NEU-24 Ghost Block

    /** The block comes back: hit it and it vanishes and reappears, three times; then it breaks into a fresh roll. */
    static void ghostBlock(OutcomeContext c) {
        if (!c.level().isEmptyBlock(c.pos())) {
            return;
        }
        c.level().setBlockAndUpdate(c.pos(), RNGambling.LUCKY_BLOCK.get().defaultBlockState());
        if (c.level().getBlockEntity(c.pos()) instanceof LuckyBlockEntity lucky) {
            lucky.setLuck(c.luck());
            lucky.setChain(c.chain() + 1);
            lucky.setGhosts(3);
        }
        c.level().sendParticles(ParticleTypes.SOUL, c.pos().getX() + 0.5, c.pos().getY() + 0.5, c.pos().getZ() + 0.5, 12, 0.4, 0.4, 0.4, 0.02);
    }

    // ---------------------------------------------------------------- NEU-21 Mob Shuffle

    private static final List<EntityType<? extends Mob>> HOSTILE = List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER,
            EntityType.CREEPER, EntityType.HUSK, EntityType.STRAY, EntityType.CAVE_SPIDER, EntityType.WITCH, EntityType.ZOMBIE_VILLAGER,
            EntityType.BOGGED, EntityType.SLIME);
    private static final List<EntityType<? extends Mob>> PASSIVE = List.of(EntityType.COW, EntityType.PIG, EntityType.SHEEP,
            EntityType.CHICKEN, EntityType.RABBIT, EntityType.GOAT, EntityType.FOX, EntityType.ARMADILLO, EntityType.MOOSHROOM);

    /** Every wild mob within 16 blocks becomes another of the same kind: hostile for hostile, animal for animal. */
    static void mobShuffle(OutcomeContext c) {
        List<Mob> near = c.level().getEntitiesOfClass(Mob.class, c.player().getBoundingBox().inflate(16), CustomEffects::shuffles);
        int count = 0;
        for (Mob mob : near) {
            if (count++ >= 24) {
                break;
            }
            List<EntityType<? extends Mob>> pool = mob instanceof Enemy ? HOSTILE : PASSIVE;
            EntityType<? extends Mob> type;
            do {
                type = c.pickOne(pool);
            } while (type == mob.getType());
            Mob swapped = c.spawn(type, mob.position());
            if (swapped != null) {
                swapped.setYRot(mob.getYRot());
                swapped.setHealth(swapped.getMaxHealth() * mob.getHealth() / mob.getMaxHealth());
                c.level().sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 0.5, mob.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
                mob.discard();
            }
        }
        c.level().playSound(null, c.player().blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 1.0f, 1.0f);
    }

    /** Wild mobs only: not pets, named or leashed mobs, villagers and golems, riders, or bosses. */
    static boolean shuffles(Mob mob) {
        if (mob.hasCustomName() || mob.isLeashed() || mob.isPassenger() || mob.isVehicle() || mob.is(Tags.EntityTypes.BOSSES)
                || mob.getType() == EntityType.WARDEN || mob.getType() == EntityType.ELDER_GUARDIAN || mob.entityTags().contains(TEMPORARY_TAG)) {
            return false;
        }
        if (mob instanceof Enemy) {
            return true;
        }
        return mob instanceof Animal && !(mob instanceof TamableAnimal pet && pet.isTame()) && !(mob instanceof AbstractHorse horse && horse.isTamed());
    }

    // ---------------------------------------------------------------- NEU-20 Message in a Bottle

    /** A treasure map to the nearest buried treasure (in a world that has any; elsewhere the message is too smudged to read). */
    static void messageInABottle(OutcomeContext c) {
        c.drop(Items.GLASS_BOTTLE, 1);
        BlockPos target = c.level().findNearestMapStructure(StructureTags.ON_TREASURE_MAPS, c.pos(), 50, true);
        if (target == null) {
            c.player().sendSystemMessage(Component.translatable("outcome.rngambling.message_in_a_bottle.smudged").withStyle(ChatFormatting.ITALIC));
            c.drop(Items.PAPER, 1);
            return;
        }
        ItemStack map = MapItem.create(c.level(), target.getX(), target.getZ(), (byte) 1, true, true);
        MapItem.renderBiomePreviewMap(c.level(), map);
        MapItemSavedData.addTargetDecoration(map, target, "+", MapDecorationTypes.RED_X);
        map.set(DataComponents.ITEM_NAME, Component.translatable("outcome.rngambling.message_in_a_bottle.map"));
        c.drop(map);
    }

    // ---------------------------------------------------------------- NEU-16 Disco Floor

    private static final List<Block> GLASS = List.of(Blocks.RED_STAINED_GLASS, Blocks.ORANGE_STAINED_GLASS, Blocks.YELLOW_STAINED_GLASS,
            Blocks.LIME_STAINED_GLASS, Blocks.LIGHT_BLUE_STAINED_GLASS, Blocks.BLUE_STAINED_GLASS, Blocks.PURPLE_STAINED_GLASS, Blocks.MAGENTA_STAINED_GLASS);
    private static final float[] SCALE = {0.5f, 0.6f, 0.67f, 0.8f, 0.9f, 1.0f, 1.2f, 1.33f, 1.6f, 1.8f, 2.0f};

    /**
     * The floor around the player flashes coloured glass to a beat for 15 seconds, then goes
     * back exactly as it was (even if the server stops first). Only plain floor is changed:
     * full blocks without contents, open to the air above.
     */
    static void discoFloor(OutcomeContext c) {
        BlockPos feet = c.player().blockPosition();
        Map<BlockPos, BlockState> floor = new HashMap<>();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                BlockPos p = feet.offset(dx, -1, dz);
                BlockState state = c.level().getBlockState(p);
                if (discoable(c.level(), p, state)) {
                    floor.put(p, state);
                }
            }
        }
        if (floor.isEmpty()) {
            fireworksInstead(c);
            return;
        }
        Scheduler.repeat(5, 60, i -> {
            for (BlockPos p : floor.keySet()) {
                BlockState now = c.level().getBlockState(p);
                if (now.isAir() || isDiscoGlass(now) || now == floor.get(p)) {
                    c.level().setBlock(p, c.pickOne(GLASS).defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
            c.level().playSound(null, feet, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.RECORDS, 1.0f, SCALE[c.random().nextInt(SCALE.length)]);
            if (i % 2 == 0) {
                c.level().playSound(null, feet, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.RECORDS, 1.0f, 1.0f);
            }
        });
        Scheduler.undoLater(5 * 60 + 2, () -> floor.forEach((p, state) -> {
            BlockState now = c.level().getBlockState(p);
            if (now.isAir() || isDiscoGlass(now)) {
                c.level().setBlock(p, state, Block.UPDATE_CLIENTS);
            }
        }));
    }

    private static boolean discoable(ServerLevel level, BlockPos p, BlockState state) {
        return !state.isAir() && !state.hasBlockEntity() && state.getFluidState().isEmpty() && state.getBlock().defaultDestroyTime() >= 0
                && state.isCollisionShapeFullBlock(level, p) && level.getBlockState(p.above()).isAir()
                && !(state.getBlock() instanceof rngambling.block.LuckyBlock) && !isDiscoGlass(state);
    }

    private static boolean isDiscoGlass(BlockState state) {
        return GLASS.contains(state.getBlock());
    }

    private static void fireworksInstead(OutcomeContext c) {
        Catalog.fireworks(c, 4);
    }

    // ---------------------------------------------------------------- NEU-17 Llama Spit Party

    /** Four llamas surround the player and spit at them for ten seconds (it doesn't hurt), then they're gone. */
    static void llamaSpitParty(OutcomeContext c) {
        var llamas = c.spawnRing(EntityType.LLAMA, 4, 5);
        for (var llama : llamas) {
            llama.setNoAi(true);
            llama.addTag(TEMPORARY_TAG);
        }
        Scheduler.repeat(15, 14, i -> {
            for (var llama : llamas) {
                if (!llama.isAlive() || !c.playerHere()) {
                    continue;
                }
                if (i == 13) {
                    c.level().sendParticles(ParticleTypes.POOF, llama.getX(), llama.getY() + 1, llama.getZ(), 15, 0.4, 0.6, 0.4, 0.02);
                    llama.discard();
                    continue;
                }
                double dx = c.player().getX() - llama.getX(), dz = c.player().getZ() - llama.getZ();
                float yaw = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90f;
                llama.setYRot(yaw);
                llama.setYHeadRot(yaw);
                llama.setYBodyRot(yaw);
                if (c.random().nextFloat() < 0.6f) {
                    LlamaSpit spit = new LlamaSpit(c.level(), llama);
                    spit.addTag(HARMLESS_TAG);
                    double dy = c.player().getEyeY() - 0.3 - spit.getY();
                    double flat = Math.sqrt(dx * dx + dz * dz);
                    spit.shoot(dx, dy + flat * 0.2, dz, 1.5f, 6.0f);
                    c.level().addFreshEntity(spit);
                    c.level().playSound(null, llama.blockPosition(), SoundEvents.LLAMA_SPIT, SoundSource.NEUTRAL, 1.0f, 0.9f + c.random().nextFloat() * 0.2f);
                }
            }
        });
    }
}
