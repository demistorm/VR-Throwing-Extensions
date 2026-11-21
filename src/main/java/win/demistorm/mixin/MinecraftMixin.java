package win.demistorm.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import win.demistorm.client.PancakeHandler;

// Gives flatscreen the drop key before vanilla drops anything
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void vr_throwing_extensions$pancakeDropKey(CallbackInfo ci) {
        PancakeHandler.onHandleKeybinds();
    }
}
