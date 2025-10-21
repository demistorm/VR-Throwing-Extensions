package win.demistorm.forge;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
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
        TickEvent.ServerTickEvent.Post.BUS.addListener(event -> listener.accept(event.server()));
    }

    // Listen for player ticks
    @SuppressWarnings("unused")
    public static void registerServerPlayerPostTickListener(Consumer<ServerPlayer> listener) {
        TickEvent.ServerTickEvent.Post.BUS.addListener(event ->
                event.server().getPlayerList().getPlayers().forEach(listener));
    }

    // Listen for player joins
    @SuppressWarnings("unused")
    public static void registerServerPlayerJoinListener(Consumer<ServerPlayer> listener) {
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer sp) {
                listener.accept(sp);
            }
        });
    }

    // Listen for player leaves
    @SuppressWarnings("unused")
    public static void registerServerPlayerLeaveListener(Consumer<ServerPlayer> listener) {
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer sp) {
                listener.accept(sp);
            }
        });
    }

    // Register commands
    @SuppressWarnings("unused")
    public static void registerCommands(Consumer<CommandDispatcher<CommandSourceStack>> listener) {
        RegisterCommandsEvent.BUS.addListener(event -> listener.accept(event.getDispatcher()));
    }

    // Send packet to server
    @SuppressWarnings("unused")
    public static void sendToServer(RegistryFriendlyByteBuf message) {
        // Convert buffer for Forge
        net.minecraft.network.FriendlyByteBuf forgeBuf = new net.minecraft.network.FriendlyByteBuf(message);
        VRThrowingExtensionsForge.NETWORK.send(forgeBuf, PacketDistributor.SERVER.noArg());
    }

    // Send packet to player
    @SuppressWarnings("unused")
    public static void sendToPlayer(ServerPlayer player, RegistryFriendlyByteBuf message) {
        // Convert buffer for Forge
        net.minecraft.network.FriendlyByteBuf forgeBuf = new net.minecraft.network.FriendlyByteBuf(message);
        VRThrowingExtensionsForge.NETWORK.send(forgeBuf, PacketDistributor.PLAYER.with(player));
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
        var container = ModList.get().getModContainerById("minecraft");
        if (container.isPresent()) {
            return container.get().getModInfo().getVersion().toString();
        }
        return "unknown";
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
        // Register client input events using EventBus 7 API
        PlatformClientImpl.register();
    }
}