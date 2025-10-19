package win.demistorm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.level.ServerPlayer;
import win.demistorm.network.ConfigSyncData;
import win.demistorm.network.Network;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

// Handles server config file and syncs settings to players
public final class ConfigHelper {

    // Config settings
    public static final class Data {
        // What weapons do when thrown (boomerang is default)
        public WeaponEffectType weaponEffect = WeaponEffectType.BOOMERANG;
        public boolean aimAssist = true;       // Aim assist is on by default
    }

    private static final Gson  GSON      = new GsonBuilder().setPrettyPrinting().create();
    private static final Path  CONFIGDIR = Path.of("config");
    private static final Path  FILE      = CONFIGDIR.resolve("vr-throwing-extensions.json");

    // Client singleplayer settings
    public static final Data CLIENT      = new Data();
    // Current settings (server config when online, client when offline)
    public static final Data ACTIVE      = new Data();

    // Convert config to/from json
    private static String toJson(Data d)      { return GSON.toJson(d); }
    private static Data   fromJson(String js) { return GSON.fromJson(js, Data.class); }

    // Load or create server config file
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

    // Send current config to a player
    public static void sendConfigToPlayer(ServerPlayer player) {
        ConfigSyncData data = new ConfigSyncData(toJson(ACTIVE));
        Network.INSTANCE.sendToPlayer(player, data);
    }

    // Got config from server
    public static void clientReceivedRemote(String json) {
        copyInto(fromJson(json), ACTIVE);
        VRThrowingExtensions.log.debug("Received remote config: {}", json);
    }

    // Player left server
     public static void clientDisconnected() {
        copyInto(CLIENT, ACTIVE);
    }
}