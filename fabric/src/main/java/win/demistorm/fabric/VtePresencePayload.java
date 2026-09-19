package win.demistorm.fabric;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import win.demistorm.VRThrowingExtensions;

public record VtePresencePayload() implements CustomPacketPayload {

    public static final Type<VtePresencePayload> ID =
        new Type<>(Identifier.fromNamespaceAndPath(VRThrowingExtensions.MOD_ID, "presence"));

    public static final StreamCodec<FriendlyByteBuf, VtePresencePayload> CODEC =
        StreamCodec.unit(new VtePresencePayload());

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
