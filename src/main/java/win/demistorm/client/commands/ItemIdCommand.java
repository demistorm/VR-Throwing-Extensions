package win.demistorm.client.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

// Clientside command to report the held item's full item_id
public final class ItemIdCommand {

    private ItemIdCommand() {}

    // Register the /itemID command
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("itemID")
            .executes(ItemIdCommand::executeItemId)
        );
    }

    // Execute the command (show held item ID)
    private static int executeItemId(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null) {
            return 0;
        }

        ItemStack heldStack = player.getMainHandItem();

        if (heldStack.isEmpty()) {
            player.displayClientMessage(Component.literal("§7Empty hand - no item to identify"), false);
            return 1;
        }

        // Get the full item ID (namespace:name)
        Identifier itemKey = BuiltInRegistries.ITEM.getKey(heldStack.getItem());
        String itemId = itemKey.toString();
        String itemName = heldStack.getDisplayName().getString();
        int stackSize = heldStack.getCount();

        // Display item information to player
        player.displayClientMessage(Component.literal(String.format(
            "§aHeld Item: §f%s§7 (ID: §f%s§7) Count: §f%d§7",
            itemName, itemId, stackSize
        )), false);

        return 1;
    }
}