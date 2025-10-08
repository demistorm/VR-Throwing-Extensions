package win.demistorm.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import win.demistorm.VRThrowingExtensions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ClientOnlyConfig {

    public static final class Data {
        public boolean bloodEffect = false;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIGDIR = Path.of("config");
    private static final Path FILE = CONFIGDIR.resolve("vr-throwing-extensions-client.json");

    public static final Data ACTIVE = new Data();

    public static void loadOrCreate() {
        Data d = read();
        write(d);
        ACTIVE.bloodEffect = d.bloodEffect;
    }

    private static Data read() {
        try {
            if (Files.exists(FILE))
                return GSON.fromJson(Files.readString(FILE), Data.class);
        } catch (IOException ignored) {}
        return new Data();
    }

    public static void write(Data d) {
        try {
            Files.createDirectories(CONFIGDIR);
            Files.writeString(FILE, GSON.toJson(d));
        } catch (IOException e) {
            VRThrowingExtensions.log.error("Unable to write client-only config!", e);
        }
    }

    private ClientOnlyConfig() {}
}