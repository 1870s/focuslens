package dev.focuslens.mixin;

import dev.focuslens.culling.Cullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Stores the occlusion result written by the culling thread on the object itself. */
@Mixin({Entity.class, BlockEntity.class})
public abstract class CullableMixin implements Cullable {
    @Unique
    private volatile long focuslens$occludedPass = -1L;

    @Override
    public long focuslens$getOccludedPass() {
        return this.focuslens$occludedPass;
    }

    @Override
    public void focuslens$setOccludedPass(long pass) {
        this.focuslens$occludedPass = pass;
    }
}
