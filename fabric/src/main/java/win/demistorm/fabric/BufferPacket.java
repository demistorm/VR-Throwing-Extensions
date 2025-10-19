package win.demistorm.fabric;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import win.demistorm.VRThrowingExtensions;

// Fabric packet wrapper for cross-platform networking
public record BufferPacket(RegistryFriendlyByteBuf buffer) implements CustomPacketPayload {

    public static final Type<BufferPacket> ID =
        new Type<>(ResourceLocation.fromNamespaceAndPath(VRThrowingExtensions.MOD_ID, "network"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BufferPacket> CODEC =
            CustomPacketPayload.codec(BufferPacket::write, BufferPacket::read);

    public static BufferPacket read(RegistryFriendlyByteBuf buffer) {
        return new BufferPacket(new RegistryFriendlyByteBuf(buffer.readBytes(buffer.readInt()), buffer.registryAccess()));
    }

    public void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeInt(this.buffer.readableBytes());
        buffer.writeBytes(this.buffer);
        this.buffer.resetReaderIndex();
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}