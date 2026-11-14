package rngambling.outcome;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import rngambling.RNGambling;
import rngambling.luck.Luck;

/**
 * Every outcome a lucky block can have, and the luck-weighted draw between them.
 * To add one, add a line to {@link #ALL} and a message to the language file.
 */
public final class Outcomes {
    private Outcomes() {
    }

    public static final List<Outcome> ALL = List.of(
            // ---- Good ----
            new Outcome("tokens", 14, 4, c -> c.drop(new ItemStack(RNGambling.CASINO_TOKEN.get(), c.between(2, 6)))),
            new Outcome("token_jackpot", 2, 10, c -> {
                c.drop(new ItemStack(RNGambling.CASINO_TOKEN.get(), c.between(16, 32)));
                fireworks(c, 5);
            }),
            new Outcome("diamonds", 5, 7, c -> c.drop(new ItemStack(Items.DIAMOND, c.between(1, 3)))),
            new Outcome("emeralds", 6, 4, c -> c.drop(new ItemStack(Items.EMERALD, c.between(3, 8)))),
            new Outcome("ingots", 8, 3, c -> {
                c.drop(new ItemStack(Items.IRON_INGOT, c.between(4, 10)));
                c.drop(new ItemStack(Items.GOLD_INGOT, c.between(2, 6)));
            }),
            new Outcome("golden_apples", 4, 6, c -> c.drop(new ItemStack(Items.GOLDEN_APPLE, c.between(1, 2)))),
            new Outcome("enchanted_gear", 4, 7, Outcomes::enchantedGear),
            new Outcome("experience", 8, 3, c -> ExperienceOrb.award(c.level(), Vec3.atCenterOf(c.pos()), c.between(20, 60))),
            new Outcome("feast", 7, 2, c -> {
                c.drop(new ItemStack(Items.COOKED_BEEF, c.between(4, 8)));
                c.drop(new ItemStack(Items.BREAD, c.between(3, 6)));
                c.drop(new ItemStack(Items.CAKE));
            }),
            new Outcome("blessing", 6, 4, c -> {
                ServerPlayer p = c.player();
                p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 15, 1));
                p.addEffect(new MobEffectInstance(MobEffects.SPEED, 20 * 60, 1));
                p.addEffect(new MobEffectInstance(MobEffects.HASTE, 20 * 60, 1));
                p.addEffect(new MobEffectInstance(MobEffects.LUCK, 20 * 120, 0));
            }),

            // ---- Neither here nor there ----
            new Outcome("fireworks", 5, 1, c -> fireworks(c, 3)),
            new Outcome("chickens", 5, 0, c -> {
                for (int i = c.between(5, 8); i > 0; i--) {
                    EntityType.CHICKEN.spawn(c.level(), c.freeSpotNear(c.pos(), 2), EntitySpawnReason.EVENT);
                }
                c.drop(new ItemStack(Items.FEATHER, c.between(3, 8)));
            }),

            // ---- Bad ----
            new Outcome("tnt", 6, -5, c -> {
                PrimedTnt tnt = new PrimedTnt(c.level(), c.pos().getX() + 0.5, c.pos().getY(), c.pos().getZ() + 0.5, null);
                tnt.setFuse(40);
                c.level().addFreshEntity(tnt);
            }).inSandbox(),
            new Outcome("tnt_ring", 2, -8, c -> {
                Vec3 at = c.player().position();
                int count = c.between(4, 6);
                for (int i = 0; i < count; i++) {
                    double angle = 2 * Math.PI * i / count;
                    PrimedTnt tnt = new PrimedTnt(c.level(), at.x + 3 * Math.cos(angle), at.y + 1, at.z + 3 * Math.sin(angle), null);
                    tnt.setFuse(c.between(60, 80));
                    c.level().addFreshEntity(tnt);
                }
            }).inSandbox(),
            new Outcome("zombies", 6, -5, c -> {
                for (int i = c.between(3, 5); i > 0; i--) {
                    EntityType.ZOMBIE.spawn(c.level(), c.freeSpotNear(c.pos(), 3), EntitySpawnReason.EVENT);
                }
            }),
            new Outcome("creeper", 5, -6, c -> EntityType.CREEPER.spawn(c.level(), c.freeSpotNear(c.pos(), 2), EntitySpawnReason.EVENT)).inSandbox(),
            new Outcome("silverfish", 5, -4, c -> {
                for (int i = c.between(4, 7); i > 0; i--) {
                    EntityType.SILVERFISH.spawn(c.level(), c.freeSpotNear(c.pos(), 2), EntitySpawnReason.EVENT);
                }
            }).inSandbox(),
            new Outcome("curse", 6, -4, c -> {
                ServerPlayer p = c.player();
                p.addEffect(new MobEffectInstance(MobEffects.POISON, 20 * 8, 0));
                p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20 * 30, 1));
                p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20 * 8, 0));
                p.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 20 * 12, 0));
            }),
            new Outcome("lightning", 4, -5, c -> {
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(c.level(), EntitySpawnReason.EVENT);
                if (bolt != null) {
                    c.spawn(bolt, c.player().position());
                }
            }).inSandbox(),
            new Outcome("anvil", 4, -5, c -> {
                BlockPos above = c.player().blockPosition().above(8);
                if (c.level().isEmptyBlock(above)) {
                    FallingBlockEntity anvil = FallingBlockEntity.fall(c.level(), above, Blocks.ANVIL.defaultBlockState());
                    anvil.setHurtsEntities(2.0f, 40);
                }
            }).inSandbox(),
            new Outcome("cobwebs", 5, -3, c -> {
                BlockPos feet = c.player().blockPosition();
                for (BlockPos spot : BlockPos.betweenClosed(feet.offset(-1, 0, -1), feet.offset(1, 1, 1))) {
                    if (c.level().isEmptyBlock(spot)) {
                        c.level().setBlockAndUpdate(spot, Blocks.COBWEB.defaultBlockState());
                    }
                }
            }).inSandbox(),
            new Outcome("launch", 3, -7, c -> {
                ServerPlayer p = c.player();
                p.setDeltaMovement(p.getDeltaMovement().x, 2.4, p.getDeltaMovement().z);
                p.hurtMarked = true;
            })
    );

    /**
     * Picks an outcome for this luck, themed by the luck zone where the block is broken (see
     * {@link Themes}), and runs it, telling the player what happened.
     */
    public static Outcome roll(ServerLevel level, BlockPos pos, ServerPlayer player, int luck) {
        String zone = rngambling.luck.LuckZone.name(level, rngambling.luck.LuckZone.at(level, pos));
        Outcome outcome = pick(level.getRandom(), luck, Themes.outcomesFor(zone));
        play(level, pos, player, luck, outcome);
        return outcome;
    }

    /** Plays one outcome for a player, in the Sandbox if it's one that would change the world. */
    public static void play(ServerLevel level, BlockPos pos, ServerPlayer player, int luck, Outcome outcome) {
        // Outcomes that would change the world are played out in the Sandbox instead.
        if (outcome.sandbox()) {
            Sandbox.Arena arena = Sandbox.start(player);
            if (arena != null) {
                level = arena.level();
                pos = arena.centre();
            }
        }
        outcome.action().run(new OutcomeContext(level, pos.immutable(), player, level.getRandom(), Luck.clamp(luck)));
        ChatFormatting colour = outcome.rating() > 0 ? ChatFormatting.GREEN
                : outcome.rating() < 0 ? ChatFormatting.RED : ChatFormatting.YELLOW;
        player.sendOverlayMessage(Component.translatable("outcome.rngambling." + outcome.id()).withStyle(colour));
        level.playSound(null, pos, outcome.rating() > 0 ? SoundEvents.PLAYER_LEVELUP : SoundEvents.WITCH_CELEBRATE,
                SoundSource.BLOCKS, 0.6f, 1.2f);
    }

    /** A weighted draw, with each outcome's weight tilted by luck (see {@link Luck#weightFactor}). */
    public static Outcome pick(RandomSource random, int luck) {
        return pick(random, luck, ALL);
    }

    public static Outcome pick(RandomSource random, int luck, List<Outcome> outcomes) {
        double total = 0;
        for (Outcome outcome : outcomes) {
            total += outcome.weight() * Luck.weightFactor(luck, outcome.rating());
        }
        double roll = random.nextDouble() * total;
        for (Outcome outcome : outcomes) {
            roll -= outcome.weight() * Luck.weightFactor(luck, outcome.rating());
            if (roll < 0) {
                return outcome;
            }
        }
        return outcomes.getLast();
    }

    /** Spawns {@code min} to {@code max} of a mob near where the block was. */
    static void spawnMobs(OutcomeContext c, net.minecraft.world.entity.EntityType<?> type, int min, int max, int radius) {
        for (int i = c.between(min, max); i > 0; i--) {
            type.spawn(c.level(), c.freeSpotNear(c.pos(), radius), EntitySpawnReason.EVENT);
        }
    }

    private static final Item[] GEAR = {
            Items.DIAMOND_SWORD, Items.DIAMOND_PICKAXE, Items.IRON_SWORD, Items.IRON_PICKAXE, Items.IRON_AXE,
            Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS, Items.BOW, Items.BOOK,
    };

    private static void enchantedGear(OutcomeContext c) {
        ItemStack gear = new ItemStack(GEAR[c.random().nextInt(GEAR.length)]);
        c.drop(EnchantmentHelper.enchantItem(c.random(), gear, c.between(15, 30), c.level().registryAccess(), Optional.empty()));
    }

    private static final int[] COLOURS = {0xFF3B3B, 0xFFD23B, 0x3BFF6B, 0x3BB8FF, 0xC43BFF, 0xFFFFFF};

    private static void fireworks(OutcomeContext c, int count) {
        for (int i = 0; i < count; i++) {
            ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
            FireworkExplosion burst = new FireworkExplosion(
                    c.random().nextBoolean() ? FireworkExplosion.Shape.LARGE_BALL : FireworkExplosion.Shape.STAR,
                    IntList.of(COLOURS[c.random().nextInt(COLOURS.length)], COLOURS[c.random().nextInt(COLOURS.length)]),
                    IntList.of(), true, true);
            rocket.set(DataComponents.FIREWORKS, new Fireworks(c.between(1, 2), List.of(burst)));
            Vec3 at = Vec3.atBottomCenterOf(c.pos()).add(c.between(-2, 2), 0, c.between(-2, 2));
            c.level().addFreshEntity(new FireworkRocketEntity(c.level(), at.x, at.y, at.z, rocket));
        }
    }
}
