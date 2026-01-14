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
                player.sendSystemMessage(Component.literal("§cNo item in main hand!"));
                return 0;
            }

            // Build comprehensive item info
            player.sendSystemMessage(Component.literal("§6========== ITEM INFO =========="));
            player.sendSystemMessage(Component.literal("§eDisplay Name: §f" + stack.getHoverName().getString()));
            player.sendSystemMessage(Component.literal("§eItem ID: §f" + BuiltInRegistries.ITEM.getKey(stack.getItem())));

            // Item class info
            player.sendSystemMessage(Component.literal("§eItem Class: §f" + stack.getItem().getClass().getName()));
            player.sendSystemMessage(Component.literal("§eItem Class (simple): §f" + stack.getItem().getClass().getSimpleName()));

            // Stack info
            player.sendSystemMessage(Component.literal("§eCount: §f" + stack.getCount()));
            player.sendSystemMessage(Component.literal("§eMax Stack Size: §f" + stack.getMaxStackSize()));
            player.sendSystemMessage(Component.literal("§eDamage: §f" + stack.getDamageValue()));
            player.sendSystemMessage(Component.literal("§eMax Damage: §f" + stack.getMaxDamage()));

            // NBT Data (from components in 1.21+)
            var tag = stack.get(DataComponents.CUSTOM_DATA);
            if (tag != null && !tag.isEmpty()) {
                player.sendSystemMessage(Component.literal("§aCustom NBT Data:"));
                try {
                    String nbtString = tag.copyTag().toString();
                    player.sendSystemMessage(Component.literal(nbtString));
                } catch (Exception e) {
                    player.sendSystemMessage(Component.literal("  §cError reading NBT: " + e.getMessage()));
                }
            } else {
                player.sendSystemMessage(Component.literal("§7Custom NBT Data: §o(None)"));
            }

            // Components (1.21+)
            var components = stack.getComponents();
            if (components != null && !components.isEmpty()) {
                player.sendSystemMessage(Component.literal("§aComponents: §f" + components.size() + " entries"));

                // Show important components
                player.sendSystemMessage(Component.literal("§eKey Components:"));

                // Damage
                if (stack.has(DataComponents.DAMAGE)) {
                    player.sendSystemMessage(Component.literal("  §f- DAMAGE: " + stack.get(DataComponents.DAMAGE)));
                }

                // Enchantments
                var enchantments = stack.get(DataComponents.ENCHANTMENTS);
                if (enchantments != null && !enchantments.isEmpty()) {
                    player.sendSystemMessage(Component.literal("  §f- ENCHANTMENTS: " + enchantments));
                }

                // Custom Name
                if (stack.has(DataComponents.CUSTOM_NAME)) {
                    player.sendSystemMessage(Component.literal("  §f- CUSTOM_NAME: " + stack.get(DataComponents.CUSTOM_NAME)));
                }

                // Attribute Modifiers
                var attributes = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
                if (attributes != null && !attributes.modifiers().isEmpty()) {
                    player.sendSystemMessage(Component.literal("  §f- ATTRIBUTE_MODIFIERS: " + attributes.modifiers().size() + " modifiers"));
                }

                // Custom Model Data
                if (stack.has(DataComponents.CUSTOM_MODEL_DATA)) {
                    player.sendSystemMessage(Component.literal("  §f- CUSTOM_MODEL_DATA: " + stack.get(DataComponents.CUSTOM_MODEL_DATA)));
                }

                // Check all component types present
                player.sendSystemMessage(Component.literal("§eAll component types:"));
                for (var componentType : components) {
                    player.sendSystemMessage(Component.literal("  §7- " + componentType.type().toString()));
                }
            } else {
                player.sendSystemMessage(Component.literal("§7Components: §o(None)"));
            }

            // Item-specific properties
            player.sendSystemMessage(Component.literal("§eIs Damageable: §f" + stack.isDamageableItem()));
            player.sendSystemMessage(Component.literal("§eIs Enchantable: §f" + stack.isEnchantable()));

            // Rarity
            player.sendSystemMessage(Component.literal("§eRarity: §f" + stack.getRarity()));

            // Food info (if applicable)
            var food = stack.get(DataComponents.FOOD);
            if (food != null) {
                player.sendSystemMessage(Component.literal("§aIs Food: §fYes (Nutrition: " + food.nutrition() + ")"));
            }

            player.sendSystemMessage(Component.literal("§6==============================="));

            return 1;
        }

        return 0;
    }
}
