package rngambling.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.ChatFormatting;
import rngambling.RNGambling;

/**
 * A development aid ({@code gradlew runPlaytest}): goes straight into the playtest world, a normal
 * survival world with cheats on (so the {@code /rngambling} commands work), made the first time
 * and reopened after that. On first arrival the player gets a few of each lucky block, some
 * tokens and charms, and a reminder of the test commands.
 */
public final class PlaytestStart {
    public static final String WORLD = "RNGambling Playtest";
    private static final String KIT_TAG = "rngambling_playtest_kit";
    private static int ticks;
    private static boolean asked;
    private static boolean kitChecked;

    private PlaytestStart() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean("rngambling.playtest");
    }

    public static void tick(Minecraft minecraft) {
        ticks++;
        if (!asked) {
            if (minecraft.level == null && minecraft.getOverlay() == null && minecraft.screen != null && ticks > 40) {
                asked = true;
                if (minecraft.getLevelSource().levelExists(WORLD)) {
                    minecraft.createWorldOpenFlows().openWorld(WORLD, () -> minecraft.setScreen(new TitleScreen()));
                } else {
                    LevelSettings settings = new LevelSettings(WORLD, GameType.SURVIVAL,
                            new LevelSettings.DifficultySettings(Difficulty.NORMAL, false, false), true, WorldDataConfiguration.DEFAULT);
                    minecraft.createWorldOpenFlows().createFreshLevel(WORLD, settings,
                            new WorldOptions(WorldOptions.randomSeed(), true, false), WorldPresets::createNormalWorldDimensions, new TitleScreen());
                }
            }
            return;
        }
        if (!kitChecked && minecraft.player != null && minecraft.getSingleplayerServer() != null) {
            kitChecked = true;
            var server = minecraft.getSingleplayerServer();
            server.execute(() -> server.getPlayerList().getPlayers().forEach(PlaytestStart::welcome));
        }
    }

    private static void welcome(ServerPlayer player) {
        if (!player.entityTags().contains(KIT_TAG)) {
            player.addTag(KIT_TAG);
            give(player, new ItemStack(RNGambling.LUCKY_BLOCK_ITEM.get(), 16));
            give(player, new ItemStack(RNGambling.SAFE_LUCKY_BLOCK_ITEM.get(), 4));
            give(player, new ItemStack(RNGambling.EXTREME_LUCKY_BLOCK_ITEM.get(), 4));
            give(player, new ItemStack(RNGambling.BLESSED_LUCKY_BLOCK_ITEM.get(), 2));
            give(player, new ItemStack(RNGambling.CASINO_TOKEN.get(), 32));
            give(player, new ItemStack(RNGambling.FOUR_LEAF_CLOVER.get(), 2));
            give(player, new ItemStack(RNGambling.GOLDEN_HORSESHOE.get(), 1));
            give(player, new ItemStack(net.minecraft.world.item.Items.BREAD, 16));
        }
        for (String key : new String[] {"intro", "play", "roll", "list", "sandbox", "creative"}) {
            player.sendSystemMessage(Component.translatable("message.rngambling.playtest." + key)
                    .withStyle(key.equals("intro") ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        }
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        player.getInventory().placeItemBackInInventory(stack);
    }
}
