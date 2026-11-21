package win.demistorm.fabric;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import win.demistorm.VRThrowingExtensions;

// Fabric packet wrapper for cross-platform networking
public record BufferPacket(FriendlyByteBuf buffer) {

    public static final ResourceLocation ID =
            new ResourceLocation(VRThrowingExtensions.MOD_ID, "network");

    public static BufferPacket read(FriendlyByteBuf buffer) {
        return new BufferPacket(new FriendlyByteBuf(buffer.readBytes(buffer.readInt())));
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeInt(this.buffer.readableBytes());
        buffer.writeBytes(this.buffer);
        this.buffer.resetReaderIndex();
    }
}