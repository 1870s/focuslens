package dev.focuslens;

import dev.focuslens.compat.ModCompat;
import dev.focuslens.config.ConfigManager;
import dev.focuslens.config.FocusLensConfig;
import dev.focuslens.culling.OcclusionCuller;
import dev.focuslens.platform.Services;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared entry point. Each loader module calls {@link #init()} once from its client entrypoint.
 */
public final class FocusLens {
    public static final String MOD_ID = "focuslens";
    public static final String MOD_NAME = "FocusLens";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);

    private static boolean initialized;

    private FocusLens() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        ConfigManager.load();
        ModCompat.logDetectedMods();
        OcclusionCuller.INSTANCE.start();
        LOG.info("{} initialized on {} (Minecraft {})", MOD_NAME, Services.PLATFORM.loaderName(), Services.PLATFORM.minecraftVersion());
    }

    public static FocusLensConfig config() {
        return ConfigManager.get();
    }
}
