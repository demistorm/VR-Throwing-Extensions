package win.demistorm.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.util.ActionResult;
import org.vivecraft.api.client.VRClientAPI;
import win.demistorm.VRThrowingExtensions;
import win.demistorm.client.config.ClientConfigHelper;
import win.demistorm.client.particles.BleedingParticle;
import win.demistorm.client.particles.BloodParticle;

import static win.demistorm.VRThrowingExtensions.log;

// Client mod initializer
public class VRThrowingExtensionsClient implements ClientModInitializer {
	// Initializing everything
	@Override
	public void onInitializeClient() {
		log.info("VR Throwing Extensions (CLIENT) starting!");
		// Load config
		ClientConfigHelper.init();
		// Initialize blood effects
		BloodParticle.init();
		BleedingParticle.init();
		// Register the throwing tracker (replaces init and client tick event)
		registerTracker();
		// Well you can see what this does, it's right under here
		registerClientEvents();
		// Register the thrown item entity and renderer
		EntityRendererRegistry.register(
				VRThrowingExtensions.THROWN_ITEM_TYPE,
				ThrownItemRenderer::new
		);
	}

	// Register the tracker with Vivecraft
	private static void registerTracker() {
		VRClientAPI.instance().addClientRegistrationHandler(event ->
				event.registerTrackers(new ThrowHelper.ThrowTracker()));
	}

	// Cancel block breaking and placing/using when throwing is active
	public static void registerClientEvents() {
		// Cancel vanilla block breaking
		AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) ->
				ThrowHelper.cancellingBreaks() ? ActionResult.FAIL : ActionResult.PASS);
		// Cancel vanilla place/use keybind
		UseBlockCallback.EVENT.register((player, world, hand, hit) ->
				ThrowHelper.cancellingUse() ? ActionResult.FAIL : ActionResult.PASS);
		// Cancel using item with place/use keybind
		UseItemCallback.EVENT.register((player, world, hand) ->
				ThrowHelper.cancellingUse() ? ActionResult.FAIL : ActionResult.PASS);
	}
}