package win.demistorm.forge;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.PacketDistributor;
import win.demistorm.Platform;

import java.io.File;
import java.util.function.Consumer;

// Forge platform implementations
@SuppressWarnings("unused")
public class PlatformImpl {

    // Check if running on client
    @SuppressWarnings("unused")
    public static boolean isClientSide() {
        return FMLEnvironment.dist == Dist.CLIENT;
    }

    // Check if in dev mode
    @SuppressWarnings("unused")
    public static boolean isDevelopmentEnvironment() {
        return !FMLEnvironment.production;
    }

    // Check if Forge-like (always true for Forge)
    @SuppressWarnings("unused")
    public static boolean isForgeLike() {
        return true;
    }

    // Check if mod is installed
    @SuppressWarnings("unused")
    public static boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    // Get config directory
    @SuppressWarnings("unused")
    public static File getConfigFolder() {
        return FMLPaths.CONFIGDIR.get().toFile();
    }

    // Listen for server ticks
    @SuppressWarnings("unused")
    public static void registerServerPostTickListener(Consumer<MinecraftServer> listener) {
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                listener.accept(event.getServer());
            }
        });
    }

    // Listen for player ticks
    @SuppressWarnings("unused")
    public static void registerServerPlayerPostTickListener(Consumer<ServerPlayer> listener) {
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                event.getServer().getPlayerList().getPlayers().forEach(listener);
            }
        });
    }

    // Listen for player joins
    @SuppressWarnings("unused")
    public static void registerServerPlayerJoinListener(Consumer<ServerPlayer> listener) {
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer sp) {
                listener.accept(sp);
            }
        });
    }

    // Listen for player leaves
    @SuppressWarnings("unused")
    public static void registerServerPlayerLeaveListener(Consumer<ServerPlayer> listener) {
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer sp) {
                listener.accept(sp);
            }
        });
    }

    // Register commands
    @SuppressWarnings("unused")
    public static void registerCommands(Consumer<CommandDispatcher<CommandSourceStack>> listener) {
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> listener.accept(event.getDispatcher()));
    }

    // Send packet to server
    @SuppressWarnings("unused")
    public static void sendToServer(FriendlyByteBuf message) {
        VRThrowingExtensionsForge.NETWORK.sendToServer(new BufferPacket(message));
    }

    // Send packet to player
    @SuppressWarnings("unused")
    public static void sendToPlayer(ServerPlayer player, FriendlyByteBuf message) {
        VRThrowingExtensionsForge.NETWORK.send(PacketDistributor.PLAYER.with(() -> player), new BufferPacket(message));
    }

    
    // Register packet handlers (handled in main mod class)
    @SuppressWarnings("unused")
    public static void registerC2SPacketHandler(ResourceLocation packetId, Consumer<Platform.PacketContext> handler) {
        // Registration happens in main mod class with ChannelBuilder
    }

    // Register client packet handlers (handled in main mod class)
    @SuppressWarnings("unused")
    public static void registerS2CPacketHandler(ResourceLocation packetId, Consumer<Platform.PacketContext> handler) {
        // Registration happens in main mod class with ChannelBuilder
    }

    // Register entity type (handled in main mod class)
    @SuppressWarnings("unused")
    public static void registerEntityType(Object entityType) {
        // Registration handled in main initializer
    }

    // Get Minecraft version
    @SuppressWarnings("unused")
    public static String getMinecraftVersion() {
        return ModList.get().getModContainerById("minecraft")
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
    }

    // Get mod loader name
    public static String getLoaderName() {
        return "forge";
    }

    // Get client registry access
    @SuppressWarnings("unused")
    public static RegistryAccess getClientRegistryAccess() {
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() != null) {
            return client.getConnection().registryAccess();
        }
        return null;
    }

    // Register input events (handled in client code)
    @SuppressWarnings("unused")
    public static void registerClientInputEventHandlers() {
        // Implemented in PlatformClientImpl with @SubscribeEvent
    }
}