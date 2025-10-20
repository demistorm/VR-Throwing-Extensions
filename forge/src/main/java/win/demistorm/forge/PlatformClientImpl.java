package win.demistorm.forge;

import net.minecraftforge.event.entity.player.PlayerInteractEvent;

// Forge client input event handlers
public class PlatformClientImpl {

    // Register client input event handlers
    public static void register() {
        // Cancel block breaking while throwing
        PlayerInteractEvent.LeftClickBlock.BUS.addListener(event -> {
            if (win.demistorm.client.ThrowHelper.cancellingBreaks()) {
                win.demistorm.VRThrowingExtensions.log.debug("[VR Cancel] Block breaking cancelled due to throwing motion");
                return true; // Cancel event
            }
            return false; // Don't cancel
        });

        // Cancel block placing while throwing
        PlayerInteractEvent.RightClickBlock.BUS.addListener(event -> {
            if (win.demistorm.client.ThrowHelper.cancellingUse()) {
                win.demistorm.VRThrowingExtensions.log.debug("[VR Cancel] Block placing cancelled due to throwing motion");
                return true; // Cancel event
            }
            return false; // Don't cancel
        });

        // Cancel item use while throwing
        PlayerInteractEvent.RightClickItem.BUS.addListener(event -> {
            if (win.demistorm.client.ThrowHelper.cancellingUse()) {
                win.demistorm.VRThrowingExtensions.log.debug("[VR Cancel] Item use cancelled due to throwing motion");
                return true; // Cancel event
            }
            return false; // Don't cancel
        });
    }
}