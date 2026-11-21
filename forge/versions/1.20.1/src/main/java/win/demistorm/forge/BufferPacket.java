package win.demistorm.forge;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import win.demistorm.VRThrowingExtensions;
import win.demistorm.network.Network;

import java.util.function.Supplier;

// Simple packet wrapper for Forge networking
public class BufferPacket implements Packet<PacketListener> {
    private final FriendlyByteBuf buffer;

    public BufferPacket(FriendlyByteBuf buffer) {
        this.buffer = buffer;
    }

    public FriendlyByteBuf getBuffer() {
        return buffer;
    }

    public static void encode(BufferPacket packet, FriendlyByteBuf buf) {
        buf.writeBytes(packet.buffer);
    }

    public static BufferPacket decode(FriendlyByteBuf buf) {
        FriendlyByteBuf newBuf = new FriendlyByteBuf(buf.copy());
        return new BufferPacket(newBuf);
    }

    public static void handle(BufferPacket packet, Supplier<NetworkEvent.Context> ctx) {
        VRThrowingExtensions.log.debug("[Forge Network] Received packet on {}",
            ctx.get().getDirection().getReceptionSide().isClient() ? "client" : "server");

        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                VRThrowingExtensions.log.debug("[Forge Network] Forwarding to client handler");
                ClientSetup.handleNetworkPacket(packet.buffer);
            } else {
                ServerPlayer sender = ctx.get().getSender();
                VRThrowingExtensions.log.debug("[Forge Network] Forwarding to server handler, sender: {}",
                    sender != null ? sender.getName().getString() : "null");
                Network.INSTANCE.handlePacket(sender, packet.buffer);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    @Override
    public void handle(PacketListener listener) {
        // Handled by Forge network system
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBytes(buffer);
    }
}