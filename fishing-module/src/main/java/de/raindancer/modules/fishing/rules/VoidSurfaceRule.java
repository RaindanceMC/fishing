package de.raindancer.modules.fishing.rules;

import java.util.OptionalDouble;

/**
 * The End's void is fished like water: it has a surface at y 47. A hook over nothing but void comes to rest there —
 * also when it fell a little past it between two looks — and floats; above it, it is still falling.
 */
public final class VoidSurfaceRule implements IFishingRule {

    /** Where the void's surface is, as a float rests on it. */
    public static final double SURFACE = 47.0;
    /** How close above the surface counts as on it. */
    private static final double ON_IT = 0.75;

    /** @return where the hook rests; empty while it is still falling, or when this is no void to fish */
    public OptionalDouble restAt(boolean inTheEnd, boolean voidBelow, double hookY) {
        if (!inTheEnd || !voidBelow || hookY > SURFACE + ON_IT) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(SURFACE);
    }

    @Override
    public String describe() {
        return "where a hook rests over the End's void: on its surface at y 47";
    }
}
