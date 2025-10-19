package win.demistorm.network;

// Data for bleeding particle packets (server to client)
// Used for drip effects when weapons are stuck in enemies
public record BleedingParticleData(double posX, double posY, double posZ) {
    // Position where bleeding particles spawn
}