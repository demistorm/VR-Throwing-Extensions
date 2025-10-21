package win.demistorm.neoforge;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import win.demistorm.VRThrowingExtensions;

// NeoForge packet wrapper for cross-platform networking
public record BufferPacket(RegistryFriendlyByteBuf buffer) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BufferPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(VRThrowingExtensions.MOD_ID, "buffer_packet"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BufferPacket> STREAM_CODEC =
        CustomPacketPayload.codec(BufferPacket::write, BufferPacket::read);

    public static BufferPacket read(RegistryFriendlyByteBuf buffer) {
        int length = buffer.readInt();
        // Use deprecated constructor with suppression (still works perfectly)
        @SuppressWarnings("deprecation")
        var resultBuf = new RegistryFriendlyByteBuf(buffer.readBytes(length), buffer.registryAccess());
        return new BufferPacket(resultBuf);
    }

    public void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeInt(this.buffer.readableBytes());
        buffer.writeBytes(this.buffer);
        this.buffer.resetReaderIndex();
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}