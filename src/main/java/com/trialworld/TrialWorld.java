package com.trialworld;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

/**
 * Trial World: a world preset in which the whole overworld is solid deepslate
 * with trial chambers generated densely throughout it.
 *
 * Almost everything is data driven (see src/main/resources/data/trialworld):
 *  - the world preset, biome, dense structure placement and extra mob spawns are plain JSON
 *  - the palette edits are a config list of "from=to" block ids
 *  - the player spawn is found at runtime by asking the world where a chamber actually is,
 *    so no coordinates or template names are hardcoded
 */
@Mod(TrialWorld.MOD_ID)
public class TrialWorld {
    public static final String MOD_ID = "trialworld";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** The single biome used by the Trial Chamber World preset. */
    public static final ResourceLocation CHAMBER_BIOME = ResourceLocation.fromNamespaceAndPath(MOD_ID, "chamber");

    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, MOD_ID);

    public static final DeferredHolder<Feature<?>, PaletteSwapFeature> PALETTE_SWAP =
            FEATURES.register("palette_swap", () -> new PaletteSwapFeature(NoneFeatureConfiguration.CODEC));

    public TrialWorld(IEventBus modBus, ModContainer container) {
        FEATURES.register(modBus);
        container.registerConfig(ModConfig.Type.COMMON, TrialWorldConfig.SPEC);
        NeoForge.EVENT_BUS.register(WorldEvents.class);
    }
}
