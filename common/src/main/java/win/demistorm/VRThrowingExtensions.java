package win.demistorm;

import net.minecraft.world.entity.EntityType;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import win.demistorm.network.Network;

// Common initialization code
public class VRThrowingExtensions {

	public static final String MOD_ID = "vr-throwing-extensions";
	public static final Logger log = LoggerFactory.getLogger(MOD_ID);

	public static EntityType<ThrownProjectileEntity> THROWN_ITEM_TYPE;

	// Debug mode switch
	public static final boolean debugMode = true;

	static {
		Configurator.setLevel(MOD_ID, debugMode ? Level.DEBUG : Level.INFO);
	}

	public static void initialize() {
		log.info("VR Throwing Extensions (SERVER) starting!");

		// Entity registration is handled by each platform (Fabric, Forge, NeoForge)
		// They handle it in their own ways

		// Load or create server config file
		ConfigHelper.loadOrCreateServerConfig();

		// Start the networking system
		Network.initialize();

		// Set up server events for config sync
		registerServerEventHandlers();
	}

	// Handle server events for syncing config with players
	private static void registerServerEventHandlers() {
		// Send config to new players when they join
		Platform.registerServerPlayerJoinListener(player -> {
			ConfigHelper.sendConfigToPlayer(player);
			log.debug("Sent config to joining player: {}", player.getName().getString());
		});
	}
}