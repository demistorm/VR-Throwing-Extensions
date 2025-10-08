package win.demistorm;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import java.util.HashSet;
import java.util.Set;

// Blacklists various items from being thrown, handles mod compatibility with ImmersiveMC
public class ModCompat {

    // Checks if ImmersiveMC is present
    private static final boolean IMCLoaded = FabricLoader.getInstance()
            .isModLoaded("immersivemc");

    // List of blacklisted items
    private static final Set<Identifier> blockedItems = new HashSet<>();

    static {
        // Always block these items
        blockedItems.add(Identifier.of("minecraft", "bow"));
        // Add more if I find any other conflicts
    }

    // Disables throwing blocked items
    public static boolean throwingDisabled(ItemStack stack) {
        if (stack.isEmpty()) return true;

        Item item = stack.getItem();
        Identifier id = Registries.ITEM.getId(item);

        // If ImmersiveMC is loaded, disable throwing immersiveMCExceptions
        if (IMCLoaded && immersiveMCExceptions(id)) {
            return true;
        }

        // If on blacklist, skip
        return blockedItems.contains(id);
    }

    // List of items ImmersiveMC has throwing logic already for
    private static boolean immersiveMCExceptions(Identifier itemId) {
        // Known items ImmersiveMC modifies throwing for
        return itemId.getPath().equals("snowball")
                || itemId.getPath().equals("ender_pearl")
                || itemId.getPath().equals("egg")
                || itemId.getPath().equals("experience_bottle")
                || itemId.getPath().startsWith("splash_potion")
                || itemId.getPath().startsWith("lingering_potion")
                || itemId.getPath().startsWith("trident");
    }
}