package win.demistorm.mixin;

import net.minecraft.client.KeyMapping;
import org.apache.commons.lang3.ArrayUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import win.demistorm.client.RemapBindings;

// Injects custom keybinds
@Mixin(net.minecraft.client.Options.class)
public class OptionsMixin {

    @WrapOperation(
        method = "<init>",
        at = @At(
                value = "INVOKE",
                target = "Ljava/util/stream/Stream;toArray(Ljava/util/function/IntFunction;)[Ljava/lang/Object;",
                remap = false)
    )
    private Object[] addKeyMappings(java.util.stream.Stream instance, java.util.function.IntFunction<Object[]> intFunction, Operation<Object[]> original) {
        KeyMapping[] keyMappings = (KeyMapping[]) original.call(instance, intFunction);

        // Add custom keybindings to the array
        keyMappings = ArrayUtils.add(keyMappings, RemapBindings.THROW);
        keyMappings = ArrayUtils.add(keyMappings, RemapBindings.THROW_STACK);
        keyMappings = ArrayUtils.add(keyMappings, RemapBindings.THROW_OFFHAND);

        return keyMappings;
    }
}
