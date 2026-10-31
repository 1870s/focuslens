package dev.focuslens.compat;

import dev.focuslens.FocusLens;
import dev.focuslens.platform.Services;

import java.util.List;
import java.util.Optional;

/**
 * Detects mods whose features overlap ours, so we can step aside instead of doing the same work twice
 * or fighting over the same code.
 *
 * <p>Two detection paths exist: {@link #isLoaded} asks the mod loader (available once mods are loaded),
 * {@link #classPresent} looks for a well-known class file without loading it (safe during mixin
 * configuration, before the loader API can be trusted on every loader).
 */
public final class ModCompat {
    private static final List<String> ZOOM_MODS = List.of(
            "zoomify", "ok_zoomer", "logical_zoom", "wi_zoom", "simplezoom", "zoomer_boomer", "spyglass_improvements");

    private ModCompat() {
    }

    public static boolean isLoaded(String modId) {
        try {
            return Services.PLATFORM.isModLoaded(modId);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean classPresent(String className) {
        String resource = className.replace('.', '/') + ".class";
        ClassLoader loader = ModCompat.class.getClassLoader();
        return loader != null && loader.getResource(resource) != null;
    }

    /** Sodium replaces terrain and block-entity rendering, and culls entities by section itself. */
    public static boolean sodium() {
        return isLoaded("sodium") || isLoaded("embeddium") || classPresent("net.caffeinemc.mods.sodium.client.SodiumClientMod");
    }

    public static boolean iris() {
        return isLoaded("iris") || isLoaded("oculus") || classPresent("net.irisshaders.iris.api.v0.IrisApi");
    }

    /** tr7zw's Entity Culling does the same ray-traced occlusion culling as ours. */
    public static boolean entityCulling() {
        return isLoaded("entityculling") || classPresent("dev.tr7zw.entityculling.EntityCullingModBase");
    }

    public static boolean immediatelyFast() {
        return isLoaded("immediatelyfast");
    }

    public static boolean optifine() {
        return isLoaded("optifabric") || classPresent("net.optifine.Config");
    }

    public static Optional<String> otherZoomMod() {
        for (String id : ZOOM_MODS) {
            if (isLoaded(id)) {
                return Optional.of(id);
            }
        }
        return Optional.empty();
    }

    public static void logDetectedMods() {
        if (sodium()) FocusLens.LOG.info("Sodium detected: entity section culling is left to Sodium, block-entity hooks are disabled.");
        if (iris()) FocusLens.LOG.info("Iris detected: occlusion culling is paused while a shader pack renders its shadow pass.");
        if (entityCulling()) FocusLens.LOG.info("Entity Culling detected: FocusLens ray-traced culling is disabled to avoid duplicate work.");
        if (immediatelyFast()) FocusLens.LOG.info("ImmediatelyFast detected: no overlapping features, both stay active.");
        if (optifine()) FocusLens.LOG.warn("OptiFine detected: FocusLens is not designed to run alongside OptiFine.");
        otherZoomMod().ifPresent(id -> FocusLens.LOG.warn("Zoom mod '{}' detected: the FocusLens zoom key starts unbound to avoid a key conflict.", id));
    }
}
