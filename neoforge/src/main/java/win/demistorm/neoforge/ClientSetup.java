package win.demistorm.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import win.demistorm.VRThrowingExtensions;
import win.demistorm.client.VRThrowingExtensionsClient;
import win.demistorm.Platform;
import win.demistorm.network.NetworkHandlers;
import win.demistorm.network.data.BloodParticleData;
import win.demistorm.network.data.BleedingParticleData;
import win.demistorm.network.data.ConfigSyncData;
import win.demistorm.ConfigHelper;

// NeoForge client setup
@EventBusSubscriber(modid = "vr_throwing_extensions", value = Dist.CLIENT)
public class ClientSetup {

    public static void doClientSetup() {
        // Start client systems
        VRThrowingExtensionsClient.initializeClient();

        // Set up input cancellation
        Platform.registerClientInputEventHandlers();
    }

    // Process incoming packets
    public static void handleNetworkPacket(RegistryFriendlyByteBuf buffer) {
        // Need a player to process packets
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        // Convert for reading
        FriendlyByteBuf friendlyBuf = new FriendlyByteBuf(buffer);

        // Get packet type
        int packetId = friendlyBuf.readInt();

        // Handle each packet type
        switch (packetId) {
            case 4: // BloodParticleData
                BloodParticleData bloodData = new BloodParticleData(
                    friendlyBuf.readDouble(), friendlyBuf.readDouble(), friendlyBuf.readDouble(),
                    friendlyBuf.readDouble(), friendlyBuf.readDouble(), friendlyBuf.readDouble()
                );
                VRThrowingExtensions.log.debug("[NeoForge Client] Received blood particle packet ID 4 at ({}, {}, {})",
                    bloodData.posX(), bloodData.posY(), bloodData.posZ());
                NetworkHandlers.handleBloodParticle(client.player, bloodData);
                break;

            case 5: // BleedingParticleData
                BleedingParticleData bleedingData = new BleedingParticleData(
                    friendlyBuf.readDouble(), friendlyBuf.readDouble(), friendlyBuf.readDouble()
                );
                VRThrowingExtensions.log.debug("[NeoForge Client] Received bleeding particle packet ID 5 at ({}, {}, {})",
                    bleedingData.posX(), bleedingData.posY(), bleedingData.posZ());
                NetworkHandlers.handleBleedingParticle(client.player, bleedingData);
                break;

            case 6: // ConfigSyncData
                int length = friendlyBuf.readVarInt();
                String json = friendlyBuf.readUtf(length);
                ConfigSyncData configData = new ConfigSyncData(json);
                NetworkHandlers.handleConfigSync(client.player, configData);
                break;

            default:
                VRThrowingExtensions.log.warn("[NeoForge Client] Received unknown packet ID: {}", packetId);
                break;
        }
    }

    // Handle join events (start config send timer)
    @SubscribeEvent
    public static void onClientLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        win.demistorm.client.VRThrowingExtensionsClient.startConfigSendTimer();
        VRThrowingExtensions.log.debug("NeoForge client joining server, starting config send timer");
    }

    // Handle disconnect (restore local config)
    @SubscribeEvent
    public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ConfigHelper.clientDisconnected();
        win.demistorm.client.VRThrowingExtensionsClient.resetConfigSendTimer();
        VRThrowingExtensions.log.debug("NeoForge client disconnected, restored local config and reset timer");
    }
}