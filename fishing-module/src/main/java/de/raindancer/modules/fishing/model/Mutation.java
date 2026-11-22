package de.raindancer.modules.fishing.model;

import java.util.Locale;
import java.util.Optional;

/**
 * A rare variant of any fish: its own colours, its own model, worth a multiple of the plain one. Listed commonest
 * first.
 *
 * @param chance the chance per fish caught, before luck
 */
public enum Mutation {
    ALBINO("Albino", "#f4eef0", 3, 1.0 / 250),
    MELANISTIC("Melanistic", "#7a7a90", 3, 1.0 / 250),
    GOLDEN("Golden", "#ffc83a", 10, 1.0 / 1000),
    PRISMATIC("Prismatic", "#c77dff", 25, 1.0 / 3000);

    private final String title;
    private final String hex;
    private final int priceFactor;
    private final double chance;

    Mutation(String title, String hex, int priceFactor, double chance) {
        this.title = title;
        this.hex = hex;
        this.priceFactor = priceFactor;
        this.chance = chance;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String title() {
        return title;
    }

    public String hex() {
        return hex;
    }

    public int priceFactor() {
        return priceFactor;
    }

    public double chance() {
        return chance;
    }

    /** Golden and prismatic are rare enough that the whole server hears of one. */
    public boolean announced() {
        return this == GOLDEN || this == PRISMATIC;
    }

    /** The item model of this kind in this variant, as the pack names it. */
    public String model(FishKind kind) {
        return kind.model() + "_" + id();
    }

    public static Optional<Mutation> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        for (Mutation mutation : values()) {
            if (mutation.id().equalsIgnoreCase(text.trim())) {
                return Optional.of(mutation);
            }
        }
        return Optional.empty();
    }
}
