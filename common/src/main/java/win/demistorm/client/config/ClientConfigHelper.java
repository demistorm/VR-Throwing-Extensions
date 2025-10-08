package win.demistorm.client.config;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import win.demistorm.ConfigHelper;
import win.demistorm.client.ClientOnlyConfig;

// Bridges the server config networking wiht the client
@Environment(EnvType.CLIENT)
public final class ClientConfigHelper {
    private ClientConfigHelper() {}

    public static void init() {
        // Local config files
        ConfigHelper.loadOrCreateClientConfig();
        ClientOnlyConfig.loadOrCreate();

        // Handle the sync packet
        ClientPlayNetworking.registerGlobalReceiver(
                ConfigHelper.SyncPayload.ID,
                (payload, context) ->
                        ConfigHelper.clientReceivedRemote(payload.json())
        );

        // Restore the local copy when leaving the server
        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> ConfigHelper.clientDisconnected()
        );
    }
}