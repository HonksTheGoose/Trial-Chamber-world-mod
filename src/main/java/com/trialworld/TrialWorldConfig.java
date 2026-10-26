package com.trialworld;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Common config: config/trialworld-common.toml
 */
public final class TrialWorldConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<String> STRUCTURE = BUILDER
            .comment("Structure the player spawn search looks for. Change this only if you swap the structure",
                    "in the world preset for a different one.")
            .define("structure", "minecraft:trial_chambers");

    public static final ModConfigSpec.ConfigValue<List<? extends String>> PALETTE = BUILDER
            .comment("Optional block palette edits. EMPTY BY DEFAULT, so trial chambers use the vanilla palette.",
                    "Format: \"from_block=to_block\". Blockstate properties (facing, half, waterlogged, ...)",
                    "are copied across when the target block has them, so stairs, slabs and walls keep",
                    "their shape. Unknown ids are skipped with a warning, so removing a mod or a block",
                    "never crashes world generation. Set the list empty to disable palette edits.")
            .defineListAllowEmpty("palette_replacements", List.of(), () -> "minecraft:stone=minecraft:stone", o -> o instanceof String);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private static volatile List<? extends String> cachedRaw = null;
    private static volatile Map<Block, Block> cachedMap = Map.of();

    private TrialWorldConfig() {}

    /** Parsed palette map. Rebuilt only when the config list changes. Safe to call from worldgen threads. */
    public static Map<Block, Block> palette() {
        List<? extends String> raw = PALETTE.get();
        if (!raw.equals(cachedRaw)) {
            synchronized (TrialWorldConfig.class) {
                if (!raw.equals(cachedRaw)) {
                    cachedMap = parse(raw);
                    cachedRaw = List.copyOf(raw);
                }
            }
        }
        return cachedMap;
    }

    private static Map<Block, Block> parse(List<? extends String> raw) {
        Map<Block, Block> map = new HashMap<>();
        for (String entry : raw) {
            String[] parts = entry.split("=", 2);
            if (parts.length != 2) {
                TrialWorld.LOGGER.warn("[trialworld] Ignoring malformed palette entry '{}' (expected from=to)", entry);
                continue;
            }
            ResourceLocation fromId = ResourceLocation.tryParse(parts[0].trim());
            ResourceLocation toId = ResourceLocation.tryParse(parts[1].trim());
            if (fromId == null || toId == null) {
                TrialWorld.LOGGER.warn("[trialworld] Ignoring palette entry '{}': invalid resource location", entry);
                continue;
            }
            var from = BuiltInRegistries.BLOCK.getOptional(fromId);
            var to = BuiltInRegistries.BLOCK.getOptional(toId);
            if (from.isEmpty() || to.isEmpty()) {
                TrialWorld.LOGGER.warn("[trialworld] Skipping palette entry '{}': unknown block id", entry);
                continue;
            }
            map.put(from.get(), to.get());
        }
        return Map.copyOf(map);
    }
}
