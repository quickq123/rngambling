package rngambling.outcome;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.TriState;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.core.component.DataComponents;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * Effects that last a while and react to the game: Fool's Diamonds (picked up, they're
 * silverfish), Cursed Mining (breaking blocks may set off silverfish or a small blast) and Mob
 * Repellent (no hostile mobs spawn near the player). Kept in memory; a restart ends them early.
 */
public final class EffectEvents {
    private static final Map<UUID, Long> CURSED_MINING = new HashMap<>();
    private static final Map<UUID, Long> REPELLING = new HashMap<>();

    private EffectEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EffectEvents::onPickup);
        NeoForge.EVENT_BUS.addListener(EffectEvents::onBreak);
        NeoForge.EVENT_BUS.addListener(EffectEvents::onSpawn);
    }

    static void curseMining(ServerPlayer player, int ticks) {
        CURSED_MINING.put(player.getUUID(), player.level().getGameTime() + ticks);
    }

    static void repelMobs(ServerPlayer player, int ticks) {
        REPELLING.put(player.getUUID(), player.level().getGameTime() + ticks);
    }

    private static boolean active(Map<UUID, Long> effects, Player player) {
        Long until = effects.get(player.getUUID());
        if (until == null) {
            return false;
        }
        if (player.level().getGameTime() > until) {
            effects.remove(player.getUUID());
            return false;
        }
        return true;
    }

    /** Fool's Diamonds: the "diamond" vanishes and a silverfish takes its place. */
    private static void onPickup(ItemEntityPickupEvent.Pre event) {
        ItemStack stack = event.getItemEntity().getItem();
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null && data.copyTag().getBooleanOr(Catalog.FOOLS_GOLD_TAG, false) && event.getItemEntity().level() instanceof ServerLevel level) {
            event.setCanPickup(TriState.FALSE);
            for (int i = 0; i < stack.getCount(); i++) {
                EntityType.SILVERFISH.spawn(level, event.getItemEntity().blockPosition(), EntitySpawnReason.EVENT);
            }
            event.getItemEntity().discard();
        }
    }

    /** Cursed Mining: a quarter of the blocks broken set off a silverfish or a small blast. */
    private static void onBreak(BreakBlockEvent event) {
        Player player = event.getPlayer();
        if (player instanceof ServerPlayer && active(CURSED_MINING, player) && player.level() instanceof ServerLevel level
                && level.getRandom().nextFloat() < 0.25f) {
            BlockPos pos = event.getPos();
            if (level.getRandom().nextBoolean()) {
                EntityType.SILVERFISH.spawn(level, pos, EntitySpawnReason.EVENT);
            } else {
                level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1.5f, Level.ExplosionInteraction.TNT);
            }
        }
    }

    /** Mob Repellent: no natural hostile spawns within 32 blocks of a repelling player. */
    private static void onSpawn(FinalizeSpawnEvent event) {
        if (REPELLING.isEmpty() || !(event.getEntity() instanceof Enemy) || event.getSpawnType() != EntitySpawnReason.NATURAL) {
            return;
        }
        for (Player player : event.getLevel().players()) {
            if (active(REPELLING, player) && player.distanceToSqr(event.getX(), event.getY(), event.getZ()) <= 32 * 32) {
                event.setSpawnCancelled(true);
                return;
            }
        }
    }
}
