package win.demistorm.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class ItemInfoCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("itemInfo")
            .executes(ItemInfoCommand::getItemInfo)
        );
    }

    private static int getItemInfo(CommandContext<CommandSourceStack> context) {
        if (context.getSource().getEntity() instanceof ServerPlayer player) {
            ItemStack stack = player.getMainHandItem();

            if (stack.isEmpty()) {
                player.displayClientMessage(Component.literal("§cNo item in main hand!"), false);
                return 0;
            }

            // Build comprehensive item info
            player.displayClientMessage(Component.literal("§6========== ITEM INFO =========="), false);
            player.displayClientMessage(Component.literal("§eDisplay Name: §f" + stack.getHoverName().getString()), false);
            player.displayClientMessage(Component.literal("§eItem ID: §f" + BuiltInRegistries.ITEM.getKey(stack.getItem())), false);

            // Item class info
            player.displayClientMessage(Component.literal("§eItem Class: §f" + stack.getItem().getClass().getName()), false);
            player.displayClientMessage(Component.literal("§eItem Class (simple): §f" + stack.getItem().getClass().getSimpleName()), false);

            // Stack info
            player.displayClientMessage(Component.literal("§eCount: §f" + stack.getCount()), false);
            player.displayClientMessage(Component.literal("§eMax Stack Size: §f" + stack.getMaxStackSize()), false);
            player.displayClientMessage(Component.literal("§eDamage: §f" + stack.getDamageValue()), false);
            player.displayClientMessage(Component.literal("§eMax Damage: §f" + stack.getMaxDamage()), false);

            // NBT Data (from components in 1.21+)
            var tag = stack.get(DataComponents.CUSTOM_DATA);
            if (tag != null && !tag.isEmpty()) {
                player.displayClientMessage(Component.literal("§aCustom NBT Data:"), false);
                try {
                    String nbtString = tag.copyTag().toString();
                    player.displayClientMessage(Component.literal(nbtString), false);
                } catch (Exception e) {
                    player.displayClientMessage(Component.literal("  §cError reading NBT: " + e.getMessage()), false);
                }
            } else {
                player.displayClientMessage(Component.literal("§7Custom NBT Data: §o(None)"), false);
            }

            // Components (1.21+)
            var components = stack.getComponents();
            if (components != null && !components.isEmpty()) {
                player.displayClientMessage(Component.literal("§aComponents: §f" + components.size() + " entries"), false);

                // Show important components
                player.displayClientMessage(Component.literal("§eKey Components:"), false);

                // Damage
                if (stack.has(DataComponents.DAMAGE)) {
                    player.displayClientMessage(Component.literal("  §f- DAMAGE: " + stack.get(DataComponents.DAMAGE)), false);
                }

                // Enchantments
                var enchantments = stack.get(DataComponents.ENCHANTMENTS);
                if (enchantments != null && !enchantments.isEmpty()) {
                    player.displayClientMessage(Component.literal("  §f- ENCHANTMENTS: " + enchantments), false);
                }

                // Custom Name
                if (stack.has(DataComponents.CUSTOM_NAME)) {
                    player.displayClientMessage(Component.literal("  §f- CUSTOM_NAME: " + stack.get(DataComponents.CUSTOM_NAME)), false);
                }

                // Attribute Modifiers
                var attributes = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
                if (attributes != null && !attributes.modifiers().isEmpty()) {
                    player.displayClientMessage(Component.literal("  §f- ATTRIBUTE_MODIFIERS: " + attributes.modifiers().size() + " modifiers"), false);
                }

                // Custom Model Data
                if (stack.has(DataComponents.CUSTOM_MODEL_DATA)) {
                    player.displayClientMessage(Component.literal("  §f- CUSTOM_MODEL_DATA: " + stack.get(DataComponents.CUSTOM_MODEL_DATA)), false);
                }

                // Check all component types present
                player.displayClientMessage(Component.literal("§eAll component types:"), false);
                for (var componentType : components) {
                    player.displayClientMessage(Component.literal("  §7- " + componentType.type().toString()), false);
                }
            } else {
                player.displayClientMessage(Component.literal("§7Components: §o(None)"), false);
            }

            // Item-specific properties
            player.displayClientMessage(Component.literal("§eIs Damageable: §f" + stack.isDamageableItem()), false);
            player.displayClientMessage(Component.literal("§eIs Enchantable: §f" + stack.isEnchantable()), false);

            // Rarity
            player.displayClientMessage(Component.literal("§eRarity: §f" + stack.getRarity()), false);

            // Food info (if applicable)
            var food = stack.get(DataComponents.FOOD);
            if (food != null) {
                player.displayClientMessage(Component.literal("§aIs Food: §fYes (Nutrition: " + food.nutrition() + ")"), false);
            }

            player.displayClientMessage(Component.literal("§6==============================="), false);

            return 1;
        }

        return 0;
    }
}
