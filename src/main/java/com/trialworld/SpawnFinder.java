package com.trialworld;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Finds a safe standing spot inside a generated trial chamber.
 *
 * Nothing about the chamber layout is assumed. We ask the chunk generator where the nearest
 * structure start is, read the real placed pieces back from the world, and scan their bounding
 * boxes for open floor. If someone edits the templates, pools or palette this still works.
 */
public final class SpawnFinder {
    /** Search radius handed to the generator, in placement grid cells. */
    private static final int SEARCH_RADIUS = 32;
    /** Give up on a start after this many pieces without finding a spot. */
    private static final int MAX_PIECES_TRIED = 60;
    private static final int FALLBACK_Y = -20;

    private SpawnFinder() {}

    /** True when the overworld uses only the Trial World biome (i.e. the preset was picked). */
    public static boolean isTrialWorld(ServerLevel level) {
        Set<Holder<Biome>> biomes = level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes();
        return !biomes.isEmpty() && biomes.stream().allMatch(holder -> holder.is(TrialWorld.CHAMBER_BIOME));
    }

    public static BlockPos find(ServerLevel level) {
        try {
            BlockPos found = findInsideStructure(level);
            if (found != null) {
                return found;
            }
            TrialWorld.LOGGER.warn("[trialworld] No safe spot found in a trial chamber, building a small fallback room.");
        } catch (RuntimeException e) {
            TrialWorld.LOGGER.warn("[trialworld] Spawn search failed, building a small fallback room.", e);
        }
        return buildFallbackRoom(level);
    }

    private static BlockPos findInsideStructure(ServerLevel level) {
        ResourceLocation id = ResourceLocation.tryParse(TrialWorldConfig.STRUCTURE.get());
        if (id == null) {
            return null;
        }
        Optional<Holder.Reference<Structure>> holder = level.registryAccess()
                .registryOrThrow(Registries.STRUCTURE)
                .getHolder(ResourceKey.create(Registries.STRUCTURE, id));
        if (holder.isEmpty()) {
            return null;
        }

        Pair<BlockPos, Holder<Structure>> nearest = level.getChunkSource().getGenerator()
                .findNearestMapStructure(level, HolderSet.direct(holder.get()), BlockPos.ZERO, SEARCH_RADIUS, false);
        if (nearest == null) {
            return null;
        }

        ChunkPos startChunk = new ChunkPos(nearest.getFirst());
        level.getChunk(startChunk.x, startChunk.z, ChunkStatus.FULL);

        Structure structure = holder.get().value();
        List<StructureStart> starts = new ArrayList<>(
                level.structureManager().startsForStructure(startChunk, s -> s == structure));
        starts.removeIf(start -> !start.isValid());
        starts.sort(Comparator.comparingDouble(start -> start.getBoundingBox().getCenter().distSqr(BlockPos.ZERO)));

        for (StructureStart start : starts) {
            BlockPos center = start.getBoundingBox().getCenter();
            List<StructurePiece> pieces = new ArrayList<>(start.getPieces());
            pieces.sort(Comparator.comparingDouble(piece -> piece.getBoundingBox().getCenter().distSqr(center)));

            int tried = 0;
            for (StructurePiece piece : pieces) {
                if (tried++ >= MAX_PIECES_TRIED) {
                    break;
                }
                BoundingBox box = piece.getBoundingBox();
                loadChunks(level, box);
                BlockPos spot = scan(level, box);
                if (spot != null) {
                    return spot;
                }
            }
        }
        return null;
    }

    private static void loadChunks(ServerLevel level, BoundingBox box) {
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                level.getChunk(cx, cz, ChunkStatus.FULL);
            }
        }
    }

    /** Standable position inside the box closest to the box centre, or null. */
    private static BlockPos scan(ServerLevel level, BoundingBox box) {
        BlockPos center = box.getCenter();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        int minY = Math.max(box.minY(), level.getMinBuildHeight() + 1);
        int maxY = Math.min(box.maxY(), level.getMaxBuildHeight() - 3);

        BlockPos.MutableBlockPos feet = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos head = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();

        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int y = minY; y <= maxY; y++) {
                    feet.set(x, y, z);
                    double dist = feet.distSqr(center);
                    if (dist >= bestDist) {
                        continue;
                    }
                    if (isStandable(level, feet, head, below)) {
                        best = feet.immutable();
                        bestDist = dist;
                    }
                }
            }
        }
        return best;
    }

    private static boolean isStandable(ServerLevel level, BlockPos feet, BlockPos.MutableBlockPos head,
                                       BlockPos.MutableBlockPos below) {
        if (!level.getBlockState(feet).isAir()) {
            return false;
        }
        head.set(feet).move(Direction.UP);
        if (!level.getBlockState(head).isAir()) {
            return false;
        }
        below.set(feet).move(Direction.DOWN);
        BlockState floor = level.getBlockState(below);
        // Sturdy floor, and not a spawner, vault, chest, crafter, etc.
        return floor.isFaceSturdy(level, below, Direction.UP)
                && !(floor.getBlock() instanceof EntityBlock)
                && !floor.is(Blocks.MAGMA_BLOCK);
    }

    /** Last resort: a lit 7x7 room carved out of the solid deepslate. */
    private static BlockPos buildFallbackRoom(ServerLevel level) {
        int baseY = Math.max(FALLBACK_Y, level.getMinBuildHeight() + 8);
        level.getChunk(0, 0, ChunkStatus.FULL);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                pos.set(dx, baseY - 1, dz);
                boolean corner = Math.abs(dx) == 3 && Math.abs(dz) == 3;
                level.setBlock(pos, corner ? Blocks.SEA_LANTERN.defaultBlockState()
                        : Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 3);
                for (int dy = 0; dy < 3; dy++) {
                    pos.set(dx, baseY + dy, dz);
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        return new BlockPos(0, baseY, 0);
    }
}
