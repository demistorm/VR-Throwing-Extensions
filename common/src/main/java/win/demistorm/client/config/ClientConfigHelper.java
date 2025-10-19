package win.demistorm.client.config;

import win.demistorm.ConfigHelper;
// Sets up client config and handles server config sync
public final class ClientConfigHelper {
    private ClientConfigHelper() {}

    public static void init() {
        // Local config files
        ConfigHelper.loadOrCreateClientConfig();
        ClientOnlyConfig.loadOrCreate();
    }
}