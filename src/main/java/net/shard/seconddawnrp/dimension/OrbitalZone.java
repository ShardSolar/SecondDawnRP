package net.shard.seconddawnrp.dimension;

/**
 * Optional orbital zone configuration attached to a LocationDefinition.
 *
 * When present, the dimension is only reachable when the home ship is within
 * radius of centerX/centerZ AND warpSpeed >= minimumWarpSpeed.
 *
 * When absent, the dimension is always accessible regardless of ship position
 * (e.g. the Space dimension, internal ship locations).
 *
 * JSON format (inside LocationDefinition JSON):
 * "orbitalZone": {
 *   "centerX": 500,
 *   "centerZ": -200,
 *   "radius": 50,
 *   "minimumWarpSpeed": 2
 * }
 */
public record OrbitalZone(
        double centerX,
        double centerZ,
        double radius,
        int    minimumWarpSpeed
) {
    /**
     * Returns the Euclidean distance from the given ship position to this zone's center.
     */
    public double distanceTo(double shipX, double shipZ) {
        double dx = shipX - centerX;
        double dz = shipZ - centerZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Returns true if the ship is within proximity range of this zone,
     * ignoring warp speed requirement.
     */
    public boolean inRange(double shipX, double shipZ) {
        return distanceTo(shipX, shipZ) <= radius;
    }
}