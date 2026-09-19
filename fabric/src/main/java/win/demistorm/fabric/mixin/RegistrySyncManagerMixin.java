package win.demistorm.fabric.mixin;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.impl.registry.sync.RegistrySyncManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import win.demistorm.VRThrowingExtensions;
import win.demistorm.fabric.VtePresencePayload;

import java.util.Map;

@Mixin(value = RegistrySyncManager.class, remap = false)
public class RegistrySyncManagerMixin {

    @Redirect(method = "configureClient", at = @At(value = "INVOKE",
            target = "Lnet/fabricmc/fabric/impl/registry/sync/RegistrySyncManager;createAndPopulateRegistryMap()Ljava/util/Map;"))
    private static Map<Identifier, Object2IntMap<Identifier>> vrThrowingExtensions$filterVteEntities(
            ServerConfigurationPacketListenerImpl handler, MinecraftServer server) {
        Map<Identifier, Object2IntMap<Identifier>> map = RegistrySyncManager.createAndPopulateRegistryMap();

        if (map == null || ServerConfigurationNetworking.canSend(handler, VtePresencePayload.ID)) {
            return map;
        }

        Identifier entityTypeId = Identifier.fromNamespaceAndPath("minecraft", "entity_type");
        Object2IntMap<Identifier> entries = map.get(entityTypeId);
        if (entries != null) {
            entries.remove(Identifier.fromNamespaceAndPath(VRThrowingExtensions.MOD_ID, "generic_thrown_item"));
            entries.remove(Identifier.fromNamespaceAndPath("vr-throwing-extensions", "thrown_primed_tnt"));

            if (entries.isEmpty()) {
                map.remove(entityTypeId);
            }
        }

        VRThrowingExtensions.log.debug("Filtered VTE entity types from registry sync for a client without the mod");
        return map;
    }
}
