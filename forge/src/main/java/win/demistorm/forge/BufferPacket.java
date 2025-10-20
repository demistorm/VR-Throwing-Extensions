//package win.demistorm.forge;
//
//import net.minecraft.network.FriendlyByteBuf;
//import net.minecraft.network.PacketListener;
//import net.minecraft.network.protocol.Packet;
//import net.minecraftforge.network.NetworkEvent;
//import win.demistorm.network.Network;
//
//import java.util.function.Supplier;
//
//// Simple packet wrapper for Forge networking
//public class BufferPacket implements Packet<PacketListener> {
//    private final FriendlyByteBuf buffer;
//
//    public BufferPacket(FriendlyByteBuf buffer) {
//        this.buffer = buffer;
//    }
//
//    public FriendlyByteBuf getBuffer() {
//        return buffer;
//    }
//
//    public static void encode(BufferPacket packet, FriendlyByteBuf buf) {
//        buf.writeBytes(packet.buffer);
//    }
//
//    public static BufferPacket decode(FriendlyByteBuf buf) {
//        FriendlyByteBuf newBuf = new FriendlyByteBuf(buf.copy());
//        return new BufferPacket(newBuf);
//    }
//
//    public static void handle(BufferPacket packet, Supplier<NetworkEvent.Context> ctx) {
//        ctx.get().enqueueWork(() -> {
//            if (ctx.get().getDirection().getReceptionSide().isClient()) {
//                ClientSetup.handleNetworkPacket(packet.buffer);
//            } else {
//                Network.INSTANCE.handlePacket(ctx.get().getSender(), packet.buffer);
//            }
//        });
//        ctx.get().setPacketHandled(true);
//    }
//
//    @Override
//    public void handle(PacketListener listener) {
//        // Handled by Forge network system
//    }
//
//    @Override
//    public void write(FriendlyByteBuf buf) {
//        buf.writeBytes(buffer);
//    }
//}