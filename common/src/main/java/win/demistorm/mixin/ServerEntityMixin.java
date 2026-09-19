package win.demistorm.mixin;

import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import win.demistorm.Platform;
import win.demistorm.ThrownProjectileEntity;
import win.demistorm.ThrownTNTEntity;

@Mixin(ServerEntity.class)
public class ServerEntityMixin {

    @Shadow
    private Entity entity;

    @Inject(method = "addPairing", at = @At("HEAD"), cancellable = true)
    private void vrThrowingExtensions$skipPairingWithoutClientMod(ServerPlayer player, CallbackInfo ci) {
        if ((entity instanceof ThrownProjectileEntity || entity instanceof ThrownTNTEntity)
                && !Platform.playerHasVTEClient(player)) {
            ci.cancel();
        }
    }
}
