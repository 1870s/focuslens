package dev.focuslens.mc;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The few Minecraft calls whose names differ between supported versions. One copy of this class
 * exists per version overlay (src/mc26_1, src/mc26_2, src/mc26_3); exactly one is compiled.
 *
 * <p>Variant: Minecraft 26.1.x (screen and HUD state still live on Minecraft/Options).
 */
public final class McCompat {
    private McCompat() {
    }

    public static @Nullable Screen currentScreen(Minecraft minecraft) {
        return minecraft.screen;
    }

    public static void setScreen(Minecraft minecraft, @Nullable Screen screen) {
        minecraft.setScreen(screen);
    }

    public static boolean isHudHidden(Minecraft minecraft) {
        return minecraft.options.hideGui;
    }

    public static Camera mainCamera(Minecraft minecraft) {
        return minecraft.gameRenderer.getMainCamera();
    }

    public static @Nullable Vec3 cameraPosition(Minecraft minecraft) {
        Camera camera = mainCamera(minecraft);
        return camera.isInitialized() ? camera.position() : null;
    }

    public static RenderTarget mainRenderTarget(Minecraft minecraft) {
        return minecraft.getMainRenderTarget();
    }

    public static String gpuDescription() {
        try {
            GpuDevice device = RenderSystem.getDevice();
            return device.getRenderer() + " (" + device.getVendor() + ", " + device.getBackendName() + ", " + device.getVersion() + ")";
        } catch (Throwable t) {
            return "unknown";
        }
    }
}
