package net.automation.core.client.cutscene;

import net.automation.core.AutomationCore;
import net.automation.core.client.cutscene.background.CutsceneBackgrounds;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AutomationCore.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CutsceneClientEvents {

    private CutsceneClientEvents() {}

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(CutsceneBackgrounds.PresetLoader.INSTANCE);
    }
}
