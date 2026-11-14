package rngambling.outcome;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BaseFireBlock;

/**
 * Outcomes themed by the luck zone a lucky block is broken in: each theme swaps some of the
 * ordinary outcomes for its own (same weight and rating unless given) and adds a few more. A
 * zone without a theme uses the ordinary outcomes. The theme follows where the block is
 * broken, not where it came from; its luck still sets how good or bad the draw is likely to be.
 */
public final class Themes {
    private Themes() {
    }

    /** A theme: replacements for ordinary outcomes (by id) and extra outcomes of its own. */
    public record Theme(Map<String, Outcome> replacements, List<Outcome> extras) {
    }

    /** The themes, by luck zone name (the zone's file name). */
    public static final Map<String, Theme> BY_ZONE = Map.of(
            "nether", new Theme(Map.of(
                    "zombies", new Outcome("blazes", 6, -6, c -> Outcomes.spawnMobs(c, EntityType.BLAZE, 2, 3, 3)),
                    "silverfish", new Outcome("magma_cubes", 5, -4, c -> Outcomes.spawnMobs(c, EntityType.MAGMA_CUBE, 2, 4, 3)),
                    "creeper", new Outcome("wither_skeletons", 4, -6, c -> Outcomes.spawnMobs(c, EntityType.WITHER_SKELETON, 1, 2, 2)),
                    "lightning", new Outcome("fire_ring", 4, -5, Themes::fireRing),
                    "chickens", new Outcome("striders", 5, 0, c -> Outcomes.spawnMobs(c, EntityType.STRIDER, 2, 3, 2)),
                    "ingots", new Outcome("gold_blocks", 8, 3, c -> c.drop(new ItemStack(Items.GOLD_BLOCK, c.between(1, 3)))),
                    "feast", new Outcome("nether_feast", 7, 2, c -> c.drop(new ItemStack(Items.COOKED_PORKCHOP, c.between(6, 12))))),
                    List.of(
                            new Outcome("ghast", 2, -7, c -> {
                                BlockPos up = c.freeSpotNear(c.pos().above(4), 3);
                                EntityType.GHAST.spawn(c.level(), up, EntitySpawnReason.EVENT);
                            }),
                            new Outcome("netherite_scrap", 2, 8, c -> c.drop(new ItemStack(Items.NETHERITE_SCRAP, c.between(1, 2)))),
                            new Outcome("quartz_and_glowstone", 5, 2, c -> {
                                c.drop(new ItemStack(Items.QUARTZ, c.between(8, 16)));
                                c.drop(new ItemStack(Items.GLOWSTONE_DUST, c.between(8, 16)));
                            }))),
            "end", new Theme(Map.of(
                    "zombies", new Outcome("endermites", 6, -5, c -> Outcomes.spawnMobs(c, EntityType.ENDERMITE, 4, 6, 2)),
                    "creeper", new Outcome("angry_endermen", 4, -6, c -> {
                        for (int i = c.between(1, 2); i > 0; i--) {
                            EnderMan enderman = EntityType.ENDERMAN.spawn(c.level(), c.freeSpotNear(c.pos(), 3), EntitySpawnReason.EVENT);
                            if (enderman != null) {
                                enderman.setTarget(c.player());
                            }
                        }
                    }),
                    "silverfish", new Outcome("shulker", 4, -5, c -> EntityType.SHULKER.spawn(c.level(), c.freeSpotNear(c.pos(), 2), EntitySpawnReason.EVENT)),
                    "launch", new Outcome("levitation", 3, -7, c -> c.player().addEffect(new MobEffectInstance(MobEffects.LEVITATION, 20 * 7, 1))),
                    "lightning", new Outcome("void_gaze", 4, -4, c -> {
                        c.player().addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20 * 10, 0));
                        c.player().addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20 * 10, 1));
                    }),
                    "feast", new Outcome("chorus_feast", 7, 1, c -> c.drop(new ItemStack(Items.CHORUS_FRUIT, c.between(6, 12))))),
                    List.of(
                            new Outcome("ender_pearls", 6, 4, c -> c.drop(new ItemStack(Items.ENDER_PEARL, c.between(4, 8)))),
                            new Outcome("shulker_shells", 3, 7, c -> c.drop(new ItemStack(Items.SHULKER_SHELL, 2))),
                            new Outcome("elytra", 1, 10, c -> c.drop(new ItemStack(Items.ELYTRA))))),
            "ancient_city", new Theme(Map.of(
                    "zombies", new Outcome("shriek", 6, -5, c -> {
                        c.player().addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 15, 0));
                        c.level().playSound(null, c.pos(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.BLOCKS, 2.0f, 1.0f);
                    })),
                    List.of(new Outcome("echo_shards", 4, 6, c -> c.drop(new ItemStack(Items.ECHO_SHARD, c.between(2, 4)))))),
            "mushroom_fields", new Theme(Map.of(
                    "chickens", new Outcome("mooshrooms", 5, 0, c -> Outcomes.spawnMobs(c, EntityType.MOOSHROOM, 2, 3, 2)),
                    "feast", new Outcome("mushroom_feast", 7, 2, c -> {
                        for (int i = c.between(2, 4); i > 0; i--) {
                            c.drop(new ItemStack(Items.MUSHROOM_STEW));
                        }
                    })),
                    List.of()));

    private static final Map<String, List<Outcome>> CACHE = new ConcurrentHashMap<>();

    /** The outcomes for a luck zone: the ordinary ones with the zone's theme applied, if it has one. */
    public static List<Outcome> outcomesFor(String zone) {
        return CACHE.computeIfAbsent(zone, name -> {
            Theme theme = BY_ZONE.get(name);
            if (theme == null) {
                return Outcomes.ALL;
            }
            List<Outcome> outcomes = new ArrayList<>();
            for (Outcome outcome : Outcomes.ALL) {
                outcomes.add(theme.replacements().getOrDefault(outcome.id(), outcome));
            }
            outcomes.addAll(theme.extras());
            return List.copyOf(outcomes);
        });
    }

    /** Fire on the ground in a ring around the player. */
    private static void fireRing(OutcomeContext c) {
        ServerPlayer player = c.player();
        BlockPos feet = player.blockPosition();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != 2) {
                    continue;
                }
                BlockPos spot = feet.offset(dx, 0, dz);
                if (c.level().isEmptyBlock(spot) && BaseFireBlock.canBePlacedAt(c.level(), spot, net.minecraft.core.Direction.UP)) {
                    c.level().setBlockAndUpdate(spot, BaseFireBlock.getState(c.level(), spot));
                }
            }
        }
    }
}
