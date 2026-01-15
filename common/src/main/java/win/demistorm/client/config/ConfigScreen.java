package win.demistorm.client.config;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.Button;
import win.demistorm.ConfigHelper;
import win.demistorm.WeaponEffectType;
import win.demistorm.effects.ProjectileEffect;


import java.util.Arrays;
import java.util.List;

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

        // Button bounds for tooltip detection
        private int weaponEffectButtonX, weaponEffectButtonY, weaponEffectButtonWidth, weaponEffectButtonHeight;
        private int aimAssistButtonX, aimAssistButtonY, aimAssistButtonWidth, aimAssistButtonHeight;
        private int throwableProjectilesButtonX, throwableProjectilesButtonY, throwableProjectilesButtonWidth, throwableProjectilesButtonHeight;
        private int extrasButtonX, extrasButtonY, extrasButtonWidth, extrasButtonHeight;
        private int resetButtonX, resetButtonY, resetButtonWidth, resetButtonHeight;

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
            // Store button bounds for tooltip detection
            resetButtonX = width - 50;
            resetButtonY = 5;
            resetButtonWidth = 45;
            resetButtonHeight = 20;

            weaponEffectButtonX = width / 2 - 80;
            weaponEffectButtonY = height / 6 - 10;
            weaponEffectButtonWidth = 160;
            weaponEffectButtonHeight = 20;

            aimAssistButtonX = width / 2 - 80;
            aimAssistButtonY = height / 6 + 11;
            aimAssistButtonWidth = 160;
            aimAssistButtonHeight = 20;

            throwableProjectilesButtonX = width / 2 - 80;
            throwableProjectilesButtonY = height / 6 + 32;
            throwableProjectilesButtonWidth = 160;
            throwableProjectilesButtonHeight = 20;

            extrasButtonX = width / 2 - 80;
            extrasButtonY = height / 6 + 53;
            extrasButtonWidth = 160;
            extrasButtonHeight = 20;

            // Reset config button (top right)
            addRenderableWidget(
                    new Button(resetButtonX, resetButtonY, resetButtonWidth, resetButtonHeight,
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
                            }));

            // Weapon effect button
            weaponEffectButton = new Button(weaponEffectButtonX, weaponEffectButtonY, weaponEffectButtonWidth, weaponEffectButtonHeight,
                    Component.literal("Weapon Effect: " + weaponEffectValue.name()),
                    btn -> {
                        // Switch between effects
                        weaponEffectValue = switch (weaponEffectValue) {
                            case OFF -> WeaponEffectType.BOOMERANG;
                            case BOOMERANG -> WeaponEffectType.EMBED;
                            case EMBED -> WeaponEffectType.OFF;
                        };
                        btn.setMessage(Component.literal("Weapon Effect: " + weaponEffectValue.name()));
                    });
            addRenderableWidget(weaponEffectButton);

            // Aim assist button
            aimAssistButton = new Button(aimAssistButtonX, aimAssistButtonY, aimAssistButtonWidth, aimAssistButtonHeight,
                    Component.literal("Aim Assist: " + (aimAssistValue ? "ON" : "OFF")),
                    btn -> {
                        aimAssistValue = !aimAssistValue;
                        btn.setMessage(Component.literal(
                                "Aim Assist: " + (aimAssistValue ? "ON" : "OFF")));
                    });
            addRenderableWidget(aimAssistButton);

            // Throwable projectiles button
            addRenderableWidget(
                    new Button(throwableProjectilesButtonX, throwableProjectilesButtonY, throwableProjectilesButtonWidth, throwableProjectilesButtonHeight,
                            Component.literal("Throwable Projectiles..."),
                            btn -> client.setScreen(new ThrowableItemsScreen(this))));

            // Extras button
            addRenderableWidget(
                    new Button(extrasButtonX, extrasButtonY, extrasButtonWidth, extrasButtonHeight,
                            Component.literal("Extras..."),
                            btn -> client.setScreen(new ExtrasScreen.ExtrasToggleScreen(this))));

            // Done button
            addRenderableWidget(
                    new Button(width / 2 - 100, height - 30, 200, 20,
                            Component.literal("Done"),
                            btn -> {
                                ConfigHelper.CLIENT.weaponEffect = weaponEffectValue;
                                ConfigHelper.CLIENT.aimAssist = aimAssistValue;
                                ConfigHelper.write(ConfigHelper.CLIENT);

                                if (client.hasSingleplayerServer()) {
                                    ConfigHelper.ACTIVE.weaponEffect = ConfigHelper.CLIENT.weaponEffect;
                                    ConfigHelper.ACTIVE.aimAssist = ConfigHelper.CLIENT.aimAssist;
                                }
                                client.setScreen(parent);
                            }));
        }

        @Override
        public void render(PoseStack poseStack, int mouseX, int mouseY, float delta) {
            renderBackground(poseStack);
            super.render(poseStack, mouseX, mouseY, delta);
            // Draw title at top
            drawCenteredString(poseStack, font, title, width / 2, 20, 0xFFFFFF);

            // Render tooltips based on mouse position
            renderTooltip(poseStack, mouseX, mouseY);
        }

        private void renderTooltip(PoseStack poseStack, int mouseX, int mouseY) {
            // Check if mouse is over reset button
            if (isMouseOverButton(mouseX, mouseY, resetButtonX, resetButtonY, resetButtonWidth, resetButtonHeight)) {
                renderTooltip(poseStack, Component.literal("Reset all settings to default"), mouseX, mouseY);
            }
            // Check if mouse is over weapon effect button
            else if (isMouseOverButton(mouseX, mouseY, weaponEffectButtonX, weaponEffectButtonY, weaponEffectButtonWidth, weaponEffectButtonHeight)) {
                List<Component> tooltip = Arrays.asList(
                        Component.literal("OFF: Weapons drop normally"),
                        Component.literal("BOOMERANG: Weapons return after hitting (catch them!)"),
                        Component.literal("EMBED: Weapons stick in enemies and cause bleeding")
                );
                renderComponentTooltip(poseStack, tooltip, mouseX, mouseY);
            }
            // Check if mouse is over aim assist button
            else if (isMouseOverButton(mouseX, mouseY, aimAssistButtonX, aimAssistButtonY, aimAssistButtonWidth, aimAssistButtonHeight)) {
                renderTooltip(poseStack, Component.literal("Helps aim at nearby targets"), mouseX, mouseY);
            }
            // Check if mouse is over throwable projectiles button
            else if (isMouseOverButton(mouseX, mouseY, throwableProjectilesButtonX, throwableProjectilesButtonY, throwableProjectilesButtonWidth, throwableProjectilesButtonHeight)) {
                renderTooltip(poseStack, Component.literal("Toggle and manage vanilla and modded items to be thrown immersively"), mouseX, mouseY);
            }
            // Check if mouse is over extras button
            else if (isMouseOverButton(mouseX, mouseY, extrasButtonX, extrasButtonY, extrasButtonWidth, extrasButtonHeight)) {
                renderTooltip(poseStack, Component.literal("More features and settings"), mouseX, mouseY);
            }
        }

        private boolean isMouseOverButton(int mouseX, int mouseY, int buttonX, int buttonY, int buttonWidth, int buttonHeight) {
            return mouseX >= buttonX && mouseX <= buttonX + buttonWidth &&
                    mouseY >= buttonY && mouseY <= buttonY + buttonHeight;
        }
    }
}