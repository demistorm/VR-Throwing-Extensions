package win.demistorm.network;

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
                buf.writeBoolean(data.wholeStack());
                buf.writeFloat(data.rollDeg());
            },
            // Load throw data from buffer
            (buf) -> new ThrowData(
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readBoolean(), buf.readFloat()
            ),
            // Process throw packet
            (data, player) -> NetworkHandlers.handleThrow(player, data)
        );

        // Catch packet (client starts or stops catching)
        INSTANCE.register(CatchData.class,
            (data, buf) -> {
                buf.writeInt(data.entityId());
                buf.writeBoolean(data.startCatch());
            },
            (buf) -> new CatchData(buf.readInt(), buf.readBoolean()),
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
    }
}