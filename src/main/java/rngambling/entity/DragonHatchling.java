package rngambling.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * LEG-16 Dragon Hatchling: a small tame dragon that walks with its owner and fights for them,
 * like a wolf (it is one underneath: sitting, following, defending and healing on meat all work
 * the same). Tougher than a wolf, immune to fire, and its bite sets things alight. It can't
 * breed, and it doesn't fly.
 */
public class DragonHatchling extends Wolf {
    public static final double HEALTH = 50, DAMAGE = 6;

    public DragonHatchling(EntityType<? extends Wolf> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Wolf.createAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.ATTACK_DAMAGE, DAMAGE)
                .add(Attributes.ARMOR, 4).add(Attributes.MOVEMENT_SPEED, 0.32);
    }

    /** A wolf's taming sets its health to a wolf's; a hatchling keeps its own. */
    @Override
    protected void applyTamingSideEffects() {
        super.applyTamingSideEffects();
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(HEALTH);
        if (isTame()) {
            setHealth((float) HEALTH);
        }
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit) {
            target.igniteForSeconds(3);
        }
        return hit;
    }

    @Override
    public @Nullable Wolf getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    @Override
    public boolean canMate(Animal partner) {
        return false;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return isAngry() || random.nextInt(3) == 0 ? SoundEvents.PARROT_IMITATE_ENDER_DRAGON : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENDER_DRAGON_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PARROT_IMITATE_ENDER_DRAGON;
    }

    @Override
    protected float getSoundVolume() {
        return 0.5f;
    }

    @Override
    public float getVoicePitch() {
        return 1.5f + (random.nextFloat() - 0.5f) * 0.2f;
    }
}
