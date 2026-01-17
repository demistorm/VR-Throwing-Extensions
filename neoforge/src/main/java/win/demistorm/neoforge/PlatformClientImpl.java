package win.demistorm.neoforge;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;

// NeoForge client input event handlers
public class PlatformClientImpl {

    // Register client input event handlers
    public static void register() {
        // Cancel interactions at the input level (before they become interaction events)
        // This properly prevents packets from being sent to the server in multiplayer
        NeoForge.EVENT_BUS.addListener((InputEvent.InteractionKeyMappingTriggered event) -> {
            // Check if this is a left-click (attack/break) or right-click (use/place)
            boolean isLeftClick = event.getKeyMapping() == Minecraft.getInstance().options.keyAttack;
            boolean isRightClick = event.getKeyMapping() == Minecraft.getInstance().options.keyUse;

            if (isLeftClick && win.demistorm.client.ThrowHelper.cancellingBreaks()) {
                win.demistorm.VRThrowingExtensions.log.debug("[VR Cancel] Block breaking cancelled at input level due to throwing motion");
                event.setCanceled(true);
            }

            if (isRightClick && win.demistorm.client.ThrowHelper.cancellingUse()) {
                win.demistorm.VRThrowingExtensions.log.debug("[VR Cancel] Block placing/item use cancelled at input level due to throwing motion");
                event.setCanceled(true);
            }
        });

        // Suppress arm swing when place/use is held during throwing
        // This is needed on NeoForge/Forge because InputEvent fires after the swing already started
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            // Only suppress when throwing is active AND place/use key is held
            if (win.demistorm.client.ThrowHelper.cancellingUse() && mc.options.keyUse.isDown()) {
                mc.player.swingingArm = null;
            }
        });
    }
}