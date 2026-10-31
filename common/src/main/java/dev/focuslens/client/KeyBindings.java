package dev.focuslens.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.focuslens.FocusLens;
import dev.focuslens.compat.ModCompat;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Key mappings. Created by the loader module (which also registers them), because Fabric and NeoForge
 * register key categories differently.
 */
public final class KeyBindings {
    public static final Identifier CATEGORY_ID = Identifier.fromNamespaceAndPath(FocusLens.MOD_ID, "main");

    public static KeyMapping ZOOM;
    public static KeyMapping OPEN_SETTINGS;
    public static KeyMapping RUN_BENCHMARK;

    private KeyBindings() {
    }

    public static List<KeyMapping> create(KeyMapping.Category category) {
        // OptiFine binds zoom to C. If another zoom mod is installed it almost certainly uses C as well,
        // so ours starts unbound to avoid both zooms firing at once. Users can still bind it.
        int zoomKey = ModCompat.otherZoomMod().isPresent() ? InputConstants.UNKNOWN.getValue() : InputConstants.KEY_C;
        ZOOM = new KeyMapping("key.focuslens.zoom", zoomKey, category);
        OPEN_SETTINGS = new KeyMapping("key.focuslens.open_settings", InputConstants.UNKNOWN.getValue(), category);
        RUN_BENCHMARK = new KeyMapping("key.focuslens.benchmark", InputConstants.UNKNOWN.getValue(), category);
        return List.of(ZOOM, OPEN_SETTINGS, RUN_BENCHMARK);
    }
}
