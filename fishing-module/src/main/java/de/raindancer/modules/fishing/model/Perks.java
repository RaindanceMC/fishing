package de.raindancer.modules.fishing.model;

import de.raindancer.core.ui.tree.SkillTree;

import java.util.EnumMap;
import java.util.Map;

/** What a fisher's skill tree adds up to. */
public record Perks(Map<Perk, Double> values) {

    public static final Perks NONE = new Perks(Map.of());

    public Perks {
        values = values.isEmpty() ? Map.of() : Map.copyOf(new EnumMap<>(values));
    }

    /** Effects by {@link Perk} id; ids that are no perk count for nothing. */
    public static Perks of(Map<String, Double> effects) {
        Map<Perk, Double> values = new EnumMap<>(Perk.class);
        effects.forEach((id, value) -> Perk.parse(id).ifPresent(perk -> values.merge(perk, value, Double::sum)));
        return new Perks(values);
    }

    /** What these ranks of the tree add up to. */
    public static Perks from(SkillTree tree, Map<String, Integer> ranks) {
        return of(tree.effects(ranks));
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
