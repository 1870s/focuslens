package dev.focuslens.platform;

import java.nio.file.Path;

/**
 * The only loader-specific services the shared code needs. Implemented once per loader module and
 * located through {@link java.util.ServiceLoader} (see META-INF/services in each loader module).
 */
public interface Platform {
    String loaderName();

    String minecraftVersion();

    boolean isModLoaded(String modId);

    Path configDir();

    Path gameDir();

    /**
     * NeoForge tests every block entity against the view frustum itself (using the renderer's
     * render bounding box); vanilla and Fabric only test whole 16x16x16 sections.
     */
    boolean loaderCullsBlockEntitiesByFrustum();
}
