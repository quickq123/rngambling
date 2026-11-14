package rngambling.outcome;

import static rngambling.outcome.Outcome.of;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Effects themed by the luck zone a lucky block is broken in: each theme swaps some catalog
 * effects for its own (keeping their tier, luck band, weight and flags) and adds a few more. A zone
 * without a theme uses the plain catalog. The theme follows where the block is broken, not where it
 * came from; its luck still decides how good or bad the draw is likely to be.
 */
public final class Themes {
    private Themes() {
    }

    /** A theme: replacement actions for catalog effects (by id, with a new id), and extra effects of its own. */
    public record Theme(Map<String, Swap> swaps, List<Outcome> extras) {
    }

    public record Swap(String id, Outcome.Action action) {
    }

    /** The themes, by luck zone name (the zone's file name). */
    public static final Map<String, Theme> BY_ZONE = Map.of(
            "nether", new Theme(Map.of(
                    "zombie_horde", new Swap("blaze_horde", c -> c.spawnRing(EntityType.BLAZE, 2 + (int) Math.round(2 * c.strength()), 5)),
                    "cave_spider_pack", new Swap("magma_cube_pack", c -> c.spawnRing(EntityType.MAGMA_CUBE, 4, 4)),
                    "witch_coven", new Swap("wither_skeleton_trio", c -> c.spawnRing(EntityType.WITHER_SKELETON, 3, 5)),
                    "iron_haul", new Swap("gold_block_haul", c -> c.drop(Items.GOLD_BLOCK, 2, 4)),
                    "food_basket", new Swap("nether_feast", c -> c.drop(Items.COOKED_PORKCHOP, 12, 24))),
                    List.of(
                            of("", "netherite_scrap", Tier.VERY_GOOD, 45, 80, 3, c -> c.drop(Items.NETHERITE_SCRAP, 1, 3)),
                            of("", "quartz_and_glowstone", Tier.GOOD, 10, 35, 5, c -> {
                                c.drop(Items.QUARTZ, 16, 32);
                                c.drop(Items.GLOWSTONE_DUST, 16, 32);
                            }))),
            "end", new Theme(Map.of(
                    "zombie_horde", new Swap("endermite_horde", c -> c.spawnRing(EntityType.ENDERMITE, 8, 4)),
                    "fused_creeper", new Swap("shulker_ambush", c -> c.spawnNear(EntityType.SHULKER, 2, 3)),
                    "orbital_launch", new Swap("levitation_lift", c -> c.effect(MobEffects.LEVITATION, 12, 2)),
                    "food_basket", new Swap("chorus_feast", c -> c.drop(Items.CHORUS_FRUIT, 12, 24)),
                    "single_lightning_bolt", new Swap("void_gaze", c -> {
                        c.effect(MobEffects.BLINDNESS, 10, 0);
                        c.effect(MobEffects.SLOWNESS, 10, 1);
                    })),
                    List.of(
                            of("", "shulker_shells", Tier.VERY_GOOD, 50, 80, 3, c -> c.drop(Items.SHULKER_SHELL, 2, 4)),
                            of("", "dragons_breath", Tier.GOOD, 15, 40, 4, c -> c.drop(Items.DRAGON_BREATH, 3, 6)))),
            "ancient_city", new Theme(Map.of(
                    "zombie_horde", new Swap("shriek", c -> {
                        c.effect(MobEffects.DARKNESS, 15, 0);
                        c.level().playSound(null, c.pos(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.BLOCKS, 2.0f, 1.0f);
                    })),
                    List.of(of("", "echo_shards", Tier.VERY_GOOD, 45, 80, 4, c -> c.drop(Items.ECHO_SHARD, 2, 4)))),
            "mushroom_fields", new Theme(Map.of(
                    "barnyard", new Swap("mooshrooms", c -> c.spawnNear(EntityType.MOOSHROOM, 3, 3)),
                    "food_basket", new Swap("mushroom_feast", c -> {
                        for (int i = c.between(3, 5); i > 0; i--) {
                            c.drop(new ItemStack(Items.MUSHROOM_STEW));
                        }
                    })),
                    List.of()));

    private static final Map<String, List<Outcome>> CACHE = new ConcurrentHashMap<>();

    /** The effects for a luck zone: the catalog with the zone's theme applied, if it has one. */
    public static List<Outcome> outcomesFor(String zone) {
        return CACHE.computeIfAbsent(zone, name -> {
            Theme theme = BY_ZONE.get(name);
            if (theme == null) {
                return Outcomes.ALL;
            }
            List<Outcome> outcomes = new ArrayList<>();
            for (Outcome outcome : Outcomes.ALL) {
                Swap swap = theme.swaps().get(outcome.id());
                outcomes.add(swap == null ? outcome : outcome.swapped(swap.id(), swap.action()));
            }
            outcomes.addAll(theme.extras());
            return List.copyOf(outcomes);
        });
    }
}
