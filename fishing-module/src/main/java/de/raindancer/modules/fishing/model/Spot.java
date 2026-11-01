package de.raindancer.modules.fishing.model;

import java.util.UUID;

/**
 * A fishing spot: a shoal somewhere on the water (or hanging in the void) that bites better. A wandering one comes up
 * by itself and goes when it is fished out or wanders off; a fixed one is put there by an admin, with the size and
 * best tier they chose, and stays.
 *
 * @param world       the world's id
 * @param x           the spot's centre, on the surface
 * @param best        the best sea chest that can bite here, which the hologram names
 * @param catchesLeft how many more catches before it is fished out (a fixed spot's never runs down)
 * @param endsAt      when it wanders off anyway, epoch millis (never, for a fixed one)
 * @param radius      how far around its centre a hook is in it, in blocks
 * @param fixed       an admin's, kept for good
 */
public record Spot(UUID id, UUID world, double x, double y, double z, Water water, Tier best, int catchesLeft,
                   long endsAt, double radius, boolean fixed) {

    /** How far a wandering spot reaches. */
    public static final double WANDERING_RADIUS = 4.0;
    /** How far above or below the centre a hook still counts. */
    private static final double HEIGHT = 4.0;

    /** A wandering spot. */
    public Spot(UUID id, UUID world, double x, double y, double z, Water water, Tier best, int catchesLeft,
                long endsAt) {
        this(id, world, x, y, z, water, best, catchesLeft, endsAt, WANDERING_RADIUS, false);
    }

    /** A fixed spot. */
    public static Spot fixed(UUID id, UUID world, double x, double y, double z, Water water, Tier best,
                             double radius) {
        return new Spot(id, world, x, y, z, water, best, Integer.MAX_VALUE, Long.MAX_VALUE, radius, true);
    }

    public boolean covers(UUID inWorld, double atX, double atY, double atZ) {
        return covers(inWorld, atX, atY, atZ, radius);
    }

    public boolean covers(UUID inWorld, double atX, double atY, double atZ, double reach) {
        if (!world.equals(inWorld)) {
            return false;
        }
        double dx = atX - x;
        double dz = atZ - z;
        return dx * dx + dz * dz <= reach * reach && Math.abs(atY - y) <= HEIGHT;
    }

    /** Whether the two spots' water touches: one may not come up inside the other. */
    public boolean overlaps(Spot other) {
        if (!world.equals(other.world) || Math.abs(y - other.y) > HEIGHT * 2) {
            return false;
        }
        double dx = other.x - x;
        double dz = other.z - z;
        double reach = radius + other.radius;
        return dx * dx + dz * dz < reach * reach;
    }

    public Spot fished() {
        return fixed ? this : new Spot(id, world, x, y, z, water, best, Math.max(0, catchesLeft - 1), endsAt, radius,
                false);
    }

    public boolean isOver(long now) {
        return !fixed && (catchesLeft <= 0 || now >= endsAt);
    }

    /** The shoal's name on the hologram: "Pearl Shoal". */
    public String shoalName() {
        return best.chestName().replace(" Chest", "") + " Shoal";
    }
}
