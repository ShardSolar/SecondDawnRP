package net.shard.seconddawnrp.dimension;

import org.jetbrains.annotations.Nullable;

/**
 * Immutable data loaded from data/seconddawnrp/dimensions/[id].json
 *
 * JSON format:
 * {
 *   "dimensionId": "colony_alpha",
 *   "displayName": "Colony Alpha",
 *   "description": "A temperate Class-M colony planet.",
 *   "defaultEntryX": 0.5,
 *   "defaultEntryY": 64.0,
 *   "defaultEntryZ": 0.5,
 *   "taskPoolIsolated": true,
 *   "proximityRequired": true,
 *   "orbitalZone": {
 *     "centerX": 500,
 *     "centerZ": -200,
 *     "radius": 50,
 *     "minimumWarpSpeed": 2
 *   }
 * }
 *
 * orbitalZone is optional — dimensions without it are always accessible
 * regardless of ship position (e.g. Space dimension, local installations).
 *
 * proximityRequired: when true, LocationService.isReachable() enforces the
 * orbitalZone check. When false (or when orbitalZone is null), the dimension
 * is reachable whenever GM-activated.
 */
public record LocationDefinition(
        String dimensionId,
        String displayName,
        String description,
        double defaultEntryX,
        double defaultEntryY,
        double defaultEntryZ,
        boolean taskPoolIsolated,
        boolean proximityRequired,
        @Nullable OrbitalZone orbitalZone
) {
    /** The Minecraft dimension key, e.g. "seconddawnrp:colony_alpha" */
    public String dimensionKey() {
        return "seconddawnrp:" + dimensionId;
    }

    /** True if this dimension has an orbital zone AND proximity is required. */
    public boolean hasOrbitalZone() {
        return orbitalZone != null && proximityRequired;
    }
}