package win.demistorm.client.config;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.Button;
import win.demistorm.ConfigHelper;
import win.demistorm.WeaponEffectType;

// Settings menu
public final class ConfigScreen {

    private ConfigScreen() {}

    public static class SimpleToggleScreen extends Screen {
        private final Screen parent;
        private final Minecraft client = Minecraft.getInstance();
        private WeaponEffectType weaponEffectValue = ConfigHelper.CLIENT.weaponEffect;
        private boolean aimAssistValue = ConfigHelper.CLIENT.aimAssist;
        private boolean bloodEffectValue = ClientOnlyConfig.ACTIVE.bloodEffect;

        // Button bounds for tooltip detection
        private int weaponEffectButtonX, weaponEffectButtonY, weaponEffectButtonWidth, weaponEffectButtonHeight;
        private int aimAssistButtonX, aimAssistButtonY, aimAssistButtonWidth, aimAssistButtonHeight;
        private int bloodEffectsButtonX, bloodEffectsButtonY, bloodEffectsButtonWidth, bloodEffectsButtonHeight;

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
            weaponEffectButtonX = width / 2 - 80;
            weaponEffectButtonY = height / 4 + 24;
            weaponEffectButtonWidth = 160;
            weaponEffectButtonHeight = 20;

            aimAssistButtonX = width / 2 - 80;
            aimAssistButtonY = height / 4 + 54;
            aimAssistButtonWidth = 160;
            aimAssistButtonHeight = 20;

            bloodEffectsButtonX = width / 2 - 80;
            bloodEffectsButtonY = height / 4 + 84;
            bloodEffectsButtonWidth = 160;
            bloodEffectsButtonHeight = 20;

            // Weapon effect button
            addRenderableWidget(
                    new Button(weaponEffectButtonX, weaponEffectButtonY, weaponEffectButtonWidth, weaponEffectButtonHeight,
                                    Component.literal("Weapon Effect: " + weaponEffectValue.name()),
                                    btn -> {
                                        // Switch between effects
                                        weaponEffectValue = switch (weaponEffectValue) {
                                            case OFF -> WeaponEffectType.BOOMERANG;
                                            case BOOMERANG -> WeaponEffectType.EMBED;
                                            case EMBED -> WeaponEffectType.OFF;
                                        };
                                        btn.setMessage(Component.literal("Weapon Effect: " + weaponEffectValue.name()));
                                    }));

            // Aim assist button
            addRenderableWidget(
                    new Button(aimAssistButtonX, aimAssistButtonY, aimAssistButtonWidth, aimAssistButtonHeight,
                                    Component.literal("Aim Assist: " + (aimAssistValue ? "ON" : "OFF")),
                                    btn -> {
                                        aimAssistValue = !aimAssistValue;
                                        btn.setMessage(Component.literal(
                                                "Aim Assist: " + (aimAssistValue ? "ON" : "OFF")));
                                    }));

            // Blood effects button
            addRenderableWidget(
                    new Button(bloodEffectsButtonX, bloodEffectsButtonY, bloodEffectsButtonWidth, bloodEffectsButtonHeight,
                                    Component.literal("Blood Effects: " + (bloodEffectValue ? "ON" : "OFF")),
                                    btn -> {
                                        bloodEffectValue = !bloodEffectValue;
                                        btn.setMessage(Component.literal("Blood Effects: " + (bloodEffectValue ? "ON" : "OFF")));
                                    }));

            // Done button
            addRenderableWidget(
                    new Button(width / 2 - 100, height - 27, 200, 20,
                                    Component.literal("Done"),
                                    btn -> {
                                        ConfigHelper.CLIENT.weaponEffect = weaponEffectValue;
                                        ConfigHelper.CLIENT.aimAssist = aimAssistValue;
                                        ConfigHelper.write(ConfigHelper.CLIENT);

                                        // Save blood effect setting
                                        ClientOnlyConfig.ACTIVE.bloodEffect = bloodEffectValue;
                                        ClientOnlyConfig.write(ClientOnlyConfig.ACTIVE);

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
            // Check if mouse is over weapon effect button
            if (isMouseOverButton(mouseX, mouseY, weaponEffectButtonX, weaponEffectButtonY, weaponEffectButtonWidth, weaponEffectButtonHeight)) {
                String effect = weaponEffectValue.name();
                String tooltip = switch (effect) {
                    case "OFF" -> "Weapons drop normally";
                    case "BOOMERANG" -> "Weapons return after hitting (catch them!)";
                    case "EMBED" -> "Weapons stick in enemies and cause bleeding";
                    default -> "Weapon effect mode";
                };
                Component tooltipText = Component.literal(tooltip);
                renderTooltip(poseStack, tooltipText, mouseX, mouseY);
            }
            // Check if mouse is over aim assist button
            else if (isMouseOverButton(mouseX, mouseY, aimAssistButtonX, aimAssistButtonY, aimAssistButtonWidth, aimAssistButtonHeight)) {
                Component tooltipText = Component.literal("Helps aim at nearby targets");
                renderTooltip(poseStack, tooltipText, mouseX, mouseY);
            }
            // Check if mouse is over blood effects button
            else if (isMouseOverButton(mouseX, mouseY, bloodEffectsButtonX, bloodEffectsButtonY, bloodEffectsButtonWidth, bloodEffectsButtonHeight)) {
                Component tooltipText = Component.literal("Show blood particles when weapons hit");
                renderTooltip(poseStack, tooltipText, mouseX, mouseY);
            }
        }

        private boolean isMouseOverButton(int mouseX, int mouseY, int buttonX, int buttonY, int buttonWidth, int buttonHeight) {
            return mouseX >= buttonX && mouseX <= buttonX + buttonWidth &&
                   mouseY >= buttonY && mouseY <= buttonY + buttonHeight;
        }
    }
}