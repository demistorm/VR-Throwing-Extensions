package win.demistorm;

import com.mojang.brigadier.CommandDispatcher;
import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;

import java.io.File;
import java.util.function.Consumer;

// Platform abstraction layer for VR Throwing Extensions
// This lets it use the same code on Fabric, Forge, and NeoForge
// Each loader implements these methods in their own PlatformImpl class
public class Platform {

    // Check if mod is on the client side
    @ExpectPlatform
    public static boolean isClientSide() {
        throw new RuntimeException("@ExpectPlatform should have replaced this");
    }

    // Check if mod is in a dev environment
    @ExpectPlatform
    public static boolean isDevelopmentEnvironment() {
        throw new RuntimeException("@ExpectPlatform should have replaced this");
    }

    // Check if this is Forge or NeoForge (not Fabric)
    @ExpectPlatform
    public static boolean isForgeLike() {
        throw new RuntimeException("@ExpectPlatform should have replaced this");
    }

    // Check if a specific mod is loaded
    @ExpectPlatform
    public static boolean isModLoaded(String modId) {
        throw new RuntimeException("@ExpectPlatform should have replaced this");
    }

    // Get the config folder for our mod
    @ExpectPlatform
    public static File getConfigFolder() {
        throw new RuntimeException("@ExpectPlatform should have replaced this");
    }

    // Register server tick events
    @ExpectPlatform
    public static void registerServerPostTickListener(Consumer<MinecraftServer> listener) {
        throw new RuntimeException("@ExpectPlatform should have replaced this");
    }

    // Register player tick events
    @ExpectPlatform
    public static void registerServerPlayerPostTickListener(Consumer<ServerPlayer> listener) {
        throw new RuntimeException("@ExpectPlatform should have replaced this");
    }

    // Register when a player joins the server
    @ExpectPlatform
    public static void registerServerPlayerJoinListener(Consumer<ServerPlayer> listener) {
        throw new RuntimeException("@ExpectPlatform should have replaced this");
    }

    // Register when a player leaves the server
    @ExpectPlatform
    public static void registerServerPlayerLeaveListener(Consumer<ServerPlayer> listener) {
        throw new RuntimeException("@ExpectPlatform should have replaced this");
    }

    // Register command handlers
    @ExpectPlatform
    public static void registerCommands(Consumer<CommandDispatcher<CommandSourceStack>> listener) {
        throw new RuntimeException("@ExpectPlatform should have replaced this");
    }

    // Send packet from client to server
    @ExpectPlatform
    public static void sendToServer(RegistryFriendlyByteBuf message) {
        throw new RuntimeException("@ExpectPlatform should have replaced this");
    }

    // Send packet from server to one player
    @ExpectPlatform
    public static void sendToPlayer(ServerPlayer player, RegistryFriendlyByteBuf message) {
        throw new RuntimeException("@ExpectPlatform should have replaced this");
    }

    
    // Register a packet handler for client-to-server packets
    @ExpectPlatform
    public static void registerC2SPacketHandler(ResourceLocation packetId, Consumer<PacketContext> handler) {
        throw new RuntimeException("@ExpectPlatform should have replaced this");
    }

    // Register a packet handler for server-to-client packets
    @ExpectPlatform
    public static void registerS2CPacketHandler(ResourceLocation packetId, Consumer<PacketContext> handler) {
        throw new RuntimeException("@ExpectPlatform should have replaced this");
    }

    // Register a custom entity type (like a thrown projectile)
    @ExpectPlatform
    public static void registerEntityType(Object entityType) {
        throw new RuntimeException("@ExpectPlatform should have replaced this");
    }

    // Get the current Minecraft version
    @ExpectPlatform
    public static String getMinecraftVersion() {
        throw new RuntimeException("@ExpectPlatform should have replaced this");
    }

    // Get the current mod loader name
    @ExpectPlatform
    public static String getLoaderName() {
        throw new RuntimeException("@ExpectPlatform should have replaced this");
    }

    // Get client registry access for network packets
    @ExpectPlatform
    public static RegistryAccess getClientRegistryAccess() {
        throw new RuntimeException("@ExpectPlatform should have replaced this");
    }

    // Register client-side input event handlers for VR throwing cancellation
    @ExpectPlatform
    public static void registerClientInputEventHandlers() {
        throw new RuntimeException("@ExpectPlatform should have replaced this");
    }

        // Simple context for packet handlers
        // Gives the player who sent the packet and the packet data
        public record PacketContext(ServerPlayer player, RegistryFriendlyByteBuf buffer) {
    }
}