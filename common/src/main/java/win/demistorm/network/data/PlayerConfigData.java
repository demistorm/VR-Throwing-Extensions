package win.demistorm.network.data;

import win.demistorm.ConfigHelper;
import win.demistorm.WeaponEffectType;

// Data for player config packets (client to server)
public record PlayerConfigData(
    WeaponEffectType weaponEffect,             // Weapon effect type
    boolean throwableProjectiles,              // Throwable projectiles enabled
    ConfigHelper.CrouchBehavior crouchBehaviorProjectiles, // Crouch behavior for projectiles
    boolean placeBlocksOnThrow,                // Place blocks on throw enabled
    ConfigHelper.CrouchBehavior crouchBehaviorPlaceBlocks, // Crouch behavior for place blocks
    boolean onlyPlaceLights,                   // Only place lights enabled
    boolean immersiveMCThrowables,             // ImmersiveMC throwables compat enabled
    boolean throwConflictingItems              // Throw conflicting items enabled
) {}
