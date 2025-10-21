package win.demistorm.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import win.demistorm.VRThrowingExtensions;
import win.demistorm.client.VRThrowingExtensionsClient;
import win.demistorm.client.ThrownItemRenderer;
import win.demistorm.client.ThrowHelper;
import win.demistorm.network.BloodParticleData;
import win.demistorm.network.BleedingParticleData;
import win.demistorm.network.ConfigSyncData;
import win.demistorm.ConfigHelper;

// Fabric client setup and networking
public class PlatformClientImpl implements ClientModInitializer {


    @Override
    @SuppressWarnings("deprecation") // EntityRendererRegistry going out of style apparently
    public void onInitializeClient() {
        // Set up client packet handling
        ClientPlayNetworking.registerGlobalReceiver(BufferPacket.ID, (payload, context) -> {
            payload.buffer().retain();
            context.client().execute(() -> {
                try {
                    handleBufferPacket(payload.buffer());
                } finally {
                    payload.buffer().release();
                }
            });
        });

        // Start client VR systems
        VRThrowingExtensionsClient.initializeClient();

        // Set up input cancellation
        registerClientInputEventHandlers();

        // Register projectile renderer
        EntityRendererRegistry.register(VRThrowingExtensions.THROWN_ITEM_TYPE, ThrownItemRenderer::new);

        // Handle disconnect events (restore local config)
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ConfigHelper.clientDisconnected();
            VRThrowingExtensions.log.debug("Client disconnected, restored local config");
        });
    }

    
    // Process incoming packets
    private static void handleBufferPacket(RegistryFriendlyByteBuf buffer) {
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
                VRThrowingExtensions.log.debug("[Client] Received blood particle packet ID 4 at ({}, {}, {})",
                    bloodData.posX(), bloodData.posY(), bloodData.posZ());
                win.demistorm.network.NetworkHandlers.handleBloodParticle(client.player, bloodData);
                break;

            case 5: // BleedingParticleData
                BleedingParticleData bleedingData = new BleedingParticleData(
                    buffer.readDouble(), buffer.readDouble(), buffer.readDouble()
                );
                VRThrowingExtensions.log.debug("[Client] Received bleeding particle packet ID 5 at ({}, {}, {})",
                    bleedingData.posX(), bleedingData.posY(), bleedingData.posZ());
                win.demistorm.network.NetworkHandlers.handleBleedingParticle(client.player, bleedingData);
                break;

            case 6: // ConfigSyncData
                int length = buffer.readVarInt();
                String json = buffer.readUtf(length);
                ConfigSyncData configData = new ConfigSyncData(json);
                win.demistorm.network.NetworkHandlers.handleConfigSync(client.player, configData);
                break;

            default:
                VRThrowingExtensions.log.warn("[Client] Received unknown packet ID: {}", packetId);
                break;
        }
    }

    // Stop vanilla actions during VR throwing
    private static void registerClientInputEventHandlers() {
        // Cancel block breaking while throwing
        AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) -> {
            if (ThrowHelper.cancellingBreaks()) {
                VRThrowingExtensions.log.debug("[VR Cancel] Block breaking cancelled due to throwing motion");
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        // Cancel block placing while throwing
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (ThrowHelper.cancellingUse()) {
                VRThrowingExtensions.log.debug("[VR Cancel] Block placing cancelled due to throwing motion");
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        // Cancel item use while throwing
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (ThrowHelper.cancellingUse()) {
                VRThrowingExtensions.log.debug("[VR Cancel] Item use cancelled due to throwing motion");
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
    }
}