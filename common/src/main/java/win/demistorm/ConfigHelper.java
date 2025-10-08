package win.demistorm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

// Loads/saves json config and synchronises server config to connected clients
public final class ConfigHelper {

    // Toggles
    public static final class Data {
        // Renamed boomerangEffect to weaponEffect
        public WeaponEffectType weaponEffect = WeaponEffectType.BOOMERANG; // Default to Boomerang
        public boolean aimAssist = true;       // On by default
    }

    public static final Identifier CHANNEL =
            Identifier.of(VRThrowingExtensions.MOD_ID, "config_sync");

    private static final Gson  GSON      = new GsonBuilder().setPrettyPrinting().create();
    private static final Path  CONFIGDIR = Path.of("config");
    private static final Path  FILE      = CONFIGDIR.resolve("vr-throwing-extensions.json");

    // Client singleplayer config
    public static final Data CLIENT      = new Data();
    // Server config copy while playing on a server
    public static final Data ACTIVE      = new Data(); // Replaced while playing on a server

    // Serialization
    private static String toJson(Data d)      { return GSON.toJson(d); }
    private static Data   fromJson(String js) { return GSON.fromJson(js, Data.class); }

    // Loads/creates server config
    public static void loadOrCreateServerConfig() {
        Data d = read();
        if (!Files.exists(FILE)) {
            write(d); // Create file with defaults only if it doesn't exist
        }
        copyInto(d, ACTIVE);
    }

    public static void loadOrCreateClientConfig() {
        Data d = read();
        write(d); // Ensure file exists with defaults
        copyInto(d, CLIENT);
        copyInto(CLIENT, ACTIVE);
    }

    private static Data read() {
        try {
            if (Files.exists(FILE))
                return fromJson(Files.readString(FILE));
        } catch (IOException ignored) { }
        return new Data();            // defaults
    }

    public static void write(Data d) {
        try {
            Files.createDirectories(CONFIGDIR);
            Files.writeString(FILE, toJson(d));
        } catch (IOException e) {
            VRThrowingExtensions.log.error("Unable to write config!", e);
        }
    }

    public static void copyInto(Data from, Data to) {
        to.weaponEffect = from.weaponEffect;
        to.aimAssist = from.aimAssist;
    }

    // Sends networking data to client
    public record SyncPayload(String json) implements CustomPayload {
        public static final Id<SyncPayload> ID = new Id<>(CHANNEL);
        public static final PacketCodec<RegistryByteBuf, SyncPayload> CODEC =
                PacketCodec.of((p,b)->b.writeString(p.json), b->new SyncPayload(b.readString()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    static {
        PayloadTypeRegistry.playS2C().register(SyncPayload.ID, SyncPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SyncPayload.ID, SyncPayload.CODEC);
    }

    // Registration hooks
    public static void initServerSide() {
        // Load File
        loadOrCreateServerConfig();

        // Send to every joining player
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                sender.sendPacket(new SyncPayload(toJson(ACTIVE)))
        );
    }

    // Hears that the client recieved the config
    public static void clientReceivedRemote(String json) {
        copyInto(fromJson(json), ACTIVE);
        VRThrowingExtensions.log.debug("Received remote config: {}", json);
    }

    // Tells when the client disconnects
     public static void clientDisconnected() {
        copyInto(CLIENT, ACTIVE);
    }
}