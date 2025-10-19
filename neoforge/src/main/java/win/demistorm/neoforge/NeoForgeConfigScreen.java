package win.demistorm.neoforge;

import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.fml.ModLoadingContext;
import win.demistorm.client.config.ConfigScreen;

// NeoForge config screen registration
public class NeoForgeConfigScreen {

    public static void register() {
        ModLoadingContext.get().registerExtensionPoint(IConfigScreenFactory.class,
            () -> (minecraft, screen) -> ConfigScreen.SimpleToggleScreen.create(screen));
    }
}