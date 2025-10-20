package win.demistorm.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
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

        // Handle incoming packets
        ServerPlayNetworking.registerGlobalReceiver(BufferPacket.ID, (server, player, handler, buf, responseSender) -> {
            buf.retain();
            server.execute(() -> {
                try {
                    Network.INSTANCE.handlePacket(player, buf);
                } finally {
                    buf.release();
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
        ResourceLocation entityLocation = new ResourceLocation("vr-throwing-extensions", "generic_thrown_item");

        VRThrowingExtensions.THROWN_ITEM_TYPE = EntityType.Builder.<ThrownProjectileEntity>of(ThrownProjectileEntity::new, MobCategory.MISC)
                .sized(0.25f, 0.25f)
                .clientTrackingRange(64)
                .updateInterval(5) // Update 4 times per second
                .build("vr-throwing-extensions:generic_thrown_item");

        Registry.register(BuiltInRegistries.ENTITY_TYPE, entityLocation, VRThrowingExtensions.THROWN_ITEM_TYPE);

        log.info("Registered entity type: {}", entityLocation);
    }
}