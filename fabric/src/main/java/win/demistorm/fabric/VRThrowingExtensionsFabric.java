package win.demistorm.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import win.demistorm.VRThrowingExtensions;
import win.demistorm.ThrownProjectileEntity;
import win.demistorm.Platform;
import win.demistorm.network.Network;

import static win.demistorm.VRThrowingExtensions.log;

// Fabric mod entry point
public class VRThrowingExtensionsFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        log.info("VR Throwing Extensions (FABRIC) starting!");

        // Set up entities first
        registerEntities();

        // Register packet types with Fabric
        PayloadTypeRegistry.playC2S().register(BufferPacket.ID, BufferPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(BufferPacket.ID, BufferPacket.CODEC);

        // Handle incoming packets
        ServerPlayNetworking.registerGlobalReceiver(BufferPacket.ID, (payload, context) -> {
            payload.buffer().retain();
            context.server().execute(() -> {
                try {
                    Network.INSTANCE.handlePacket(context.player(), payload.buffer());
                } finally {
                    payload.buffer().release();
                }
            });
        });

        // Start networking system
        Network.initialize();

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

        // Check for Mod Menu support
        if (Platform.isModLoaded("modmenu")) {
            log.info("Mod Menu detected! Configuration available through mod menu.");
        }
    }

    // Add entity to Fabric's registry
    private void registerEntities() {
        // Create thrown projectile entity type
        ResourceLocation entityLocation = ResourceLocation.fromNamespaceAndPath("vr-throwing-extensions", "generic_thrown_item");

        VRThrowingExtensions.THROWN_ITEM_TYPE = EntityType.Builder.<ThrownProjectileEntity>of(ThrownProjectileEntity::new, MobCategory.MISC)
                .sized(0.25f, 0.25f)
                .clientTrackingRange(64)
                .updateInterval(5) // Update 4 times per second
                .build(ResourceKey.create(Registries.ENTITY_TYPE, entityLocation));

        Registry.register(BuiltInRegistries.ENTITY_TYPE, entityLocation, VRThrowingExtensions.THROWN_ITEM_TYPE);

        log.info("Registered entity type: {}", entityLocation);
    }
}