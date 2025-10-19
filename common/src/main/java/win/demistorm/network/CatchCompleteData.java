package win.demistorm.network;

// Data for catch complete packets (client to server)
public record CatchCompleteData(
    int entityId // Projectile that was caught
) {}