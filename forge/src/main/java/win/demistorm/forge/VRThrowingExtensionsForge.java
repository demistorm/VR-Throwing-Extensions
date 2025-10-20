package win.demistorm.forge;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import net.minecraftforge.registries.RegisterEvent;
import win.demistorm.ThrownProjectileEntity;
import win.demistorm.VRThrowingExtensions;
import win.demistorm.network.Network;

import static win.demistorm.VRThrowingExtensions.log;

// Forge mod entry point
@Mod("vr_throwing_extensions")
public class VRThrowingExtensionsForge {

    public static final SimpleChannel NETWORK = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation("vr_throwing_extensions", "network"))
            .networkProtocolVersion(() -> "1.0")
            .serverAcceptedVersions("1.0"::equals)
            .clientAcceptedVersions("1.0"::equals)
            .simpleChannel();

    public VRThrowingExtensionsForge(FMLJavaModLoadingContext context) {
        log.info("VR Throwing Extensions (FORGE) starting!");

        // Get Forge event bus
        IEventBus modEventBus = context.getModEventBus();

        // Register entities
        modEventBus.addListener(this::registerEntities);

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

        // Register packet handler using 1.20.1 SimpleChannel pattern
        NETWORK.registerMessage(0, BufferPacket.class,
                BufferPacket::encode,
                BufferPacket::decode,
                BufferPacket::handle);

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
        ResourceLocation entityLocation = new ResourceLocation("vr_throwing_extensions", "generic_thrown_item");

        event.register(Registries.ENTITY_TYPE, entityLocation, () -> {
            VRThrowingExtensions.THROWN_ITEM_TYPE = EntityType.Builder.<ThrownProjectileEntity>of(ThrownProjectileEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f)
                    .clientTrackingRange(64)
                    .updateInterval(5) // Update 4 times per second
                    .build("vr_throwing_extensions:generic_thrown_item");
            return VRThrowingExtensions.THROWN_ITEM_TYPE;
        });

        log.info("Registered entity type: {}", entityLocation);
    }
}