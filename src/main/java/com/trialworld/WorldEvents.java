package com.trialworld;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

public final class WorldEvents {
    private static final String PLACED_KEY = "trialworld_spawn_placed";

    private WorldEvents() {}

    /**
     * Vanilla would put world spawn on top of the world, which is inside solid rock here.
     * Replace it with a spot inside a real chamber.
     */
    @SubscribeEvent
    public static void onCreateSpawn(LevelEvent.CreateSpawnPosition event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (level.dimension() != Level.OVERWORLD || !SpawnFinder.isTrialWorld(level)) {
            return;
        }
        BlockPos spawn = SpawnFinder.find(level);
        event.getSettings().setSpawn(spawn, 0.0F);
        // Stop vanilla scattering new players across a radius around spawn (that logic assumes open sky).
        level.getGameRules().getRule(GameRules.RULE_SPAWN_RADIUS).set(0, level.getServer());
        event.setCanceled(true);
        TrialWorld.LOGGER.info("[trialworld] World spawn set inside a trial chamber at {}", spawn.toShortString());
    }

    /**
     * A brand new player is first placed by vanilla's surface-spawn logic. Move them to world spawn once.
     */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getServer() == null) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        if (data.getBoolean(PLACED_KEY)) {
            return;
        }
        data.putBoolean(PLACED_KEY, true);

        ServerLevel overworld = player.getServer().overworld();
        if (!SpawnFinder.isTrialWorld(overworld)) {
            return;
        }
        BlockPos spawn = overworld.getSharedSpawnPos();
        player.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0F, 0.0F);
    }
}
