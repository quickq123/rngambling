package rngambling.luck;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * The vanilla Luck and Bad Luck (unluck) effects, as the mod reads them. A player's luck level is
 * their Luck level minus their Bad Luck level, each counted up to II, so -2 to +2. Each level adds
 * {@link #PER_LEVEL} to a lucky block's luck when they break it (see LuckyBlock), and tilts the
 * casino games (see CasinoLuck).
 */
public final class PlayerLuck {
    /** Added to a lucky block's luck per level of Luck (taken away per level of Bad Luck). */
    public static final int PER_LEVEL = 10;
    public static final int MAX_LEVEL = 2;

    private PlayerLuck() {
    }

    /** -2 to +2: Luck's level minus Bad Luck's, each up to II. */
    public static int level(LivingEntity entity) {
        return levelOf(entity, MobEffects.LUCK) - levelOf(entity, MobEffects.UNLUCK);
    }

    private static int levelOf(LivingEntity entity, Holder<MobEffect> effect) {
        MobEffectInstance instance = entity.getEffect(effect);
        return instance == null ? 0 : Math.min(MAX_LEVEL, instance.getAmplifier() + 1);
    }

    /** What the player's luck adds to a lucky block's luck: +10 or +20, -10 or -20. */
    public static int blockBonus(LivingEntity entity) {
        return PER_LEVEL * level(entity);
    }
}
