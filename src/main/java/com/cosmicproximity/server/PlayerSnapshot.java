package com.cosmicproximity.server;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Position + dimension snapshot taken on the server tick. Used by the UDP receive
 * thread for proximity calculations without touching live world state.
 */
public record PlayerSnapshot(Vec3 position, ResourceKey<Level> dimension) {}
