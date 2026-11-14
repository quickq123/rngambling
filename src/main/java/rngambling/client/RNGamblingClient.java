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
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) ->
        {
            event.register(RNGambling.SLOT_MACHINE_MENU.get(), SlotMachineScreen::new);
            event.register(RNGambling.ROULETTE_MENU.get(), RouletteScreen::new);
            event.register(RNGambling.BLACKJACK_MENU.get(), BlackjackScreen::new);
        });
        modBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions event) ->
                event.registerLayerDefinition(DragonHatchlingModel.LAYER, DragonHatchlingModel::createBodyLayer));
        modBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) ->
                event.registerEntityRenderer(RNGambling.DRAGON_HATCHLING.get(), DragonHatchlingRenderer::new));
        if (PlaytestStart.enabled()) {
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> PlaytestStart.tick(Minecraft.getInstance()));
        }
        if (ScreenshotRun.enabled()) {
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> ScreenshotRun.tick(Minecraft.getInstance()));
        }
    }
}
