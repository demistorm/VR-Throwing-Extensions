package win.demistorm;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import win.demistorm.effects.ProjectileEffect;
import win.demistorm.network.Network;
import win.demistorm.network.TNTServer;

import java.util.ArrayList;
import java.util.List;

// Common initialization code
public class VRThrowingExtensions {

	public static final String MOD_ID = "vr_throwing_extensions";
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

		// Set up server events for config sync
		registerServerEventHandlers();

		// Set up TNT server (fuse timers)
		TNTServer.instance().registerTickHandler();
	}

	// Handle server events for syncing config with players
	private static void registerServerEventHandlers() {
		// Send config to new players when they join
		Platform.registerServerPlayerJoinListener(player -> {
			if (ConfigHelper.ACTIVE.serverAuthoritative) {
				// Send server config to player
				ConfigHelper.sendConfigToPlayer(player);
				log.debug("Sent config to joining player: {}", player.getName().getString());

				// Send welcome message showing VTE server's configured features
				sendWelcomeMessage(player);
			} else {
				// Don't send ConfigSync for if non-authoritative
				log.debug("Non-authoritative server: {} will use local config", player.getName().getString());

				player.sendSystemMessage(Component.literal("§a[VTE]§r This server uses your personal VTE settings!"));
			}
		});
	}

	// Send welcome message showing server settings (authoritative mode)
	private static void sendWelcomeMessage(ServerPlayer player) {
		MutableComponent message = Component.literal("§a[VTE]§r Server settings: ");

		// Weapon effect
		String weaponEffectName = switch (ConfigHelper.ACTIVE.weaponEffect) {
			case BOOMERANG -> "§eBoomerang";
			case EMBED -> "§cEmbedding";
			case OFF -> "§7Off";
		};
		message.append(Component.literal(weaponEffectName + "§r, "));

		// Key features
		List<String> features = new ArrayList<>();
		if (ConfigHelper.ACTIVE.throwableProjectiles) features.add("Projectiles");
		if (ConfigHelper.ACTIVE.throwableTNT) features.add("TNT Throwing");
		if (ConfigHelper.ACTIVE.placeBlocksOnThrow) features.add("Block Throwing");

		message.append(Component.literal(String.join(", ", features)));
		player.sendSystemMessage(message);
	}
}