package win.demistorm.client;

import org.vivecraft.api.client.VRClientAPI;

// Router over VR backends
public final class VRAbstraction {

    private VRAbstraction() {}

    public static boolean isVRAvailable() {
        return VivecraftGate.isVivecraftPresent();
    }

    public static boolean isVRActive() {
        return VivecraftGate.isVivecraftPresent() && Impl.isVRActive();
    }

    public static void registerThrowTracker() {
        if (VivecraftGate.isVivecraftPresent()) {
            Impl.registerThrowTracker();
        }
    }

    // Only loaded when Vivecraft is around
    private static final class Impl {

        static boolean isVRActive() {
            return VRClientAPI.instance().isVRActive();
        }

        static void registerThrowTracker() {
            VRClientAPI.instance().addClientRegistrationHandler(event ->
                    event.registerTrackers(new ThrowHelper.ThrowTracker()));
        }
    }
}
