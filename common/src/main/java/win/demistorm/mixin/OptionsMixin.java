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
                target = "Lorg/apache/commons/lang3/ArrayUtils;addAll([Ljava/lang/Object;[Ljava/lang/Object;)[Ljava/lang/Object;",
                remap = false)
    )
    private Object[] addKeyMappings(Object[] array1, Object[] array2, Operation<Object[]> original) {
        KeyMapping[] keyMappings = (KeyMapping[]) original.call(array1, array2);

        // Add custom keybindings to the array
        keyMappings = ArrayUtils.add(keyMappings, RemapBindings.THROW);
        keyMappings = ArrayUtils.add(keyMappings, RemapBindings.THROW_STACK);
        keyMappings = ArrayUtils.add(keyMappings, RemapBindings.THROW_OFFHAND);

        return keyMappings;
    }
}
