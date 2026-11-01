package de.raindancer.modules.fishing.model;

import java.util.UUID;

/**
 * A fishing spot: a shoal somewhere on the water (or hanging in the void) that bites better until it is fished
 * out or wanders off.
 *
 * @param world      the world's id
 * @param x          the spot's centre, on the surface
 * @param best       the best sea chest that can bite here, which the hologram names
 * @param catchesLeft how many more catches before it is fished out
 * @param endsAt     when it wanders off anyway, epoch millis
 */
public record Spot(UUID id, UUID world, double x, double y, double z, Water water, Tier best, int catchesLeft,
                   long endsAt) {

    public boolean covers(UUID inWorld, double atX, double atY, double atZ, double radius) {
        if (!world.equals(inWorld)) {
            return false;
        }
        double dx = atX - x;
        double dz = atZ - z;
        return dx * dx + dz * dz <= radius * radius && Math.abs(atY - y) <= 4.0;
    }

    public Spot fished() {
        return new Spot(id, world, x, y, z, water, best, Math.max(0, catchesLeft - 1), endsAt);
    }

    public boolean isOver(long now) {
        return catchesLeft <= 0 || now >= endsAt;
    }

    /** The shoal's name on the hologram: "Pearl Shoal". */
    public String shoalName() {
        return best.chestName().replace(" Chest", "") + " Shoal";
    }
}
