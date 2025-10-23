package win.demistorm.forge;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
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

    private static final IEventBus MOD_EVENT_BUS = FMLJavaModLoadingContext.get().getModEventBus();

    public static final SimpleChannel NETWORK = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation("vr_throwing_extensions", "network"))
            .networkProtocolVersion(() -> "1.0")
            .serverAcceptedVersions("1.0"::equals)
            .clientAcceptedVersions("1.0"::equals)
            .simpleChannel();

    public VRThrowingExtensionsForge() {
        log.info("VR Throwing Extensions (FORGE) starting!");

        // Make sure Vivecraft is installed
        try {
            Class.forName("org.vivecraft.api.VRAPI");
            log.info("Vivecraft detected! VR throwing enabled.");
        } catch (ClassNotFoundException e) {
            log.error("Vivecraft not found! VR Throwing Extensions requires Vivecraft to function.");
            throw new RuntimeException("Vivecraft is required for VR Throwing Extensions");
        }

        // Initialize common systems FIRST
        VRThrowingExtensions.initialize();
        // Note: Network.initialize() will be called after entity registration

        // Register packet handler using 1.20.1 SimpleChannel pattern
        NETWORK.registerMessage(0, BufferPacket.class,
                BufferPacket::encode,
                BufferPacket::decode,
                BufferPacket::handle);

        // Register entity registration event listener
        MOD_EVENT_BUS.register(VRThrowingExtensionsForge.class);

        // Set up client side
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientSetup.doClientSetup();
            // Register config screen
            ForgeConfigScreen.register();
        }

        log.info("VR Throwing Extensions (FORGE) initialization complete!");
    }

    // Add entities using Forge's registration
    @SubscribeEvent
    public static void registerEntities(RegisterEvent event) {
        // Create thrown projectile entity
        ResourceLocation entityLocation = new ResourceLocation("vr_throwing_extensions", "generic_thrown_item");

        event.register(Registries.ENTITY_TYPE, entityLocation, () -> {
            VRThrowingExtensions.THROWN_ITEM_TYPE = EntityType.Builder.<ThrownProjectileEntity>of(ThrownProjectileEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f)
                    .clientTrackingRange(64)
                    .updateInterval(5) // Update 4 times per second
                    .build("vr_throwing_extensions:generic_thrown_item");

            log.info("Registered entity type: {}", entityLocation);
            log.info("Entity type created: {}", VRThrowingExtensions.THROWN_ITEM_TYPE);

            // Now that entity type is registered, initialize networking
            log.info("Initializing networking after entity registration...");
            Network.initialize();

            return VRThrowingExtensions.THROWN_ITEM_TYPE;
        });

    }
}