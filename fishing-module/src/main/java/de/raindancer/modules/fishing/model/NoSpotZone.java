package de.raindancer.modules.fishing.model;

import java.util.Locale;
import java.util.UUID;

/**
 * Ground where no fishing spot comes up on its own: a rectangle of block columns in one world, the full height.
 * An admin's own spots ({@code /fishing spot}) still go anywhere.
 */
public record NoSpotZone(String name, UUID world, int minX, int minZ, int maxX, int maxZ) {

    /** Corners in any order. */
    public static NoSpotZone of(String name, UUID world, int x1, int z1, int x2, int z2) {
        return new NoSpotZone(name.strip(), world, Math.min(x1, x2), Math.min(z1, z2), Math.max(x1, x2),
                Math.max(z1, z2));
    }

    /** A square {@code radius} blocks each way around a point. */
    public static NoSpotZone around(String name, UUID world, double x, double z, int radius) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        return of(name, world, bx - radius, bz - radius, bx + radius, bz + radius);
    }

    /** What it is known by: its name, any case. */
    public String key() {
        return name.toLowerCase(Locale.ROOT);
    }

    public boolean contains(UUID inWorld, double x, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        return world.equals(inWorld) && bx >= minX && bx <= maxX && bz >= minZ && bz <= maxZ;
    }
}
