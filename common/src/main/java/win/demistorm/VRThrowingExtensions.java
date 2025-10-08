package win.demistorm;

import net.fabricmc.api.ModInitializer;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import win.demistorm.network.NetworkHelper;

// Common side initialization
public class VRThrowingExtensions implements ModInitializer {

	public static final String MOD_ID = "vr-throwing-extensions";
	public static final Logger log = LoggerFactory.getLogger(MOD_ID);

	public static EntityType<ThrownProjectileEntity> THROWN_ITEM_TYPE;

	// DEBUG mode on/off
	public static final boolean debugMode = false;

	static {
		Configurator.setLevel(MOD_ID, debugMode ? Level.DEBUG : Level.INFO);
	}

	@Override
	public void onInitialize() {
		log.info("VR Throwing Extensions (SERVER) starting!");

		// Register entity type with improved tracking settings
		RegistryKey<EntityType<?>> entityTypeKey = RegistryKey.of(RegistryKeys.ENTITY_TYPE,
				Identifier.of(MOD_ID, "generic_thrown_item"));

		THROWN_ITEM_TYPE = Registry.register(
				Registries.ENTITY_TYPE,
				entityTypeKey.getValue(),
				EntityType.Builder.<ThrownProjectileEntity>create(ThrownProjectileEntity::new, SpawnGroup.MISC)
						.dimensions(0.25f, 0.25f)
						.maxTrackingRange(64)
						.trackingTickInterval(5) // Update every 5 ticks = 4 times per second
						.build(entityTypeKey));

		// Loads/creates config
		ConfigHelper.initServerSide();

		// Initializes server networking
		NetworkHelper.initServer();
	}
}