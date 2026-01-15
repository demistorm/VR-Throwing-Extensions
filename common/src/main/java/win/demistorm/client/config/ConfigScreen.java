package win.demistorm.client.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import win.demistorm.ConfigHelper;
import win.demistorm.WeaponEffectType;
import win.demistorm.effects.ProjectileEffect;

// Settings menu
public final class ConfigScreen {

    private ConfigScreen() {}

    public static class SimpleToggleScreen extends Screen {
        private final Screen parent;
        private final Minecraft client = Minecraft.getInstance();
        private WeaponEffectType weaponEffectValue = ConfigHelper.CLIENT.weaponEffect;
        private boolean aimAssistValue = ConfigHelper.CLIENT.aimAssist;
        private Button weaponEffectButton;
        private Button aimAssistButton;

        protected SimpleToggleScreen(Screen parent) {
            super(Component.literal("VR Throwing Extensions Configuration"));
            this.parent = parent;
        }

        // Create screen for ModMenu
        public static SimpleToggleScreen create(Screen parent) {
            return new SimpleToggleScreen(parent);
        }

        @Override
        protected void init() {
            // Reset config button (top right)
            addRenderableWidget(
                    Button.builder(
                                    Component.literal("Reset"),
                                    btn -> {
                                        // Reset all config values to defaults
                                        weaponEffectValue = WeaponEffectType.BOOMERANG;
                                        aimAssistValue = true;

                                        // Reset ConfigHelper.CLIENT to defaults
                                        ConfigHelper.CLIENT.weaponEffect = WeaponEffectType.BOOMERANG;
                                        ConfigHelper.CLIENT.aimAssist = true;
                                        ConfigHelper.CLIENT.throwableProjectiles = true;
                                        ConfigHelper.CLIENT.placeBlocksOnThrow = false;
                                        ConfigHelper.CLIENT.onlyPlaceLights = false;
                                        ConfigHelper.CLIENT.throwableTNT = true;
                                        ConfigHelper.CLIENT.immersiveMCThrowables = true;
                                        ConfigHelper.CLIENT.throwConflictingItems = true;
                                        ConfigHelper.CLIENT.crouchBehaviorProjectiles = ConfigHelper.CrouchBehavior.NORMAL;
                                        ConfigHelper.CLIENT.crouchBehaviorPlaceBlocks = ConfigHelper.CrouchBehavior.INVERTED;
                                        ConfigHelper.write(ConfigHelper.CLIENT);

                                        // Reset ClientOnlyConfig to defaults
                                        ClientOnlyConfig.ACTIVE.bloodEffect = true;
                                        ClientOnlyConfig.write(ClientOnlyConfig.ACTIVE);

                                        // Reset projectile items to defaults
                                        ProjectileEffect.resetProjectileItems();

                                        // Update button messages immediately
                                        if (weaponEffectButton != null) {
                                            weaponEffectButton.setMessage(Component.literal("Weapon Effect: " + weaponEffectValue.name()));
                                        }
                                        if (aimAssistButton != null) {
                                            aimAssistButton.setMessage(Component.literal("Aim Assist: " + (aimAssistValue ? "ON" : "OFF")));
                                        }
                                    })
                            .bounds(width - 50, 5, 45, 20)
                            .tooltip(Tooltip.create(Component.literal("Reset all settings to default")))
                            .build());

            // Weapon effect button
            weaponEffectButton = Button.builder(
                                    Component.literal("Weapon Effect: " + weaponEffectValue.name()),
                                    btn -> {
                                        // Switch between effects
                                        weaponEffectValue = switch (weaponEffectValue) {
                                            case OFF -> WeaponEffectType.BOOMERANG;
                                            case BOOMERANG -> WeaponEffectType.EMBED;
                                            case EMBED -> WeaponEffectType.OFF;
                                        };
                                        btn.setMessage(Component.literal("Weapon Effect: " + weaponEffectValue.name()));
                                    })
                            .bounds(width / 2 - 80, height / 6 - 10, 160, 20)
                            .tooltip(Tooltip.create(Component.literal(
                                    """
                                            OFF: Weapons drop normally
                                            BOOMERANG: Weapons return after hitting (catch them!)
                                            EMBED: Weapons stick in enemies and cause bleeding""")))
                            .build();
            addRenderableWidget(weaponEffectButton);

            // Aim assist button
            aimAssistButton = Button.builder(
                                    Component.literal("Aim Assist: " + (aimAssistValue ? "ON" : "OFF")),
                                    btn -> {
                                        aimAssistValue = !aimAssistValue;
                                        btn.setMessage(Component.literal(
                                                "Aim Assist: " + (aimAssistValue ? "ON" : "OFF")));
                                    })
                            .bounds(width / 2 - 80, height / 6 + 11, 160, 20)
                            .tooltip(Tooltip.create(Component.literal("Helps aim at nearby targets")))
                            .build();
            addRenderableWidget(aimAssistButton);

            // Throwable projectiles button
            addRenderableWidget(
                    Button.builder(
                                    Component.literal("Throwable Projectiles..."),
                                    btn -> client.setScreen(new ThrowableItemsScreen(this)))
                            .bounds(width / 2 - 80, height / 6 + 32, 160, 20)
                            .tooltip(Tooltip.create(Component.literal("Toggle and manage vanilla and modded items to be thrown immersively")))
                            .build());

            // Extras button
            addRenderableWidget(
                    Button.builder(
                                    Component.literal("Extras..."),
                                    btn -> client.setScreen(new ExtrasScreen.ExtrasToggleScreen(this)))
                            .bounds(width / 2 - 80, height / 6 + 53, 160, 20)
                            .tooltip(Tooltip.create(Component.literal("More features and settings")))
                            .build());

            // Done button
            addRenderableWidget(
                    Button.builder(Component.literal("Done"),
                                    btn -> {
                                        ConfigHelper.CLIENT.weaponEffect = weaponEffectValue;
                                        ConfigHelper.CLIENT.aimAssist = aimAssistValue;
                                        ConfigHelper.write(ConfigHelper.CLIENT);

                                        if (client.hasSingleplayerServer()) {
                                            ConfigHelper.ACTIVE.weaponEffect = ConfigHelper.CLIENT.weaponEffect;
                                            ConfigHelper.ACTIVE.aimAssist = ConfigHelper.CLIENT.aimAssist;
                                        }
                                        client.setScreen(parent);
                                    })
                            .bounds(width / 2 - 100, height - 30, 200, 20)
                            .build());
        }

        @Override
        public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
            super.render(context, mouseX, mouseY, delta);
            // Draw title at top
            context.drawCenteredString(font, title, width / 2, 20, 0xFFFFFFFF);
        }
    }
}