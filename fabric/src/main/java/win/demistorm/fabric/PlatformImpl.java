package win.demistorm.fabric;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import win.demistorm.Platform;

import java.io.File;
import java.util.function.Consumer;

// Fabric platform implementations
@SuppressWarnings("unused")
public class PlatformImpl {

    // Check if running on client
    @SuppressWarnings("unused")
    public static boolean isClient() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    // Check if in dev mode
    @SuppressWarnings("unused")
    public static boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    // Check if Forge-like (never true for Fabric)
    @SuppressWarnings("unused")
    public static boolean isForgeLike() {
        return false;
    }

    // Check if mod is installed
    @SuppressWarnings("unused")
    public static boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    // Get config directory
    @SuppressWarnings("unused")
    public static File getConfigFolder() {
        return FabricLoader.getInstance().getConfigDir().toFile();
    }

    // Listen for server ticks
    @SuppressWarnings("unused")
    public static void registerServerPostTickListener(Consumer<MinecraftServer> listener) {
        ServerTickEvents.END_SERVER_TICK.register(listener::accept);
    }

    // Listen for player ticks
    @SuppressWarnings("unused")
    public static void registerServerPlayerPostTickListener(Consumer<ServerPlayer> listener) {
        ServerTickEvents.END_SERVER_TICK.register(server ->
                server.getPlayerList().getPlayers().forEach(listener));
    }

    // Listen for player joins
    @SuppressWarnings("unused")
    public static void registerServerPlayerJoinListener(Consumer<ServerPlayer> listener) {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                listener.accept(handler.getPlayer()));
    }

    // Listen for player leaves
    @SuppressWarnings("unused")
    public static void registerServerPlayerLeaveListener(Consumer<ServerPlayer> listener) {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                listener.accept(handler.getPlayer()));
    }

    // Register commands
    @SuppressWarnings("unused")
    public static void registerCommands(Consumer<CommandDispatcher<CommandSourceStack>> listener) {
        CommandRegistrationCallback.EVENT.register(((dispatcher, registryAccess, environment) ->
                listener.accept(dispatcher)));
    }

    // Send packet to server
    @SuppressWarnings("unused")
    public static void sendToServer(FriendlyByteBuf message) {
        ClientPlayNetworking.send(BufferPacket.ID, message);
    }

    // Send packet to player
    @SuppressWarnings("unused")
    public static void sendToPlayer(ServerPlayer player, FriendlyByteBuf message) {
        ServerPlayNetworking.send(player, BufferPacket.ID, message);
    }

    
    // Register packet handlers (handled in mod initializer now)
    @SuppressWarnings("unused")
    public static void registerC2SPacketHandler(ResourceLocation packetId, Consumer<Platform.PacketContext> handler) {
        // Registration happens in VRThrowingExtensionsFabric.onInitialize()
    }

    // Register client packet handlers (handled in client init)
    @SuppressWarnings("unused")
    public static void registerS2CPacketHandler(ResourceLocation packetId, Consumer<Platform.PacketContext> handler) {
        // Registration happens in PlatformClientImpl.onInitializeClient()
    }

    // Register entity type (handled in main mod class)
    @SuppressWarnings("unused")
    public static void registerEntityType(Object entityType) {
        // Registration handled in main initializer
    }

    // Get Minecraft version
    @SuppressWarnings("unused")
    public static String getMinecraftVersion() {
        return FabricLoader.getInstance().getModContainer("minecraft")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    // Get mod loader name
    @SuppressWarnings("unused")
    public static String getLoaderName() {
        return "fabric";
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
        // Implemented in PlatformClientImpl
    }
}