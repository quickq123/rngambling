package rngambling.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import rngambling.RNGambling;
import rngambling.block.LuckyBlockEntity;

/**
 * What the Luck Glasses show: while a creative player wears them, every lucky block within
 * {@link #RANGE} blocks gets its luck written over it, through walls, red (cursed) through white
 * to green (lucky). Drawn on the HUD at each block's projected position rather than in the world,
 * because the game doesn't draw underground spaces the player can't see, and those are exactly
 * where the glasses need to look.
 */
final class LuckOverlay {
    static final int RANGE = 32;

    private LuckOverlay() {
    }

    static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, RNGambling.id("luck_glasses"), LuckOverlay::render);
    }

    static boolean active(Minecraft minecraft) {
        Player player = minecraft.player;
        return player != null && minecraft.level != null && !minecraft.options.hideGui && player.isCreative()
                && player.getItemBySlot(EquipmentSlot.HEAD).is(RNGambling.LUCK_GLASSES.get());
    }

    private record Label(Vec3 at, int luck, double distance) {
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!active(minecraft)) {
            return;
        }
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 eye = camera.position();
        List<Label> labels = new ArrayList<>();
        BlockPos centre = BlockPos.containing(eye);
        int chunks = (RANGE >> 4) + 1;
        for (int cx = (centre.getX() >> 4) - chunks; cx <= (centre.getX() >> 4) + chunks; cx++) {
            for (int cz = (centre.getZ() >> 4) - chunks; cz <= (centre.getZ() >> 4) + chunks; cz++) {
                LevelChunk chunk = minecraft.level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    if (entity instanceof LuckyBlockEntity lucky) {
                        Vec3 at = Vec3.atCenterOf(lucky.getBlockPos());
                        double distance = at.distanceTo(eye);
                        if (distance <= RANGE) {
                            labels.add(new Label(at, lucky.luck(), distance));
                        }
                    }
                }
            }
        }
        // Far ones first, so near ones are drawn on top.
        labels.sort(Comparator.comparingDouble(Label::distance).reversed());

        Matrix4f projection = camera.getViewRotationProjectionMatrix(new Matrix4f());
        Vector4f point = new Vector4f();
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        for (Label label : labels) {
            Vec3 relative = label.at().subtract(eye);
            projection.transform(point.set((float) relative.x, (float) relative.y, (float) relative.z, 1f));
            if (point.w <= 0.05f) {
                continue; // behind the camera
            }
            float x = (point.x / point.w + 1f) / 2f * width;
            float y = (1f - point.y / point.w) / 2f * height;
            if (x < -20 || x > width + 20 || y < -20 || y > height + 20) {
                continue;
            }
            String text = (label.luck() > 0 ? "+" : "") + label.luck();
            int textWidth = minecraft.font.width(text);
            int left = Math.round(x) - textWidth / 2;
            int top = Math.round(y) - 4;
            graphics.fill(left - 2, top - 2, left + textWidth + 1, top + 9, 0x90000000);
            graphics.text(minecraft.font, text, left, top, colour(label.luck()), false);
        }
    }

    /** Red at -100, white at 0, green at +100, opaque. */
    static int colour(int luck) {
        float t = Math.abs(luck) / 100f;
        int fade = Math.round(255 * (1 - t * 0.75f));
        return luck >= 0 ? 0xFF000000 | (fade << 16) | (0xFF << 8) | fade : 0xFF000000 | (0xFF << 16) | (fade << 8) | fade;
    }
}
