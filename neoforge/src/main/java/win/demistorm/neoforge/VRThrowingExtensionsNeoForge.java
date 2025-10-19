package win.demistorm.neoforge;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.RegisterEvent;
import win.demistorm.ThrownProjectileEntity;
import win.demistorm.VRThrowingExtensions;
import win.demistorm.network.Network;

import static win.demistorm.VRThrowingExtensions.log;

// NeoForge mod entry point
@Mod("vr_throwing_extensions")
public class VRThrowingExtensionsNeoForge {

    public VRThrowingExtensionsNeoForge(IEventBus modEventBus) {
        log.info("VR Throwing Extensions (NEOFORGE) starting!");

        // Set up networking
        modEventBus.addListener((RegisterPayloadHandlersEvent event) -> {
            final PayloadRegistrar registrar = event.registrar("vr_throwing_extensions").optional();

            // Register packet handlers
            registrar.playBidirectional(BufferPacket.TYPE, BufferPacket.STREAM_CODEC,
                (packet, context) -> {
                    if (context.flow().isClientbound()) {
                        // Server to client packets
                        if (FMLEnvironment.dist.isClient()) {
                            ClientSetup.handleNetworkPacket(packet.buffer());
                        }
                    } else {
                        // Client to server packets
                        handleServerPacket(packet.buffer(), context);
                    }
                });

            log.info("Registered NeoForge network handlers in constructor");
        });

        // Register entities
        modEventBus.addListener(this::registerEntities);

        // Register renderer (client only)
        if (FMLEnvironment.dist.isClient()) {
            modEventBus.addListener(this::registerEntityRenderers);
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

        // Set up client side
        if (FMLEnvironment.dist.isClient()) {
            ClientSetup.doClientSetup();
            // Register config screen
            NeoForgeConfigScreen.register();
        }

        log.info("VR Throwing Extensions (NEOFORGE) initialization complete!");
    }

    // Add entities using NeoForge registration
    private void registerEntities(RegisterEvent event) {
        // Create thrown projectile entity
        ResourceLocation entityLocation = ResourceLocation.fromNamespaceAndPath("vr_throwing_extensions", "generic_thrown_item");

        if (event.getRegistryKey() == Registries.ENTITY_TYPE) {
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

    // Register renderer (client only)
    private void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // Client-side only
        if (FMLEnvironment.dist.isClient() && VRThrowingExtensions.THROWN_ITEM_TYPE != null) {
            event.registerEntityRenderer(VRThrowingExtensions.THROWN_ITEM_TYPE,
                win.demistorm.client.ThrownItemRenderer::new);
            log.info("Registered thrown item renderer for NeoForge");
        }
    }

    // Process client packets
    private void handleServerPacket(RegistryFriendlyByteBuf buffer, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();

            // Check if entities are ready (NeoForge timing)
            if (VRThrowingExtensions.THROWN_ITEM_TYPE == null) {
                VRThrowingExtensions.log.error("[NeoForge] THROWN_ITEM_TYPE is null when processing packet! Entity registration may not be complete.");
                return;
            }

            // Handle packet with network system
            Network.INSTANCE.handlePacket(player, buffer);
        });
    }
}