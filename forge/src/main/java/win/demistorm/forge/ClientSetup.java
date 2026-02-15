package win.demistorm.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import win.demistorm.VRThrowingExtensions;
import win.demistorm.client.VRThrowingExtensionsClient;
import win.demistorm.ConfigHelper;
import win.demistorm.Platform;
import win.demistorm.network.NetworkHandlers;
import win.demistorm.network.data.BloodParticleData;
import win.demistorm.network.data.BleedingParticleData;
import win.demistorm.network.data.ConfigSyncData;

// Forge client setup
@Mod.EventBusSubscriber(modid = "vr_throwing_extensions", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

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

    public static void doClientSetup() {
        // Start client systems
        VRThrowingExtensionsClient.initializeClient();

        // Set up input cancellation
        Platform.registerClientInputEventHandlers();

        // Register entity renderer after a short delay to ensure entity type is registered
        VRThrowingExtensions.log.info("Scheduling entity renderer registration...");
    }

    // Register entity renderer
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // Register projectile renderer only if entity type is initialized
        if (win.demistorm.VRThrowingExtensions.THROWN_ITEM_TYPE != null) {
            event.registerEntityRenderer(win.demistorm.VRThrowingExtensions.THROWN_ITEM_TYPE,
                win.demistorm.client.ThrownItemRenderer::new);
            VRThrowingExtensions.log.info("Registered entity renderer for thrown items");
        } else {
            VRThrowingExtensions.log.warn("Entity type not yet initialized, skipping renderer registration");
        }

        if (win.demistorm.VRThrowingExtensions.THROWN_TNT_TYPE != null) {
            // Register thrown primed TNT renderer
            event.registerEntityRenderer(win.demistorm.VRThrowingExtensions.THROWN_TNT_TYPE,
                    win.demistorm.client.ThrownTNTRenderer::new);
            VRThrowingExtensions.log.info("Registered entity renderer for thrown TNT");
        } else {
            VRThrowingExtensions.log.warn("TNT Entity type not yet initialized, skipping renderer registration");
        }
    }

    // Process incoming packets
    public static void handleNetworkPacket(FriendlyByteBuf buffer) {
        // Get packet type
        int packetId = buffer.readInt();

        // Need a player to process packets
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        // Handle each packet type
        switch (packetId) {
            case 4: // BloodParticleData
                BloodParticleData bloodData = new BloodParticleData(
                    buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readDouble(), buffer.readDouble(), buffer.readDouble()
                );
                VRThrowingExtensions.log.debug("[Forge Client] Received blood particle packet ID 4 at ({}, {}, {})",
                    bloodData.posX(), bloodData.posY(), bloodData.posZ());
                NetworkHandlers.handleBloodParticle(client.player, bloodData);
                break;

            case 5: // BleedingParticleData
                BleedingParticleData bleedingData = new BleedingParticleData(
                    buffer.readDouble(), buffer.readDouble(), buffer.readDouble()
                );
                VRThrowingExtensions.log.debug("[Forge Client] Received bleeding particle packet ID 5 at ({}, {}, {})",
                    bleedingData.posX(), bleedingData.posY(), bleedingData.posZ());
                NetworkHandlers.handleBleedingParticle(client.player, bleedingData);
                break;

            case 6: // ConfigSyncData
                int length = buffer.readVarInt();
                String json = buffer.readUtf(length);
                ConfigSyncData configData = new ConfigSyncData(json);
                NetworkHandlers.handleConfigSync(client.player, configData);
                break;

            default:
                VRThrowingExtensions.log.warn("[Forge Client] Received unknown packet ID: {}", packetId);
                break;
        }
    }
}