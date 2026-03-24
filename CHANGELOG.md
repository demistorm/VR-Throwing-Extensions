## Version 1.4.0
#### Additions
- Added [LambDynamicLights](https://modrinth.com/mod/lambdynamiclights) support for thrown torches/lanterns!
- Offhand throwing! **Needs to be bound in SteamVR controller settings.** Supports all the same features from the main hand throwing!
- Rebindable throwing and throw stack binds! Set them in the SteamVR modded binds tab and then change their 
vanilla keybind to something you don't use and viola!
- Added Server Authoritative option to the mod's config. Setting it to false will allow players on the server to
choose their own VTE settings
- Added a message on server join listing what VTE features are enabled

#### Changes
- Configs now properly update on update first run
- Fix held lit TNT from exploding even after throw is canceled
- Dropped 1.21.8, 1.21.4, and 1.19.2 support (sorry, I just don't have enough time to update all of them forever)

---

## Version 1.3.0
- Added option to place blocks when thrown
- Added throwable projectiles! Works for vanilla and modded items!
- Throwable TNT! Flint&Steel in the offhand, tnt in the main hand. Throw them like grenades! (it is very fun)
- Added /addProjectileItemID command to automatically add the held item to the throwable projectile list
- Added /itemID command to check the ID of an item
- Added /itemInfo to view item's ID and extra info that might be helpful
- Disabled Vivecraft's Climbing Claws and Jumping boots by default
- Reorganized config menu with toggles for all new options
- Added immersiveMC projectile bypass (kinda). Enables items 
that immersiveMC compatibility blocks in case users disabled immersiveMC's throwing and would rather use this mod's
- Added "Throw Conflicting Items" option to give a way to throw things like bows/crossbows/climbing claws

---

- Also made a [wiki](https://github.com/demistorm/VR-Throwing-Extensions/wiki) with more details about 
everything in VTE!