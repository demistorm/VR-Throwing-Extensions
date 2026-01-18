package win.demistorm;

import net.minecraft.world.entity.EntityType;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import win.demistorm.command.ItemInfoCommand;
import win.demistorm.effects.ProjectileEffect;
import win.demistorm.network.Network;
import win.demistorm.network.TNTServer;

// Common initialization code
public class VRThrowingExtensions {

	public static final String MOD_ID = "vr-throwing-extensions";
	public static final Logger log = LoggerFactory.getLogger(MOD_ID);

	public static EntityType<ThrownProjectileEntity> THROWN_ITEM_TYPE;
	public static EntityType<ThrownTNTEntity> THROWN_TNT_TYPE;

	// Debug mode switch
	public static final boolean debugMode = false;

	static {
		Configurator.setLevel(MOD_ID, debugMode ? Level.DEBUG : Level.INFO);
	}

	public static void initialize() {
		log.info("VR Throwing Extensions (SERVER) starting!");

		// Entity registration is handled by each platform (Fabric, Forge, NeoForge)
		// They handle it in their own ways

		// Load or create server config file
		ConfigHelper.loadOrCreateServerConfig();

		// Load or create projectile items config file
		ProjectileEffect.loadOrCreateConfig();

		// Start the networking system
		Network.initialize();

		// Register commands
		Platform.registerCommands(dispatcher -> {
			ItemInfoCommand.register(dispatcher);
		});

		// Set up server events for config sync
		registerServerEventHandlers();

		// Set up TNT server (fuse timers)
		TNTServer.instance().registerTickHandler();
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