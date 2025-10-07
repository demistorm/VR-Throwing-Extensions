package win.demistorm.neoforge;

import net.neoforged.fml.common.Mod;

import win.demistorm.ExampleMod;

@Mod(ExampleMod.MOD_ID)
public final class ExampleModNeoForge {
    public ExampleModNeoForge() {
        // Run our common setup.
        ExampleMod.init();
    }
}
