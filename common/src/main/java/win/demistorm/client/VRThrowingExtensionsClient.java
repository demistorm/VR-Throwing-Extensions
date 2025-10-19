package win.demistorm.client;

import org.vivecraft.api.client.VRClientAPI;
import win.demistorm.client.config.ClientConfigHelper;

import static win.demistorm.VRThrowingExtensions.log;

// Client initialization (called by each platform)
public class VRThrowingExtensionsClient {

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
}