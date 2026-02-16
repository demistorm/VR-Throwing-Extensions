package win.demistorm.forge;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import win.demistorm.VRThrowingExtensions;
import win.demistorm.ConfigHelper;

// Forge client input event handlers
@Mod.EventBusSubscriber(modid = "vr_throwing_extensions", value = Dist.CLIENT)
public class PlatformClientImpl {

    // Cancel interactions at the input level (before they become interaction events)
    // This properly prevents packets from being sent to the server in multiplayer
    @SubscribeEvent
    public static void onInteractionKeyMappingTriggered(InputEvent.InteractionKeyMappingTriggered event) {
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
    }

    // Suppress arm swing when place/use is held during throwing
    // This is needed on NeoForge/Forge because InputEvent fires after the swing already started
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        // Only run on POST phase to match the EventBus 7 behavior
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Only suppress when throwing is active AND place/use key is held
        if (win.demistorm.client.ThrowHelper.cancellingUse() && mc.options.keyUse.isDown()) {
            mc.player.swingingArm = null;
        }
    }

    // Handle join events (start config send timer)
    @SubscribeEvent
    public static void onClientLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        win.demistorm.client.VRThrowingExtensionsClient.startConfigSendTimer();
        VRThrowingExtensions.log.debug("Forge client joining server, starting config send timer");
    }

    // Handle client disconnect events to restore local config
    @SubscribeEvent
    public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ConfigHelper.clientDisconnected();
        win.demistorm.client.VRThrowingExtensionsClient.resetConfigSendTimer();
        VRThrowingExtensions.log.debug("Forge client disconnected, restored local config and reset timer");
    }
}