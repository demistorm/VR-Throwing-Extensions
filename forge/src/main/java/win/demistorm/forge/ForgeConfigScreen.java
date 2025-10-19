package win.demistorm.forge;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import win.demistorm.client.config.ConfigScreen;

// Forge config screen registration
public class ForgeConfigScreen {
    public static void register(FMLJavaModLoadingContext context) {
        context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new ConfigScreenHandler.ConfigScreenFactory((mc, screen) -> ConfigScreen.SimpleToggleScreen.create(screen)));
    }
}