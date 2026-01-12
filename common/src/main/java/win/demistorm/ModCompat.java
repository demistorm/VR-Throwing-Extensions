package win.demistorm;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.contents.TranslatableContents;
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
        // Items that are blocked since they need the inputs for their various functions
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
            // If toggle is ON, let ImmersiveMC handle it (block this mod)
            // If toggle is OFF and item is in projectile-items config, let this mod handle it
            if (ConfigHelper.ACTIVE.immersiveMCThrowables) {
                return true;
            } else {
                return !isThrowableProjectileItem(stack);
            }
        }

        // Check Vivecraft items
        if (isVivecraftItem(stack)) {
            return true;
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

    // Check by translation key (works for any language)
    private static boolean isVivecraftItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        // Check if the hover name has a Vivecraft translation key
        if (stack.getHoverName().getContents() instanceof TranslatableContents translatableContent) {
            String translationKey = translatableContent.getKey();
            return translationKey.equals("vivecraft.item.climbclaws") ||
                   translationKey.equals("vivecraft.item.jumpboots");
        }

        return false;
    }
}