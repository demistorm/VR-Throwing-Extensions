package win.demistorm.network;

import win.demistorm.ConfigHelper;
import win.demistorm.WeaponEffectType;
import win.demistorm.network.data.*;
import win.demistorm.network.data.CancelTNTData;

// Handles all networking between client and server
public class Network {

    // Main network channel
    public static final NetworkChannel INSTANCE = new NetworkChannel();

    // Set up networking (call from main mod class)
    public static void initialize() {
        registerPackets();
    }

    // Register all packet types
    private static void registerPackets() {
        // Throw packet (client tells server it threw something)
        INSTANCE.register(ThrowData.class,
            // Save throw data to buffer
            (data, buf) -> {
                buf.writeDouble(data.posX());
                buf.writeDouble(data.posY());
                buf.writeDouble(data.posZ());
                buf.writeDouble(data.velX());
                buf.writeDouble(data.velY());
                buf.writeDouble(data.velZ());
                buf.writeBoolean(data.useBindHeld());
                buf.writeBoolean(data.playerCrouched());
                buf.writeFloat(data.rollDeg());
                buf.writeEnum(data.hand());
            },
            // Load throw data from buffer
            (buf) -> new ThrowData(
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readBoolean(), buf.readBoolean(), buf.readFloat(),
                buf.readEnum(net.minecraft.world.InteractionHand.class)
            ),
            // Process throw packet
            (data, player) -> NetworkHandlers.handleThrow(player, data)
        );

        // Catch packet (client starts or stops catching)
        INSTANCE.register(CatchData.class,
            (data, buf) -> {
                buf.writeInt(data.entityId());
                buf.writeBoolean(data.startCatch());
                buf.writeEnum(data.hand());
            },
            (buf) -> new CatchData(buf.readInt(), buf.readBoolean(), buf.readEnum(net.minecraft.world.InteractionHand.class)),
            (data, player) -> NetworkHandlers.handleCatch(player, data)
        );

        // Catch update packet (client sends velocity updates while catching)
        INSTANCE.register(CatchUpdateData.class,
            (data, buf) -> {
                buf.writeInt(data.entityId());
                buf.writeDouble(data.velX());
                buf.writeDouble(data.velY());
                buf.writeDouble(data.velZ());
                buf.writeFloat(data.rollDeg());
            },
            (buf) -> new CatchUpdateData(
                buf.readInt(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readFloat()
            ),
            (data, player) -> NetworkHandlers.handleCatchUpdate(player, data)
        );

        // Catch complete packet (client caught the item)
        INSTANCE.register(CatchCompleteData.class,
            (data, buf) -> buf.writeInt(data.entityId()),
            (buf) -> new CatchCompleteData(buf.readInt()),
            (data, player) -> NetworkHandlers.handleCatchComplete(player, data)
        );

        // Blood particle packet (server tells clients to show impact effects)
        INSTANCE.register(BloodParticleData.class,
            (data, buf) -> {
                buf.writeDouble(data.posX());
                buf.writeDouble(data.posY());
                buf.writeDouble(data.posZ());
                buf.writeDouble(data.velX());
                buf.writeDouble(data.velY());
                buf.writeDouble(data.velZ());
            },
            (buf) -> new BloodParticleData(
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble()
            ),
            (data, player) -> NetworkHandlers.handleBloodParticle(player, data)
        );

        // Bleeding particle packet (server tells clients to show bleeding effects)
        INSTANCE.register(BleedingParticleData.class,
            (data, buf) -> {
                buf.writeDouble(data.posX());
                buf.writeDouble(data.posY());
                buf.writeDouble(data.posZ());
            },
            (buf) -> new BleedingParticleData(
                buf.readDouble(), buf.readDouble(), buf.readDouble()
            ),
            (data, player) -> NetworkHandlers.handleBleedingParticle(player, data)
        );

        // Config sync packet (server sends settings to clients)
        INSTANCE.register(ConfigSyncData.class,
            (data, buf) -> {
                buf.writeVarInt(data.json().length());
                buf.writeUtf(data.json());
            },
            (buf) -> {
                int length = buf.readVarInt();
                return new ConfigSyncData(buf.readUtf(length));
            },
            (data, player) -> NetworkHandlers.handleConfigSync(player, data)
        );

        // Player config packet (for non-authoritative servers)
        INSTANCE.register(PlayerConfigData.class,
            (data, buf) -> {
                buf.writeEnum(data.weaponEffect());
                buf.writeBoolean(data.throwableProjectiles());
                buf.writeEnum(data.crouchBehaviorProjectiles());
                buf.writeBoolean(data.placeBlocksOnThrow());
                buf.writeEnum(data.crouchBehaviorPlaceBlocks());
                buf.writeBoolean(data.onlyPlaceLights());
                buf.writeBoolean(data.immersiveMCThrowables());
                buf.writeBoolean(data.throwConflictingItems());
            },
            (buf) -> new PlayerConfigData(
                buf.readEnum(WeaponEffectType.class),
                buf.readBoolean(),
                buf.readEnum(ConfigHelper.CrouchBehavior.class),
                buf.readBoolean(),
                buf.readEnum(ConfigHelper.CrouchBehavior.class),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean()
            ),
            (data, player) -> NetworkHandlers.handlePlayerConfig(player, data)
        );

        // TNT lit packet (client lit TNT with flint & steel swipe)
        INSTANCE.register(TNTLitData.class,
            (data, buf) -> {
                // Empty packet, no data to write
            },
            (buf) -> new TNTLitData(),
            (data, player) -> NetworkHandlers.handleTNTLit(player)
        );

        // Throw lit TNT packet (client throws lit TNT with flint & steel)
        INSTANCE.register(ThrowTNTData.class,
            (data, buf) -> {
                buf.writeDouble(data.posX());
                buf.writeDouble(data.posY());
                buf.writeDouble(data.posZ());
                buf.writeDouble(data.velX());
                buf.writeDouble(data.velY());
                buf.writeDouble(data.velZ());
                buf.writeFloat(data.rollDeg());
                buf.writeEnum(data.hand());
            },
            (buf) -> new ThrowTNTData(
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readFloat(),
                buf.readEnum(net.minecraft.world.InteractionHand.class)
            ),
            (data, player) -> NetworkHandlers.handleThrowTNT(player, data)
        );

        // Cancel TNT packet (client canceled lit TNT throw)
        INSTANCE.register(CancelTNTData.class,
            (data, buf) -> {
                // Empty packet, no data to write
            },
            (buf) -> new CancelTNTData(),
            (data, player) -> NetworkHandlers.handleCancelTNT(player)
        );
    }
}