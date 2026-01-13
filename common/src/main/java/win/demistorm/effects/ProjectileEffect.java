package win.demistorm.effects;

import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import win.demistorm.ConfigHelper;
import win.demistorm.ModCompat;
import win.demistorm.VRThrowingExtensions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Handles throwable projectile interception and redirection
public final class ProjectileEffect {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = Path.of("config/vr-throwing-extensions/projectile-items.json");
    private static final double SEARCH_RADIUS = 10.0;

    // In-memory cache of projectile item IDs
    private static volatile Set<ResourceLocation> projectileItems = Sets.newConcurrentHashSet();
    private static volatile Set<Item> projectileItemCache = Sets.newConcurrentHashSet();

    // Player throw tracking for entity interception
    private static final ConcurrentHashMap<UUID, ThrowTracking> activeThrows = new ConcurrentHashMap<>();

    // Load projectile items from config
    static {
        loadProjectileItems();
    }

    // Configuration data (default throwable projectile items)
    private static class ProjectileConfig {
        public List<String> projectile_items = List.of(
            // Tridents not present, their charging function does not work with the throwable projectiles system
            "minecraft:snowball",
            "minecraft:egg",
            "minecraft:blue_egg",
            "minecraft:brown_egg",
            "minecraft:ender_pearl",
            "minecraft:experience_bottle",
            "minecraft:splash_potion",
            "minecraft:lingering_potion",
            "minecraft:fishing_rod",
            "minecraft:wind_charge",
            "minecraft:firework_rocket"
        );
    }

    // Track player's throw attempt for entity interception
    public static class ThrowTracking {
        public final UUID playerId;
        public final Set<UUID> entitiesBeforeUse;
        public boolean useBindHeld;
        public boolean playerCrouched;
        public Vec3 throwOrigin;
        public Vec3 throwVelocity;
        public float rollDeg;

        public ThrowTracking(UUID playerId, boolean useBindHeld, boolean playerCrouched, Vec3 origin, Vec3 velocity, float rollDeg) {
            this.playerId = playerId;
            this.entitiesBeforeUse = Sets.newHashSet();
            this.useBindHeld = useBindHeld;
            this.playerCrouched = playerCrouched;
            this.throwOrigin = origin;
            this.throwVelocity = velocity;
            this.rollDeg = rollDeg;
        }
    }

    // Entity detector for tracking spawned projectiles
    public static class ItemProjectileDetector {
        public static void startTracking(ServerPlayer player, boolean useBindHeld, boolean playerCrouched, Vec3 origin, Vec3 velocity, float rollDeg) {
            UUID playerId = player.getUUID();

            // Capture entities before item use
            ThrowTracking tracking = new ThrowTracking(playerId, useBindHeld, playerCrouched, origin, velocity, rollDeg);

            // Find existing entities owned by player within search radius
            AABB searchBox = new AABB(
                player.getX() - SEARCH_RADIUS, player.getY() - SEARCH_RADIUS, player.getZ() - SEARCH_RADIUS,
                player.getX() + SEARCH_RADIUS, player.getY() + SEARCH_RADIUS, player.getZ() + SEARCH_RADIUS
            );

            player.level.getEntities(player, searchBox, entity -> {
                if (entity instanceof Projectile && isEntityOwnedByPlayer(entity, player)) {
                    tracking.entitiesBeforeUse.add(entity.getUUID());
                }
                return false;
            });

            activeThrows.put(playerId, tracking);
            VRThrowingExtensions.log.debug("[ProjectileEffect] Started tracking throw for player {}, found {} existing entities",
                player.getName().getString(), tracking.entitiesBeforeUse.size());
        }

        public static void interceptAndRedirect(ServerPlayer player) {
            UUID playerId = player.getUUID();
            ThrowTracking tracking = activeThrows.remove(playerId);

            if (tracking == null) return;

            Level level = player.level;

            // Find new projectiles that were spawned
            AABB searchBox = new AABB(
                player.getX() - SEARCH_RADIUS, player.getY() - SEARCH_RADIUS, player.getZ() - SEARCH_RADIUS,
                player.getX() + SEARCH_RADIUS, player.getY() + SEARCH_RADIUS, player.getZ() + SEARCH_RADIUS
            );

            level.getEntities(player, searchBox, entity -> {
                if (entity instanceof Projectile projectile && isEntityOwnedByPlayer(entity, player)) {
                    UUID entityId = entity.getUUID();

                    // Check if new entity
                    if (!tracking.entitiesBeforeUse.contains(entityId)) {
                        VRThrowingExtensions.log.debug("[ProjectileEffect] Found new projectile {} for player {}",
                            entityId, player.getName().getString());

                        redirectProjectile(projectile, tracking);
                    }
                }
                return false;
            });
        }

        private static boolean isEntityOwnedByPlayer(Entity entity, ServerPlayer player) {
            if (entity instanceof Projectile projectile) {
                return projectile.getOwner() == player;
            }
            return false;
        }

        private static void redirectProjectile(Projectile projectile, ThrowTracking tracking) {
            // Cast to Entity for chunk tracking access

            // Cast to ServerLevel for chunk source access
            ServerLevel serverLevel = (ServerLevel) projectile.level;

            // Remove from client tracking (preventing wrong spawn packet from being sent)
            serverLevel.getChunkSource().removeEntity(projectile);

            // Override position and velocity with throw data
            projectile.setPos(tracking.throwOrigin);
            projectile.setDeltaMovement(tracking.throwVelocity);

            // Add back to client tracking (sends spawn packet with updated data)
            serverLevel.getChunkSource().addEntity(projectile);

            VRThrowingExtensions.log.debug("[ProjectileEffect] Redirected projectile {} to pos {} vel {}",
                projectile.getId(), tracking.throwOrigin, tracking.throwVelocity);
        }
    }

    // Check if an item should use projectile interception
    public static boolean isProjectileItem(ItemStack stack, boolean playerCrouched, boolean placePressed) {
        if (!ConfigHelper.ACTIVE.throwableProjectiles) {
            return false;
        }

        if (ModCompat.throwingDisabled(stack, playerCrouched, placePressed)) {
            return false;
        }

        // Check cache first for performance
        Item item = stack.getItem();
        if (projectileItemCache.contains(item)) {
            return true;
        }

        // Check by resource location
        ResourceLocation itemKey = Registry.ITEM.getKey(item);
        boolean isProjectile = projectileItems.contains(itemKey);

        if (isProjectile) {
            projectileItemCache.add(item);
        }

        return isProjectile;
    }

    // Determine throw behavior based on keybinds and item type
    public static ThrowBehavior determineThrowBehavior(ItemStack stack, boolean useBindHeld, boolean playerCrouched) {
        boolean isProjectile = isProjectileItem(stack, playerCrouched, useBindHeld);

        if (isProjectile) {
            // Determine if crouch modifier is active based on config
            boolean crouchModifierActive = switch (ConfigHelper.ACTIVE.crouchBehaviorProjectiles) {
                case NORMAL -> playerCrouched;       // Crouch activates the feature
                case INVERTED -> !playerCrouched;    // Not crouching activates the feature
            };

            // Default throw (no modifiers) = Vanilla projectile behavior
            if (!useBindHeld && !crouchModifierActive) {
                return ThrowBehavior.VANILLA_PROJECTILE;
            }
            // Any modifier held = Force custom projectile
            if (useBindHeld || crouchModifierActive) {
                return useBindHeld && crouchModifierActive ?
                    ThrowBehavior.CUSTOM_PROJECTILE_WHOLE_STACK :
                    ThrowBehavior.CUSTOM_PROJECTILE_SINGLE;
            }
        } else {
            // Normal items: use bind determines quantity
            return useBindHeld ?
                ThrowBehavior.CUSTOM_PROJECTILE_WHOLE_STACK :
                ThrowBehavior.CUSTOM_PROJECTILE_SINGLE;
        }

        return ThrowBehavior.CUSTOM_PROJECTILE_SINGLE;
    }

    public enum ThrowBehavior {
        VANILLA_PROJECTILE,           // Use item's normal projectile behavior
        CUSTOM_PROJECTILE_SINGLE,     // Throw as custom projectile (single item)
        CUSTOM_PROJECTILE_WHOLE_STACK // Throw as custom projectile (whole stack)
    }

    // Load projectile items from config file
    private static void loadProjectileItems() {
        ProjectileConfig config = readConfig();

        Set<ResourceLocation> items = Sets.newHashSet();
        for (String itemId : config.projectile_items) {
            try {
                ResourceLocation key = ResourceLocation.tryParse(itemId);
                if (Registry.ITEM.containsKey(key)) {
                    items.add(key);
                    Registry.ITEM.getOptional(key).ifPresent(projectileItemCache::add);
                } else {
                    VRThrowingExtensions.log.warn("[ProjectileEffect] Unknown item in config: {}", itemId);
                }
            } catch (Exception e) {
                VRThrowingExtensions.log.warn("[ProjectileEffect] Invalid item ID in config: {}", itemId, e);
            }
        }

        projectileItems = items;
        VRThrowingExtensions.log.info("[ProjectileEffect] Loaded {} projectile items from config", items.size());
    }

    private static ProjectileConfig readConfig() {
        try {
            if (Files.exists(CONFIG_FILE)) {
                String json = Files.readString(CONFIG_FILE);
                return GSON.fromJson(json, ProjectileConfig.class);
            }
        } catch (IOException e) {
            VRThrowingExtensions.log.error("[ProjectileEffect] Failed to read config file", e);
        }

        // Return default config if file doesn't exist or is invalid
        ProjectileConfig config = new ProjectileConfig();
        writeConfig(config);
        return config;
    }

    private static void writeConfig(ProjectileConfig config) {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            String json = GSON.toJson(config);
            Files.writeString(CONFIG_FILE, json);
        } catch (IOException e) {
            VRThrowingExtensions.log.error("[ProjectileEffect] Failed to write config file", e);
        }
    }

    // Get current projectile items as a list
    public static List<String> getProjectileItemsList() {
        List<String> items = new ArrayList<>();
        for (ResourceLocation key : projectileItems) {
            items.add(key.toString());
        }
        return items;
    }

    // Set projectile items from a list and save to config
    public static void setProjectileItemsList(List<String> items) {
        Set<ResourceLocation> newItems = Sets.newHashSet();
        Set<Item> newCache = Sets.newConcurrentHashSet();

        // Convert string list to ResourceLocation set
        for (String itemId : items) {
            try {
                ResourceLocation key = ResourceLocation.tryParse(itemId);
                if (Registry.ITEM.containsKey(key)) {
                    newItems.add(key);
                    Registry.ITEM.getOptional(key).ifPresent(newCache::add);
                } else {
                    VRThrowingExtensions.log.warn("[ProjectileEffect] Unknown item ID: {}", itemId);
                }
            } catch (Exception e) {
                VRThrowingExtensions.log.warn("[ProjectileEffect] Invalid item ID: {}", itemId, e);
            }
        }

        projectileItems = newItems;
        projectileItemCache = newCache;

        // Save to config file
        ProjectileConfig config = new ProjectileConfig();
        config.projectile_items = new ArrayList<>(items);
        writeConfig(config);

        VRThrowingExtensions.log.info("[ProjectileEffect] Updated {} projectile items in config", newItems.size());
    }

    // Load projectile items from config file (public for external access)
    public static void loadProjectileItemsFromConfig() {
        projectileItemCache.clear();
        loadProjectileItems();
    }

    // Reset projectile items to defaults
    public static void resetProjectileItems() {
        // Get default items from a fresh config instance
        ProjectileConfig defaultConfig = new ProjectileConfig();
        setProjectileItemsList(defaultConfig.projectile_items);
        VRThrowingExtensions.log.info("[ProjectileEffect] Reset projectile items to defaults ({} items)", defaultConfig.projectile_items.size());
    }

    private ProjectileEffect() {}
}