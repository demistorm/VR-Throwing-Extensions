package win.demistorm.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// Forge client input event handlers
@Mod.EventBusSubscriber(modid = "vr_throwing_extensions", value = Dist.CLIENT)
public class PlatformClientImpl {

    // Cancel block breaking while throwing
    @SubscribeEvent
    public static boolean onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (win.demistorm.client.ThrowHelper.cancellingBreaks()) {
            win.demistorm.VRThrowingExtensions.log.debug("[VR Cancel] Block breaking cancelled due to throwing motion");
            return true; // Cancel event
        }
        return false; // Don't cancel
    }

    // Cancel block placing while throwing
    @SubscribeEvent
    public static boolean onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (win.demistorm.client.ThrowHelper.cancellingUse()) {
            win.demistorm.VRThrowingExtensions.log.debug("[VR Cancel] Block placing cancelled due to throwing motion");
            return true; // Cancel event
        }
        return false; // Don't cancel
    }

    // Cancel item use while throwing
    @SubscribeEvent
    public static boolean onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (win.demistorm.client.ThrowHelper.cancellingUse()) {
            win.demistorm.VRThrowingExtensions.log.debug("[VR Cancel] Item use cancelled due to throwing motion");
            return true; // Cancel event
        }
        return false; // Don't cancel
    }
}