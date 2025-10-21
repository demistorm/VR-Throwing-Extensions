package win.demistorm.neoforge;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import win.demistorm.VRThrowingExtensions;

// NeoForge packet wrapper for cross-platform networking
public record BufferPacket(RegistryFriendlyByteBuf buffer, boolean toServer) implements CustomPacketPayload {

    // Separate payload types for each direction
    public static final Type<BufferPacket> CLIENTBOUND_TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(VRThrowingExtensions.MOD_ID, "buffer_packet_client"));

    public static final Type<BufferPacket> SERVERBOUND_TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(VRThrowingExtensions.MOD_ID, "buffer_packet_server"));

    // Shared codec for both directions
    public static final StreamCodec<RegistryFriendlyByteBuf, BufferPacket> STREAM_CODEC =
            CustomPacketPayload.codec(BufferPacket::write, BufferPacket::read);

    // Deserialize
    public static BufferPacket read(RegistryFriendlyByteBuf buffer) {
        int length = buffer.readInt();
        @SuppressWarnings("deprecation")
        var resultBuf = new RegistryFriendlyByteBuf(buffer.readBytes(length), buffer.registryAccess());
        // Direction doesn’t matter on receive
        return new BufferPacket(resultBuf, false);
    }

    // Serialize
    public void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeInt(this.buffer.readableBytes());
        buffer.writeBytes(this.buffer);
        this.buffer.resetReaderIndex();
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        // Choose type based on direction
        return toServer ? SERVERBOUND_TYPE : CLIENTBOUND_TYPE;
    }

    // Factory helpers
    public static BufferPacket toClient(RegistryFriendlyByteBuf buf) {
        return new BufferPacket(buf, false);
    }

    public static BufferPacket toServer(RegistryFriendlyByteBuf buf) {
        return new BufferPacket(buf, true);
    }
}
