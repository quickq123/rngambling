package rngambling.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** The Lucky Journal: right-click to read it (the screen is the client's, see client.JournalScreen). */
public class LuckyJournalItem extends Item {
    /** Set by the client when it starts; does nothing on a dedicated server. */
    public static Runnable openScreen = () -> {
    };

    public LuckyJournalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            openScreen.run();
        }
        return InteractionResult.SUCCESS;
    }
}
