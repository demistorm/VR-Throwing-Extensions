package win.demistorm.network;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import win.demistorm.Platform;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

// Cross-platform networking system
public class NetworkChannel {

    // All registered packet types
    private final List<PacketRegistrationData<?>> packets = new ArrayList<>();

    // Add a new packet type
    // encoder: saves packet data to buffer
    // decoder: loads packet data from buffer
    // handler: runs when packet arrives
    public <T> void register(Class<T> clazz, BiConsumer<T, FriendlyByteBuf> encoder,
                             Function<FriendlyByteBuf, T> decoder, BiConsumer<T, ServerPlayer> handler) {
        packets.add(new PacketRegistrationData<>(packets.size(), clazz, encoder, decoder, handler));
    }

    // Send packet to server (client to server)
    public <T> void sendToServer(T message) {
        Platform.sendToServer(encode(message, getClientRegistryAccess()));
    }

    // Send packet to one player (server to client)
    public <T> void sendToPlayer(ServerPlayer player, T message) {
        Platform.sendToPlayer(player, encode(message, player.level().registryAccess()));
    }


    // Process incoming packet (called by platform code)
    @SuppressWarnings("unchecked")
    public <T> void handlePacket(ServerPlayer player, FriendlyByteBuf buffer) {
        // Get packet type from buffer
        int packetId = buffer.readInt();

        // Check if packet type exists
        if (packetId < 0 || packetId >= packets.size()) {
            return; // Bad packet ID
        }

        PacketRegistrationData<T> data = (PacketRegistrationData<T>) packets.get(packetId);
        T message;

        try {
            // Load packet from buffer
            message = data.decoder.apply(buffer);
        } catch (Exception e) {
            return; // Failed to read packet
        }

        // Run packet handler
        data.handler.accept(message, player);
    }

    // Turn packet into buffer
    private <T> FriendlyByteBuf encode(T message, RegistryAccess access) {
        // Find packet registration
        PacketRegistrationData<T> data = getData(message);

        // Make buffer with registry access
        FriendlyByteBuf buffer = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());

        // Save packet type ID
        buffer.writeInt(data.id());

        // Save packet data
        data.encoder().accept(message, buffer);

        return buffer;
    }

    // Find packet info by message type
    @SuppressWarnings("unchecked")
    private <T> PacketRegistrationData<T> getData(T message) {
        // Search for matching packet class
        for (PacketRegistrationData<?> data : packets) {
            if (data.clazz == message.getClass()) {
                return (PacketRegistrationData<T>) data;
            }
        }
        throw new IllegalArgumentException("Packet type not registered: " + message.getClass().getName());
    }

    // Get client registry access (platform specific)
    private RegistryAccess getClientRegistryAccess() {
        return Platform.getClientRegistryAccess();
    }

    // Info about a packet type
    public record PacketRegistrationData<T>(
        int id,                              // Packet ID number
        Class<T> clazz,                     // Packet class
        BiConsumer<T, FriendlyByteBuf> encoder,  // Saves to buffer
        Function<FriendlyByteBuf, T> decoder,     // Loads from buffer
        BiConsumer<T, ServerPlayer> handler               // Runs when received
    ) {}
}