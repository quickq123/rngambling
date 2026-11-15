package rngambling.outcome;

import static rngambling.outcome.Outcome.of;
import static rngambling.outcome.Tier.BAD;
import static rngambling.outcome.Tier.CATASTROPHIC;
import static rngambling.outcome.Tier.GOOD;
import static rngambling.outcome.Tier.LEGENDARY;
import static rngambling.outcome.Tier.NEUTRAL;
import static rngambling.outcome.Tier.VERY_BAD;
import static rngambling.outcome.Tier.VERY_GOOD;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import rngambling.RNGambling;

/**
 * The second batch of effects (added after the first survival playtest, for variety): mostly Bad,
 * Neutral and Good, with a few at each end. Part of {@link Catalog#ALL}. Nothing here changes
 * blocks; items are only ever dropped or moved, never deleted.
 */
final class MoreEffects {
    private MoreEffects() {
    }

    /** How long the luck effects (Lucky Streak, Fortune's Favor, Broken Mirror, Black Cat) last: 5 minutes. */
    static final int LUCK_SECONDS = 5 * 60;

    static final List<Outcome> ALL = List.of(
            // ================= Catastrophic =================
            of("CAT-23", "piglin_brute_squad", CATASTROPHIC, -100, -75, 4, c -> {
                for (var brute : c.spawnRing(EntityType.PIGLIN_BRUTE, 3, 6)) {
                    brute.setImmuneToZombification(true); // they stay brutes in the Overworld
                    brute.setTarget(c.player());
                }
            }),
            of("CAT-24", "shulker_barrage", CATASTROPHIC, -100, -80, 3, c -> {
                for (var shulker : c.spawnRing(EntityType.SHULKER, 4, 5)) {
                    shulker.setTarget(c.player());
                }
            }),

            // ================= Very bad =================
            of("VBD-31", "illager_ambush", VERY_BAD, -70, -45, 5, c -> {
                List<Mob> illagers = new ArrayList<>(c.spawnRing(EntityType.VINDICATOR, 3, 7));
                illagers.addAll(c.spawnRing(EntityType.PILLAGER, 1, 9));
                illagers.forEach(mob -> mob.setTarget(c.player()));
            }),
            of("VBD-32", "blaze_visit", VERY_BAD, -70, -40, 5, c -> {
                for (var blaze : c.spawnRing(EntityType.BLAZE, 3, 6)) {
                    blaze.setPos(blaze.getX(), blaze.getY() + 2, blaze.getZ());
                    blaze.setTarget(c.player());
                }
            }),
            of("VBD-33", "breeze_storm", VERY_BAD, -65, -40, 4, c -> {
                for (var breeze : c.spawnRing(EntityType.BREEZE, 2, 6)) {
                    breeze.setTarget(c.player());
                }
            }),
            of("VBD-34", "lost_armor", VERY_BAD, -60, -35, 5, c -> {
                // One worn piece flies off in a random direction (never deleted).
                List<EquipmentSlot> worn = new ArrayList<>();
                for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
                    if (!c.player().getItemBySlot(slot).isEmpty()) {
                        worn.add(slot);
                    }
                }
                if (worn.isEmpty()) {
                    c.effect(MobEffects.WEAKNESS, 60, 1); // nothing to lose: feel exposed instead
                    return;
                }
                EquipmentSlot slot = c.pickOne(worn);
                ItemStack piece = c.player().getItemBySlot(slot).copy();
                c.player().setItemSlot(slot, ItemStack.EMPTY);
                double angle = c.random().nextDouble() * Math.PI * 2;
                fling(c, piece, Math.cos(angle) * 1.3, 0.7, Math.sin(angle) * 1.3);
            }),
            of("VBD-35", "black_cat", VERY_BAD, -65, -40, 5, c -> {
                // Bad Luck II for 5 minutes (-20 on every lucky block broken, the casino against you), and the cat that did it.
                c.effect(MobEffects.UNLUCK, LUCK_SECONDS, 1);
                var cat = c.spawn(EntityType.CAT, Vec3.atBottomCenterOf(c.freeSpotNear(c.pos(), 3)));
                if (cat != null) {
                    c.level().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.CAT_VARIANT)
                            .get(net.minecraft.world.entity.animal.feline.CatVariants.ALL_BLACK)
                            .ifPresent(black -> cat.setComponent(DataComponents.CAT_VARIANT, black));
                }
                sound(c, SoundEvents.CAT_HISS_BABY.value(), 0.7f);
            }),

            // ================= Bad =================
            of("BAD-26", "hiccups", BAD, -25, -5, 6, c -> Scheduler.repeat(50, 12, i -> {
                if (c.playerHere()) {
                    c.player().push(0, 0.42, 0);
                    c.player().hurtMarked = true;
                    sound(c, SoundEvents.PLAYER_BURP, 1.4f);
                }
            })),
            of("BAD-27", "leaky_pockets", BAD, -35, -10, 5, c -> Scheduler.repeat(100, 12, i -> {
                // For a minute, every 5 s a stack from the backpack (not the hotbar) falls out.
                if (!c.playerHere()) {
                    return;
                }
                var inventory = c.player().getInventory();
                List<Integer> full = new ArrayList<>();
                for (int slot = 9; slot < 36; slot++) {
                    if (!inventory.getItem(slot).isEmpty()) {
                        full.add(slot);
                    }
                }
                if (!full.isEmpty()) {
                    ItemStack stack = inventory.removeItemNoUpdate(c.pickOne(full));
                    c.player().drop(stack, false);
                }
            })),
            of("BAD-28", "stage_fright", BAD, -35, -10, 5, c -> {
                c.effect(MobEffects.DARKNESS, 15, 0);
                Scheduler.repeat(25, 12, i -> {
                    if (c.playerHere()) {
                        sound(c, SoundEvents.WARDEN_HEARTBEAT, 1.0f);
                    }
                });
            }),
            of("BAD-29", "pillager_patrol", BAD, -40, -15, 5, c -> {
                for (var pillager : c.spawnRing(EntityType.PILLAGER, 3, 9)) {
                    pillager.setTarget(c.player());
                }
            }),
            of("BAD-30", "bogged_ambush", BAD, -40, -15, 5, c -> {
                for (var bogged : c.spawnRing(EntityType.BOGGED, 3, 8)) {
                    bogged.setTarget(c.player());
                }
            }),
            of("BAD-31", "hotbar_shuffle", BAD, -25, -5, 6, c -> {
                var inventory = c.player().getInventory();
                List<ItemStack> hotbar = new ArrayList<>();
                for (int slot = 0; slot < 9; slot++) {
                    hotbar.add(inventory.getItem(slot));
                }
                Collections.shuffle(hotbar, new java.util.Random(c.random().nextLong()));
                for (int slot = 0; slot < 9; slot++) {
                    inventory.setItem(slot, hotbar.get(slot));
                }
            }),
            of("BAD-32", "puffer_surprise", BAD, -30, -10, 5, c -> c.spawnNear(EntityType.PUFFERFISH, 3, 1)),
            of("BAD-33", "up_up_and_away", BAD, -35, -10, 5, c -> c.effect(MobEffects.LEVITATION, 3, 1)),
            of("BAD-34", "spoiled_food", BAD, -30, -10, 5, c -> {
                // One stack of food (not already rotten) goes off: the same number of rotten flesh.
                var inventory = c.player().getInventory();
                List<Integer> food = new ArrayList<>();
                for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                    ItemStack stack = inventory.getItem(slot);
                    if (stack.has(DataComponents.FOOD) && !stack.is(Items.ROTTEN_FLESH)) {
                        food.add(slot);
                    }
                }
                if (food.isEmpty()) {
                    c.effect(MobEffects.HUNGER, 30, 1); // nothing to spoil: the stomach goes off instead
                    return;
                }
                int slot = c.pickOne(food);
                int count = Math.min(inventory.getItem(slot).getCount(), Items.ROTTEN_FLESH.getDefaultMaxStackSize());
                inventory.setItem(slot, new ItemStack(Items.ROTTEN_FLESH, count));
            }),
            of("BAD-35", "broken_mirror", BAD, -35, -10, 5, c -> {
                // Bad Luck I for 5 minutes: -10 on every lucky block broken, the casino a little against you.
                c.effect(MobEffects.UNLUCK, LUCK_SECONDS, 0);
                sound(c, SoundEvents.GLASS_BREAK, 1.0f);
            }),

            // ================= Neutral =================
            of("NEU-25", "fish_rain", NEUTRAL, -10, 10, 5, c -> {
                List<EntityType<? extends Mob>> fish = List.of(EntityType.COD, EntityType.SALMON, EntityType.TROPICAL_FISH);
                for (int i = 0; i < 8; i++) {
                    c.spawn(c.pickOne(fish), c.player().position().add(c.between(-4, 4), 10 + c.between(0, 4), c.between(-4, 4)));
                }
            }),
            of("NEU-26", "topsy_turvy", NEUTRAL, -10, 10, 4, c -> {
                // Up to 8 nearby animals (not pets, not already named) are named Dinnerbone: upside down.
                var animals = c.level().getEntitiesOfClass(Animal.class, c.player().getBoundingBox().inflate(12),
                        a -> !a.hasCustomName() && !(a instanceof net.minecraft.world.entity.TamableAnimal pet && pet.isTame()));
                for (Animal animal : animals.subList(0, Math.min(8, animals.size()))) {
                    animal.setCustomName(Component.literal("Dinnerbone"));
                }
                if (animals.isEmpty()) {
                    for (var pig : c.spawnNear(EntityType.PIG, 2, 2)) {
                        pig.setCustomName(Component.literal("Dinnerbone"));
                    }
                }
            }),
            of("NEU-27", "statue_of_you", NEUTRAL, -10, 10, 4, c -> {
                var stand = c.spawn(EntityType.ARMOR_STAND, Vec3.atBottomCenterOf(c.freeSpotNear(c.pos(), 2)));
                if (stand != null) {
                    ItemStack head = new ItemStack(Items.PLAYER_HEAD);
                    head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(c.player().getGameProfile()));
                    stand.setItemSlot(EquipmentSlot.HEAD, head);
                    stand.setCustomName(Component.translatable("outcome.rngambling.statue_of_you.name", c.player().getName()));
                    stand.setCustomNameVisible(true);
                }
            }),
            of("NEU-28", "sniffer_egg", NEUTRAL, -5, 15, 4, c -> c.drop(Items.SNIFFER_EGG, 1)),
            of("NEU-29", "spotlight", NEUTRAL, -10, 10, 5, c -> c.effect(MobEffects.GLOWING, 120, 0)),
            of("NEU-30", "haunted", NEUTRAL, -10, 5, 4, c -> {
                List<SoundEvent> creaks = List.of(SoundEvents.WOODEN_DOOR_OPEN, SoundEvents.WOODEN_DOOR_CLOSE, SoundEvents.CHEST_OPEN,
                        SoundEvents.AMBIENT_CAVE.value(), SoundEvents.ZOMBIE_AMBIENT, SoundEvents.SKELETON_STEP);
                Scheduler.repeat(40, 10, i -> {
                    if (c.playerHere()) {
                        Vec3 at = c.player().position().add(c.between(-6, 6), c.between(-1, 2), c.between(-6, 6));
                        c.level().playSound(null, at.x, at.y, at.z, c.pickOne(creaks), SoundSource.AMBIENT, 1.0f, 0.8f + c.random().nextFloat() * 0.4f);
                    }
                });
            }),
            of("NEU-31", "about_face", NEUTRAL, -10, 10, 5, c -> c.player().teleportTo(c.level(), c.player().getX(), c.player().getY(), c.player().getZ(),
                    Set.of(), c.player().getYRot() + 180f, c.player().getXRot(), false)),
            of("NEU-32", "moon_gravity", NEUTRAL, -5, 15, 5, c -> {
                c.effect(MobEffects.JUMP_BOOST, 60, 2);
                c.effect(MobEffects.SLOW_FALLING, 60, 0);
            }),
            of("NEU-33", "frog_chorus", NEUTRAL, -10, 10, 4, c -> c.spawnNear(EntityType.FROG, 4, 2)),

            // ================= Good =================
            of("GOD-33", "copper_haul", GOOD, 10, 35, 6, c -> {
                c.drop(Items.COPPER_INGOT, 24, 40);
                c.drop(Items.SPYGLASS, 1);
            }),
            of("GOD-34", "redstone_kit", GOOD, 10, 35, 5, c -> {
                c.drop(Items.REDSTONE, 32);
                c.drop(Items.REPEATER, 4);
                c.drop(Items.COMPARATOR, 2);
                c.drop(Items.PISTON, 4);
                c.drop(Items.STICKY_PISTON, 2);
                c.drop(Items.OBSERVER, 2);
            }),
            of("GOD-35", "night_owl", GOOD, 10, 35, 6, c -> {
                c.effect(MobEffects.NIGHT_VISION, 600, 0);
                c.drop(Items.TORCH, 32);
            }),
            of("GOD-36", "tamers_pack", GOOD, 10, 35, 5, c -> {
                c.drop(Items.NAME_TAG, 2);
                c.drop(Items.LEAD, 4);
                c.drop(Items.SADDLE, 1);
                c.drop(Items.BONE, 8);
            }),
            of("GOD-37", "divers_kit", GOOD, 15, 40, 5, c -> {
                c.drop(new ItemStack(Items.TURTLE_HELMET));
                c.drop(c.potion(Items.POTION, Potions.LONG_WATER_BREATHING));
                c.drop(c.potion(Items.POTION, Potions.LONG_WATER_BREATHING));
                c.drop(c.potion(Items.POTION, Potions.LONG_NIGHT_VISION));
            }),
            of("GOD-38", "travelers_kit", GOOD, 10, 35, 5, c -> {
                c.drop(Items.COMPASS, 1);
                c.drop(Items.MAP, 1);
                c.drop(Items.CLOCK, 1);
                c.drop(Items.OAK_BOAT, 1);
                c.drop(Items.WHITE_BED, 1);
                c.drop(Items.COOKED_BEEF, 16);
            }),
            of("GOD-39", "beekeeper", GOOD, 10, 35, 4, c -> {
                c.drop(Items.BEEHIVE, 1);
                c.drop(Items.CAMPFIRE, 1);
                c.drop(Items.HONEY_BOTTLE, 4);
                c.drop(Items.HONEYCOMB, 6);
                c.spawnNear(EntityType.BEE, 2, 2);
            }),
            of("GOD-40", "lucky_clover", GOOD, 20, 45, 4, c -> c.drop(new ItemStack(RNGambling.FOUR_LEAF_CLOVER.get()))),
            of("GOD-41", "trim_collector", GOOD, 15, 40, 4, c -> {
                List<Item> trims = new ArrayList<>();
                for (Item item : BuiltInRegistries.ITEM) {
                    if (BuiltInRegistries.ITEM.getKey(item).getPath().endsWith("_armor_trim_smithing_template")) {
                        trims.add(item);
                    }
                }
                for (int i = 0; i < 2 && !trims.isEmpty(); i++) {
                    c.drop(c.pickOne(trims), 1);
                }
            }),
            of("GOD-42", "lucky_streak", GOOD, 15, 40, 5, c -> {
                // Luck I for 5 minutes: +10 on every lucky block broken, a slight edge at the casino.
                c.effect(MobEffects.LUCK, LUCK_SECONDS, 0);
                sound(c, SoundEvents.AMETHYST_BLOCK_CHIME, 1.4f);
            }),

            // ================= Very good =================
            of("VGD-25", "netherite_upgrade_kit", VERY_GOOD, 55, 80, 4, c -> {
                c.drop(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1);
                c.drop(Items.NETHERITE_INGOT, 1);
            }),
            of("VGD-26", "diamond_tool_set", VERY_GOOD, 45, 75, 5, c -> {
                for (Item tool : List.of(Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.DIAMOND_SHOVEL)) {
                    c.drop(c.enchanted(tool, Enchantments.EFFICIENCY, 3, Enchantments.UNBREAKING, 3));
                }
            }),
            of("VGD-27", "wind_charge_arsenal", VERY_GOOD, 45, 75, 4, c -> {
                c.drop(Items.WIND_CHARGE, 48);
                c.drop(Items.BREEZE_ROD, 4);
            }),
            of("VGD-29", "fortunes_favor", VERY_GOOD, 45, 80, 4, c -> {
                // Luck II for 5 minutes: +20 on every lucky block broken, a good edge at the casino.
                c.effect(MobEffects.LUCK, LUCK_SECONDS, 1);
                sound(c, SoundEvents.PLAYER_LEVELUP, 1.6f);
            }),
            of("VGD-28", "elixir_stash", VERY_GOOD, 45, 75, 5, c -> {
                for (var potion : List.of(Potions.STRONG_STRENGTH, Potions.STRONG_REGENERATION, Potions.LONG_FIRE_RESISTANCE,
                        Potions.STRONG_SWIFTNESS, Potions.LONG_INVISIBILITY, Potions.LONG_NIGHT_VISION)) {
                    c.drop(c.potion(Items.POTION, potion));
                }
            }),

            // ================= Legendary =================
            of("LEG-20", "trident_of_storms", LEGENDARY, 80, 100, 3, c -> c.drop(c.enchanted(Items.TRIDENT, Enchantments.LOYALTY, 3,
                    Enchantments.CHANNELING, 1, Enchantments.IMPALING, 7, Enchantments.UNBREAKING, 5, Enchantments.MENDING, 1))),
            of("LEG-21", "heavenly_mace", LEGENDARY, 85, 100, 3, c -> {
                c.drop(c.enchanted(Items.MACE, Enchantments.DENSITY, 7, Enchantments.WIND_BURST, 3, Enchantments.UNBREAKING, 5,
                        Enchantments.MENDING, 1));
                c.drop(Items.WIND_CHARGE, 16);
            })
    );

    /** Throws a stack from the player, where it can be picked up again. */
    private static void fling(OutcomeContext c, ItemStack stack, double dx, double dy, double dz) {
        ItemEntity item = new ItemEntity(c.level(), c.player().getX(), c.player().getEyeY(), c.player().getZ(), stack, dx, dy, dz);
        item.setExtendedLifetime();
        item.setPickUpDelay(30);
        c.level().addFreshEntity(item);
    }

    private static void sound(OutcomeContext c, SoundEvent sound, float pitch) {
        c.level().playSound(null, c.player().blockPosition(), sound, SoundSource.PLAYERS, 1.0f, pitch);
    }
}
