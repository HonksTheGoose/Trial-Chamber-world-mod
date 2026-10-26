package com.trialworld;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.Map;

/**
 * Runs once per chunk in the last decoration step, which is after structure pieces
 * have been placed into that chunk. It swaps blocks according to the config palette.
 *
 * It works purely on block ids, never on structure templates, so it keeps working if
 * the chamber's pools, templates or even the structure itself are changed.
 */
public class PaletteSwapFeature extends Feature<NoneFeatureConfiguration> {

    public PaletteSwapFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        Map<Block, Block> palette = TrialWorldConfig.palette();
        if (palette.isEmpty()) {
            return false;
        }

        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        int chunkX = origin.getX() >> 4;
        int chunkZ = origin.getZ() >> 4;
        int x0 = chunkX << 4;
        int z0 = chunkZ << 4;

        ChunkAccess chunk = level.getChunk(chunkX, chunkZ);
        LevelChunkSection[] sections = chunk.getSections();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean changed = false;

        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            // Cheap skip: most sections of this world are pure deepslate.
            if (section.hasOnlyAir() || !section.maybeHas(state -> palette.containsKey(state.getBlock()))) {
                continue;
            }
            int baseY = chunk.getMinBuildHeight() + (i << 4);
            for (int dy = 0; dy < 16; dy++) {
                for (int dz = 0; dz < 16; dz++) {
                    for (int dx = 0; dx < 16; dx++) {
                        BlockState state = section.getBlockState(dx, dy, dz);
                        Block target = palette.get(state.getBlock());
                        if (target != null) {
                            pos.set(x0 + dx, baseY + dy, z0 + dz);
                            level.setBlock(pos, convert(state, target), 2);
                            changed = true;
                        }
                    }
                }
            }
        }
        return changed;
    }

    /** Target block's default state, with every property the two blocks share copied over. */
    private static BlockState convert(BlockState from, Block target) {
        BlockState result = target.defaultBlockState();
        for (Property<?> property : from.getProperties()) {
            result = copyProperty(from, result, property);
        }
        return result;
    }

    private static <T extends Comparable<T>> BlockState copyProperty(BlockState from, BlockState to, Property<T> property) {
        return to.hasProperty(property) ? to.setValue(property, from.getValue(property)) : to;
    }
}
