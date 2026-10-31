package dev.focuslens.compat;

import dev.focuslens.FocusLens;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Talks to Iris' public API (net.irisshaders.iris.api.v0.IrisApi) through method handles, so there is
 * no compile-time dependency and nothing breaks when Iris is absent.
 *
 * <p>Occlusion results are computed from the player's camera. A shader pack's shadow pass renders the
 * scene from the sun, where an entity hidden from the player can still cast a visible shadow, so
 * culling must be paused during that pass.
 */
public final class IrisCompat {
    private static final MethodHandle IS_SHADOW_PASS;
    private static final MethodHandle IS_SHADER_PACK_IN_USE;
    private static final Object API;

    static {
        MethodHandle shadow = null;
        MethodHandle inUse = null;
        Object api = null;
        if (ModCompat.iris()) {
            try {
                Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                api = lookup.findStatic(apiClass, "getInstance", MethodType.methodType(apiClass)).invoke();
                shadow = lookup.findVirtual(apiClass, "isRenderingShadowPass", MethodType.methodType(boolean.class));
                inUse = lookup.findVirtual(apiClass, "isShaderPackInUse", MethodType.methodType(boolean.class));
            } catch (Throwable t) {
                FocusLens.LOG.warn("Iris is installed but its API could not be accessed; culling stays active in shadow passes", t);
                shadow = null;
                inUse = null;
                api = null;
            }
        }
        IS_SHADOW_PASS = shadow;
        IS_SHADER_PACK_IN_USE = inUse;
        API = api;
    }

    private IrisCompat() {
    }

    public static boolean isRenderingShadowPass() {
        if (IS_SHADOW_PASS == null) return false;
        try {
            return (boolean) IS_SHADOW_PASS.invoke(API);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isShaderPackInUse() {
        if (IS_SHADER_PACK_IN_USE == null) return false;
        try {
            return (boolean) IS_SHADER_PACK_IN_USE.invoke(API);
        } catch (Throwable t) {
            return false;
        }
    }
}
