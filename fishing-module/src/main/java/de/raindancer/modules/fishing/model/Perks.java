package de.raindancer.modules.fishing.model;

import java.util.EnumMap;
import java.util.Map;

/** What a fisher's skill tree adds up to. */
public record Perks(Map<Perk, Double> values) {

    public static final Perks NONE = new Perks(Map.of());

    public Perks {
        values = values.isEmpty() ? Map.of() : Map.copyOf(new EnumMap<>(values));
    }

    public double get(Perk perk) {
        return values.getOrDefault(perk, 0.0);
    }

    /** A percentage as a factor on a wait: 20 is 0.8. Never below a tenth, so nothing bites at once. */
    public double sooner(Perk perk) {
        return Math.max(0.1, 1.0 - get(perk) / 100.0);
    }

    /** A percentage as a factor on a weight or an amount: 20 is 1.2. Never below nothing. */
    public double more(Perk perk) {
        return Math.max(0.0, 1.0 + get(perk) / 100.0);
    }

    public boolean has(Perk perk) {
        return get(perk) > 0;
    }
}
