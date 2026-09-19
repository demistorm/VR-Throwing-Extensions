package win.demistorm.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import win.demistorm.ThrownProjectileEntity;

public class VteThrowCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("vteThrow")
            .executes(ctx -> throwItem(ctx, "held", 2.5, 45.0))
            .then(Commands.argument("item", StringArgumentType.word())
                .executes(ctx -> throwItem(ctx, StringArgumentType.getString(ctx, "item"), 2.5, 45.0))
                .then(Commands.argument("speed", DoubleArgumentType.doubleArg(0.1, 10.0))
                    .executes(ctx -> throwItem(ctx,
                            StringArgumentType.getString(ctx, "item"),
                            DoubleArgumentType.getDouble(ctx, "speed"), 45.0))
                    .then(Commands.argument("roll", DoubleArgumentType.doubleArg(0.0, 360.0))
                        .executes(ctx -> throwItem(ctx,
                                StringArgumentType.getString(ctx, "item"),
                                DoubleArgumentType.getDouble(ctx, "speed"),
                                DoubleArgumentType.getDouble(ctx, "roll"))))
                )
            )
        );
    }

    private static int throwItem(CommandContext<CommandSourceStack> context, String itemArg, double speed, double roll) {
        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
            context.getSource().sendFailure(Component.literal("Only players can use this command"));
            return 0;
        }

        ItemStack stack = null;
        if (itemArg.equals("held")) {
            stack = player.getMainHandItem();
        } else {
            String id = itemArg.contains(":") ? itemArg : "minecraft:" + itemArg;
            Identifier location = Identifier.tryParse(id);
            if (location != null && BuiltInRegistries.ITEM.containsKey(location)) {
                stack = new ItemStack(BuiltInRegistries.ITEM.getValue(location));
            } else {
                context.getSource().sendFailure(Component.literal("Unknown item: " + id));
                return 0;
            }
        }
        if (stack.isEmpty()) {
            stack = new ItemStack(Items.DIAMOND_SWORD);
        }
        final ItemStack thrownStack = stack;

        Vec3 origin = player.getEyePosition();
        Vec3 velocity = player.getLookAngle().normalize().scale(speed);

        ThrownProjectileEntity proj = new ThrownProjectileEntity(player.level(), player, thrownStack, false);

        proj.setPos(origin);
        proj.setOriginalThrowPos(origin);
        proj.setDeltaMovement(velocity);
        proj.setHandRoll((float) roll);
        proj.setYRot(player.getYRot());
        proj.setXRot(player.getXRot());

        player.level().addFreshEntity(proj);

        player.level().playSound(null, player.blockPosition(),
                SoundEvents.WITCH_THROW, SoundSource.PLAYERS, 0.6f, 1.05f);

        context.getSource().sendSuccess(() -> Component.literal(
                "Threw " + thrownStack.getHoverName().getString() + " (speed " + speed + ", roll " + roll + ")"), false);

        return 1;
    }
}
