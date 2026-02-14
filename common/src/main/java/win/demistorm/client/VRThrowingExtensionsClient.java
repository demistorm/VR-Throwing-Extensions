package win.demistorm.client;

import org.vivecraft.api.client.VRClientAPI;
import win.demistorm.ConfigHelper;
import win.demistorm.Platform;
import win.demistorm.client.config.ClientConfigHelper;
import win.demistorm.client.commands.ItemIdCommand;
import win.demistorm.client.commands.AddProjectileItemCommand;
import win.demistorm.network.Network;
import win.demistorm.network.data.PlayerConfigData;

import static win.demistorm.VRThrowingExtensions.log;

// Client initialization (called by each platform)
public class VRThrowingExtensionsClient {

	private static int configSendTimer = 0;

	// Set up client-side systems
	public static void initializeClient() {
		log.info("VR Throwing Extensions (CLIENT) starting!");
		// Load client config
		ClientConfigHelper.init();
		// Set up input cancellation for VR throwing
		win.demistorm.Platform.registerClientInputEventHandlers();
		// Register VR tracker with Vivecraft
		registerTracker();
		// Register projectile renderer
		registerEntityRenderer();
		// Register client-side commands
		registerCommands();
	}

	// Start timer to send config to server after 5 seconds (called when player joins server)
	public static void startConfigSendTimer() {
		// Reset timer on client init
		configSendTimer = 0;

		// Schedule task to run every tick
		Platform.registerClientTickEvent(() -> {
			if (configSendTimer >= 0) {
				configSendTimer++;
				if (configSendTimer >= 100) { // 5 seconds
					sendPlayerConfigToServer();
					configSendTimer = -1; // Don't send again
				}
			}
		});
	}

	// Send player config to server (only if non-authoritative)
	private static void sendPlayerConfigToServer() {
		if (!ConfigHelper.receivedServerConfig()) {
			PlayerConfigData data = new PlayerConfigData(
				ConfigHelper.CLIENT.weaponEffect,
				ConfigHelper.CLIENT.throwableProjectiles,
				ConfigHelper.CLIENT.crouchBehaviorProjectiles,
				ConfigHelper.CLIENT.placeBlocksOnThrow,
				ConfigHelper.CLIENT.crouchBehaviorPlaceBlocks,
				ConfigHelper.CLIENT.onlyPlaceLights,
				ConfigHelper.CLIENT.immersiveMCThrowables,
				ConfigHelper.CLIENT.throwConflictingItems
			);
			Network.INSTANCE.sendToServer(data);
			log.debug("[Client] Sent player config to server (non-authoritative mode)");
		}
	}

	// Reset config send timer (called when player disconnects)
	public static void resetConfigSendTimer() {
		configSendTimer = -1;
	}

	// Platform-specific renderer registration
	private static void registerEntityRenderer() {
		// Each platform implements this differently
		log.info("Registering entity renderer for thrown projectile");
	}

	// Add tracker to Vivecraft system
	private static void registerTracker() {
		VRClientAPI.instance().addClientRegistrationHandler(event ->
				event.registerTrackers(new ThrowHelper.ThrowTracker()));
	}

	// Register clientside commands
	private static void registerCommands() {
		win.demistorm.Platform.registerCommands(dispatcher -> {
			ItemIdCommand.register(dispatcher);
			AddProjectileItemCommand.register(dispatcher);
		});
	}
}