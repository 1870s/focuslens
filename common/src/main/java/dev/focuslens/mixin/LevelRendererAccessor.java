package dev.focuslens.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {
    @Accessor("viewArea")
    @Nullable ViewArea focuslens$getViewArea();

    @Accessor("sectionOcclusionGraph")
    SectionOcclusionGraph focuslens$getSectionOcclusionGraph();
}
