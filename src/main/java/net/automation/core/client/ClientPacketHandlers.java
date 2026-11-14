package net.automation.core.client;

import net.automation.core.AutomationCore;
import net.automation.core.client.cutscene.CutsceneManager;
import net.automation.core.network.S2CCutsceneSyncPacket;
import net.automation.core.network.S2CPlayCutscenePacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AutomationCore.MOD_ID, value = Dist.CLIENT)
public final class ClientPacketHandlers {

    private ClientPacketHandlers() {}

    public static void playCutscene(S2CPlayCutscenePacket msg) {
        CutsceneManager.play(msg);
    }

    public static void syncCutscene(S2CCutsceneSyncPacket msg) {
        CutsceneManager.sync(msg);
    }

    @SubscribeEvent
    public static void onLeave(ClientPlayerNetworkEvent.LoggingOut event) {
        CutsceneManager.clear();
    }
}
