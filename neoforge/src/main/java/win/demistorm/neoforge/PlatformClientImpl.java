package win.demistorm.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

// NeoForge client input event handlers
@EventBusSubscriber(modid = "vr_throwing_extensions", value = Dist.CLIENT)
public class PlatformClientImpl {

    // Cancel block breaking while throwing
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (win.demistorm.client.ThrowHelper.cancellingBreaks()) {
            win.demistorm.VRThrowingExtensions.log.debug("[VR Cancel] Block breaking cancelled due to throwing motion");
            event.setCanceled(true);
        }
    }

    // Cancel block placing while throwing
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (win.demistorm.client.ThrowHelper.cancellingUse()) {
            win.demistorm.VRThrowingExtensions.log.debug("[VR Cancel] Block placing cancelled due to throwing motion");
            event.setCanceled(true);
        }
    }

    // Cancel item use while throwing
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (win.demistorm.client.ThrowHelper.cancellingUse()) {
            win.demistorm.VRThrowingExtensions.log.debug("[VR Cancel] Item use cancelled due to throwing motion");
            event.setCanceled(true);
        }
    }
}