package win.demistorm.forge;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.EventNetworkChannel;
import net.minecraftforge.registries.RegisterEvent;
import win.demistorm.ThrownProjectileEntity;
import win.demistorm.VRThrowingExtensions;
import win.demistorm.network.Network;

import static win.demistorm.VRThrowingExtensions.log;

// Forge mod entry point
@Mod(VRThrowingExtensions.MOD_ID)
public class VRThrowingExtensionsForge {

    public static final EventNetworkChannel NETWORK = ChannelBuilder.named(ResourceLocation.fromNamespaceAndPath(VRThrowingExtensions.MOD_ID, "network"))
            .acceptedVersions((status, version) -> true)
            .optional()
            .networkProtocolVersion(0)
            .eventNetworkChannel();

    public VRThrowingExtensionsForge(FMLJavaModLoadingContext context) {
        log.info("VR Throwing Extensions (FORGE) starting!");

        // Get Forge event bus
        var modBusGroup = context.getModBusGroup();

        // Register entities
        RegisterEvent.getBus(modBusGroup).addListener(this::registerEntities);

        // Register renderers
        if (FMLEnvironment.dist == Dist.CLIENT) {
            EntityRenderersEvent.RegisterRenderers.BUS.addListener(ClientSetup::registerRenderers);
        }

        // Make sure Vivecraft is installed
        try {
            Class.forName("org.vivecraft.api.VRAPI");
            log.info("Vivecraft detected! VR throwing enabled.");
        } catch (ClassNotFoundException e) {
            log.error("Vivecraft not found! VR Throwing Extensions requires Vivecraft to function.");
            throw new RuntimeException("Vivecraft is required for VR Throwing Extensions");
        }

        // Run common setup
        VRThrowingExtensions.initialize();

        // Start networking
        Network.initialize();

        // Set up packet handling
        NETWORK.addListener(event -> {
            FriendlyByteBuf payload = event.getPayload();
            if (payload == null) return;

            if (event.getSource().isServerSide()) {
                ServerPlayer sender = event.getSource().getSender();
                if (sender != null) {
                    // Convert buffer with registry access
                    RegistryFriendlyByteBuf registryBuf =
                            new RegistryFriendlyByteBuf(payload, sender.level().registryAccess());
                    Network.INSTANCE.handlePacket(sender, registryBuf);
                }
            } else {
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    ClientSetup.handleNetworkPacket(payload);
                }
            }
            event.getSource().setPacketHandled(true);
        });

        // Set up client side
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientSetup.doClientSetup();
            // Register config screen
            ForgeConfigScreen.register(context);
        }

        log.info("VR Throwing Extensions (FORGE) initialization complete!");
    }

    // Add entities using Forge's registration
    private void registerEntities(RegisterEvent event) {
        // Create thrown projectile entity
        ResourceLocation entityLocation = ResourceLocation.fromNamespaceAndPath
                (VRThrowingExtensions.MOD_ID, "generic_thrown_item");

        event.register(Registries.ENTITY_TYPE, entityLocation, () -> {
            VRThrowingExtensions.THROWN_ITEM_TYPE = EntityType.Builder.<ThrownProjectileEntity>of(ThrownProjectileEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f)
                    .clientTrackingRange(64)
                    .updateInterval(5) // Update 4 times per second
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, entityLocation));
            return VRThrowingExtensions.THROWN_ITEM_TYPE;
        });

        log.info("Registered entity type: {}", entityLocation);
    }
}