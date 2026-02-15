package win.demistorm.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import win.demistorm.ThrownProjectileEntity;
import win.demistorm.ThrownTNTEntity;
import win.demistorm.VRThrowingExtensions;
import win.demistorm.client.ThrownItemRenderer;
import win.demistorm.client.ThrownTNTRenderer;
import win.demistorm.client.VRThrowingExtensionsClient;
import win.demistorm.ConfigHelper;
import win.demistorm.Platform;
import win.demistorm.network.NetworkHandlers;
import win.demistorm.network.data.BloodParticleData;
import win.demistorm.network.data.BleedingParticleData;
import win.demistorm.network.data.ConfigSyncData;

// Forge client setup
@Mod.EventBusSubscriber(modid = "vr_throwing_extensions", value = Dist.CLIENT)
public class ClientSetup {
    private static final ResourceLocation THROWN_ITEM_ID =
            ResourceLocation.fromNamespaceAndPath(VRThrowingExtensions.MOD_ID, "generic_thrown_item");

    private static final ResourceLocation THROWN_TNT_ID =
            ResourceLocation.fromNamespaceAndPath(VRThrowingExtensions.MOD_ID, "thrown_primed_tnt");

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
    }

    // Register entity renderer using a lookup by id (avoids null field use)
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // Register projectile renderer
        final EntityType<?> anyProjectileType = ForgeRegistries.ENTITY_TYPES.getValue(THROWN_ITEM_ID);
        if (anyProjectileType == null) {
            VRThrowingExtensions.log.error("Entity type not found during renderer registration: {}", THROWN_ITEM_ID);
            return; // Avoid inserting a null key
        }

        // Narrow the type in the smallest possible scope
        @SuppressWarnings("unchecked")
        final EntityType<ThrownProjectileEntity> projectileType = (EntityType<ThrownProjectileEntity>) anyProjectileType;

        event.registerEntityRenderer(projectileType, ThrownItemRenderer::new);

        // Register thrown primed TNT renderer
        final EntityType<?> anyTNTType = ForgeRegistries.ENTITY_TYPES.getValue(THROWN_TNT_ID);
        if (anyTNTType == null) {
            VRThrowingExtensions.log.error("Entity type not found during renderer registration: {}", THROWN_TNT_ID);
            return;
        }

        @SuppressWarnings("unchecked")
        final EntityType<ThrownTNTEntity> tntType = (EntityType<ThrownTNTEntity>) anyTNTType;

        event.registerEntityRenderer(tntType, ThrownTNTRenderer::new);
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