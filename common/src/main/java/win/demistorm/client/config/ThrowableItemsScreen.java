package win.demistorm.client.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import win.demistorm.ConfigHelper;
import win.demistorm.effects.ProjectileEffect;

import java.util.ArrayList;
import java.util.List;

// Screen for managing throwable projectile items
public class ThrowableItemsScreen extends Screen {
    private final Screen parent;
    private final Minecraft client = Minecraft.getInstance();

    // UI Components
    private EditBox itemIdInput;
    private ItemListWidget itemList;
    private Button toggleButton;

    // Layout
    private int listTopY;

    // Data
    private List<String> projectileItems;
    private boolean throwableProjectilesEnabled;
    private ConfigHelper.CrouchBehavior crouchBehaviorValue = ConfigHelper.CLIENT.crouchBehaviorProjectiles;

    protected ThrowableItemsScreen(Screen parent) {
        super(Component.literal("Configure Throwable Projectiles"));
        this.parent = parent;

        // Load current data
        this.projectileItems = ProjectileEffect.getProjectileItemsList();
        this.throwableProjectilesEnabled = ConfigHelper.CLIENT.throwableProjectiles;
    }

    @Override
    protected void init() {
        int topY = 40;
        int listTopY = topY + 80; // Start list right after controls
        int bottomMargin = 70; // Space at bottom for Done button (prevents overlap)

        // Text input for new items (left side)
        itemIdInput = new EditBox(
            font,
            20,
            topY,
            180,
            18,
            Component.literal("Item ID")
        );
        itemIdInput.setHint(Component.literal("minecraft:snowball"));
        itemIdInput.setMaxLength(100);
        addRenderableWidget(itemIdInput);

        // Add button (next to text input)
        addRenderableWidget(
            Button.builder(
                Component.literal("Add"),
                btn -> addItemId())
            .bounds(205, topY, 40, 18)
            .build());

        // Throwable Projectiles toggle (right side)
        toggleButton = Button.builder(
            Component.literal("Throwable Projectiles: " + (throwableProjectilesEnabled ? "ON" : "OFF")),
            btn -> {
                throwableProjectilesEnabled = !throwableProjectilesEnabled;
                btn.setMessage(Component.literal("Throwable Projectiles: " + (throwableProjectilesEnabled ? "ON" : "OFF")));
                // Update config immediately
                ConfigHelper.setThrowableProjectilesEnabled(throwableProjectilesEnabled);
            })
        .bounds(width - 180, topY, 160, 18)
        .tooltip(Tooltip.create(Component.literal("Enable VR control over vanilla projectile items")))
        .build();
        addRenderableWidget(toggleButton);

        // Crouch behavior toggle (below Throwable Projectiles toggle)
        addRenderableWidget(
            Button.builder(
                Component.literal("Crouch Behavior: " + crouchBehaviorValue.name()),
                btn -> {
                    // Switch between NORMAL and INVERTED
                    crouchBehaviorValue = crouchBehaviorValue == ConfigHelper.CrouchBehavior.NORMAL
                        ? ConfigHelper.CrouchBehavior.INVERTED
                        : ConfigHelper.CrouchBehavior.NORMAL;
                    btn.setMessage(Component.literal("Crouch Behavior: " + crouchBehaviorValue.name()));
                    ConfigHelper.setCrouchBehaviorProjectiles(crouchBehaviorValue);
                })
            .bounds(width - 180, topY + 23, 160, 18)
            .tooltip(Tooltip.create(Component.literal(
                    """
                            NORMAL: Crouch throws projectile without effect
                            INVERTED: Crouch activates effect""")))
            .build());

        // Create scrollable item list (two columns) - starts below the controls
        int listBottom = height - bottomMargin;
        itemList = new ItemListWidget(client, width, height, listTopY, listBottom);
        itemList.updateEntries();
        addWidget(itemList);

        // Done button at bottom
        addRenderableWidget(
            Button.builder(
                Component.literal("Done"),
                btn -> {
                    // Save crouch behavior
                    ConfigHelper.CLIENT.crouchBehaviorProjectiles = crouchBehaviorValue;
                    ConfigHelper.write(ConfigHelper.CLIENT);

                    // Save any changes to projectile items
                    ProjectileEffect.setProjectileItemsList(projectileItems);
                    client.setScreen(parent);
                })
            .bounds(width / 2 - 100, height - 30, 200, 20)
            .build());
    }

    private void addItemId() {
        String text = itemIdInput.getValue().trim();
        if (!text.isEmpty() && !projectileItems.contains(text)) {
            projectileItems.add(text);
            itemIdInput.setValue("");
            itemList.updateEntries();

            // Save immediately
            ProjectileEffect.setProjectileItemsList(projectileItems);
        }
    }

    private void removeItemId(String itemId) {
        projectileItems.remove(itemId);
        itemList.updateEntries();

        // Save immediately
        ProjectileEffect.setProjectileItemsList(projectileItems);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        // Render the item list after background and other widgets
        if (itemList != null) {
            itemList.render(context, mouseX, mouseY, delta);
        }

        // Title at top
        context.drawCenteredString(font, title, width / 2, 10, 0xFFFFFFFF);

        // Label for text input
        context.drawString(font, "Add Item ID:", 20, 28, 0xFFFFFFFF);

        // Label for item list
        context.drawString(font, "Custom Projectile Items:", 20, listTopY + 105, 0xFFFFFFFF);

        // Show count of items
        context.drawString(font, "(" + projectileItems.size() + " items)", 180, listTopY + 105, 0xFFAAAAAA);
    }


    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        // Handle Enter key in text field (257 = Enter, 335 = Numpad Enter)
        if (itemIdInput.isFocused() && (keyEvent.key() == 257 || keyEvent.key() == 335)) {
            addItemId();
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    // Custom list widget for displaying items in two columns
    private class ItemListWidget extends ObjectSelectionList<ItemListWidget.ItemEntry> {

        public ItemListWidget(Minecraft client, int width, int height, int y, int bottom) {
            super(client, width, bottom - y, y, 20);
        }

        public void updateEntries() {
            clearEntries();

            // Add items in pairs for two-column layout
            for (int i = 0; i < projectileItems.size(); i += 2) {
                String leftItem = projectileItems.get(i);
                String rightItem = i + 1 < projectileItems.size() ? projectileItems.get(i + 1) : null;
                addEntry(new ItemEntry(leftItem, rightItem));
            }
        }

        @Override
        public int getRowWidth() {
            return width - 60;
        }

        // Entry representing a row with two items
        public class ItemEntry extends ObjectSelectionList.Entry<ItemEntry> {
            private final String leftItem;
            private final String rightItem;
            private final Button leftRemoveButton;
            private final Button rightRemoveButton;

            public ItemEntry(String leftItem, String rightItem) {
                this.leftItem = leftItem;
                this.rightItem = rightItem;

                // Create remove buttons for each item
                this.leftRemoveButton = Button.builder(
                    Component.literal("×"),
                    btn -> removeItemId(leftItem))
                .bounds(0, 0, 16, 16)
                .tooltip(Tooltip.create(Component.literal("Remove " + leftItem)))
                .build();

                this.rightRemoveButton = rightItem != null ? Button.builder(
                    Component.literal("×"),
                    btn -> removeItemId(rightItem))
                .bounds(0, 0, 16, 16)
                .tooltip(Tooltip.create(Component.literal("Remove " + rightItem)))
                .build() : null;
            }

            @Override
            public void renderContent(GuiGraphics context, int mouseX, int mouseY, boolean isHovering, float delta) {
                // Get entry position from instance methods
                int x = this.getX();
                int y = this.getY();
                int width = this.getWidth();
                int height = this.getHeight();

                int columnWidth = width / 2;

                // Left column
                String leftDisplay = leftItem;
                int leftTextWidth = font.width(leftDisplay);
                if (leftTextWidth > columnWidth - 25) {
                    // Truncate if too long
                    leftDisplay = font.plainSubstrByWidth(leftItem, columnWidth - 30) + "...";
                }
                context.drawString(font, leftDisplay, x + 5, y + 4, 0xFFFFFFFF);

                // Position and render left remove button
                leftRemoveButton.setPosition(x + columnWidth - 20, y + 1);
                leftRemoveButton.render(context, mouseX, mouseY, delta);

                // Right column (if exists)
                if (rightItem != null) {
                    String rightDisplay = rightItem;
                    int rightTextWidth = font.width(rightItem);
                    if (rightTextWidth > columnWidth - 25) {
                        rightDisplay = font.plainSubstrByWidth(rightItem, columnWidth - 30) + "...";
                    }
                    context.drawString(font, rightDisplay, x + columnWidth + 5, y + 4, 0xFFFFFFFF);
                    rightRemoveButton.setPosition(x + width - 20, y + 1);
                    rightRemoveButton.render(context, mouseX, mouseY, delta);
                }
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
                // Forward click events to buttons
                if (leftRemoveButton.mouseClicked(event, isDoubleClick)) {
                    return true;
                }
                if (rightRemoveButton != null && rightRemoveButton.mouseClicked(event, isDoubleClick)) {
                    return true;
                }
                return false;
            }

            @Override
            public Component getNarration() {
                return Component.literal(leftItem + (rightItem != null ? " and " + rightItem : ""));
            }
        }
    }
}