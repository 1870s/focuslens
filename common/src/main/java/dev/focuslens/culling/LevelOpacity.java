package dev.focuslens.culling;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Reads opacity straight from the client's loaded chunks. Used only by the culling thread.
 *
 * <p>Reading block states off the main thread is a benign data race here: chunk storage is published
 * through atomic references and a paletted container's data is replaced as one object, so a read sees
 * either the old or the new block, never a torn one. A stale answer for one pass is acceptable because
 * the result is recomputed every frame. Missing chunks count as transparent (conservative).
 */
final class LevelOpacity implements OpacityGrid {
    private final ClientLevel level;
    private final int minY;
    private final int maxY;
    private long cachedSectionKey = Long.MIN_VALUE;
    private LevelChunkSection cachedSection;

    LevelOpacity(ClientLevel level) {
        this.level = level;
        this.minY = level.getMinY();
        this.maxY = level.getMaxY();
    }

    @Override
    public boolean isOpaque(int x, int y, int z) {
        if (y < minY || y > maxY) {
            return false;
        }
        int sectionX = x >> 4;
        int sectionY = y >> 4;
        int sectionZ = z >> 4;
        long key = SectionPos.asLong(sectionX, sectionY, sectionZ);
        if (key != cachedSectionKey) {
            cachedSectionKey = key;
            cachedSection = null;
            LevelChunk chunk = level.getChunkSource().getChunk(sectionX, sectionZ, ChunkStatus.FULL, false);
            if (chunk != null) {
                int index = chunk.getSectionIndexFromSectionY(sectionY);
                LevelChunkSection[] sections = chunk.getSections();
                if (index >= 0 && index < sections.length) {
                    cachedSection = sections[index];
                }
            }
        }
        LevelChunkSection section = cachedSection;
        if (section == null || section.hasOnlyAir()) {
            return false;
        }
        return section.getBlockState(x & 15, y & 15, z & 15).isSolidRender();
    }
}
