package de.raindancer.modules.fishing.model;

import java.util.Comparator;

/** Where a tank block stands, in block coordinates. */
public record TankPos(int x, int y, int z) {

    /** Bottom layer first, then west to east, then north to south: the order a big tank's slots are counted in. */
    public static final Comparator<TankPos> ORDER = Comparator.comparingInt(TankPos::y)
            .thenComparingInt(TankPos::x).thenComparingInt(TankPos::z);

    public TankPos plus(int dx, int dy, int dz) {
        return new TankPos(x + dx, y + dy, z + dz);
    }
}
