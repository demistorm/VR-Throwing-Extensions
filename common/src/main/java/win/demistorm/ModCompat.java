package win.demistorm;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import win.demistorm.effects.ProjectileEffect;

import java.util.HashSet;
import java.util.Set;

// Prevents throwing certain items and handles ImmersiveMC compatibility
public class ModCompat {

    // Checks if ImmersiveMC is present
    private static final boolean IMCLoaded = Platform.isModLoaded("immersivemc");

    // Items that can't be thrown
    private static final Set<ResourceLocation> blockedItems = new HashSet<>();

    static {
        // Block bows since they need the inputs for shooting
        blockedItems.add(ResourceLocation.fromNamespaceAndPath("minecraft", "bow"));
        blockedItems.add(ResourceLocation.fromNamespaceAndPath("minecraft", "crossbow"));
    }

    // Check if an item can't be thrown
    public static boolean throwingDisabled(ItemStack stack) {
        if (stack.isEmpty()) return true;

        Item item = stack.getItem();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);

        // If ImmersiveMC is loaded, check compatibility toggle
        if (IMCLoaded && immersiveMCExceptions(id)) {
            // If toggle is ON, let ImmersiveMC handle it (block our mod)
            // If toggle is OFF and item is in projectile-items config, let our mod handle it
            if (ConfigHelper.ACTIVE.immersiveMCThrowables) {
                return true; // Toggle ON: block our mod, let ImmersiveMC handle
            } else {
                // Toggle OFF: only block if NOT in projectile-items config
                return !isThrowableProjectileItem(stack);
            }
        }

        // Block items on our blacklist
        return blockedItems.contains(id);
    }

    // Check if an item is in the throwable projectiles config
    private static boolean isThrowableProjectileItem(ItemStack stack) {
        // Check if the item is in the projectile items list
        Item item = stack.getItem();
        ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(item);

        // Get the list of configured projectile items
        return ProjectileEffect.getProjectileItemsList().contains(itemKey.toString());
    }

    // Items that ImmersiveMC already handles
    private static boolean immersiveMCExceptions(ResourceLocation itemId) {
        return itemId.getPath().equals("snowball")
                || itemId.getPath().equals("ender_pearl")
                || itemId.getPath().equals("egg")
                || itemId.getPath().equals("experience_bottle")
                || itemId.getPath().startsWith("splash_potion")
                || itemId.getPath().startsWith("lingering_potion")
                || itemId.getPath().startsWith("trident")
                || itemId.getPath().startsWith("fishing_rod");
    }
}