package dev.focuslens.culling;

/**
 * Added to entities and block entities by mixin. Holds the id of the last culling pass that found the
 * object occluded, or -1 when the last result was "visible".
 */
public interface Cullable {
    long focuslens$getOccludedPass();

    void focuslens$setOccludedPass(long pass);
}
