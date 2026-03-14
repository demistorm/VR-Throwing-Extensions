package win.demistorm.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

// Custom key mappings
public class RemapBindings {

    public static final String CATEGORY = "key.categories.vr_throwing_extensions";

    // Throw/catch (main hand)
    public static final KeyMapping THROW = new KeyMapping(
            "key.vr_throwing_extensions.throw",
            -1,  // Uses vanilla left click unless rebound
            CATEGORY
    );

    // Throw stack modifier
    public static final KeyMapping THROW_STACK = new KeyMapping(
            "key.vr_throwing_extensions.throw_stack",
            -1,  // Uses vanilla right click unless rebound
            CATEGORY
    );

    // Offhand throw/catch
    public static final KeyMapping THROW_OFFHAND = new KeyMapping(
        "key.vr_throwing_extensions.throw_offhand",
            -1,
            CATEGORY
    );

    // Returns custom binding if rebound, otherwise default attack key
    public static boolean isThrowPressed() {
        return !THROW.isDefault() ? THROW.isDown()
                : Minecraft.getInstance().options.keyAttack.isDown();
    }

    // Returns custom binding if rebound, otherwise default use key
    public static boolean isThrowStackPressed() {
        return !THROW_STACK.isDefault() ? THROW_STACK.isDown()
                : Minecraft.getInstance().options.keyUse.isDown();
    }
}
