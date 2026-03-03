package win.demistorm.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

// Custom key mappings
public class RemapBindings {

    public static final String CATEGORY = "key.categories.vr_throwing_extensions";

    // Throw/catch (main hand)
    public static final KeyMapping THROW = new KeyMapping(
            "key.vr_throwing_extensions.throw",
            InputConstants.Type.MOUSE,
            InputConstants.MOUSE_BUTTON_LEFT,
            CATEGORY
    );

    public static final KeyMapping THROW_STACK = new KeyMapping(
            "key.vr_throwing_extensions.throw_stack",
            InputConstants.Type.MOUSE,
            InputConstants.MOUSE_BUTTON_RIGHT,
            CATEGORY
    );

    // Offhand throw/catch
    public static final KeyMapping THROW_OFFHAND = new KeyMapping(
            "key.vr_throwing_extensions.throw_offhand",
            -1,
            CATEGORY
    );
}

