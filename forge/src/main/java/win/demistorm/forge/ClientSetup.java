package win.demistorm.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.registries.ForgeRegistries;
import win.demistorm.ThrownProjectileEntity;
import win.demistorm.VRThrowingExtensions;
import win.demistorm.client.ThrownItemRenderer;
import win.demistorm.client.VRThrowingExtensionsClient;
import win.demistorm.Platform;
import win.demistorm.network.NetworkHandlers;
import win.demistorm.network.BloodParticleData;
import win.demistorm.network.BleedingParticleData;
import win.demistorm.network.ConfigSyncData;

// Forge client setup
public class ClientSetup {
    private static final ResourceLocation THROWN_ITEM_ID =
            ResourceLocation.fromNamespaceAndPath(VRThrowingExtensions.MOD_ID, "generic_thrown_item");

    public static void doClientSetup() {
        // Start client systems
        VRThrowingExtensionsClient.initializeClient();

        // Set up input cancellation
        Platform.registerClientInputEventHandlers();
    }

    // Register entity renderer using a lookup by id (avoids null field use)
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        final EntityType<?> anyType = ForgeRegistries.ENTITY_TYPES.getValue(THROWN_ITEM_ID);
        if (anyType == null) {
            VRThrowingExtensions.log.error("Entity type not found during renderer registration: {}", THROWN_ITEM_ID);
            return; // Avoid inserting a null key
        }

        // Narrow the type in the smallest possible scope
        @SuppressWarnings("unchecked")
        final EntityType<ThrownProjectileEntity> type = (EntityType<ThrownProjectileEntity>) anyType;

        event.registerEntityRenderer(type, ThrownItemRenderer::new);
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