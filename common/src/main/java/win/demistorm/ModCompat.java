package win.demistorm;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import java.util.HashSet;
import java.util.Set;

// Prevents throwing certain items and handles ImmersiveMC compatibility
public class ModCompat {

    // Checks if ImmersiveMC is present
    private static final boolean IMCLoaded = Platform.isModLoaded("immersivemc");

    // Items that can't be thrown
    private static final Set<ResourceLocation> blockedItems = new HashSet<>();

    static {
        // Block bows since they have their own throwing
        blockedItems.add(ResourceLocation.fromNamespaceAndPath("minecraft", "bow"));
    }

    // Check if an item can't be thrown
    public static boolean throwingDisabled(ItemStack stack) {
        if (stack.isEmpty()) return true;

        Item item = stack.getItem();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);

        // If ImmersiveMC is loaded, skip items it handles
        if (IMCLoaded && immersiveMCExceptions(id)) {
            return true;
        }

        // Block items on our blacklist
        return blockedItems.contains(id);
    }

    // Items that ImmersiveMC already handles throwing for
    private static boolean immersiveMCExceptions(ResourceLocation itemId) {
        return itemId.getPath().equals("snowball")
                || itemId.getPath().equals("ender_pearl")
                || itemId.getPath().equals("egg")
                || itemId.getPath().equals("experience_bottle")
                || itemId.getPath().startsWith("splash_potion")
                || itemId.getPath().startsWith("lingering_potion")
                || itemId.getPath().startsWith("trident");
    }
}