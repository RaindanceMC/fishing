package de.raindancer.modules.fishing.model;

import java.util.Set;

/**
 * One kind of fish: what it is called, how rare it is and where it bites.
 *
 * @param glows whether its model has lit parts, so a display of it is drawn at full brightness
 */
public record FishKind(String id, String name, Tier tier, Set<Water> waters, boolean glows) {

    public FishKind {
        waters = Set.copyOf(waters);
    }

    public boolean livesIn(Water water) {
        return waters.contains(water);
    }

    /** The item key under Core's items, {@code fishing:fish/pebble_trout}'s last part. */
    public String itemId() {
        return "fish-" + id;
    }

    /** Its model, as the pack names it. */
    public String model() {
        return "rainsfishing:fish/" + id;
    }
}
