package de.raindancer.modules.fishing.rules;

import de.raindancer.modules.fishing.model.Water;

import java.util.Locale;
import java.util.Optional;

/** Which water a hook is in, from the biome and what is around it — a fact about a place, read without a server. */
public final class WaterRule implements IFishingRule {

    public enum Dimension { OVERWORLD, NETHER, END, OTHER }

    /**
     * Where a hook is.
     *
     * @param biome     the biome's key, {@code minecraft:warm_ocean}
     * @param lava      the hook is in lava
     * @param voidBelow nothing but the void underneath it
     * @param sky       the sky can be seen from there; water without it is cave water
     */
    public record Place(String biome, Dimension dimension, boolean lava, boolean voidBelow, boolean sky) {
    }

    public Optional<Water> classify(Place place) {
        if (place.lava()) {
            return Optional.of(Water.LAVA);
        }
        if (place.voidBelow()) {
            return place.dimension() == Dimension.END ? Optional.of(Water.VOID) : Optional.empty();
        }
        String biome = place.biome() == null ? "" : place.biome().toLowerCase(Locale.ROOT);
        int colon = biome.indexOf(':');
        String name = colon < 0 ? biome : biome.substring(colon + 1);
        if (name.contains("cave") || name.equals("deep_dark") || !place.sky()) {
            return Optional.of(Water.CAVE);
        }
        if (name.contains("swamp") || name.contains("mangrove")) {
            return Optional.of(Water.SWAMP);
        }
        if (name.contains("frozen") || name.contains("snowy") || name.startsWith("ice") || name.equals("grove")
                || name.contains("jagged") || name.contains("frost")) {
            return Optional.of(Water.FROZEN);
        }
        if (name.contains("warm_ocean") || name.contains("lukewarm")) {
            return Optional.of(Water.WARM_OCEAN);
        }
        if (name.contains("ocean") || name.contains("beach") || name.contains("shore")) {
            return Optional.of(Water.OCEAN);
        }
        return Optional.of(Water.RIVER);
    }

    @Override
    public String describe() {
        return "which water a hook is in, from its biome, lava, the void and the sky";
    }
}
