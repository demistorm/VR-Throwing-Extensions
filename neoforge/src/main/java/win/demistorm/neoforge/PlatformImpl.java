package win.demistorm.neoforge;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

// NeoForge platform implementations
@SuppressWarnings("unused")
public class PlatformImpl {

    // Get platform name
    @SuppressWarnings("unused")
    public static String getPlatformName() {
        return "NeoForge";
    }

    // Check if running on client
    @SuppressWarnings("unused")
    public static boolean isClientSide() {
        return FMLEnvironment.getDist().isClient();
    }

    // Check if in dev mode
    @SuppressWarnings("unused")
    public static boolean isDevelopmentEnvironment() {
        return !FMLEnvironment.isProduction();
    }

    // Check if mod is installed
    @SuppressWarnings("unused")
    public static boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    // Get config directory
    @SuppressWarnings("unused")
    public static Path getConfigFolder() {
        return FMLPaths.CONFIGDIR.get();
    }

    // Get Minecraft version (has issues on 1.21.10 NeoForge but I don't need it currently anyway
//    @SuppressWarnings("unused")
//    public static String getMinecraftVersion() {
//        return net.neoforged.fml.loading.FMLLoader.versionInfo().mcVersion();
//    }

    // Check if Forge-like (true for NeoForge)
    @SuppressWarnings("unused")
    public static boolean isForgeLike() {
        return true;
    }

    // Send packet to server
    @SuppressWarnings("unused")
    public static void sendToServer(RegistryFriendlyByteBuf buffer) {
        ClientPacketDistributor.sendToServer(new BufferPacket(buffer));
    }

    // Send packet to player
    @SuppressWarnings("unused")
    public static void sendToPlayer(ServerPlayer player, RegistryFriendlyByteBuf buffer) {
        PacketDistributor.sendToPlayer(player, new BufferPacket(buffer));
    }


    // Get client registry access
    @SuppressWarnings("unused")
    public static RegistryAccess getClientRegistryAccess() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            return client.player.registryAccess();
        }
        return Objects.requireNonNull(client.level).registryAccess();
    }

    // Listen for server ticks
    @SuppressWarnings("unused")
    public static void registerServerPostTickListener(Runnable listener) {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> listener.run());
    }

    // Listen for player ticks
    @SuppressWarnings("unused")
    public static void registerServerPlayerPostTickListener(java.util.function.Consumer<ServerPlayer> listener) {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> event.getServer().getPlayerList().getPlayers().forEach(listener));
    }

    // Listen for player joins
    @SuppressWarnings("unused")
    public static void registerServerPlayerJoinListener(java.util.function.Consumer<ServerPlayer> listener) {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer sp) {
                listener.accept(sp);
            }
        });
    }

    // Listen for player leaves
    @SuppressWarnings("unused")
    public static void registerServerPlayerLeaveListener(java.util.function.Consumer<ServerPlayer> listener) {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer sp) {
                listener.accept(sp);
            }
        });
    }

    // Register commands
    @SuppressWarnings("unused")
    public static void registerCommands(java.util.function.Consumer<CommandDispatcher<CommandSourceStack>> registrar) {
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> registrar.accept(event.getDispatcher()));
    }

    // Register entity type (handled in main mod class)
    @SuppressWarnings("unused")
    public static void registerEntityType(Object entityType) {
        // Registration handled in main initializer
    }

    // Get mod loader name
    @SuppressWarnings("unused")
    public static String getLoaderName() {
        return "neoforge";
    }

    // Register input events (handled in client code)
    @SuppressWarnings("unused")
    public static void registerClientInputEventHandlers() {
        // Implemented in PlatformClientImpl with @SubscribeEvent
    }

    // Register packet handlers (handled in main mod class)
    @SuppressWarnings("unused")
    public static void registerC2SPacketHandler(ResourceLocation packetId, Consumer<win.demistorm.Platform.PacketContext> handler) {
        // Registration happens in main mod class with RegisterPayloadHandlersEvent
    }

    // Register client packet handlers (handled in main mod class)
    @SuppressWarnings("unused")
    public static void registerS2CPacketHandler(ResourceLocation packetId, Consumer<win.demistorm.Platform.PacketContext> handler) {
        // Registration happens in main mod class with RegisterPayloadHandlersEvent
    }
}