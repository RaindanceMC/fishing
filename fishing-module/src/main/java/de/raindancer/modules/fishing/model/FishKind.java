package de.raindancer.modules.fishing.model;

import java.util.Set;

/**
 * One kind of fish: what it is called, how rare it is and where it bites.
 *
 * @param glows  whether its model has lit parts, so a display of it is drawn at full brightness
 * @param biomes where in its waters it bites — the nether's biomes by name ({@code crimson_forest}), and
 *               {@code overworld} for anywhere outside the nether; empty for everywhere its waters are
 */
public record FishKind(String id, String name, Tier tier, Set<Water> waters, boolean glows, Set<String> biomes) {

    /** The nether's own biomes; lava anywhere else is the overworld's. */
    public static final Set<String> NETHER = Set.of("nether_wastes", "crimson_forest", "warped_forest",
            "soul_sand_valley", "basalt_deltas");

    public FishKind {
        waters = Set.copyOf(waters);
        biomes = biomes == null ? Set.of() : Set.copyOf(biomes);
    }

    public FishKind(String id, String name, Tier tier, Set<Water> waters, boolean glows) {
        this(id, name, tier, waters, glows, Set.of());
    }

    public boolean livesIn(Water water) {
        return waters.contains(water);
    }

    /** Whether it bites in this water in this biome ({@code minecraft:crimson_forest}); a null biome is anywhere. */
    public boolean bitesIn(Water water, String biome) {
        if (!livesIn(water)) {
            return false;
        }
        if (biomes.isEmpty() || biome == null) {
            return true;
        }
        String name = biome.substring(biome.indexOf(':') + 1).toLowerCase(java.util.Locale.ROOT);
        return biomes.contains(NETHER.contains(name) ? name : "overworld");
    }

    /** Where it bites, for people: "Lava (Crimson Forest, Warped Forest)", "River, Ocean". */
    public String where() {
        String in = waters.stream().sorted().map(Water::title).collect(java.util.stream.Collectors.joining(", "));
        if (biomes.isEmpty()) {
            return in;
        }
        return in + " (" + biomes.stream().sorted().map(FishKind::biomeTitle)
                .collect(java.util.stream.Collectors.joining(", ")) + ")";
    }

    private static String biomeTitle(String biome) {
        StringBuilder out = new StringBuilder();
        for (String word : biome.split("_")) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
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
