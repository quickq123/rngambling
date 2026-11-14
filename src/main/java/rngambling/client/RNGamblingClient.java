package rngambling.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import rngambling.RNGambling;
import rngambling.client.shots.ScreenshotRun;

/** The client-only side: drawing disguised lucky blocks. */
@Mod(value = RNGambling.MOD_ID, dist = Dist.CLIENT)
public final class RNGamblingClient {
    public RNGamblingClient(IEventBus modBus) {
        modBus.addListener(MimicModel::registerLighten);
        modBus.addListener(MimicModel::wrap);
        modBus.addListener(MimicTint::register);
        modBus.addListener(LuckOverlay::register);
        if (ScreenshotRun.enabled()) {
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> ScreenshotRun.tick(Minecraft.getInstance()));
        }
    }
}
