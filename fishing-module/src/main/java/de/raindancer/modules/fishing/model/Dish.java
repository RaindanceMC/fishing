package de.raindancer.modules.fishing.model;

import java.util.List;

/**
 * Something cooked from the catch: what goes in, how filling it is and what eating it does.
 *
 * @param ingredients a fish id, {@code any:<tier>} for any fish of that tier, or a vanilla material key
 * @param food        hunger points it restores
 * @param saturation  saturation it adds
 */
public record Dish(String id, String name, Tier tier, List<String> ingredients, int food, float saturation,
                   List<DishEffect> effects) {

    public Dish {
        ingredients = List.copyOf(ingredients);
        effects = List.copyOf(effects);
    }

    /**
     * One effect a dish gives.
     *
     * @param effect    the potion effect's key, {@code night_vision}
     * @param amplifier 0 for level I
     * @param seconds   how long it lasts
     */
    public record DishEffect(String effect, int amplifier, int seconds) {
    }

    public String itemId() {
        return "dish-" + id;
    }

    public String model() {
        return "rainsfishing:dish/" + id;
    }

    /** Whether a bowl comes back after eating it. */
    public boolean leavesABowl() {
        return ingredients.contains("minecraft:bowl");
    }
}
