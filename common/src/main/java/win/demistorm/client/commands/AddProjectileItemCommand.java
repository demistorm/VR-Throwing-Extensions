package win.demistorm.client.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import win.demistorm.effects.ProjectileEffect;

import java.util.List;

// Clientside command to add held item to projectile items list
public final class AddProjectileItemCommand {

    private AddProjectileItemCommand() {}

    // Register the /addProjectileItemID command
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("addProjectileItemID")
            .executes(AddProjectileItemCommand::executeAddProjectileItem)
        );
    }

    // Execute the command (add held item to projectile items list)
    private static int executeAddProjectileItem(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null) {
            return 0;
        }

        ItemStack heldStack = player.getMainHandItem();

        if (heldStack.isEmpty()) {
            player.displayClientMessage(Component.literal("§cEmpty hand - no item to add"), false);
            return 0;
        }

        // Get the full item ID (namespace:name)
        ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(heldStack.getItem());
        String itemId = itemKey.toString();
        String itemName = heldStack.getDisplayName().getString();

        // Get current projectile items list
        List<String> projectileItems = ProjectileEffect.getProjectileItemsList();

        // Check if item is already in the list
        if (projectileItems.contains(itemId)) {
            player.displayClientMessage(Component.literal(String.format(
                "§e%s§7 (§f%s§7) is §6already§7 in the projectile items list!",
                itemName, itemId
            )), false);
            return 0;
        }

        // Add item to the list
        projectileItems.add(itemId);
        ProjectileEffect.setProjectileItemsList(projectileItems);

        // Trigger config reload to update the mod
        ProjectileEffect.loadProjectileItemsFromConfig();

        player.displayClientMessage(Component.literal(String.format(
            "§aAdded §f%s§7 (§f%s§7) to projectile items list! §7(%d items total)",
            itemName, itemId, projectileItems.size()
        )), false);

        player.displayClientMessage(Component.literal(String.format(
            "§7Tip: Configure projectile items in the config menu or type §f/itemID§7 to identify items"
        )), false);

        return 1;
    }
}