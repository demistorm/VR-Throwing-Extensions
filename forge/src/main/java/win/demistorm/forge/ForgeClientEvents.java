package win.demistorm.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import win.demistorm.ConfigHelper;

import static win.demistorm.VRThrowingExtensions.log;

@Mod.EventBusSubscriber(modid = "vr_throwing_extensions", value = Dist.CLIENT)
public class ForgeClientEvents {

    // Handle client disconnect events to restore local config
    @SubscribeEvent
    public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ConfigHelper.clientDisconnected();
        log.debug("Forge client disconnected, restored local config");
    }
}