package win.demistorm.network;

// Data for config sync packets (server to client)
public record ConfigSyncData(
    String json // Config settings in json format
) {}