package rngambling.outcome;

import static rngambling.outcome.Outcome.of;
import static rngambling.outcome.Tier.BAD;
import static rngambling.outcome.Tier.CATASTROPHIC;
import static rngambling.outcome.Tier.GOOD;
import static rngambling.outcome.Tier.LEGENDARY;
import static rngambling.outcome.Tier.NEUTRAL;
import static rngambling.outcome.Tier.VERY_BAD;
import static rngambling.outcome.Tier.VERY_GOOD;
import static rngambling.outcome.Tier.WILDCARD;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import rngambling.RNGamblingConfig;

/**
 * The lucky block effects, from the effect catalog (docs/effect-catalog.csv): ID, luck band, tier
 * and weight as there. How luck picks among them is in {@link Outcomes#pick}. Effects that build
 * or place blocks (or would tear a lot up) are played out in the Sandbox; explosions and fire
 * happen in the world. Still to come (later passes): the structure rooms, the Wither fights and
 * the "easy custom" effects (Hot Potato, Tax Collector, Genie's Wish and so on).
 */
public final class Catalog {
    private Catalog() {
    }

    /** The custom-data tags some effects mark items with. */
    public static final String FOOLS_GOLD_TAG = "rngambling_fools", CURSED_TAG = "rngambling_cursed";
    private static final Identifier SIZE_SHIFT = Identifier.fromNamespaceAndPath("rngambling", "size_shift");
    public static final Identifier BONUS_HEARTS = Identifier.fromNamespaceAndPath("rngambling", "bonus_hearts");

    public static final List<Outcome> ALL = List.of(
            // ================= Catastrophic =================
            of("CAT-04", "meteor_strike", CATASTROPHIC, -100, -80, 6, Catalog::meteor),
            of("CAT-05", "tnt_monsoon", CATASTROPHIC, -100, -75, 6, c -> {
                int count = c.between(20, 40);
                BlockPos centre = c.pos();
                Scheduler.repeat(Math.max(1, 200 / count), count, i -> {
                    PrimedTnt tnt = new PrimedTnt(c.level(), centre.getX() + c.between(-12, 12) + 0.5, centre.getY() + 20,
                            centre.getZ() + c.between(-12, 12) + 0.5, null);
                    tnt.setFuse(c.between(60, 80));
                    c.level().addFreshEntity(tnt);
                });
            }),
            of("CAT-06", "charged_creeper_squad", CATASTROPHIC, -95, -70, 6, c -> {
                for (var creeper : c.spawnRing(EntityType.CREEPER, 3, 4.5)) {
                    var bolt = EntityType.LIGHTNING_BOLT.create(c.level(), EntitySpawnReason.EVENT);
                    if (bolt != null) {
                        creeper.thunderHit(c.level(), bolt); // charges it
                        creeper.clearFire();
                        creeper.setHealth(creeper.getMaxHealth());
                        bolt.setVisualOnly(true);
                        c.spawn(bolt, creeper.position());
                    }
                }
            }),
            of("CAT-09", "cursed_armor_swap", CATASTROPHIC, -100, -75, 5, c -> {
                Item[] leather = {Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS};
                EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
                for (int i = 0; i < slots.length; i++) {
                    ItemStack worn = c.player().getItemBySlot(slots[i]);
                    if (!worn.isEmpty()) {
                        c.player().drop(worn.copy(), false); // never deleted: dropped at their feet
                    }
                    c.player().setItemSlot(slots[i], c.enchanted(leather[i], Enchantments.BINDING_CURSE, 1));
                }
            }),
            of("CAT-10", "total_xp_drain", CATASTROPHIC, -100, -80, 4, c -> {
                c.level().sendParticles(ParticleTypes.PORTAL, c.player().getX(), c.player().getY() + 1, c.player().getZ(), 80, 0.6, 1, 0.6, 0.5);
                c.player().setExperienceLevels(0);
                c.player().setExperiencePoints(0);
            }),
            of("CAT-11", "inventory_explosion", CATASTROPHIC, -100, -70, 6, c -> {
                var inventory = c.player().getInventory();
                for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                    ItemStack stack = inventory.removeItemNoUpdate(slot);
                    if (!stack.isEmpty()) {
                        ItemEntity item = new ItemEntity(c.level(), c.player().getX(), c.player().getEyeY() - 0.3, c.player().getZ(), stack,
                                (c.random().nextDouble() - 0.5) * 1.2, 0.4 + c.random().nextDouble() * 0.5, (c.random().nextDouble() - 0.5) * 1.2);
                        item.setExtendedLifetime(); // ten minutes to gather it up
                        item.setPickUpDelay(20);
                        c.level().addFreshEntity(item);
                    }
                }
            }),
            of("CAT-12", "orbital_launch", CATASTROPHIC, -100, -76, 5, c -> {
                c.player().setDeltaMovement(0, 7.0, 0);
                c.player().hurtMarked = true;
            }),
            of("CAT-13", "thunder_gods_wrath", CATASTROPHIC, -100, -76, 5, c -> Scheduler.repeat(40, 10, i -> {
                if (c.playerHere()) {
                    var bolt = EntityType.LIGHTNING_BOLT.create(c.level(), EntitySpawnReason.EVENT);
                    if (bolt != null) {
                        c.spawn(bolt, c.player().position());
                    }
                }
            })),
            of("CAT-15", "the_crater", CATASTROPHIC, -100, -85, 3, Catalog::crater),
            of("CAT-16", "raid_omen", CATASTROPHIC, -90, -65, 4, c -> {
                c.effect(MobEffects.BAD_OMEN, 100 * 60, 4);
                c.spawnRing(EntityType.PILLAGER, 3, 10);
            }),
            of("CAT-18", "the_last_gamble", CATASTROPHIC, -100, -90, 3, c -> {
                c.player().setHealth(1.0f);
                title(c.player(), Component.translatable("outcome.rngambling.the_last_gamble.rules").withStyle(ChatFormatting.GOLD));
                Scheduler.later(20 * 60, () -> {
                    if (c.playerHere()) {
                        c.player().sendSystemMessage(Component.translatable("outcome.rngambling.the_last_gamble.won").withStyle(ChatFormatting.GOLD));
                        Outcomes.playTier(c.level(), c.player().blockPosition(), c.player(), 100, c.chain(), LEGENDARY);
                    }
                });
            }),
            of("CAT-19", "cursed_mining", CATASTROPHIC, -100, -76, 4, c -> EffectEvents.curseMining(c.player(), 20 * 60 * 5)),
            of("CAT-21", "herd_of_ravagers", CATASTROPHIC, -100, -80, 4, c -> {
                for (var ravager : c.spawnRing(EntityType.RAVAGER, 3, 9)) {
                    var evoker = c.spawn(EntityType.EVOKER, ravager.position());
                    if (evoker != null) {
                        evoker.startRiding(ravager, true, false);
                    }
                    ravager.setTarget(c.player());
                }
            }),
            of("CAT-22", "doomsday_clock", CATASTROPHIC, -100, -85, 3, c -> {
                ServerBossEvent bar = new ServerBossEvent(UUID.randomUUID(), Component.translatable("outcome.rngambling.doomsday_clock.bar"),
                        BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
                bar.addPlayer(c.player());
                BlockPos at = c.pos();
                Scheduler.repeat(20, 30, second -> {
                    bar.setProgress(1f - (second + 1) / 30f);
                    c.level().playSound(null, at, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.BLOCKS, 1.0f, second >= 25 ? 2.0f : 1.0f);
                    if (second == 29) {
                        bar.removeAllPlayers();
                        c.explode(Vec3.atCenterOf(at), 7f, true);
                    }
                });
            }),

            of("CAT-01", "wither_arena", CATASTROPHIC, -100, -80, 5, c -> room(c, new Rooms.WitherArena())).room(),
            of("CAT-03", "wardens_hollow", CATASTROPHIC, -100, -70, 5, c -> room(c, new Rooms.WardensHollow())).room(),
            of("CAT-07", "void_platform", CATASTROPHIC, -100, -80, 5, c -> room(c, new Rooms.VoidPlatform())).room(),
            of("CAT-08", "rising_lava_tomb", CATASTROPHIC, -100, -75, 5, c -> room(c, new Rooms.RisingLavaTomb())).room(),
            of("CAT-14", "elder_guardian_monument", CATASTROPHIC, -95, -70, 4, c -> room(c, new Rooms.ElderGuardianMonument())).room(),
            of("CAT-17", "nether_fortress_ambush", CATASTROPHIC, -100, -75, 5, c -> room(c, new Rooms.FortressAmbush())).room(),
            of("CAT-20", "empowered_wither", CATASTROPHIC, -100, -90, 2, c -> room(c, new Rooms.EmpoweredWither())).room(),

            // ================= Very bad =================
            of("VBD-01", "anvil_rain", VERY_BAD, -75, -45, 6, c -> Scheduler.repeat(6, 26, i -> {
                BlockPos above = c.player().blockPosition().offset(c.between(-3, 3), 12, c.between(-3, 3));
                if (c.level().isEmptyBlock(above)) {
                    FallingBlockEntity.fall(c.level(), above, Blocks.ANVIL.defaultBlockState()).setHurtsEntities(2.0f, 40);
                }
            })).inSandbox(200),
            of("VBD-02", "zombie_horde", VERY_BAD, -75, -40, 7, c -> {
                Item[][] armour = {{Items.LEATHER_HELMET, Items.IRON_HELMET, Items.CHAINMAIL_HELMET}, {Items.LEATHER_CHESTPLATE, Items.IRON_CHESTPLATE}};
                for (var zombie : c.spawnRing(EntityType.ZOMBIE, 8 + (int) Math.round(4 * c.strength()), 6)) {
                    if (c.chance(0.6)) {
                        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(armour[0][c.random().nextInt(3)]));
                    }
                    if (c.chance(0.4)) {
                        zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(armour[1][c.random().nextInt(2)]));
                    }
                }
            }),
            of("VBD-03", "skeleton_snipers", VERY_BAD, -70, -40, 6, c -> c.spawnRing(EntityType.SKELETON, 4, 9)),
            of("VBD-04", "fused_creeper", VERY_BAD, -75, -40, 7, c -> {
                Vec3 behind = c.player().position().subtract(c.player().getLookAngle().multiply(2, 0, 2));
                var creeper = c.spawn(EntityType.CREEPER, behind);
                if (creeper != null) {
                    creeper.ignite();
                }
            }),
            of("VBD-05", "block_detonation", VERY_BAD, -75, -30, 8, c -> c.explode(Vec3.atCenterOf(c.pos()), 3f, false)),
            of("VBD-06", "obsidian_cage", VERY_BAD, -70, -45, 5, c -> {
                List<BlockPos> cage = shell(c.player().blockPosition(), 2, 3);
                for (BlockPos p : cage) {
                    c.level().setBlockAndUpdate(p, Blocks.OBSIDIAN.defaultBlockState());
                }
                c.spawn(EntityType.CREEPER, Vec3.atBottomCenterOf(c.player().blockPosition().east()));
                Scheduler.later(600, () -> clear(c, cage, Blocks.OBSIDIAN));
            }).inSandbox(620),
            of("VBD-07", "spider_web_trap", VERY_BAD, -65, -35, 6, c -> {
                List<BlockPos> webs = new ArrayList<>();
                for (BlockPos p : BlockPos.betweenClosed(c.player().blockPosition().offset(-3, 0, -3), c.player().blockPosition().offset(3, 2, 3))) {
                    if (c.level().isEmptyBlock(p) && c.chance(0.45)) {
                        c.level().setBlockAndUpdate(p, Blocks.COBWEB.defaultBlockState());
                        webs.add(p.immutable());
                    }
                }
                for (Vec3 at : c.ring(3, 2)) {
                    c.spawn(EntityType.CAVE_SPIDER, at.add(0, 4, 0));
                }
                Scheduler.later(900, () -> clear(c, webs, Blocks.COBWEB));
            }).inSandbox(300),
            of("VBD-08", "silverfish_infestation", VERY_BAD, -75, -45, 5, c -> {
                for (Vec3 at : c.ring(8, 4)) {
                    BlockPos base = BlockPos.containing(at);
                    for (int dy = 0; dy < 3; dy++) {
                        c.level().setBlockAndUpdate(base.above(dy), Blocks.INFESTED_STONE.defaultBlockState());
                    }
                }
                c.spawnNear(EntityType.SILVERFISH, 6, 3);
            }).inSandbox(200),
            of("VBD-09", "lava_shower", VERY_BAD, -75, -50, 4, c -> {
                BlockPos source = c.player().blockPosition().above(4);
                if (c.level().isEmptyBlock(source)) {
                    c.level().setBlockAndUpdate(source, Blocks.LAVA.defaultBlockState());
                    Scheduler.later(300, () -> clear(c, List.of(source), Blocks.LAVA));
                }
            }).inSandbox(340),
            of("VBD-10", "witch_coven", VERY_BAD, -70, -40, 5, c -> {
                c.spawnRing(EntityType.WITCH, 3, 5);
                c.level().playSound(null, c.pos(), SoundEvents.WITCH_CELEBRATE, SoundSource.HOSTILE, 1.0f, 0.8f);
            }),
            of("VBD-11", "phantom_squadron", VERY_BAD, -65, -35, 5, c -> {
                List<LivingEntity> phantoms = new ArrayList<>();
                for (Vec3 at : c.ring(6, 6)) {
                    var phantom = c.spawn(EntityType.PHANTOM, at.add(0, 14, 0));
                    if (phantom != null) {
                        phantom.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.FIRE_RESISTANCE,
                                net.minecraft.world.effect.MobEffectInstance.INFINITE_DURATION));
                        phantom.setTarget(c.player());
                        phantoms.add(phantom);
                    }
                }
                Scheduler.later(20 * 90, () -> phantoms.forEach(p -> {
                    if (p.isAlive()) {
                        p.discard();
                    }
                }));
            }),
            of("VBD-12", "durability_shatter", VERY_BAD, -75, -45, 5, c -> {
                ItemStack held = c.player().getMainHandItem();
                if (held.isDamageableItem()) {
                    int left = held.getMaxDamage() - held.getDamageValue();
                    held.setDamageValue(Math.min(held.getMaxDamage() - 1, held.getDamageValue() + left / 2)); // never breaks it
                }
            }),
            of("VBD-13", "hunger_collapse", VERY_BAD, -65, -35, 6, c -> {
                c.player().getFoodData().setFoodLevel(1);
                c.player().getFoodData().setSaturation(0);
                c.effect(MobEffects.HUNGER, 60, 2);
            }),
            of("VBD-14", "poison_cloud", VERY_BAD, -60, -30, 6, c -> {
                var cloud = new net.minecraft.world.entity.AreaEffectCloud(c.level(), c.pos().getX() + 0.5, c.pos().getY(), c.pos().getZ() + 0.5);
                cloud.setRadius(4f);
                cloud.setDuration(200);
                cloud.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.POISON, 200, 1));
                c.level().addFreshEntity(cloud);
            }),
            of("VBD-15", "blind_hedge_maze", VERY_BAD, -75, -45, 5, c -> room(c, new Rooms.BlindHedgeMaze())).room(),
            of("VBD-16", "mob_arena_waves", VERY_BAD, -75, -50, 5, c -> room(c, new Rooms.MobArenaWaves())).room(),
            of("VBD-17", "skyblock_strand", VERY_BAD, -75, -50, 4, c -> room(c, new Rooms.SkyblockStrand())).room(),
            of("VBD-24", "drowned_pit", VERY_BAD, -70, -45, 4, c -> room(c, new Rooms.DrownedPit())).room(),
            of("VBD-18", "ravager_charge", VERY_BAD, -75, -50, 4, c -> {
                var ravager = c.spawn(EntityType.RAVAGER, c.ring(1, 12).getFirst());
                if (ravager != null) {
                    var rider = c.spawn(EntityType.PILLAGER, ravager.position());
                    if (rider != null) {
                        rider.startRiding(ravager, true, false);
                    }
                    ravager.setTarget(c.player());
                }
            }),
            of("VBD-19", "ghast_bombardment", VERY_BAD, -75, -45, 4, c -> {
                var ghast = c.spawn(EntityType.GHAST, c.player().position().add(0, 16, 0));
                if (ghast != null) {
                    ghast.setTarget(c.player());
                }
            }),
            of("VBD-21", "fools_diamonds", VERY_BAD, -60, -40, 6, c -> {
                ItemStack fake = new ItemStack(Items.DIAMOND, c.between(6, 10));
                CompoundTag tag = new CompoundTag();
                tag.putBoolean(FOOLS_GOLD_TAG, true);
                fake.set(DataComponents.CUSTOM_DATA, CustomData.of(tag)); // picking one up wakes a silverfish (EffectEvents)
                c.drop(fake);
            }),
            of("VBD-22", "inventory_shuffle", VERY_BAD, -60, -30, 6, c -> {
                var inventory = c.player().getInventory();
                List<ItemStack> main = new ArrayList<>();
                for (int slot = 0; slot < 36; slot++) {
                    main.add(inventory.getItem(slot));
                }
                Collections.shuffle(main, new java.util.Random(c.random().nextLong()));
                for (int slot = 0; slot < 36; slot++) {
                    inventory.setItem(slot, main.get(slot));
                }
            }),
            of("VBD-23", "slime_split", VERY_BAD, -60, -35, 5, c -> {
                var slime = c.spawn(EntityType.SLIME, Vec3.atBottomCenterOf(c.freeSpotNear(c.pos(), 3)));
                if (slime != null) {
                    slime.setSize(4, true);
                }
            }),
            of("VBD-25", "ender_swarm", VERY_BAD, -70, -45, 4, c -> {
                for (var enderman : c.spawnRing(EntityType.ENDERMAN, 5, 5)) {
                    enderman.setTarget(c.player());
                }
            }),
            of("VBD-26", "gravity_well", VERY_BAD, -65, -40, 4, c -> {
                c.effect(MobEffects.LEVITATION, 10, 1);
                Scheduler.later(200, () -> {
                    if (c.playerHere()) {
                        c.player().removeEffect(MobEffects.SLOW_FALLING);
                        c.effect(MobEffects.SLOWNESS, 10, 1);
                    }
                });
            }),
            of("VBD-27", "mob_magnet", VERY_BAD, -70, -45, 4, c -> Scheduler.repeat(40, 90, i -> {
                if (c.playerHere()) {
                    for (Mob mob : c.level().getEntitiesOfClass(Mob.class, c.player().getBoundingBox().inflate(48), m -> m instanceof Enemy)) {
                        mob.setTarget(c.player());
                    }
                }
            })),
            of("VBD-29", "exploding_chickens", VERY_BAD, -60, -35, 4, c -> {
                var chickens = c.spawnNear(EntityType.CHICKEN, 5, 3);
                Scheduler.later(60, () -> chickens.forEach(chicken -> {
                    if (chicken.isAlive()) {
                        Vec3 at = chicken.position();
                        chicken.discard();
                        c.explode(at, 1.5f, false);
                    }
                }));
            }),

            // ================= Bad =================
            of("BAD-01", "fake_explosion", BAD, -40, -5, 8, c -> {
                c.level().playSound(null, c.pos(), SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0f, 1.0f);
                Scheduler.later(40, () -> {
                    c.level().playSound(null, c.pos(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 4.0f, 1.0f);
                    c.level().sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.pos().getX() + 0.5, c.pos().getY() + 0.5, c.pos().getZ() + 0.5, 1, 0, 0, 0, 0);
                    c.level().sendParticles(ParticleTypes.LARGE_SMOKE, c.pos().getX() + 0.5, c.pos().getY() + 0.5, c.pos().getZ() + 0.5, 40, 1, 1, 1, 0.05);
                });
            }),
            of("BAD-02", "fake_death_message", BAD, -35, -10, 5, c -> c.player().sendSystemMessage(
                    Component.translatable("outcome.rngambling.fake_death_message.text", c.player().getDisplayName()))),
            of("BAD-03", "chicken_jockey_party", BAD, -40, -15, 5, c -> {
                for (var chicken : c.spawnRing(EntityType.CHICKEN, 3, 4)) {
                    var zombie = c.spawn(EntityType.ZOMBIE, chicken.position());
                    if (zombie != null) {
                        zombie.setBaby(true);
                        zombie.startRiding(chicken, true, false);
                    }
                }
            }),
            of("BAD-04", "angry_bees", BAD, -35, -10, 6, c -> {
                for (var bee : c.spawnNear(EntityType.BEE, 4, 2)) {
                    bee.setTarget(c.player());
                    ((NeutralMob) bee).setTimeToRemainAngry(400);
                }
            }),
            of("BAD-05", "sand_drop", BAD, -35, -10, 6, c -> {
                for (int dy = 3; dy <= 6; dy++) {
                    BlockPos above = c.player().blockPosition().above(dy);
                    if (c.level().isEmptyBlock(above)) {
                        FallingBlockEntity.fall(c.level(), above, (dy % 2 == 0 ? Blocks.SAND : Blocks.GRAVEL).defaultBlockState());
                    }
                }
            }),
            of("BAD-06", "sluggish_brew", BAD, -30, -10, 7, c -> {
                c.effect(MobEffects.SLOWNESS, 60, 1);
                c.effect(MobEffects.MINING_FATIGUE, 60, 1);
            }),
            of("BAD-07", "nausea_spin", BAD, -30, -10, 6, c -> {
                c.effect(MobEffects.NAUSEA, 30, 0);
                c.effect(MobEffects.WEAKNESS, 30, 0);
            }),
            of("BAD-08", "random_teleport", BAD, -40, -10, 6, Catalog::randomTeleport),
            of("BAD-09", "trapdoor_drop", BAD, -40, -15, 5, c -> {
                // A shaft opens under the player, through natural ground only (never through what players built).
                BlockPos feet = c.player().blockPosition();
                for (int dy = 1; dy <= 10; dy++) {
                    BlockPos p = feet.below(dy);
                    if (!natural(c.level().getBlockState(p))) {
                        break;
                    }
                    c.level().setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
                }
            }),
            of("BAD-10", "silent_creeper", BAD, -40, -20, 4, c -> {
                var creeper = c.spawn(EntityType.CREEPER, c.ring(1, 5).getFirst());
                if (creeper != null) {
                    creeper.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.INVISIBILITY, 20 * 120));
                    Scheduler.repeat(5, 480, i -> {
                        if (creeper.isAlive() && creeper.getSwellDir() > 0 && !creeper.hasEffect(MobEffects.GLOWING)) {
                            creeper.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.GLOWING, 60)); // fair warning
                        }
                    });
                }
            }),
            of("BAD-11", "yeet_held_item", BAD, -35, -10, 6, c -> {
                ItemStack held = c.player().getMainHandItem().copy();
                if (!held.isEmpty()) {
                    c.player().setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    Vec3 look = c.player().getLookAngle();
                    ItemEntity item = new ItemEntity(c.level(), c.player().getX(), c.player().getEyeY(), c.player().getZ(), held,
                            look.x * 1.4, 0.5, look.z * 1.4);
                    item.setExtendedLifetime();
                    item.setPickUpDelay(30);
                    c.level().addFreshEntity(item);
                }
            }),
            of("BAD-12", "pumpkin_head", BAD, -30, -10, 6, c -> {
                ItemStack helmet = c.player().getItemBySlot(EquipmentSlot.HEAD);
                if (!helmet.isEmpty()) {
                    c.player().drop(helmet.copy(), false);
                }
                ItemStack pumpkin = c.enchanted(Items.CARVED_PUMPKIN, Enchantments.BINDING_CURSE, 1);
                CompoundTag tag = new CompoundTag();
                tag.putBoolean(CURSED_TAG, true);
                pumpkin.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                c.player().setItemSlot(EquipmentSlot.HEAD, pumpkin);
                Scheduler.later(20 * 60, () -> {
                    if (isCursed(c.player().getItemBySlot(EquipmentSlot.HEAD))) {
                        c.player().setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                    }
                });
            }),
            of("BAD-13", "rotten_feast", BAD, -25, -10, 6, c -> {
                c.drop(Items.ROTTEN_FLESH, 16, 32);
                c.drop(Items.POISONOUS_POTATO, 4, 8);
            }),
            of("BAD-14", "vex_swarm", BAD, -40, -20, 4, c -> {
                for (var vex : c.spawnNear(EntityType.VEX, 4, 1)) {
                    vex.setLimitedLife(20 * 30);
                    vex.setTarget(c.player());
                }
            }),
            of("BAD-15", "endermite_burst", BAD, -30, -10, 5, c -> c.spawnNear(EntityType.ENDERMITE, c.between(4, 6), 2)),
            of("BAD-16", "cave_spider_pack", BAD, -40, -15, 5, c -> c.spawnRing(EntityType.CAVE_SPIDER, 5, 4)),
            of("BAD-17", "nightfall", BAD, -30, -10, 3, c -> c.command("time set night")).global(),
            of("BAD-18", "single_lightning_bolt", BAD, -35, -10, 6, c -> {
                var bolt = EntityType.LIGHTNING_BOLT.create(c.level(), EntitySpawnReason.EVENT);
                if (bolt != null) {
                    c.spawn(bolt, c.player().position());
                }
            }),
            of("BAD-19", "ring_of_fire", BAD, -40, -15, 5, c -> {
                BlockPos feet = c.player().blockPosition();
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        BlockPos spot = feet.offset(dx, 0, dz);
                        if (Math.max(Math.abs(dx), Math.abs(dz)) == 2 && c.level().isEmptyBlock(spot)
                                && BaseFireBlock.canBePlacedAt(c.level(), spot, net.minecraft.core.Direction.UP)) {
                            c.level().setBlockAndUpdate(spot, BaseFireBlock.getState(c.level(), spot));
                        }
                    }
                }
            }),
            of("BAD-20", "melting_ice_prison", BAD, -30, -10, 5, c -> {
                // Ice only into empty space around the player, gone again after 15 seconds.
                List<BlockPos> ice = new ArrayList<>();
                for (BlockPos p : shell(c.player().blockPosition(), 1, 2)) {
                    if (c.level().isEmptyBlock(p)) {
                        c.level().setBlockAndUpdate(p, Blocks.ICE.defaultBlockState());
                        ice.add(p);
                    }
                }
                Scheduler.later(300, () -> clear(c, ice, Blocks.ICE));
            }),
            of("BAD-21", "butterfingers", BAD, -25, -10, 5, c -> Scheduler.repeat(40, 30, i -> {
                if (c.playerHere() && c.chance(0.35) && !c.player().getMainHandItem().isEmpty()) {
                    ItemStack held = c.player().getMainHandItem().copy();
                    c.player().setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    c.player().drop(held, true);
                }
            })),
            of("BAD-22", "itchy_armor", BAD, -25, -10, 4, c -> Scheduler.repeat(60, 20, i -> {
                EquipmentSlot slot = c.pickOne(List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET));
                ItemStack worn = c.player().getItemBySlot(slot);
                if (c.playerHere() && c.chance(0.35) && !worn.isEmpty() && !isCursed(worn) && c.player().getInventory().getFreeSlot() >= 0) {
                    c.player().getInventory().add(worn.copy());
                    c.player().setItemSlot(slot, ItemStack.EMPTY);
                }
            })),
            of("BAD-23", "squid_ink", BAD, -20, -5, 6, c -> {
                c.level().sendParticles(ParticleTypes.SQUID_INK, c.player().getX(), c.player().getEyeY(), c.player().getZ(), 60, 0.5, 0.5, 0.5, 0.1);
                c.effect(MobEffects.BLINDNESS, 5, 0);
            }),
            of("BAD-24", "lucky_block_respawn", BAD, -25, -10, 5, c -> c.placeLuckyBlock(c.pos(), -20)).chain(),

            // ================= Neutral =================
            of("NEU-01", "nothing_happens", NEUTRAL, -10, 10, 10, c -> {
                c.level().sendParticles(ParticleTypes.POOF, c.pos().getX() + 0.5, c.pos().getY() + 0.5, c.pos().getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.02);
                c.level().playSound(null, c.pos(), SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 1.0f, 0.6f);
            }),
            of("NEU-02", "fortune_cookie", NEUTRAL, -10, 10, 8, c -> c.player().sendSystemMessage(
                    Component.translatable("outcome.rngambling.fortune_cookie." + c.random().nextInt(10)).withStyle(ChatFormatting.ITALIC))),
            of("NEU-03", "barnyard", NEUTRAL, -10, 10, 7, c -> {
                List<EntityType<?>> animals = List.of(EntityType.COW, EntityType.PIG, EntityType.SHEEP, EntityType.CHICKEN, EntityType.RABBIT, EntityType.GOAT);
                for (int i = c.between(4, 6); i > 0; i--) {
                    c.spawn(c.pickOne(animals), Vec3.atBottomCenterOf(c.freeSpotNear(c.pos(), 3)));
                }
            }),
            of("NEU-04", "rainbow_sheep", NEUTRAL, -10, 10, 5, c -> {
                var sheep = c.spawn(EntityType.SHEEP, Vec3.atBottomCenterOf(c.freeSpotNear(c.pos(), 2)));
                if (sheep != null) {
                    sheep.setCustomName(Component.literal("jeb_"));
                }
            }),
            of("NEU-05", "firework_show", NEUTRAL, -10, 15, 7, c -> fireworks(c, 6)),
            of("NEU-06", "random_building_blocks", NEUTRAL, -10, 10, 7, c -> c.drop(new ItemStack(c.pickOne(List.of(
                    Items.STONE_BRICKS, Items.POLISHED_ANDESITE, Items.BRICKS, Items.QUARTZ_BLOCK, Items.TERRACOTTA, Items.PRISMARINE,
                    Items.SMOOTH_SANDSTONE, Items.DEEPSLATE_TILES, Items.MUD_BRICKS, Items.CHERRY_PLANKS, Items.COPPER_BLOCK)), c.between(16, 64)))),
            of("NEU-07", "position_swap", NEUTRAL, -10, 10, 5, c -> {
                List<Mob> near = c.level().getEntitiesOfClass(Mob.class, c.player().getBoundingBox().inflate(16));
                near.stream().min(java.util.Comparator.comparingDouble(m -> m.distanceToSqr(c.player()))).ifPresent(mob -> {
                    Vec3 mine = c.player().position();
                    Vec3 theirs = mob.position();
                    c.player().teleportTo(theirs.x, theirs.y, theirs.z);
                    mob.teleportTo(mine.x, mine.y, mine.z);
                });
            }),
            of("NEU-08", "weather_roulette", NEUTRAL, -10, 10, 4, c -> c.command("weather " + c.pickOne(List.of("clear", "rain", "thunder")))).global(),
            of("NEU-09", "pig_rain", NEUTRAL, -10, 5, 5, c -> {
                for (int i = 0; i < 5; i++) {
                    var pig = c.spawn(EntityType.PIG, c.player().position().add(c.between(-4, 4), 15, c.between(-4, 4)));
                    if (pig != null) {
                        pig.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 20));
                    }
                }
            }),
            of("NEU-10", "mystery_potion", NEUTRAL, -10, 10, 7, c -> {
                var effect = c.pickOne(List.of(MobEffects.SPEED, MobEffects.SLOWNESS, MobEffects.HASTE, MobEffects.MINING_FATIGUE, MobEffects.JUMP_BOOST,
                        MobEffects.NAUSEA, MobEffects.REGENERATION, MobEffects.WEAKNESS, MobEffects.NIGHT_VISION, MobEffects.BLINDNESS,
                        MobEffects.STRENGTH, MobEffects.HUNGER));
                c.effect(effect, 30, 0);
            }),
            of("NEU-11", "size_shift", NEUTRAL, -10, 10, 5, c -> {
                AttributeInstance scale = c.player().getAttribute(Attributes.SCALE);
                if (scale != null) {
                    scale.removeModifier(SIZE_SHIFT);
                    scale.addTransientModifier(new AttributeModifier(SIZE_SHIFT, c.chance(0.5) ? -0.5 : 1.0,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)); // gone on log out too
                    Scheduler.later(20 * 60, () -> scale.removeModifier(SIZE_SHIFT));
                }
            }),
            of("NEU-12", "coin_flip", NEUTRAL, -10, 10, 6, c -> {
                boolean heads = c.chance(0.5);
                title(c.player(), Component.translatable(heads ? "outcome.rngambling.coin_flip.heads" : "outcome.rngambling.coin_flip.tails")
                        .withStyle(heads ? ChatFormatting.GREEN : ChatFormatting.RED));
                int luck = Math.max(-100, Math.min(100, c.luck() + (heads ? 30 : -30)));
                Scheduler.later(30, () -> {
                    if (c.playerHere()) {
                        Outcomes.playRandom(c.level(), c.pos(), c.player(), luck, c.chain(), "coin_flip");
                    }
                });
            }),
            of("NEU-13", "echo_blocks", NEUTRAL, -10, 10, 5, c -> {
                c.placeLuckyBlock(c.pos().east(2), c.between(-20, 20));
                c.placeLuckyBlock(c.pos().west(2), c.between(-20, 20));
            }).chain(),
            of("NEU-14", "wandering_trader", NEUTRAL, -10, 15, 5, c -> {
                c.spawnNear(EntityType.WANDERING_TRADER, 1, 3);
                c.spawnNear(EntityType.TRADER_LLAMA, 2, 3);
            }),
            of("NEU-18", "ghast_scream", NEUTRAL, -10, 0, 5, c -> c.level().playSound(null, c.player().blockPosition(),
                    SoundEvents.GHAST_SCREAM, SoundSource.HOSTILE, 2.0f, 1.0f)),
            of("NEU-19", "jukebox", NEUTRAL, -5, 15, 4, c -> {
                BlockPos spot = c.freeSpotNear(c.pos(), 2);
                if (c.level().isEmptyBlock(spot)) {
                    c.level().setBlockAndUpdate(spot, Blocks.JUKEBOX.defaultBlockState());
                    if (c.level().getBlockEntity(spot) instanceof net.minecraft.world.level.block.entity.JukeboxBlockEntity jukebox) {
                        jukebox.setTheItem(new ItemStack(c.pickOne(List.of(Items.MUSIC_DISC_CAT, Items.MUSIC_DISC_BLOCKS, Items.MUSIC_DISC_CHIRP,
                                Items.MUSIC_DISC_MALL, Items.MUSIC_DISC_STAL, Items.MUSIC_DISC_PIGSTEP, Items.MUSIC_DISC_OTHERSIDE))));
                    }
                    Scheduler.later(20 * 200, () -> {
                        if (c.level().getBlockState(spot).is(Blocks.JUKEBOX)) {
                            c.level().removeBlockEntity(spot); // the disc goes with it
                            c.level().setBlockAndUpdate(spot, Blocks.AIR.defaultBlockState());
                        }
                    });
                }
            }),
            of("NEU-22", "time_skip", NEUTRAL, -10, 10, 3, c -> c.command("time add 6000")).global(),
            of("NEU-23", "parrot_pal", NEUTRAL, -5, 15, 4, c -> {
                var parrot = c.spawn(EntityType.PARROT, c.player().position());
                if (parrot != null) {
                    parrot.tame(c.player());
                    parrot.setEntityOnShoulder(c.player());
                }
            }),

            // ================= Good =================
            of("GOD-01", "iron_haul", GOOD, 10, 40, 8, c -> c.drop(Items.IRON_INGOT, 16, 32)),
            of("GOD-02", "gold_haul", GOOD, 10, 40, 7, c -> c.drop(Items.GOLD_INGOT, 16, 32)),
            of("GOD-03", "food_basket", GOOD, 10, 35, 8, c -> {
                c.drop(Items.BREAD, 8, 16);
                c.drop(Items.COOKED_BEEF, 8, 16);
                c.drop(Items.GOLDEN_CARROT, 4, 8);
                c.drop(Items.CAKE, 1);
            }),
            of("GOD-04", "iron_tool_kit", GOOD, 10, 35, 7, c -> {
                for (Item tool : List.of(Items.IRON_SWORD, Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_SHOVEL, Items.IRON_HOE)) {
                    c.drop(tool, 1);
                }
            }),
            of("GOD-05", "xp_fountain", GOOD, 15, 40, 7, c -> Scheduler.repeat(4, 10, i ->
                    ExperienceOrb.award(c.level(), Vec3.atCenterOf(c.pos()).add(0, 1, 0), 16))),
            of("GOD-06", "potion_pack", GOOD, 15, 40, 6, c -> {
                c.drop(c.potion(Items.POTION, Potions.HEALING));
                c.drop(c.potion(Items.POTION, Potions.SWIFTNESS));
                c.drop(c.potion(Items.POTION, Potions.NIGHT_VISION));
            }),
            of("GOD-07", "wolf_pack", GOOD, 15, 40, 5, c -> {
                for (var wolf : c.spawnNear(EntityType.WOLF, 3, 2)) {
                    wolf.tame(c.player());
                }
            }),
            of("GOD-08", "saddled_horse", GOOD, 15, 40, 5, c -> horse(c, false)),
            of("GOD-09", "random_enchanted_book", GOOD, 15, 40, 6, c -> c.drop(EnchantmentHelper.enchantItem(c.random(), new ItemStack(Items.BOOK),
                    c.between(15, 30), c.level().registryAccess(), java.util.Optional.empty()))),
            of("GOD-10", "speed_demon", GOOD, 10, 35, 7, c -> {
                c.effect(MobEffects.SPEED, 180, 1);
                c.effect(MobEffects.HASTE, 180, 1);
            }),
            of("GOD-11", "emerald_pile", GOOD, 15, 40, 6, c -> c.drop(Items.EMERALD, 16, 32)),
            of("GOD-12", "ore_sampler", GOOD, 10, 35, 7, c -> {
                c.drop(Items.REDSTONE, 8, 16);
                c.drop(Items.LAPIS_LAZULI, 8, 16);
                c.drop(Items.COAL, 8, 16);
                c.drop(Items.COPPER_INGOT, 8, 16);
            }),
            of("GOD-14", "dungeon_chest", GOOD, 15, 40, 6, c -> c.loot("minecraft:chests/simple_dungeon").forEach(c::drop)),
            of("GOD-15", "builders_bundle", GOOD, 10, 30, 7, c -> {
                c.drop(Items.OAK_LOG, 32, 64);
                c.drop(Items.STONE_BRICKS, 32, 64);
                c.drop(Items.GLASS, 16, 32);
            }),
            of("GOD-16", "bloom_burst", GOOD, 10, 30, 5, c -> {
                for (int i = 0; i < 24; i++) {
                    BlockPos spot = c.ground(c.pos().getX() + c.between(-6, 6), c.pos().getZ() + c.between(-6, 6)).below();
                    net.minecraft.world.item.BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), c.level(), spot);
                }
                c.level().sendParticles(ParticleTypes.HAPPY_VILLAGER, c.pos().getX() + 0.5, c.pos().getY() + 0.5, c.pos().getZ() + 0.5, 40, 4, 1, 4, 0);
            }),
            of("GOD-18", "fortune_pickaxe", GOOD, 20, 40, 5, c -> c.drop(c.enchanted(Items.IRON_PICKAXE, Enchantments.FORTUNE, 2, Enchantments.EFFICIENCY, 2))),
            of("GOD-19", "lucky_rod", GOOD, 15, 35, 4, c -> c.drop(c.enchanted(Items.FISHING_ROD, Enchantments.LUCK_OF_THE_SEA, 3, Enchantments.LURE, 2))),
            of("GOD-20", "ender_pearl_stash", GOOD, 20, 40, 5, c -> c.drop(Items.ENDER_PEARL, 8)),
            of("GOD-21", "rancher_kit", GOOD, 10, 30, 5, c -> {
                c.drop(Items.NAME_TAG, 2);
                c.drop(Items.LEAD, 4);
                c.drop(Items.SADDLE, 1);
            }),
            of("GOD-22", "farmers_gift", GOOD, 10, 30, 6, c -> {
                c.drop(Items.WHEAT_SEEDS, 16);
                c.drop(Items.CARROT, 8);
                c.drop(Items.POTATO, 8);
                c.drop(Items.BEETROOT_SEEDS, 8);
                c.drop(Items.BONE_MEAL, 16);
            }),
            of("GOD-23", "archers_kit", GOOD, 15, 35, 5, c -> {
                c.drop(c.enchanted(Items.BOW, Enchantments.POWER, 2));
                c.drop(Items.ARROW, 64);
            }),
            of("GOD-24", "allay_helper", GOOD, 20, 40, 4, c -> {
                var allay = c.spawn(EntityType.ALLAY, c.player().position().add(0, 1, 0));
                if (allay != null) {
                    allay.getBrain().setMemory(MemoryModuleType.LIKED_PLAYER, c.player().getUUID());
                }
            }),
            of("GOD-25", "healing_rain", GOOD, 10, 30, 5, c -> {
                c.effect(MobEffects.REGENERATION, 20, 1);
                c.player().getFoodData().setFoodLevel(20);
                c.player().getFoodData().setSaturation(10);
                c.level().sendParticles(ParticleTypes.HEART, c.player().getX(), c.player().getY() + 2, c.player().getZ(), 15, 1, 0.5, 1, 0);
            }),
            of("GOD-26", "mini_golden_apples", GOOD, 20, 40, 5, c -> c.drop(Items.GOLDEN_APPLE, 3)),
            of("GOD-27", "cat_companion", GOOD, 10, 30, 4, c -> {
                for (var cat : c.spawnNear(EntityType.CAT, 1, 2)) {
                    cat.tame(c.player());
                }
            }),
            of("GOD-28", "bonus_lucky_blocks", GOOD, 15, 40, 4, c -> {
                c.placeLuckyBlock(c.pos().east(2), c.between(20, 50));
                c.placeLuckyBlock(c.pos().west(2), c.between(20, 50));
            }).chain(),

            // ================= Very good =================
            of("VGD-01", "diamond_haul", VERY_GOOD, 41, 75, 7, c -> c.drop(Items.DIAMOND, 8 + (int) Math.round(8 * c.strength()))),
            of("VGD-02", "diamond_armor_set", VERY_GOOD, 45, 75, 5, c -> {
                for (Item piece : List.of(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS)) {
                    c.drop(c.enchanted(piece, Enchantments.PROTECTION, 2));
                }
            }),
            of("VGD-03", "enchanted_diamond_sword", VERY_GOOD, 45, 75, 5, c ->
                    c.drop(c.enchanted(Items.DIAMOND_SWORD, Enchantments.SHARPNESS, 3, Enchantments.LOOTING, 2))),
            of("VGD-04", "totem_of_undying", VERY_GOOD, 50, 80, 5, c -> c.drop(Items.TOTEM_OF_UNDYING, 1)),
            of("VGD-05", "enchanted_golden_apple", VERY_GOOD, 55, 85, 4, c -> c.drop(Items.ENCHANTED_GOLDEN_APPLE, 1)),
            of("VGD-06", "xp_jackpot", VERY_GOOD, 45, 75, 6, c -> Scheduler.repeat(3, 20, i ->
                    ExperienceOrb.award(c.level(), Vec3.atCenterOf(c.pos()).add(0, 1, 0), 70))),
            of("VGD-07", "shulker_loot_box", VERY_GOOD, 50, 80, 4, c -> {
                List<ItemStack> loot = List.of(new ItemStack(Items.DIAMOND, c.between(4, 8)), new ItemStack(Items.GOLD_BLOCK, c.between(2, 4)),
                        new ItemStack(Items.GOLDEN_APPLE, c.between(2, 4)), new ItemStack(Items.EMERALD, c.between(8, 16)),
                        new ItemStack(Items.EXPERIENCE_BOTTLE, c.between(8, 16)), new ItemStack(Items.ENDER_PEARL, c.between(4, 8)),
                        EnchantmentHelper.enchantItem(c.random(), new ItemStack(Items.BOOK), 30, c.level().registryAccess(), java.util.Optional.empty()));
                ItemStack box = new ItemStack(Items.PURPLE_SHULKER_BOX);
                box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(loot));
                c.drop(box);
            }),
            of("VGD-08", "trident", VERY_GOOD, 50, 75, 4, c -> c.drop(c.enchanted(Items.TRIDENT, Enchantments.LOYALTY, 2))),
            of("VGD-09", "treasure_vault", VERY_GOOD, 45, 75, 5, c -> room(c, new Rooms.TreasureVault())).room(),
            of("VGD-10", "parkour_course", VERY_GOOD, 41, 70, 5, c -> room(c, new Rooms.ParkourCourse())).room(),
            of("VGD-11", "puzzle_vault", VERY_GOOD, 41, 70, 4, c -> room(c, new Rooms.PuzzleVault())).room(),
            of("VGD-12", "beacon_starter", VERY_GOOD, 55, 80, 4, c -> c.drop(Items.IRON_BLOCK, 9)),
            of("VGD-13", "mending_book", VERY_GOOD, 50, 80, 5, c -> c.drop(c.enchanted(Items.ENCHANTED_BOOK, Enchantments.MENDING, 1))),
            of("VGD-14", "champions_brew", VERY_GOOD, 41, 75, 6, c -> {
                c.effect(MobEffects.STRENGTH, 300, 1);
                c.effect(MobEffects.REGENERATION, 300, 1);
                c.effect(MobEffects.RESISTANCE, 300, 1);
            }),
            of("VGD-15", "golden_rain", VERY_GOOD, 41, 70, 6, c -> Scheduler.repeat(5, 40, i -> {
                if (c.playerHere()) {
                    Vec3 at = c.player().position().add(c.between(-3, 3), 6, c.between(-3, 3));
                    c.level().addFreshEntity(new ItemEntity(c.level(), at.x, at.y, at.z,
                            c.chance(0.3) ? new ItemStack(Items.GOLD_INGOT) : new ItemStack(Items.GOLD_NUGGET, 3)));
                }
            })),
            of("VGD-16", "lucky_fountain", VERY_GOOD, 50, 75, 4, c -> {
                for (int i = 0; i < 3; i++) {
                    c.placeLuckyBlock(c.pos().offset(c.between(-2, 2), 0, c.between(-2, 2)), c.between(40, 80));
                }
            }).chain(),
            of("VGD-17", "ancient_debris", VERY_GOOD, 55, 80, 4, c -> c.drop(Items.ANCIENT_DEBRIS, 4)),
            of("VGD-18", "warhorse", VERY_GOOD, 45, 75, 4, c -> horse(c, true)),
            of("VGD-20", "enchanted_bow", VERY_GOOD, 45, 75, 4, c ->
                    c.drop(c.enchanted(Items.BOW, Enchantments.POWER, 4, Enchantments.PUNCH, 1, Enchantments.INFINITY, 1))),
            of("VGD-21", "hero_of_the_village", VERY_GOOD, 41, 70, 4, c -> c.effect(MobEffects.HERO_OF_THE_VILLAGE, 20 * 60, 4)),
            of("VGD-22", "mob_repellent", VERY_GOOD, 45, 75, 4, c -> EffectEvents.repelMobs(c.player(), 20 * 60 * 10)),
            of("VGD-23", "ocean_heart", VERY_GOOD, 50, 80, 3, c -> {
                c.drop(Items.HEART_OF_THE_SEA, 1);
                c.drop(Items.NAUTILUS_SHELL, 8);
            }),

            // ================= Legendary =================
            of("LEG-01", "netherite_armor_set", LEGENDARY, 80, 100, 4, c -> {
                for (Item piece : List.of(Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS)) {
                    c.drop(c.enchanted(piece, Enchantments.PROTECTION, 4, Enchantments.UNBREAKING, 3));
                }
            }),
            of("LEG-02", "god_sword", LEGENDARY, 85, 100, 3, c -> c.drop(c.enchanted(Items.NETHERITE_SWORD, Enchantments.SHARPNESS, 5,
                    Enchantments.LOOTING, 3, Enchantments.FIRE_ASPECT, 2, Enchantments.MENDING, 1))),
            of("LEG-03", "elytra_rockets", LEGENDARY, 80, 100, 4, c -> {
                c.drop(c.enchanted(Items.ELYTRA, Enchantments.UNBREAKING, 3));
                c.drop(Items.FIREWORK_ROCKET, 64);
            }),
            of("LEG-04", "complete_beacon", LEGENDARY, 85, 100, 3, c -> {
                c.drop(Items.BEACON, 1);
                c.drop(Items.IRON_BLOCK, 164);
            }),
            of("LEG-05", "nether_star", LEGENDARY, 76, 100, 3, c -> c.drop(Items.NETHER_STAR, 1)),
            of("LEG-06", "treasure_island", LEGENDARY, 76, 100, 4, c -> room(c, new Rooms.TreasureIsland())).room(),
            of("LEG-07", "boss_bounty", LEGENDARY, 76, 100, 3, c -> Bounty.offer(c)).room(),
            of("LEG-08", "heart_container", LEGENDARY, 85, 100, 2, c -> {
                AttributeInstance health = c.player().getAttribute(Attributes.MAX_HEALTH);
                if (health != null) {
                    AttributeModifier old = health.getModifier(BONUS_HEARTS);
                    double extra = Math.min(2.0 * RNGamblingConfig.MAX_BONUS_HEARTS.getAsInt(), (old == null ? 0 : old.amount()) + 2);
                    health.addOrReplacePermanentModifier(new AttributeModifier(BONUS_HEARTS, extra, AttributeModifier.Operation.ADD_VALUE));
                    c.player().heal(2);
                }
            }),
            of("LEG-10", "lucky_pinata", LEGENDARY, 80, 100, 3, c -> {
                for (int i = 0; i < 10; i++) {
                    c.placeLuckyBlock(c.pos().offset(c.between(-3, 3), c.between(0, 1), c.between(-3, 3)), c.between(60, 100));
                }
                fireworks(c, 3);
            }).chain(),
            of("LEG-12", "golden_feast", LEGENDARY, 76, 100, 4, c -> {
                c.effect(MobEffects.SATURATION, 600, 0);
                c.effect(MobEffects.REGENERATION, 600, 0);
            }),
            of("LEG-13", "enchanters_study", LEGENDARY, 76, 100, 3, c -> {
                c.drop(Items.ENCHANTING_TABLE, 1);
                c.drop(Items.BOOKSHELF, 15);
                c.drop(Items.LAPIS_LAZULI, 64);
            }),
            of("LEG-14", "perfect_pickaxe", LEGENDARY, 85, 100, 3, c -> c.drop(c.enchanted(Items.NETHERITE_PICKAXE, Enchantments.EFFICIENCY, 5,
                    Enchantments.FORTUNE, 3, Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1))),
            of("LEG-15", "diamond_pillar", LEGENDARY, 90, 100, 2, c -> {
                // Rises in the Sandbox, then breaks into items that come back with the player.
                BlockPos base = c.player().blockPosition().offset(3, 0, 0);
                Scheduler.repeat(6, 6, i -> c.level().setBlockAndUpdate(base.above(i), Blocks.DIAMOND_BLOCK.defaultBlockState()));
                Scheduler.later(90, () -> {
                    for (int i = 0; i < 6; i++) {
                        c.level().destroyBlock(base.above(i), true);
                    }
                });
            }).inSandbox(120),
            of("LEG-17", "shulker_stash", LEGENDARY, 80, 100, 3, c -> {
                List<Item> boxes = List.of(Items.RED_SHULKER_BOX, Items.BLUE_SHULKER_BOX, Items.GREEN_SHULKER_BOX, Items.YELLOW_SHULKER_BOX,
                        Items.PURPLE_SHULKER_BOX, Items.ORANGE_SHULKER_BOX, Items.CYAN_SHULKER_BOX, Items.PINK_SHULKER_BOX);
                for (int i = 0; i < 5; i++) {
                    c.drop(c.pickOne(boxes), 1);
                }
            }),
            of("LEG-18", "netherite_ingot_haul", LEGENDARY, 85, 100, 3, c -> c.drop(Items.NETHERITE_INGOT, 4)),

            // ================= Wildcards =================
            of("WLD-01", "scaled_loot_chest", WILDCARD, -20, 100, 6, c -> {
                String table = c.luck() < 10 ? "minecraft:chests/simple_dungeon" : c.luck() < 40 ? "minecraft:chests/abandoned_mineshaft"
                        : c.luck() < 70 ? "minecraft:chests/desert_pyramid" : "minecraft:chests/end_city_treasure";
                c.loot(table).forEach(c::drop);
            }),
            of("WLD-03", "mystery_box_mob", WILDCARD, -60, 60, 4, c -> {
                if (c.luck() < 0) {
                    c.spawnNear(c.pickOne(List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER, EntityType.WITCH)),
                            1 + (int) Math.round(2 * c.strength()), 3);
                } else {
                    var pet = c.spawn(c.pickOne(List.<EntityType<? extends TamableAnimal>>of(EntityType.WOLF, EntityType.CAT, EntityType.PARROT)),
                            Vec3.atBottomCenterOf(c.freeSpotNear(c.pos(), 2)));
                    if (pet != null) {
                        pet.tame(c.player());
                    }
                }
            }),
            of("WLD-04", "scaled_explosion", WILDCARD, -100, -20, 4, c -> {
                double over = Math.max(0, Math.min(1, (-c.luck() - 20) / 80.0));
                c.explode(Vec3.atCenterOf(c.pos()), (float) (2 + 4 * over), false);
            })
    );

    // ---- helpers ----

    /** Starts a Sandbox room for the player. (Rooms are only drawn when the Sandbox is available.) */
    private static void room(OutcomeContext c, Room room) {
        Sandbox.startRoom(c.player(), room, c.luck());
    }

    private static void title(ServerPlayer player, Component text) {
        player.connection.send(new ClientboundSetTitleTextPacket(text));
    }

    public static boolean isCursed(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBooleanOr(CURSED_TAG, false);
    }

    /** Ground players didn't build: stone, dirt, sand, gravel and the like. Effects that dig only dig this. */
    static boolean natural(BlockState state) {
        return !state.hasBlockEntity() && state.getBlock().defaultDestroyTime() >= 0
                && (state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.DIRT) || state.is(BlockTags.SAND)
                || state.is(Blocks.GRAVEL) || state.is(BlockTags.BASE_STONE_NETHER) || state.is(Blocks.END_STONE)
                || state.is(BlockTags.TERRACOTTA) || state.is(Blocks.CLAY) || state.is(Blocks.SANDSTONE));
    }

    /** The blocks of a hollow box around a player standing at {@code feet}: {@code half} out, {@code height} tall inside. */
    static List<BlockPos> shell(BlockPos feet, int half, int height) {
        List<BlockPos> blocks = new ArrayList<>();
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                for (int dy = -1; dy <= height; dy++) {
                    if (Math.abs(dx) == half || Math.abs(dz) == half || dy == -1 || dy == height) {
                        blocks.add(feet.offset(dx, dy, dz));
                    }
                }
            }
        }
        return blocks;
    }

    /** Removes blocks an effect placed, if they're still that block. */
    static void clear(OutcomeContext c, List<BlockPos> placed, Block block) {
        for (BlockPos p : placed) {
            if (c.level().getBlockState(p).is(block)) {
                c.level().setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
            }
        }
    }

    private static final int[] COLOURS = {0xFF3B3B, 0xFFD23B, 0x3BFF6B, 0x3BB8FF, 0xC43BFF, 0xFFFFFF};

    static void fireworks(OutcomeContext c, int count) {
        for (int i = 0; i < count; i++) {
            ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
            FireworkExplosion burst = new FireworkExplosion(
                    c.random().nextBoolean() ? FireworkExplosion.Shape.LARGE_BALL : FireworkExplosion.Shape.STAR,
                    IntList.of(COLOURS[c.random().nextInt(COLOURS.length)], COLOURS[c.random().nextInt(COLOURS.length)]),
                    IntList.of(), true, true);
            rocket.set(DataComponents.FIREWORKS, new Fireworks(c.between(1, 2), List.of(burst)));
            Vec3 at = Vec3.atBottomCenterOf(c.pos()).add(c.between(-3, 3), 0, c.between(-3, 3));
            c.level().addFreshEntity(new FireworkRocketEntity(c.level(), at.x, at.y, at.z, rocket));
        }
    }

    private static void horse(OutcomeContext c, boolean war) {
        var horse = c.spawn(EntityType.HORSE, Vec3.atBottomCenterOf(c.freeSpotNear(c.pos(), 3)));
        if (horse == null) {
            return;
        }
        horse.tameWithName(c.player());
        horse.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
        horse.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(war ? 0.335 : 0.28);
        horse.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(war ? 0.95 : 0.8);
        horse.getAttribute(Attributes.MAX_HEALTH).setBaseValue(war ? 30 : 26);
        horse.setHealth(horse.getMaxHealth());
        if (war) {
            horse.setItemSlot(EquipmentSlot.BODY, new ItemStack(Items.DIAMOND_HORSE_ARMOR));
        }
    }

    private static void randomTeleport(OutcomeContext c) {
        for (int tries = 0; tries < 24; tries++) {
            int x = c.player().getBlockX() + c.between(-50, 50);
            int z = c.player().getBlockZ() + c.between(-50, 50);
            BlockPos top = c.ground(x, z);
            BlockState below = c.level().getBlockState(top.below());
            if (top.getY() > c.level().getMinY() + 1 && below.getFluidState().isEmpty() && !below.is(Blocks.MAGMA_BLOCK)
                    && !below.isAir() && c.level().isEmptyBlock(top) && c.level().isEmptyBlock(top.above())) {
                c.player().teleportTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5);
                return;
            }
        }
    }

    private static void meteor(OutcomeContext c) {
        BlockPos target = c.ground(c.player().getBlockX() + c.between(-8, 8), c.player().getBlockZ() + c.between(-8, 8));
        c.level().playSound(null, c.player().blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 1.5f, 0.5f);
        Vec3 from = Vec3.atCenterOf(target).add(12, 40, 12);
        Vec3 to = Vec3.atCenterOf(target);
        Scheduler.repeat(2, 20, i -> {
            Vec3 at = from.lerp(to, (i + 1) / 20.0);
            c.level().sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 30, 0.6, 0.6, 0.6, 0.05);
            c.level().sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 10, 0.6, 0.6, 0.6, 0.02);
            if (i == 19) {
                c.explode(to, 6f, true);
            }
        });
    }

    private static void crater(OutcomeContext c) {
        // A sphere of radius 7 under the player is taken away, natural ground only (never what players built).
        BlockPos centre = c.player().blockPosition().below(6);
        for (BlockPos p : BlockPos.betweenClosed(centre.offset(-7, -7, -7), centre.offset(7, 7, 7))) {
            if (p.distSqr(centre) <= 49 && natural(c.level().getBlockState(p))) {
                c.level().setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        c.level().playSound(null, c.player().blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 2.0f, 0.5f);
    }

    /** For the bounding box of sandbox clean-up and tests. */
    static AABB around(BlockPos pos, double radius) {
        return new AABB(pos).inflate(radius);
    }
}
